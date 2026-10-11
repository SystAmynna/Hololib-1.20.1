package com.amynna.hololib.modules.dimension.space.texture;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceTextureRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Stockage serveur des textures importees (T45, C79) : un fichier {@code <sha1>.png} par texture dans le dossier
 * {@value #DIRECTORY} du dossier {@code data} de la <b>dimension</b> (a cote de {@code hololib_space.dat}, l'archive
 * de l'Environment : {@code <monde>/data/} pour l'Overworld, {@code <monde>/dimensions/<ns>/<nom>/data/} sinon).
 * Contenu adresse par empreinte : un meme PNG n'est stocke qu'une fois, son nom suffit a le verifier.
 * <p>
 * <b>Validation</b> ({@link #validate}, avant toute ecriture ; aucune texture n'est lue sans elle) : taille
 * (1 o a {@link SpaceTextureNetwork#MAX_BYTES}), empreinte recalculee egale a celle annoncee, signature PNG,
 * dimensions lues dans l'en-tete <b>avant</b> le decodage (largeur = 2 x hauteur, {@link SpaceTextureNetwork#MIN_WIDTH}
 * a {@link SpaceTextureNetwork#MAX_WIDTH} px), puis decodage complet par {@code javax.imageio} (pas de
 * {@code NativeImage}, classe client). Metadonnees ignorees.
 * <p>
 * <b>Quota</b> : {@link SpaceTextureNetwork#QUOTA} fichiers par dimension. Quand il est atteint, les fichiers
 * <b>orphelins</b> (empreinte qu'aucun corps de la galaxie de l'Environment de la dimension ne reference) sont
 * supprimes ({@link #removeOrphans}, C137) ; s'il reste plein, l'import est refuse. Les orphelins ne sont pas nettoyes
 * au chargement.
 * <p>
 * Ecriture atomique (fichier temporaire puis deplacement), serialisee par un verrou ; lectures et validations hors
 * du fil serveur ({@link SpaceTextureServer}), sauf {@link #exists} et le nettoyage (rares, fil serveur).
 */
public final class SpaceTextureStore
{
    /** Dossier des textures importees, dans le dossier {@code data} de la dimension. */
    public static final String DIRECTORY = HoloLib.MODID + "_space_textures";
    private static final String EXTENSION = ".png";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    /** Verrou des ecritures (quota recompte et fichier ecrit ensemble). */
    private static final Object LOCK = new Object();

    private SpaceTextureStore() {}

    // ---------------------------------------------------------------- chemins

    /** Dossier des textures importees de la dimension {@code level} (cree a la premiere ecriture). */
    public static Path directory(ServerLevel level)
    {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        return DimensionType.getStorageFolder(level.dimension(), root).resolve("data").resolve(DIRECTORY).normalize();
    }

    /** Fichier de l'empreinte {@code sha1} (40 hexadecimaux minuscules, deja valides : aucun separateur possible). */
    static Path file(Path directory, String sha1)
    {
        return directory.resolve(sha1 + EXTENSION);
    }

    /** Vrai si la texture importee {@code sha1} est stockee pour la dimension {@code level} (fil serveur). */
    public static boolean exists(ServerLevel level, String sha1)
    {
        if (!SpaceTextureRef.validSha1(sha1)) return false;
        return Files.isRegularFile(file(directory(level), sha1.toLowerCase(Locale.ROOT)));
    }

    static boolean exists(Path directory, String sha1)
    {
        return Files.isRegularFile(file(directory, sha1));
    }

    /** Nombre de textures stockees dans {@code directory} (fichiers {@code <sha1>.png}) ; 0 sans dossier. */
    static int count(Path directory)
    {
        if (!Files.isDirectory(directory)) return 0;
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path path : files) if (hashOf(path) != null) count++;
        } catch (IOException e) {
            HoloLib.LOGGER.warn("Dossier des textures Space illisible : {}", directory, e);
        }
        return count;
    }

    /** Empreinte d'un fichier {@code <sha1>.png}, {@code null} pour tout autre nom. */
    private static String hashOf(Path path)
    {
        String name = path.getFileName().toString();
        if (!name.endsWith(EXTENSION)) return null;
        String hash = name.substring(0, name.length() - EXTENSION.length());
        return SpaceTextureRef.validSha1(hash) && hash.equals(hash.toLowerCase(Locale.ROOT)) ? hash : null;
    }

    // ---------------------------------------------------------------- orphelins

    /** Empreintes des textures importees referencees par la galaxie de {@code env} (tous ses systemes). */
    static Set<String> referenced(SpaceEnvironmentModule env)
    {
        Set<String> hashes = new HashSet<>();
        if (env == null) return hashes;
        for (SpaceSystem system : env.galaxy()) {
            for (SpaceBody body : system.bodies()) {
                SpaceTextureRef texture = body.texture();
                if (texture.source() == SpaceTextureRef.Source.CUSTOM) hashes.add(texture.id());
            }
        }
        return hashes;
    }

    /**
     * Supprime les textures de {@code directory} qu'aucune empreinte de {@code referenced} ne designe ; rend le nombre
     * de fichiers supprimes. Fil serveur (rare : quota atteint).
     */
    static int removeOrphans(Path directory, Set<String> referenced)
    {
        if (!Files.isDirectory(directory)) return 0;
        int removed = 0;
        synchronized (LOCK) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
                for (Path path : files) {
                    String hash = hashOf(path);
                    if (hash == null || referenced.contains(hash)) continue;
                    try {
                        Files.deleteIfExists(path);
                        removed++;
                    } catch (IOException e) {
                        HoloLib.LOGGER.warn("Texture Space orpheline non supprimee : {}", path, e);
                    }
                }
            } catch (IOException e) {
                HoloLib.LOGGER.warn("Dossier des textures Space illisible : {}", directory, e);
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- validation, ecriture, lecture

    /**
     * Verifie un PNG recu : taille, empreinte, signature, dimensions (en-tete lu avant le decodage), decodage complet.
     * Hors du fil serveur.
     *
     * @return {@code null} si l'image est valide, sinon le motif du refus (affiche au joueur)
     */
    static String validate(byte[] data, String sha1)
    {
        if (data.length == 0 || data.length > SpaceTextureNetwork.MAX_BYTES) return "Image trop lourde (2 Mo au plus)";
        if (!sha1Hex(data).equals(sha1)) return "Transmission alteree (empreinte differente)";
        if (!isPng(data)) return "Fichier refuse : PNG attendu";
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) return "Lecteur PNG indisponible sur le serveur";
        ImageReader reader = readers.next();
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width != 2 * height || width < SpaceTextureNetwork.MIN_WIDTH || width > SpaceTextureNetwork.MAX_WIDTH) {
                return "Dimensions refusees : " + width + " x " + height + " (2:1, " + SpaceTextureNetwork.MIN_WIDTH
                        + " a " + SpaceTextureNetwork.MAX_WIDTH + " px de large)";
            }
            BufferedImage image = reader.read(0);
            if (image == null || image.getWidth() != width || image.getHeight() != height) return "PNG illisible";
            return null;
        } catch (IOException | RuntimeException e) {
            return "PNG illisible";
        } finally {
            reader.dispose();
        }
    }

    /**
     * Ecrit un PNG deja valide sous son empreinte (atomique), si le quota le permet. Hors du fil serveur.
     *
     * @return {@code null} si la texture est stockee (ou l'etait deja), sinon le motif du refus
     */
    static String store(Path directory, String sha1, byte[] data)
    {
        synchronized (LOCK) {
            Path target = file(directory, sha1);
            if (Files.isRegularFile(target)) return null;
            if (count(directory) >= SpaceTextureNetwork.QUOTA) return quotaMessage();
            Path temporary = directory.resolve(sha1 + ".tmp");
            try {
                Files.createDirectories(directory);
                Files.write(temporary, data);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            } catch (IOException e) {
                HoloLib.LOGGER.warn("Texture Space non ecrite : {}", target, e);
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Rien de plus a faire : le fichier temporaire sera ignore (nom sans .png).
                }
                return "Ecriture impossible sur le serveur";
            }
        }
    }

    /** Motif du refus quand le quota est atteint. */
    static String quotaMessage()
    {
        return "Quota atteint : " + SpaceTextureNetwork.QUOTA + " textures importees dans cette dimension";
    }

    /**
     * Contenu de la texture {@code sha1}, {@code null} si absente, trop lourde ou si son nom ne correspond plus a son
     * contenu (fichier modifie a la main). Hors du fil serveur.
     */
    static byte[] read(Path directory, String sha1)
    {
        Path path = file(directory, sha1);
        try {
            long size = Files.size(path);
            if (size <= 0 || size > SpaceTextureNetwork.MAX_BYTES) return null;
            byte[] data = Files.readAllBytes(path);
            return sha1Hex(data).equals(sha1) ? data : null;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            HoloLib.LOGGER.warn("Texture Space illisible : {}", path, e);
            return null;
        }
    }

    // ---------------------------------------------------------------- empreintes

    /** Empreinte SHA-1 de {@code data} en 40 hexadecimaux minuscules. */
    public static String sha1Hex(byte[] data)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            char[] hex = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                hex[2 * i] = HEX[(digest[i] >> 4) & 0xF];
                hex[2 * i + 1] = HEX[digest[i] & 0xF];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 indisponible", e);
        }
    }

    /** Vrai si {@code data} commence par la signature PNG. */
    public static boolean isPng(byte[] data)
    {
        if (data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (data[i] != PNG_SIGNATURE[i]) return false;
        return true;
    }

    /** Ecrit une empreinte (40 hexadecimaux, casse indifferente) en 20 octets bruts. */
    static void writeSha1(FriendlyByteBuf buf, String sha1)
    {
        if (!SpaceTextureRef.validSha1(sha1)) throw new IllegalArgumentException("Empreinte invalide : " + sha1);
        for (int i = 0; i < SpaceTextureRef.SHA1_BYTES; i++) {
            buf.writeByte(Character.digit(sha1.charAt(2 * i), 16) << 4 | Character.digit(sha1.charAt(2 * i + 1), 16));
        }
    }

    /** Lit une empreinte de 20 octets bruts ; rend 40 hexadecimaux minuscules (toujours valides). */
    static String readSha1(FriendlyByteBuf buf)
    {
        char[] hex = new char[SpaceTextureRef.SHA1_HEX_LENGTH];
        for (int i = 0; i < SpaceTextureRef.SHA1_BYTES; i++) {
            int b = buf.readUnsignedByte();
            hex[2 * i] = HEX[b >> 4];
            hex[2 * i + 1] = HEX[b & 0xF];
        }
        return new String(hex);
    }
}
