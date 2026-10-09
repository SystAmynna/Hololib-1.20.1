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
 *
 * @param id         identifiant, unique dans le systeme, jamais reutilise
 * @param parentId   id du parent, {@code 0} = aucun
 * @param radius     rayon, en km
 * @param color      couleur ARGB (alpha force a {@code 0xFF})
 * @param luminosity luminosite relative au Soleil ; n'a de sens que pour une etoile (ignoree sinon)
 * @param orbit      orbite autour du parent, {@code null} si {@code parentId == 0}
 */
public record SpaceBody(int id, SpaceBodyKind kind, String name, int parentId, double radius, int color,
                        float luminosity, SpaceOrbit orbit)
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
    }

    /** Vrai si le corps n'a pas de parent (centre du systeme). */
    public boolean isRoot() { return parentId == 0; }

    // ---------------------------------------------------------------- copies modifiees (champs editables)

    public SpaceBody withName(String name)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
    }

    public SpaceBody withRadius(double radius)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
    }

    public SpaceBody withColor(int color)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
    }

    public SpaceBody withLuminosity(float luminosity)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
    }

    /** Leve si {@code orbit} ne respecte pas l'invariant de parente (orbite requise ssi parent). */
    public SpaceBody withOrbit(SpaceOrbit orbit)
    {
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
    }

    // ---------------------------------------------------------------- reseau
    // Ordre : id, kind, name, parent, radius, color, lum, [orbite si parent != 0].

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(id);
        buf.writeByte(kind.ordinal());
        buf.writeUtf(name, SpaceLimits.MAX_NAME_LENGTH);
        buf.writeVarInt(parentId);
        buf.writeDouble(radius);
        buf.writeInt(color);
        buf.writeFloat(luminosity);
        if (orbit != null) orbit.write(buf);
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
        SpaceOrbit orbit = parentId != 0 ? SpaceOrbit.read(buf) : null;
        return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
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
        if (orbit != null) tag.put("orbit", orbit.save());
        return tag;
    }

    /**
     * Lecture tolerante : cles manquantes = valeurs par defaut du type (orbite par defaut si un parent est indique
     * sans orbite ; orbite ignoree sans parent), valeurs hors bornes bornees.
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
            return new SpaceBody(id, kind, name, parentId, radius, color, luminosity, orbit);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : corps ignore ({})", e.getMessage());
            return null;
        }
    }
}
