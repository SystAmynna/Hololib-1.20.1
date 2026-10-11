package com.amynna.hololib.client.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceBodyTextureCache;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTextureNetwork;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Data;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Missing;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Result;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTextureStore;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cote client du canal des textures Space (T45, D27). Fil client (= fil de rendu).
 * <p>
 * <b>Distribution</b> : installe au setup client le demandeur de {@link SpaceBodyTextureCache} : un corps
 * {@code custom:<sha1>} dont l'image n'est pas dans le cache declenche {@link #request} (au plus une demande par
 * empreinte, redemandee apres {@link #RETRY_MS} si le serveur ne l'avait pas). Les morceaux recus ({@link #onData})
 * sont reassembles dans l'ordre ; l'image complete est verifiee (empreinte) et decodee hors du fil de rendu
 * ({@code NativeImage}, RGBA), puis enregistree dans le cache ({@code registerCustom}). En attendant, le cache dessine
 * la texture de repli ({@code auto}). Tout est oublie et les images importees liberees a la deconnexion
 * ({@code ClientPlayerNetworkEvent.LoggingOut} : changement de monde ou de serveur).
 * <p>
 * <b>Import</b> (transport seulement ; choix du fichier et preparation : {@code SpaceTextureImport} du Configurator) :
 * {@link #upload} envoie {@code Begin}, attend {@code ACCEPT} puis envoie {@link SpaceTextureNetwork#CHUNKS_PER_TICK}
 * morceaux par tick, et rend {@code DONE} ou le motif d'un refus a son {@link UploadListener}. Au succes, l'image
 * envoyee est aussi enregistree dans le cache local (rien a redemander). Sans reponse pendant
 * {@link #RESULT_TIMEOUT_TICKS} ticks (hors pause) : echec.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class SpaceTextureClient
{
    /** Delai avant de redemander une texture que le serveur n'avait pas (ms). */
    static final long RETRY_MS = 60_000L;
    /** Attente maximale d'une reponse du serveur pendant un import (ticks client hors pause). */
    static final int RESULT_TIMEOUT_TICKS = 600;
    /** Taille maximale d'une image decodee acceptee du serveur (pixels de cote). */
    private static final int MAX_DECODED_SIDE = 4096;

    /** Fin d'un import (fil client). */
    public interface UploadListener
    {
        /** Texture stockee par le serveur (ou deja presente). */
        void done(String sha1);

        /** Import refuse ou interrompu ; {@code message} affichable. */
        void failed(String message);
    }

    /** Derniere demande (ou dernier refus) par empreinte, en ms ({@code Util.getMillis}). */
    private static final Map<String, Long> requested = new HashMap<>();
    /** Textures en cours de reception. */
    private static final Map<String, Incoming> incoming = new HashMap<>();
    /** Incremente a chaque deconnexion : les decodages d'une ancienne partie sont ignores. */
    private static int generation;
    private static Upload upload;

    private SpaceTextureClient() {}

    /** Texture en cours de reception. */
    private static final class Incoming
    {
        final byte[] data;
        int received;

        Incoming(int total)
        {
            data = new byte[total];
        }
    }

    /** Import en cours. */
    private static final class Upload
    {
        final String sha1;
        final byte[] data;
        final UploadListener listener;
        boolean accepted;
        int sent;
        int waitTicks;

        Upload(String sha1, byte[] data, UploadListener listener)
        {
            this.sha1 = sha1;
            this.data = data;
            this.listener = listener;
        }
    }

    /** Branche le demandeur du cache des textures d'astres (setup client). */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> SpaceBodyTextureCache.instance().setCustomRequester(SpaceTextureClient::request));
        }
    }

    // ---------------------------------------------------------------- distribution

    /**
     * Demande au serveur la texture importee {@code sha1} si elle n'est ni en reception ni demandee recemment. Appele
     * par le cache a chaque frame tant que l'image manque : sans allocation hors de l'envoi.
     */
    public static void request(String sha1)
    {
        if (incoming.containsKey(sha1)) return;
        long now = Util.getMillis();
        Long last = requested.get(sha1);
        if (last != null && now - last < RETRY_MS) return;
        if (Minecraft.getInstance().getConnection() == null) return;
        requested.put(sha1, now);
        SpaceTextureNetwork.sendToServer(new SpaceTexturePackets.Request(sha1));
    }

    /** Morceau d'une texture demandee : reassemblage dans l'ordre ; incoherence = reception abandonnee. */
    public static void onData(Data packet)
    {
        String sha1 = packet.sha1();
        Incoming entry = incoming.get(sha1);
        if (entry == null) {
            if (packet.offset() != 0 || packet.total() <= 0 || packet.total() > SpaceTextureNetwork.MAX_BYTES) return;
            entry = new Incoming(packet.total());
            incoming.put(sha1, entry);
        }
        byte[] bytes = packet.bytes();
        if (packet.total() != entry.data.length || packet.offset() != entry.received
                || bytes.length > entry.data.length - entry.received) {
            incoming.remove(sha1);
            requested.put(sha1, Util.getMillis());
            HoloLib.LOGGER.warn("Texture Space {} : reception incoherente, abandonnee", sha1);
            return;
        }
        System.arraycopy(bytes, 0, entry.data, entry.received, bytes.length);
        entry.received += bytes.length;
        if (entry.received < entry.data.length) return;
        incoming.remove(sha1);
        register(sha1, entry.data);
    }

    /** Texture absente du serveur : redemandee au plus tot dans {@link #RETRY_MS} (repli {@code auto} d'ici la). */
    public static void onMissing(Missing packet)
    {
        requested.put(packet.sha1(), Util.getMillis());
        incoming.remove(packet.sha1());
    }

    /**
     * Verifie l'empreinte et decode le PNG hors du fil de rendu, puis l'enregistre dans le cache (fil client) ; ignore
     * si la partie a change entre-temps ou si l'image est deja enregistree.
     */
    private static void register(String sha1, byte[] data)
    {
        int session = generation;
        CompletableFuture.supplyAsync(() -> decode(sha1, data), Util.backgroundExecutor())
                .whenComplete((image, exception) -> Minecraft.getInstance().execute(() -> {
                    if (image == null) {
                        if (exception != null) HoloLib.LOGGER.warn("Texture Space {} illisible", sha1, exception);
                        return;
                    }
                    SpaceBodyTextureCache cache = SpaceBodyTextureCache.instance();
                    if (session != generation || cache.hasCustom(sha1)) {
                        image.close();
                        return;
                    }
                    cache.registerCustom(sha1, image);
                }));
    }

    /** PNG -> image RGBA ; {@code null} si l'empreinte ne correspond pas ou si l'image est hors bornes. */
    private static NativeImage decode(String sha1, byte[] data)
    {
        if (!SpaceTextureStore.sha1Hex(data).equals(sha1)) {
            HoloLib.LOGGER.warn("Texture Space {} : empreinte differente, ignoree", sha1);
            return null;
        }
        try {
            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(data));
            if (image.getWidth() > MAX_DECODED_SIDE || image.getHeight() > MAX_DECODED_SIDE) {
                image.close();
                return null;
            }
            return image;
        } catch (IOException e) {
            HoloLib.LOGGER.warn("Texture Space {} illisible", sha1, e);
            return null;
        }
    }

    // ---------------------------------------------------------------- import (transport)

    /**
     * Lance l'envoi du PNG {@code data} d'empreinte {@code sha1} (deja prepare : 2:1, au plus 1024 x 512, 2 Mio) ;
     * faux si un envoi est deja en cours ou hors partie.
     */
    public static boolean upload(String sha1, byte[] data, UploadListener listener)
    {
        if (upload != null || Minecraft.getInstance().getConnection() == null) return false;
        upload = new Upload(sha1, data, listener);
        SpaceTextureNetwork.sendToServer(new SpaceTexturePackets.Begin(sha1, data.length));
        return true;
    }

    /** Numero de la partie (incremente a chaque deconnexion) : un travail lance avant est a ignorer s'il change. */
    public static int session() { return generation; }

    /** Vrai si un envoi est en cours. */
    public static boolean uploading() { return upload != null; }

    /** Avancement de l'envoi en cours, 0 a 100 (100 : tout est envoye, validation du serveur attendue). */
    public static int uploadProgress()
    {
        Upload current = upload;
        return current == null || current.data.length == 0 ? 0 : (int) (100L * current.sent / current.data.length);
    }

    /** Reponse du serveur a l'envoi en cours. */
    public static void onResult(Result packet)
    {
        Upload current = upload;
        if (current == null || !current.sha1.equals(packet.sha1())) return;
        current.waitTicks = 0;
        switch (packet.status()) {
            case SpaceTexturePackets.ACCEPT -> current.accepted = true;
            case SpaceTexturePackets.DONE -> {
                upload = null;
                // Image deja en main : pas de demande au serveur pendant son decodage.
                requested.put(current.sha1, Util.getMillis());
                if (!SpaceBodyTextureCache.instance().hasCustom(current.sha1)) register(current.sha1, current.data);
                current.listener.done(current.sha1);
            }
            default -> {
                upload = null;
                String message = packet.message();
                current.listener.failed(message == null || message.isEmpty() ? "Import refuse" : message);
            }
        }
    }

    /** Fin de chaque tick client : morceaux de l'envoi en cours, delai de reponse. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Upload current = upload;
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            upload = null;
            current.listener.failed("Import interrompu (deconnexion)");
            return;
        }
        if (current.accepted && current.sent < current.data.length) {
            for (int i = 0; i < SpaceTextureNetwork.CHUNKS_PER_TICK && current.sent < current.data.length; i++) {
                int length = Math.min(SpaceTextureNetwork.CHUNK_SIZE, current.data.length - current.sent);
                byte[] bytes = Arrays.copyOfRange(current.data, current.sent, current.sent + length);
                SpaceTextureNetwork.sendToServer(new SpaceTexturePackets.Chunk(current.sent, bytes));
                current.sent += length;
            }
            current.waitTicks = 0;
            return;
        }
        if (!mc.isPaused() && ++current.waitTicks > RESULT_TIMEOUT_TICKS) {
            upload = null;
            current.listener.failed("Import interrompu (pas de reponse du serveur)");
        }
    }

    /** Deconnexion (changement de monde ou de serveur) : tout est oublie, images importees liberees. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        generation++;
        requested.clear();
        incoming.clear();
        Upload current = upload;
        upload = null;
        if (current != null) current.listener.failed("Import interrompu (deconnexion)");
        SpaceBodyTextureCache.instance().releaseAllCustom();
    }
}
