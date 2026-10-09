package com.amynna.hololib.modules.dimension.space.model;

import com.amynna.hololib.HoloLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Systeme stellaire (immuable) : position dans la galaxie et corps. Voir {@code docs/space/03-modele-donnees.md}.
 * <p>
 * Invariants garantis par le constructeur (leve sinon) :
 * <ul>
 *     <li>{@code id > 0}, nom nettoye, position galactique de norme au plus {@link SpaceLimits#MAX_GALACTIC_DISTANCE}
 *     (ramenee dans la borne) ;</li>
 *     <li>1 a {@link SpaceLimits#MAX_BODIES} corps, ids uniques et tous {@code < nextBodyId} ;</li>
 *     <li><b>parent avant enfant</b> : le parent d'un corps le precede dans la liste (donc pas de cycle, et le premier
 *     corps est sans parent : l'etoile principale, au centre du systeme) ;</li>
 *     <li>types compatibles ({@link SpaceBodyKind#acceptsParent}).</li>
 * </ul>
 * Les modifications rendent un nouveau systeme ({@code withXxx}) ; seule {@link SpaceGalaxy} les applique.
 *
 * @param id         identifiant, unique dans la galaxie, jamais reutilise
 * @param x          position galactique, en annees-lumiere
 * @param bodies     corps, ordre d'insertion (parent avant enfant), liste non modifiable
 * @param nextBodyId prochain id de corps libre (monotone)
 */
public record SpaceSystem(int id, String name, double x, double y, double z, List<SpaceBody> bodies, int nextBodyId)
{
    public SpaceSystem
    {
        if (id <= 0) throw new IllegalArgumentException("Id de systeme invalide : " + id);
        name = SpaceLimits.sanitizeName(name);
        double factor = SpaceLimits.galacticPositionFactor(x, y, z);
        if (factor != 1.0) {
            x *= factor;
            y *= factor;
            z *= factor;
        }
        bodies = List.copyOf(bodies);
        String problem = problem(bodies, nextBodyId);
        if (problem != null) throw new IllegalArgumentException("Systeme " + id + " invalide : " + problem);
    }

    /**
     * Motif d'invalidite d'une liste de corps, {@code null} si elle respecte les invariants du systeme (voir la
     * documentation de la classe).
     */
    public static String problem(List<SpaceBody> bodies, int nextBodyId)
    {
        if (bodies.isEmpty()) return "aucun corps";
        if (bodies.size() > SpaceLimits.MAX_BODIES) return "trop de corps (" + bodies.size() + ")";
        for (int i = 0; i < bodies.size(); i++) {
            SpaceBody body = bodies.get(i);
            if (body == null) return "corps manquant";
            if (body.id() >= nextBodyId) return "id " + body.id() + " >= prochain id " + nextBodyId;
            SpaceBodyKind parentKind = null;
            if (!body.isRoot()) {
                int parent = indexOf(bodies, body.parentId(), i);
                if (parent < 0) return "parent " + body.parentId() + " absent ou place apres " + body.id();
                parentKind = bodies.get(parent).kind();
            }
            if (!body.kind().acceptsParent(parentKind)) return body.kind() + " ne peut pas orbiter " + parentKind;
            if (indexOf(bodies, body.id(), i) >= 0) return "id " + body.id() + " en double";
        }
        return null;
    }

    // ---------------------------------------------------------------- lecture

    /** Etoile principale : premier corps, sans parent, au centre du systeme. */
    public SpaceBody primaryStar() { return bodies.get(0); }

    /** Corps d'id {@code bodyId}, {@code null} si absent. */
    public SpaceBody body(int bodyId)
    {
        int index = indexOf(bodyId);
        return index < 0 ? null : bodies.get(index);
    }

    /** Indice du corps d'id {@code bodyId} dans {@link #bodies()}, {@code -1} si absent. */
    public int indexOf(int bodyId) { return indexOf(bodies, bodyId, bodies.size()); }

    /**
     * Indice du parent du corps d'indice {@code index}, {@code -1} s'il n'a pas de parent. Toujours inferieur a
     * {@code index} (parent avant enfant). Sans allocation.
     */
    public int parentIndex(int index)
    {
        int parentId = bodies.get(index).parentId();
        return parentId == 0 ? -1 : indexOf(bodies, parentId, index);
    }

    /** Nombre de corps sans parent. */
    public int rootCount()
    {
        int count = 0;
        for (SpaceBody body : bodies) if (body.isRoot()) count++;
        return count;
    }

    /** Vrai si le corps d'id {@code bodyId} est {@code ancestorId} ou l'un de ses descendants. */
    public boolean descendsFrom(int bodyId, int ancestorId)
    {
        int index = indexOf(bodyId);
        while (index >= 0) {
            if (bodies.get(index).id() == ancestorId) return true;
            index = parentIndex(index);
        }
        return false;
    }

    /** Vue reseau reduite (liste des systemes du Configurator). */
    public SystemHeader header()
    {
        SpaceBody star = primaryStar();
        return new SystemHeader(id, name, x, y, z, star.color(), star.luminosity());
    }

    // ---------------------------------------------------------------- copies modifiees

    public SpaceSystem withName(String name) { return new SpaceSystem(id, name, x, y, z, bodies, nextBodyId); }

    public SpaceSystem withPosition(double x, double y, double z)
    {
        return new SpaceSystem(id, name, x, y, z, bodies, nextBodyId);
    }

    /** Ajoute un corps a la fin ; son id doit valoir {@link #nextBodyId()}, qui avance. */
    public SpaceSystem withAddedBody(SpaceBody body)
    {
        if (body.id() != nextBodyId) throw new IllegalArgumentException("Id de corps attendu : " + nextBodyId);
        List<SpaceBody> list = new ArrayList<>(bodies);
        list.add(body);
        return new SpaceSystem(id, name, x, y, z, list, nextBodyId + 1);
    }

    /** Remplace le corps de meme id (meme place dans la liste). */
    public SpaceSystem withReplacedBody(SpaceBody body)
    {
        int index = indexOf(body.id());
        if (index < 0) throw new IllegalArgumentException("Corps absent : " + body.id());
        List<SpaceBody> list = new ArrayList<>(bodies);
        list.set(index, body);
        return new SpaceSystem(id, name, x, y, z, list, nextBodyId);
    }

    /** Retire le corps d'id {@code bodyId} et tous ses descendants (leve s'il ne reste aucun corps). */
    public SpaceSystem withoutBody(int bodyId)
    {
        List<SpaceBody> list = new ArrayList<>(bodies.size());
        for (SpaceBody body : bodies) if (!descendsFrom(body.id(), bodyId)) list.add(body);
        return new SpaceSystem(id, name, x, y, z, list, nextBodyId);
    }

    // ---------------------------------------------------------------- reseau
    // Ordre : id, name, x, y, z, next, nombre de corps, corps.

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(id);
        buf.writeUtf(name, SpaceLimits.MAX_NAME_LENGTH);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(nextBodyId);
        buf.writeVarInt(bodies.size());
        for (SpaceBody body : bodies) body.write(buf);
    }

    /** Lecture stricte : leve sur toute valeur invalide ou tout invariant viole. */
    public static SpaceSystem read(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        String name = buf.readUtf(SpaceLimits.MAX_NAME_LENGTH);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int nextBodyId = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 1 || count > SpaceLimits.MAX_BODIES) throw new IllegalArgumentException("Nombre de corps : " + count);
        List<SpaceBody> bodies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) bodies.add(SpaceBody.read(buf));
        return new SpaceSystem(id, name, x, y, z, bodies, nextBodyId);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putInt("next", nextBodyId);
        ListTag list = new ListTag();
        for (SpaceBody body : bodies) list.add(body.save());
        tag.put("bodies", list);
        return tag;
    }

    /**
     * Lecture tolerante : un corps invalide (illisible, id en double, parent absent ou place apres lui, type
     * incompatible, au-dela de {@link SpaceLimits#MAX_BODIES}) est ignore avec un log, ses descendants aussi (leur
     * parent manque). {@code next} est remonte au-dessus du plus grand id lu.
     *
     * @return le systeme, ou {@code null} (avec un log) si l'id est invalide ou si aucun corps n'est valide
     */
    public static SpaceSystem load(CompoundTag tag)
    {
        try {
            int id = tag.getInt("id");
            if (id <= 0) {
                HoloLib.LOGGER.warn("Space : systeme ignore (id {} invalide)", id);
                return null;
            }
            String name = tag.contains("name", Tag.TAG_STRING) ? tag.getString("name") : "";
            if (SpaceLimits.sanitizeName(name).isEmpty()) name = "Systeme " + id;
            double x = tag.getDouble("x");
            double y = tag.getDouble("y");
            double z = tag.getDouble("z");
            if (!SpaceLimits.finite(x, y, z)) {
                HoloLib.LOGGER.warn("Space : position non finie du systeme {}, remise a l'origine", id);
                x = y = z = 0.0;
            }
            List<SpaceBody> bodies = new ArrayList<>();
            int maxId = 0;
            for (Tag entry : tag.getList("bodies", Tag.TAG_COMPOUND)) {
                SpaceBody body = SpaceBody.load((CompoundTag) entry);
                if (body == null) continue;
                String reason = acceptance(bodies, body);
                if (reason != null) {
                    HoloLib.LOGGER.warn("Space : corps {} du systeme {} ignore ({})", body.id(), id, reason);
                    continue;
                }
                bodies.add(body);
                maxId = Math.max(maxId, body.id());
            }
            if (bodies.isEmpty()) {
                HoloLib.LOGGER.warn("Space : systeme {} ignore (aucun corps valide)", id);
                return null;
            }
            int next = Math.max(tag.getInt("next"), maxId + 1);
            return new SpaceSystem(id, name, x, y, z, bodies, next);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : systeme ignore ({})", e.getMessage());
            return null;
        }
    }

    /** Motif de refus d'un corps ajoute a la fin de {@code accepted} au chargement, {@code null} s'il convient. */
    private static String acceptance(List<SpaceBody> accepted, SpaceBody body)
    {
        if (accepted.size() >= SpaceLimits.MAX_BODIES) return "trop de corps";
        if (indexOf(accepted, body.id(), accepted.size()) >= 0) return "id en double";
        SpaceBodyKind parentKind = null;
        if (!body.isRoot()) {
            int parent = indexOf(accepted, body.parentId(), accepted.size());
            if (parent < 0) return "parent " + body.parentId() + " absent";
            parentKind = accepted.get(parent).kind();
        }
        if (!body.kind().acceptsParent(parentKind)) return body.kind() + " ne peut pas orbiter " + parentKind;
        return null;
    }

    /** Indice du corps d'id {@code bodyId} parmi les {@code limit} premiers, {@code -1} sinon. */
    private static int indexOf(List<SpaceBody> bodies, int bodyId, int limit)
    {
        for (int i = 0; i < limit; i++) if (bodies.get(i).id() == bodyId) return i;
        return -1;
    }

    // ---------------------------------------------------------------- en-tete

    /**
     * Vue reseau d'un systeme sans ses corps : id, nom, position (al, {@code double}), couleur et luminosite de
     * l'etoile principale. Utilisee par le Configurator pour la liste de tous les systemes.
     */
    public record SystemHeader(int id, String name, double x, double y, double z, int starColor,
                               float starLuminosity)
    {
        public SystemHeader
        {
            if (id <= 0) throw new IllegalArgumentException("Id de systeme invalide : " + id);
            name = SpaceLimits.sanitizeName(name);
            double factor = SpaceLimits.galacticPositionFactor(x, y, z);
            if (factor != 1.0) {
                x *= factor;
                y *= factor;
                z *= factor;
            }
            starColor = SpaceLimits.sanitizeColor(starColor);
            starLuminosity = SpaceLimits.sanitizeLuminosity(starLuminosity);
        }

        // Ordre : id, name, x, y, z, color, lum.
        public void write(FriendlyByteBuf buf)
        {
            buf.writeVarInt(id);
            buf.writeUtf(name, SpaceLimits.MAX_NAME_LENGTH);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeInt(starColor);
            buf.writeFloat(starLuminosity);
        }

        /** Lecture stricte. */
        public static SystemHeader read(FriendlyByteBuf buf)
        {
            int id = buf.readVarInt();
            String name = buf.readUtf(SpaceLimits.MAX_NAME_LENGTH);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int color = buf.readInt();
            float luminosity = buf.readFloat();
            return new SystemHeader(id, name, x, y, z, color, luminosity);
        }

        /** Ecrit une liste d'en-tetes (nombre en varint, puis chaque en-tete). */
        public static void writeList(FriendlyByteBuf buf, List<SystemHeader> headers)
        {
            buf.writeVarInt(headers.size());
            for (SystemHeader header : headers) header.write(buf);
        }

        /** Lecture stricte d'une liste : au plus {@link SpaceLimits#MAX_SYSTEMS}. Liste non modifiable. */
        public static List<SystemHeader> readList(FriendlyByteBuf buf)
        {
            int count = buf.readVarInt();
            if (count < 0 || count > SpaceLimits.MAX_SYSTEMS) {
                throw new IllegalArgumentException("Nombre de systemes : " + count);
            }
            List<SystemHeader> headers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) headers.add(read(buf));
            return List.copyOf(headers);
        }
    }
}
