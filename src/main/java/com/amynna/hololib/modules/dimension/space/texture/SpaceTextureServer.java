package com.amynna.hololib.modules.dimension.space.texture;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Begin;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Chunk;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Data;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Missing;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Request;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Result;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cote serveur du canal des textures Space (T45, D27) : reception des imports, envoi des textures demandees. Fil
 * serveur ; validation, ecriture et lecture des fichiers hors du fil serveur ({@code Util.backgroundExecutor}),
 * resultat repris sur le fil serveur ({@code server.execute}).
 * <p>
 * <b>Import</b> (un par joueur a la fois) : {@link Begin} -> operateur verifie, taille <= 2 Mio, pas d'import en
 * cours, Environment present dans la dimension du joueur ; texture deja stockee -> {@code DONE} tout de suite (rien a
 * envoyer) ; quota atteint -> orphelins supprimes, puis refus s'il reste plein ; sinon {@code ACCEPT}. {@link Chunk} ->
 * operateur reverifie a chaque morceau (sinon import abandonne), morceaux dans l'ordre, sans depasser la taille
 * annoncee ; au dernier, validation complete ({@link SpaceTextureStore#validate}) puis ecriture, et {@code DONE} ou
 * {@code REFUSED} avec le motif. Sans morceau pendant {@link SpaceTextureNetwork#UPLOAD_TIMEOUT_TICKS} ticks : abandon
 * ({@code REFUSED}). L'application a l'astre n'est pas faite ici : le client la demande ensuite par l'action 7 du
 * Configurator (qui verifie que la texture est stockee).
 * <p>
 * <b>Distribution</b> : {@link Request} (tout joueur) -> file par joueur (au plus
 * {@link SpaceTextureNetwork#MAX_PENDING_REQUESTS} empreintes, doublons ignores) ; la texture en tete de file est lue
 * (dans le dossier de la dimension du joueur au moment de la demande), puis envoyee <b>a lui seul</b> en
 * {@link Data} de {@link SpaceTextureNetwork#CHUNK_SIZE} octets, {@link SpaceTextureNetwork#CHUNKS_PER_TICK} par tick ;
 * absente ou illisible -> {@link Missing}. Une seule texture en memoire par joueur.
 * <p>
 * Etat par joueur oublie a sa deconnexion, a l'arret du serveur et quand il n'a plus rien en cours.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class SpaceTextureServer
{
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SpaceTextureServer() {}

    /** Import en cours d'un joueur. */
    private static final class Upload
    {
        final String sha1;
        final Path directory;
        final byte[] data;
        int received;
        int lastTick;
        /** Vrai pendant la validation et l'ecriture (plus aucun morceau accepte). */
        boolean validating;

        Upload(String sha1, Path directory, int size, int tick)
        {
            this.sha1 = sha1;
            this.directory = directory;
            this.data = new byte[size];
            this.lastTick = tick;
        }
    }

    /** Texture demandee, en file d'envoi. */
    private static final class Outgoing
    {
        final String sha1;
        final Path directory;
        /** Contenu lu, {@code null} tant que la lecture n'est pas faite. */
        byte[] data;
        boolean reading;
        int offset;

        Outgoing(String sha1, Path directory)
        {
            this.sha1 = sha1;
            this.directory = directory;
        }
    }

    /** Etat d'un joueur : import en cours, textures a envoyer. */
    private static final class Session
    {
        Upload upload;
        final ArrayDeque<Outgoing> outgoing = new ArrayDeque<>();
        final Set<String> queued = new HashSet<>();

        boolean idle() { return upload == null && outgoing.isEmpty(); }
    }

    private static Session session(ServerPlayer player)
    {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    private static void reply(ServerPlayer player, String sha1, int status, String message)
    {
        SpaceTextureNetwork.send(player, new Result(sha1, status, message));
    }

    // ---------------------------------------------------------------- import

    static void onBegin(ServerPlayer player, Begin packet)
    {
        String sha1 = packet.sha1();
        if (!player.hasPermissions(SpaceTextureNetwork.OP_LEVEL)) {
            reply(player, sha1, SpaceTexturePackets.REFUSED, "Import reserve aux operateurs");
            return;
        }
        if (packet.size() <= 0 || packet.size() > SpaceTextureNetwork.MAX_BYTES) {
            reply(player, sha1, SpaceTexturePackets.REFUSED, "Image trop lourde (2 Mo au plus)");
            return;
        }
        Session session = session(player);
        if (session.upload != null) {
            reply(player, sha1, SpaceTexturePackets.REFUSED, "Un import est deja en cours");
            return;
        }
        ServerLevel level = player.serverLevel();
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(level);
        if (env == null) {
            reply(player, sha1, SpaceTexturePackets.REFUSED, "Aucun Space Environment dans cette dimension");
            return;
        }
        Path directory = SpaceTextureStore.directory(level);
        if (SpaceTextureStore.exists(directory, sha1)) {
            reply(player, sha1, SpaceTexturePackets.DONE, "");
            return;
        }
        if (SpaceTextureStore.count(directory) >= SpaceTextureNetwork.QUOTA) {
            int removed = SpaceTextureStore.removeOrphans(directory, SpaceTextureStore.referenced(env));
            if (removed > 0) HoloLib.LOGGER.info("{} texture(s) Space orpheline(s) supprimee(s) dans {}", removed,
                    directory);
            if (SpaceTextureStore.count(directory) >= SpaceTextureNetwork.QUOTA) {
                reply(player, sha1, SpaceTexturePackets.REFUSED, SpaceTextureStore.quotaMessage());
                return;
            }
        }
        session.upload = new Upload(sha1, directory, packet.size(), player.server.getTickCount());
        reply(player, sha1, SpaceTexturePackets.ACCEPT, "");
    }

    static void onChunk(ServerPlayer player, Chunk packet)
    {
        Session session = SESSIONS.get(player.getUUID());
        Upload upload = session != null ? session.upload : null;
        if (upload == null || upload.validating) return;
        if (!player.hasPermissions(SpaceTextureNetwork.OP_LEVEL)) {
            session.upload = null;
            reply(player, upload.sha1, SpaceTexturePackets.REFUSED, "Import reserve aux operateurs");
            return;
        }
        byte[] bytes = packet.bytes();
        if (packet.offset() != upload.received || bytes.length == 0
                || bytes.length > upload.data.length - upload.received) {
            session.upload = null;
            reply(player, upload.sha1, SpaceTexturePackets.REFUSED, "Transmission invalide");
            return;
        }
        System.arraycopy(bytes, 0, upload.data, upload.received, bytes.length);
        upload.received += bytes.length;
        upload.lastTick = player.server.getTickCount();
        if (upload.received < upload.data.length) return;
        upload.validating = true;
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        CompletableFuture.supplyAsync(() -> {
            String error = SpaceTextureStore.validate(upload.data, upload.sha1);
            return error != null ? error : SpaceTextureStore.store(upload.directory, upload.sha1, upload.data);
        }, Util.backgroundExecutor()).whenComplete((error, exception) -> server.execute(() -> {
            String message = exception != null ? "Import impossible (erreur du serveur)" : error;
            if (exception != null) HoloLib.LOGGER.warn("Import de texture Space en echec", exception);
            finishUpload(server, id, upload, message, name);
        }));
    }

    /** Fil serveur : fin de la validation et de l'ecriture ; reponse si le joueur est encore la. */
    private static void finishUpload(MinecraftServer server, UUID id, Upload upload, String error, String name)
    {
        Session session = SESSIONS.get(id);
        if (session != null && session.upload == upload) session.upload = null;
        if (error == null) {
            HoloLib.LOGGER.info("Texture Space importee par {} : {} ({} o)", name, upload.sha1, upload.data.length);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;
        if (error == null) reply(player, upload.sha1, SpaceTexturePackets.DONE, "");
        else reply(player, upload.sha1, SpaceTexturePackets.REFUSED, error);
    }

    // ---------------------------------------------------------------- distribution

    static void onRequest(ServerPlayer player, Request packet)
    {
        Session session = session(player);
        String sha1 = packet.sha1();
        if (session.queued.contains(sha1) || session.queued.size() >= SpaceTextureNetwork.MAX_PENDING_REQUESTS) {
            return;
        }
        session.queued.add(sha1);
        session.outgoing.add(new Outgoing(sha1, SpaceTextureStore.directory(player.serverLevel())));
    }

    /** Fil serveur : envoi des morceaux de la texture en tete de file (la lit d'abord, hors du fil serveur). */
    private static void send(MinecraftServer server, UUID id, ServerPlayer player, Session session)
    {
        Outgoing head = session.outgoing.peek();
        if (head == null || head.reading) return;
        if (head.data == null) {
            head.reading = true;
            CompletableFuture.supplyAsync(() -> SpaceTextureStore.read(head.directory, head.sha1),
                    Util.backgroundExecutor()).whenComplete((data, exception) -> server.execute(() -> {
                        head.reading = false;
                        Session current = SESSIONS.get(id);
                        if (current != session || current.outgoing.peek() != head) return;
                        if (data == null) {
                            current.outgoing.poll();
                            current.queued.remove(head.sha1);
                            ServerPlayer target = server.getPlayerList().getPlayer(id);
                            if (target != null) SpaceTextureNetwork.send(target, new Missing(head.sha1));
                            return;
                        }
                        head.data = data;
                    }));
            return;
        }
        for (int i = 0; i < SpaceTextureNetwork.CHUNKS_PER_TICK && head.offset < head.data.length; i++) {
            int length = Math.min(SpaceTextureNetwork.CHUNK_SIZE, head.data.length - head.offset);
            byte[] bytes = Arrays.copyOfRange(head.data, head.offset, head.offset + length);
            SpaceTextureNetwork.send(player, new Data(head.sha1, head.data.length, head.offset, bytes));
            head.offset += length;
        }
        if (head.offset >= head.data.length) {
            session.outgoing.poll();
            session.queued.remove(head.sha1);
        }
    }

    // ---------------------------------------------------------------- evenements

    /** Fin de chaque tick serveur : imports expires, envois, oubli des joueurs partis ou sans rien en cours. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            Upload upload = session.upload;
            if (upload != null && !upload.validating
                    && tick - upload.lastTick > SpaceTextureNetwork.UPLOAD_TIMEOUT_TICKS) {
                session.upload = null;
                reply(player, upload.sha1, SpaceTexturePackets.REFUSED, "Import expire (plus de donnees)");
            }
            send(server, entry.getKey(), player, session);
            if (session.idle()) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        SESSIONS.clear();
    }
}
