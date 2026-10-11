package com.amynna.hololib.modules.dimension.space.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * Reference de texture d'un corps celeste (immuable, T28, decision D21 / C69). Trois formes, ecrites en texte
 * ({@link #serialized()}, NBT) :
 * <ul>
 *     <li>{@code auto} (defaut de tout corps, C69) : texture choisie a l'affichage (integree par nom pour Sol, sinon
 *     generique selon le type, le rayon et la temperature) ;</li>
 *     <li>{@code builtin:<id>} : texture integree au mod (T29), id {@code [a-z0-9_]{1,32}} ;</li>
 *     <li>{@code custom:<sha1>} : texture importee (T45), empreinte SHA-1 de 40 chiffres hexadecimaux (gardee en
 *     minuscules).</li>
 * </ul>
 * Le constructeur valide (leve sinon) : toute reference existante est bien formee. Reseau compact : 1 octet pour
 * {@code auto}, 2 + longueur pour {@code builtin}, 21 pour {@code custom} (empreinte en 20 octets bruts).
 * <p>
 * Le modele ne verifie pas que la texture existe : une id integree inconnue ou une empreinte sans fichier s'affichent
 * comme {@code auto} (decide par le rendu, T30 / T45).
 *
 * @param source forme de la reference
 * @param id     id integree ({@link Source#BUILTIN}), empreinte en minuscules ({@link Source#CUSTOM}), ou {@code ""}
 */
public record SpaceTextureRef(Source source, String id)
{
    /** Longueur maximale d'une id de texture integree. */
    public static final int MAX_BUILTIN_ID_LENGTH = 32;
    /** Longueur d'une empreinte SHA-1 en hexadecimal. */
    public static final int SHA1_HEX_LENGTH = 40;
    /** Taille d'une empreinte SHA-1, en octets. */
    public static final int SHA1_BYTES = 20;
    /** Taille maximale sur le reseau, en octets (forme {@code builtin} de 32 caracteres). */
    public static final int MAX_NETWORK_BYTES = 2 + MAX_BUILTIN_ID_LENGTH;

    /** Texture automatique (defaut). */
    public static final SpaceTextureRef AUTO = new SpaceTextureRef(Source.AUTO, "");

    private static final String PREFIX_BUILTIN = "builtin:";
    private static final String PREFIX_CUSTOM = "custom:";
    private static final String AUTO_TEXT = "auto";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Forme d'une reference ; ordinal = octet reseau : ne jamais reordonner, ajouter en dernier. */
    public enum Source
    {
        AUTO,
        BUILTIN,
        CUSTOM
    }

    public SpaceTextureRef
    {
        if (source == null) throw new IllegalArgumentException("Source de texture manquante");
        if (id == null) id = "";
        switch (source) {
            case AUTO -> {
                if (!id.isEmpty()) throw new IllegalArgumentException("Texture auto avec une id : " + id);
            }
            case BUILTIN -> {
                if (!validBuiltinId(id)) throw new IllegalArgumentException("Id de texture invalide : " + id);
            }
            case CUSTOM -> {
                if (!validSha1(id)) throw new IllegalArgumentException("Empreinte de texture invalide : " + id);
                id = id.toLowerCase(Locale.ROOT);
            }
        }
    }

    /** Texture integree d'id {@code id} ({@code [a-z0-9_]{1,32}}) ; leve sinon. */
    public static SpaceTextureRef builtin(String id) { return new SpaceTextureRef(Source.BUILTIN, id); }

    /** Texture importee d'empreinte {@code sha1} (40 hexadecimaux, casse indifferente) ; leve sinon. */
    public static SpaceTextureRef custom(String sha1) { return new SpaceTextureRef(Source.CUSTOM, sha1); }

    public boolean isAuto() { return source == Source.AUTO; }

    // ---------------------------------------------------------------- forme texte

    /** {@code auto}, {@code builtin:<id>} ou {@code custom:<sha1>}. */
    public String serialized()
    {
        return switch (source) {
            case AUTO -> AUTO_TEXT;
            case BUILTIN -> PREFIX_BUILTIN + id;
            case CUSTOM -> PREFIX_CUSTOM + id;
        };
    }

    @Override
    public String toString() { return serialized(); }

    /**
     * Reference ecrite par {@link #serialized()} ; {@code null} si le texte est mal forme (forme inconnue, id ou
     * empreinte invalide). Une empreinte en majuscules est acceptee.
     */
    public static SpaceTextureRef parse(String text)
    {
        if (text == null) return null;
        if (text.equals(AUTO_TEXT)) return AUTO;
        if (text.startsWith(PREFIX_BUILTIN)) {
            String value = text.substring(PREFIX_BUILTIN.length());
            return validBuiltinId(value) ? new SpaceTextureRef(Source.BUILTIN, value) : null;
        }
        if (text.startsWith(PREFIX_CUSTOM)) {
            String value = text.substring(PREFIX_CUSTOM.length());
            return validSha1(value) ? new SpaceTextureRef(Source.CUSTOM, value) : null;
        }
        return null;
    }

    /** Vrai si {@code id} respecte {@code [a-z0-9_]{1,32}}. */
    public static boolean validBuiltinId(String id)
    {
        if (id == null || id.isEmpty() || id.length() > MAX_BUILTIN_ID_LENGTH) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return false;
        }
        return true;
    }

    /** Vrai si {@code sha1} est fait de 40 chiffres hexadecimaux (casse indifferente). */
    public static boolean validSha1(String sha1)
    {
        if (sha1 == null || sha1.length() != SHA1_HEX_LENGTH) return false;
        for (int i = 0; i < sha1.length(); i++) {
            char c = sha1.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- reseau
    // Ordre : source (octet), puis id (utf, BUILTIN) ou empreinte (20 octets bruts, CUSTOM).

    public void write(FriendlyByteBuf buf)
    {
        buf.writeByte(source.ordinal());
        switch (source) {
            case AUTO -> { }
            case BUILTIN -> buf.writeUtf(id, MAX_BUILTIN_ID_LENGTH);
            case CUSTOM -> {
                for (int i = 0; i < SHA1_BYTES; i++) {
                    int high = Character.digit(id.charAt(2 * i), 16);
                    buf.writeByte(high << 4 | Character.digit(id.charAt(2 * i + 1), 16));
                }
            }
        }
    }

    /** Lecture stricte : leve sur une forme inconnue ou une id invalide. */
    public static SpaceTextureRef read(FriendlyByteBuf buf)
    {
        int ordinal = buf.readByte();
        Source[] sources = Source.values();
        if (ordinal < 0 || ordinal >= sources.length) {
            throw new IllegalArgumentException("Source de texture : " + ordinal);
        }
        return switch (sources[ordinal]) {
            case AUTO -> AUTO;
            case BUILTIN -> new SpaceTextureRef(Source.BUILTIN, buf.readUtf(MAX_BUILTIN_ID_LENGTH));
            case CUSTOM -> {
                char[] hex = new char[SHA1_HEX_LENGTH];
                for (int i = 0; i < SHA1_BYTES; i++) {
                    int b = buf.readUnsignedByte();
                    hex[2 * i] = HEX[b >> 4];
                    hex[2 * i + 1] = HEX[b & 0xF];
                }
                yield new SpaceTextureRef(Source.CUSTOM, new String(hex));
            }
        };
    }
}
