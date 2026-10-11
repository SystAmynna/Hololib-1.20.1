package com.amynna.hololib.client.modules.monde.spaceconfigurator;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.dimension.space.SpaceTextureClient;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTextureNetwork;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTextureStore;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Import d'une texture d'astre depuis la fenetre du Configurator (T45, D21, C79), cote client : bouton « Importer »
 * -> selecteur de fichier natif ({@code TinyFileDialogs}) ouvert sur un fil a part (le jeu continue), puis, sur ce
 * meme fil :
 * <ol>
 *     <li>fichier lu (au plus {@value #MAX_SOURCE_BYTES} o), PNG ou JPEG reconnu a sa signature ;</li>
 *     <li>dimensions lues avant le decodage (au plus {@value #MAX_SOURCE_SIDE} px de cote), image decodee en RGB
 *     ({@code STBImage}) ;</li>
 *     <li>image <b>etiree</b> (jamais recadree, C138) en equirectangulaire 2:1 de largeur
 *     {@link #targetWidth} (puissance de deux la plus proche de {@code max(largeur, 2 x hauteur)}, de 64 a 1024 :
 *     au plus 1024 x 512), {@code STBImageResize} ;</li>
 *     <li>reencodee en PNG RGB ({@code STBImageWrite}, sans canal alpha : au plus 1,5 Mo bruts) ; au-dela de 2 Mio,
 *     largeur divisee par deux et nouvel essai ;</li>
 *     <li>empreinte SHA-1.</li>
 * </ol>
 * De retour sur le fil client : envoi au serveur ({@link SpaceTextureClient#upload}), puis, quand le serveur l'a
 * stockee, demande d'application a l'astre par l'action 7 du Configurator ({@code custom:<sha1>},
 * {@link SpaceConfiguratorModule#applyImportedTexture}). Messages (refus compris) dans la barre d'action. Un import a
 * la fois.
 */
public final class SpaceTextureImport
{
    /** Taille maximale du fichier choisi (avant reduction). */
    static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;
    /** Cote maximal de l'image choisie (avant reduction), en pixels. */
    static final int MAX_SOURCE_SIDE = 16384;
    /** Largeur minimale de la texture produite (puissance de deux). */
    static final int MIN_TARGET_WIDTH = 64;
    private static final String TITLE = "Importer une texture (PNG ou JPEG, 2:1)";
    private static final String FILTER_DESCRIPTION = "Images PNG ou JPEG";

    /** Vrai du clic jusqu'a la fin de l'envoi (fil client). */
    private static boolean busy;
    /** Vrai pendant l'envoi (apres la preparation). */
    private static boolean sending;
    /** Dossier du dernier fichier choisi (ouverture suivante), {@code null} au depart. */
    private static volatile String lastDirectory;

    private SpaceTextureImport() {}

    /** Resultat de la preparation : PNG pret, ou motif d'un refus ({@code error} non nul). */
    private record Prepared(String sha1, byte[] png, int width, int height, String error)
    {
        static Prepared failure(String error) { return new Prepared(null, null, 0, 0, error); }
    }

    /**
     * Fil client : lance l'import pour le corps {@code bodyId} du systeme {@code systemId} ; faux si un import est deja
     * en cours.
     */
    static boolean start(SpaceConfiguratorModule module, int systemId, int bodyId)
    {
        if (busy || SpaceTextureClient.uploading()) return false;
        busy = true;
        sending = false;
        int session = SpaceTextureClient.session();
        Thread thread = new Thread(() -> {
            Prepared prepared;
            try {
                String path = choose();
                prepared = path == null ? null : prepare(Path.of(path));
            } catch (Throwable t) {
                HoloLib.LOGGER.warn("Import de texture Space impossible", t);
                prepared = Prepared.failure("Import impossible : " + t.getClass().getSimpleName());
            }
            Prepared result = prepared;
            Minecraft.getInstance().execute(() -> prepared(module, systemId, bodyId, result, session));
        }, "HoloLib space texture import");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    /**
     * Etat pour le bouton : {@link SpaceConfiguratorModule#IMPORT_IDLE}, {@link SpaceConfiguratorModule#IMPORT_PREPARING}
     * (choix du fichier, preparation) ou avancement de l'envoi (0 a 100).
     */
    static int progress()
    {
        if (!busy) return SpaceConfiguratorModule.IMPORT_IDLE;
        if (!sending) return SpaceConfiguratorModule.IMPORT_PREPARING;
        return SpaceTextureClient.uploadProgress();
    }

    /** Fil client : fichier prepare (ou choix annule) ; envoi au serveur, sauf si la partie a change entre-temps. */
    private static void prepared(SpaceConfiguratorModule module, int systemId, int bodyId, Prepared prepared,
                                 int session)
    {
        if (session != SpaceTextureClient.session()) {
            busy = false;
            return;
        }
        if (prepared == null) {
            busy = false;
            message("Import annule");
            return;
        }
        if (prepared.error() != null) {
            busy = false;
            message(prepared.error());
            return;
        }
        String size = prepared.width() + " x " + prepared.height();
        boolean started = SpaceTextureClient.upload(prepared.sha1(), prepared.png(), new SpaceTextureClient.UploadListener()
        {
            @Override
            public void done(String sha1)
            {
                busy = false;
                sending = false;
                module.applyImportedTexture(systemId, bodyId, sha1);
                message("Texture importee (" + size + ")");
            }

            @Override
            public void failed(String error)
            {
                busy = false;
                sending = false;
                message(error);
            }
        });
        if (!started) {
            busy = false;
            message("Envoi impossible (hors partie ou envoi deja en cours)");
            return;
        }
        sending = true;
    }

    private static void message(String text)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text), true);
    }

    // ---------------------------------------------------------------- fil d'import

    /** Selecteur de fichier natif (bloquant) ; chemin choisi, ou {@code null} si annule. */
    private static String choose()
    {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(6);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                    .put(stack.UTF8("*.PNG")).put(stack.UTF8("*.JPG")).put(stack.UTF8("*.JPEG")).flip();
            String start = lastDirectory;
            // tinyfd refuse les chaines contenant des guillemets ou des apostrophes.
            if (start != null && (start.indexOf('\'') >= 0 || start.indexOf('"') >= 0)) start = null;
            String path = TinyFileDialogs.tinyfd_openFileDialog(TITLE, start, filters, FILTER_DESCRIPTION, false);
            if (path != null) {
                Path parent = Path.of(path).getParent();
                if (parent != null) lastDirectory = parent + java.io.File.separator;
            }
            return path;
        }
    }

    /** Lecture, decodage, etirement en 2:1, encodage PNG et empreinte (fil d'import). */
    private static Prepared prepare(Path path) throws IOException
    {
        if (!Files.isRegularFile(path)) return Prepared.failure("Fichier introuvable");
        long size = Files.size(path);
        if (size <= 0) return Prepared.failure("Fichier vide");
        if (size > MAX_SOURCE_BYTES) return Prepared.failure("Fichier trop gros (64 Mo au plus)");
        byte[] bytes = Files.readAllBytes(path);
        if (!SpaceTextureStore.isPng(bytes) && !isJpeg(bytes)) {
            return Prepared.failure("Fichier refuse : image PNG ou JPEG attendue");
        }
        ByteBuffer source = MemoryUtil.memAlloc(bytes.length);
        ByteBuffer pixels = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            source.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(source, w, h, channels)) {
                return Prepared.failure("Image illisible : " + STBImage.stbi_failure_reason());
            }
            int width = w.get(0);
            int height = h.get(0);
            if (width <= 0 || height <= 0) return Prepared.failure("Image vide");
            if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
                return Prepared.failure("Image trop grande (" + MAX_SOURCE_SIDE + " px de cote au plus)");
            }
            pixels = STBImage.stbi_load_from_memory(source, w, h, channels, 3);
            if (pixels == null) return Prepared.failure("Image illisible : " + STBImage.stbi_failure_reason());
            for (int target = targetWidth(width, height); target >= MIN_TARGET_WIDTH; target /= 2) {
                byte[] png = resizeAndEncode(pixels, width, height, target, target / 2);
                if (png == null) return Prepared.failure("Redimensionnement impossible");
                if (png.length <= SpaceTextureNetwork.MAX_BYTES) {
                    return new Prepared(SpaceTextureStore.sha1Hex(png), png, target, target / 2, null);
                }
            }
            return Prepared.failure("Image trop lourde (2 Mo au plus)");
        } finally {
            MemoryUtil.memFree(source);
            if (pixels != null) STBImage.stbi_image_free(pixels);
        }
    }

    /**
     * Largeur de la texture produite : puissance de deux la plus proche (en echelle logarithmique) de
     * {@code max(largeur, 2 x hauteur)}, bornee a {@code [64, 1024]} ; hauteur = largeur / 2.
     */
    static int targetWidth(int width, int height)
    {
        double size = Math.max(width, 2.0 * height);
        int exponent = (int) Math.round(Math.log(size) / Math.log(2.0));
        int min = Integer.numberOfTrailingZeros(MIN_TARGET_WIDTH);
        int max = Integer.numberOfTrailingZeros(SpaceTextureNetwork.MAX_WIDTH);
        return 1 << Math.max(min, Math.min(max, exponent));
    }

    /** Image RGB etiree a {@code targetWidth x targetHeight} puis encodee en PNG ; {@code null} si STB echoue. */
    private static byte[] resizeAndEncode(ByteBuffer pixels, int width, int height, int targetWidth, int targetHeight)
            throws IOException
    {
        ByteBuffer resized = MemoryUtil.memAlloc(targetWidth * targetHeight * 3);
        try {
            if (!STBImageResize.stbir_resize_uint8(pixels, width, height, 0, resized, targetWidth, targetHeight, 0,
                    3)) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(targetWidth * targetHeight);
            try (STBIWriteCallback callback = STBIWriteCallback.create((context, data, size) -> {
                ByteBuffer chunk = STBIWriteCallback.getData(data, size);
                byte[] copy = new byte[size];
                chunk.get(copy);
                out.write(copy, 0, size);
            })) {
                if (!STBImageWrite.stbi_write_png_to_func(callback, 0L, targetWidth, targetHeight, 3, resized,
                        targetWidth * 3)) {
                    throw new IOException("Encodage PNG impossible");
                }
            }
            return out.toByteArray();
        } finally {
            MemoryUtil.memFree(resized);
        }
    }

    private static boolean isJpeg(byte[] data)
    {
        return data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
    }
}
