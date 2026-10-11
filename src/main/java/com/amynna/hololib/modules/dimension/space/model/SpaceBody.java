package com.amynna.hololib.modules.dimension.space.model;

import com.amynna.hololib.HoloLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Corps celeste d'un systeme (immuable). Voir {@code docs/space/03-modele-donnees.md} par. 2.
 * <p>
 * Invariants garantis par le constructeur : {@code id > 0} ; nom nettoye ; rayon, luminosite bornes ; couleur
 * opaque ; orbite presente si et seulement si le corps a un parent ({@code parentId != 0}). Les regles qui dependent
 * des autres corps (parent existant, type compatible, ordre) sont verifiees par {@link SpaceSystem}.
 * Un corps sans parent est immobile au centre du systeme.
 * <p>
 * Lot 3 (T28) : rotation propre ({@link SpaceRotation}, jamais {@code null} : rotation par defaut du type si absente)
 * et reference de texture ({@link SpaceTextureRef}, {@link SpaceTextureRef#AUTO} si absente). Le constructeur a 8
 * parametres (forme du lot 2) les prend par defaut.
 * <p>
 * Lot 3 (T39, D29) : <b>masse</b> en kg ({@link SpaceLimits#sanitizeMass}), qui ne sert qu'a choisir le referentiel
 * (spheres d'influence, {@code math/SpaceMotion}) : ni gravite appliquee au vaisseau, ni effet sur les orbites (chaque
 * orbite porte sa periode). Les constructeurs sans masse (formes du lot 2 et de T28) l'estiment
 * ({@link #estimatedMass}) ; {@link #withRadius} garde la masse.
 *
 * @param id         identifiant, unique dans le systeme, jamais reutilise
 * @param parentId   id du parent, {@code 0} = aucun
 * @param radius     rayon, en km
 * @param color      couleur ARGB (alpha force a {@code 0xFF})
 * @param luminosity luminosite relative au Soleil ; n'a de sens que pour une etoile (ignoree sinon)
 * @param mass       masse en kg, bornee a {@code [MIN_MASS, MAX_MASS]} (T39)
 * @param orbit      orbite autour du parent, {@code null} si {@code parentId == 0}
 * @param rotation   rotation propre ({@code null} = {@link SpaceRotation#defaultFor} du type)
 * @param texture    reference de texture ({@code null} = {@link SpaceTextureRef#AUTO})
 */
public record SpaceBody(int id, SpaceBodyKind kind, String name, int parentId, double radius, int color,
                        float luminosity, double mass, SpaceOrbit orbit, SpaceRotation rotation,
                        SpaceTextureRef texture)
{
    public SpaceBody
    {
        if (id <= 0) throw new IllegalArgumentException("Id de corps invalide : " + id);
        if (kind == null) throw new IllegalArgumentException("Type de corps manquant");
        if (parentId < 0 || parentId == id) throw new IllegalArgumentException("Parent invalide : " + parentId);
        if ((parentId == 0) != (orbit == null)) {
            throw new IllegalArgumentException("Orbite " + (orbit == null ? "manquante" : "en trop") + " pour " + id);
        }
        name = SpaceLimits.sanitizeName(name);
        radius = SpaceLimits.sanitizeRadius(radius);
        color = SpaceLimits.sanitizeColor(color);
        luminosity = SpaceLimits.sanitizeLuminosity(luminosity);
        mass = SpaceLimits.sanitizeMass(mass);
        if (rotation == null) rotation = SpaceRotation.defaultFor(kind);
        if (texture == null) texture = SpaceTextureRef.AUTO;
    }

    /**
     * Corps avec une masse <b>estimee</b> ({@link #estimatedMass}), la rotation par defaut de son type et la texture
     * {@code auto} (forme du lot 2).
     */
    public SpaceBody(int id, SpaceBodyKind kind, String name, int parentId, double radius, int color,
                     float luminosity, SpaceOrbit orbit)
    {
        this(id, kind, name, parentId, radius, color, luminosity, orbit, null, null);
    }

    /** Corps avec une masse <b>estimee</b> ({@link #estimatedMass}) (forme de T28). */
    public SpaceBody(int id, SpaceBodyKind kind, String name, int parentId, double radius, int color,
                     float luminosity, SpaceOrbit orbit, SpaceRotation rotation, SpaceTextureRef texture)
    {
        this(id, kind, name, parentId, radius, color, luminosity, estimatedMass(kind, radius), orbit, rotation,
                texture);
    }

    /** Vrai si le corps n'a pas de parent (centre du systeme). */
    public boolean isRoot() { return parentId == 0; }

    // ---------------------------------------------------------------- copies modifiees (champs editables)

    public SpaceBody withName(String name)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    public SpaceBody withRadius(double radius)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    public SpaceBody withColor(int color)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    public SpaceBody withLuminosity(float luminosity)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    /** Masse en kg (T39), bornee ; leve si elle n'est pas finie. */
    public SpaceBody withMass(double mass)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    /** Leve si {@code orbit} ne respecte pas l'invariant de parente (orbite requise ssi parent). */
    public SpaceBody withOrbit(SpaceOrbit orbit)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    /** Rotation propre ({@code null} = rotation par defaut du type). */
    public SpaceBody withRotation(SpaceRotation rotation)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    /** Reference de texture ({@code null} = {@link SpaceTextureRef#AUTO}). */
    public SpaceBody withTexture(SpaceTextureRef texture)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    // ---------------------------------------------------------------- masse estimee (T39)

    /** Masse volumique d'une planete rocheuse, kg/m3 (Terre 5 514). */
    public static final double ROCKY_DENSITY = 5_500.0;
    /** Masse volumique d'une planete gazeuse ou de glace, kg/m3 (Jupiter 1 326, Uranus 1 270, Neptune 1 638). */
    public static final double GAS_DENSITY = 1_300.0;
    /** Rayon (km) au-dela duquel une planete est supposee gazeuse : 1,6 rayon terrestre. */
    public static final double ROCKY_MAX_RADIUS = 1.6 * 6_371.0;
    /** Masse volumique d'une lune, kg/m3 (Lune 3 344, Ganymede 1 942, Titan 1 880). */
    public static final double MOON_DENSITY = 2_500.0;
    /** Masse volumique d'un objet mineur, kg/m3 (Ceres 2 162, Pluton 1 854, Eris 2 430). */
    public static final double MINOR_DENSITY = 2_000.0;
    /** Masse solaire (kg) et rayon solaire (km) de la relation masse-rayon des etoiles. */
    private static final double SUN_MASS = SpaceKnownMasses.SUN;
    private static final double SUN_RADIUS = 695_700.0;
    /** Exposant de la relation de la sequence principale {@code M = M_sol (R / R_sol)^1,25} ({@code R ~ M^0,8}). */
    private static final double STAR_EXPONENT = 1.25;
    /** Masse minimale estimee d'une etoile, en masses solaires (limite des naines brunes). */
    private static final double STAR_MIN_MASS = 0.08;

    /**
     * Masse estimee (kg) d'un corps sans masse connue (T39) : nouveau corps ({@code addBody}), corps d'une galaxie
     * sauvegardee avant T39 dont le nom n'est pas dans la table des corps par defaut. Etoile : relation de la sequence
     * principale {@code M = M_sol (R / R_sol)^1,25}, au moins 0,08 masse solaire (une naine blanche est donc tres
     * sous-estimee) ; sinon {@code rho 4/3 pi R^3} avec une masse volumique par type : planete rocheuse
     * ({@value #ROCKY_DENSITY}) jusqu'a 1,6 rayon terrestre, gazeuse ({@value #GAS_DENSITY}) au-dela, lune
     * ({@value #MOON_DENSITY}), objet mineur ({@value #MINOR_DENSITY}). Rayon borne comme celui d'un corps (leve s'il
     * n'est pas fini) ; resultat borne ({@link SpaceLimits#sanitizeMass}). Deterministe ({@link StrictMath}).
     */
    public static double estimatedMass(SpaceBodyKind kind, double radius)
    {
        double r = SpaceLimits.sanitizeRadius(radius);
        if (kind == SpaceBodyKind.STAR) {
            double solar = Math.max(STAR_MIN_MASS, StrictMath.pow(r / SUN_RADIUS, STAR_EXPONENT));
            return SpaceLimits.sanitizeMass(solar * SUN_MASS);
        }
        double density = switch (kind) {
            case PLANET -> r <= ROCKY_MAX_RADIUS ? ROCKY_DENSITY : GAS_DENSITY;
            case MOON -> MOON_DENSITY;
            default -> MINOR_DENSITY;
        };
        double meters = r * 1_000.0;
        return SpaceLimits.sanitizeMass(density * (4.0 / 3.0) * StrictMath.PI * meters * meters * meters);
    }

    // ---------------------------------------------------------------- reseau
    // Ordre : id, kind, name, parent, radius, color, lum, mass (8 o, T39), [orbite si parent != 0], rotation (16 o,
    // T28), texture (1 o pour auto, T28).

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(id);
        buf.writeByte(kind.ordinal());
        buf.writeUtf(name, SpaceLimits.MAX_NAME_LENGTH);
        buf.writeVarInt(parentId);
        buf.writeDouble(radius);
        buf.writeInt(color);
        buf.writeFloat(luminosity);
        buf.writeDouble(mass);
        if (orbit != null) orbit.write(buf);
        rotation.write(buf);
        texture.write(buf);
    }

    /** Lecture stricte : leve sur toute valeur invalide. */
    public static SpaceBody read(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        SpaceBodyKind kind = SpaceBodyKind.byOrdinal(buf.readByte());
        String name = buf.readUtf(SpaceLimits.MAX_NAME_LENGTH);
        int parentId = buf.readVarInt();
        double radius = buf.readDouble();
        int color = buf.readInt();
        float luminosity = buf.readFloat();
        double mass = buf.readDouble();
        SpaceOrbit orbit = parentId != 0 ? SpaceOrbit.read(buf) : null;
        SpaceRotation rotation = SpaceRotation.read(buf);
        SpaceTextureRef texture = SpaceTextureRef.read(buf);
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, mass, orbit, rotation, texture);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("kind", kind.name());
        tag.putString("name", name);
        tag.putInt("parent", parentId);
        tag.putDouble("radius", radius);
        tag.putInt("color", color);
        tag.putFloat("lum", luminosity);
        tag.putDouble("mass", mass);
        if (orbit != null) tag.put("orbit", orbit.save());
        tag.put("spin", rotation.save());
        tag.putString("tex", texture.serialized());
        return tag;
    }

    /**
     * Lecture tolerante : cles manquantes = valeurs par defaut du type (orbite par defaut si un parent est indique sans
     * orbite ; orbite ignoree sans parent ; T39 : sans {@code mass}, masse reelle du corps par defaut de meme nom
     * ({@link SpaceKnownMasses}) ou sinon masse estimee ; T28 : sans {@code spin}, rotation reelle du corps de Sol de
     * meme nom ({@link SpaceSolRotations}) ou sinon rotation par defaut du type, et sans {@code tex}, texture
     * {@code auto} : un corps sauvegarde avant T28 se relit), valeurs hors bornes bornees. Une rotation illisible
     * (valeur non finie) ou une texture mal formee sont remplacees par leur defaut (log), sans perdre le corps.
     *
     * @return le corps, ou {@code null} (avec un log) si l'id ou le type manquent ou si une valeur est non finie
     */
    public static SpaceBody load(CompoundTag tag)
    {
        try {
            int id = tag.getInt("id");
            SpaceBodyKind kind = SpaceBodyKind.byName(tag.getString("kind"));
            if (id <= 0 || kind == null) {
                HoloLib.LOGGER.warn("Space : corps ignore (id {} ou type '{}' invalide)", id, tag.getString("kind"));
                return null;
            }
            String name = tag.contains("name", Tag.TAG_STRING) ? tag.getString("name") : "";
            if (SpaceLimits.sanitizeName(name).isEmpty()) name = kind.defaultName() + " " + id;
            int parentId = Math.max(0, tag.getInt("parent"));
            double radius = tag.contains("radius", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("radius") : kind.defaultRadius();
            int color = tag.contains("color", Tag.TAG_ANY_NUMERIC) ? tag.getInt("color") : kind.defaultColor();
            float luminosity = tag.contains("lum", Tag.TAG_ANY_NUMERIC) ? tag.getFloat("lum") : 1.0f;
            SpaceOrbit orbit = parentId == 0 ? null : SpaceOrbit.load(tag.getCompound("orbit"));
            String clean = SpaceLimits.sanitizeName(name);
            return new SpaceBody(id, kind, name, parentId, radius, color, luminosity,
                    loadMass(tag, id, clean, kind, radius), orbit, loadRotation(tag, id, clean, kind),
                    loadTexture(tag, id));
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : corps ignore ({})", e.getMessage());
            return null;
        }
    }

    /**
     * Masse du tag (cle {@code mass}, T39). Absente (corps sauvegarde avant T39) : masse reelle du corps par defaut de
     * meme nom exact ({@link SpaceKnownMasses}), sinon masse estimee ({@link #estimatedMass}). Presente : jamais
     * remplacee (bornee) ; non finie : masse estimee (log).
     */
    private static double loadMass(CompoundTag tag, int id, String name, SpaceBodyKind kind, double radius)
    {
        if (!tag.contains("mass", Tag.TAG_ANY_NUMERIC)) {
            double known = SpaceKnownMasses.byName(name);
            return Double.isNaN(known) ? estimatedMass(kind, radius) : known;
        }
        double mass = tag.getDouble("mass");
        if (Double.isFinite(mass)) return mass;
        HoloLib.LOGGER.warn("Space : masse du corps {} illisible ({}), masse estimee", id, mass);
        return estimatedMass(kind, radius);
    }

    /**
     * Rotation du tag (cle {@code spin}). Absente (corps sauvegarde avant T28) : rotation "auto" par nom, celle du corps
     * de Sol de meme nom exact ({@link SpaceSolRotations}), sinon defaut du type. Presente : jamais remplacee ; une cle
     * manquante prend la valeur du defaut du type, une valeur non finie donne le defaut du type (log).
     */
    private static SpaceRotation loadRotation(CompoundTag tag, int id, String name, SpaceBodyKind kind)
    {
        SpaceRotation fallback = SpaceRotation.defaultFor(kind);
        if (!tag.contains("spin", Tag.TAG_COMPOUND)) {
            SpaceRotation known = SpaceSolRotations.byName(name);
            return known != null ? known : fallback;
        }
        try {
            return SpaceRotation.load(tag.getCompound("spin"), fallback);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : rotation du corps {} illisible ({}), rotation par defaut", id, e.getMessage());
            return fallback;
        }
    }

    /** Texture du tag (cle {@code tex}), {@code auto} si absente ou mal formee. */
    private static SpaceTextureRef loadTexture(CompoundTag tag, int id)
    {
        if (!tag.contains("tex", Tag.TAG_STRING)) return SpaceTextureRef.AUTO;
        SpaceTextureRef texture = SpaceTextureRef.parse(tag.getString("tex"));
        if (texture == null) {
            HoloLib.LOGGER.warn("Space : texture '{}' du corps {} mal formee, texture auto", tag.getString("tex"), id);
            return SpaceTextureRef.AUTO;
        }
        return texture;
    }
}
