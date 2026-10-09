package com.amynna.hololib.modules.dimension.space.model;

import com.amynna.hololib.HoloLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Galaxie d'une dimension : liste <b>mutable</b> de systemes immuables (ordre d'insertion) et prochain id libre. Tenue
 * par le Space Environment seul (memoire de dimension), jamais exposee en ecriture : les autres lisent
 * {@link #systems()} (liste non modifiable d'elements immuables). Voir {@code docs/space/03-modele-donnees.md}.
 * <p>
 * Les operations d'edition (doc 03 par. 6) valident, appliquent et renvoient un {@link SpaceEditResult} ; elles ne
 * touchent ni a la revision ni a la synchronisation (role de l'Environment) et ne connaissent pas le systeme courant :
 * {@code setCurrentSystem} et le report du systeme courant apres un retrait sont faits par l'Environment. Fil serveur
 * uniquement ; non thread-safe.
 */
public final class SpaceGalaxy
{
    /** Kilometres par unite astronomique (meme valeur que {@code math/SpaceUnits}). */
    private static final double AU_KM = 149_597_870.7;
    /** Angle d'or, en degres : phases par defaut bien reparties. */
    private static final double GOLDEN_ANGLE = 137.50776405003785;
    /** Ecart entre l'apoapse la plus lointaine des freres et le demi-grand axe d'un corps ajoute. */
    private static final double ORBIT_SPACING = 1.6;
    /** Distance minimale au parent d'un corps ajoute, en rayons du parent. */
    private static final double PARENT_CLEARANCE = 3.0;

    private final List<SpaceSystem> systems = new ArrayList<>();
    private final List<SpaceSystem> systemsView = Collections.unmodifiableList(systems);
    private int nextSystemId = 1;

    /** Galaxie vide (l'Environment la remplit avec {@link #createDefault()} si elle le reste au chargement). */
    public SpaceGalaxy() {}

    // ---------------------------------------------------------------- lecture

    /** Systemes, ordre d'insertion ; vue non modifiable (refletant les modifications suivantes). */
    public List<SpaceSystem> systems() { return systemsView; }

    public int size() { return systems.size(); }

    public boolean isEmpty() { return systems.isEmpty(); }

    /** Prochain id de systeme libre (monotone, jamais reutilise). */
    public int nextSystemId() { return nextSystemId; }

    /** Systeme d'id {@code id}, {@code null} si absent. */
    public SpaceSystem system(int id)
    {
        int index = indexOf(id);
        return index < 0 ? null : systems.get(index);
    }

    public boolean contains(int id) { return indexOf(id) >= 0; }

    /** Indice du systeme d'id {@code id} dans {@link #systems()}, {@code -1} si absent. */
    public int indexOf(int id)
    {
        for (int i = 0; i < systems.size(); i++) if (systems.get(i).id() == id) return i;
        return -1;
    }

    /** En-tetes de tous les systemes, dans l'ordre (liste neuve non modifiable). */
    public List<SpaceSystem.SystemHeader> headers()
    {
        List<SpaceSystem.SystemHeader> headers = new ArrayList<>(systems.size());
        for (SpaceSystem system : systems) headers.add(system.header());
        return List.copyOf(headers);
    }

    /** Etoiles de fond : tous les systemes sauf {@code currentSystemId}, dans l'ordre (liste neuve non modifiable). */
    public List<BackgroundStar> backgroundStars(int currentSystemId)
    {
        List<BackgroundStar> stars = new ArrayList<>(systems.size());
        for (SpaceSystem system : systems) if (system.id() != currentSystemId) stars.add(BackgroundStar.of(system));
        return List.copyOf(stars);
    }

    // ---------------------------------------------------------------- operations : systemes

    /**
     * Ajoute un systeme avec une etoile par defaut (du nom du systeme). Nom vide apres nettoyage : "Systeme id".
     * Position bornee a {@link SpaceLimits#MAX_GALACTIC_DISTANCE}.
     *
     * @return {@link SpaceEditResult#created} avec l'id du systeme, ou un refus (position non finie, galaxie pleine)
     */
    public SpaceEditResult addSystem(String name, double x, double y, double z)
    {
        if (!SpaceLimits.finite(x, y, z)) return SpaceEditResult.refused("Position non finie");
        if (systems.size() >= SpaceLimits.MAX_SYSTEMS) {
            return SpaceEditResult.refused("Galaxie pleine (" + SpaceLimits.MAX_SYSTEMS + " systemes)");
        }
        int id = nextSystemId;
        String clean = SpaceLimits.sanitizeName(name);
        if (clean.isEmpty()) clean = "Systeme " + id;
        SpaceBody star = new SpaceBody(1, SpaceBodyKind.STAR, clean, 0, SpaceBodyKind.STAR.defaultRadius(),
                SpaceBodyKind.STAR.defaultColor(), 1.0f, null);
        systems.add(new SpaceSystem(id, clean, x, y, z, List.of(star), 2));
        nextSystemId++;
        return SpaceEditResult.created(id);
    }

    /**
     * Retire un systeme et tous ses corps. Refuse le dernier systeme (c'est forcement le systeme courant). Si le
     * systeme retire etait le courant, l'Environment passe au premier restant.
     */
    public SpaceEditResult removeSystem(int id)
    {
        int index = indexOf(id);
        if (index < 0) return unknownSystem(id);
        if (systems.size() <= 1) return SpaceEditResult.refused("Impossible de retirer le dernier systeme");
        systems.remove(index);
        return SpaceEditResult.ok();
    }

    /** Renomme un systeme ; refuse un nom vide apres nettoyage. */
    public SpaceEditResult renameSystem(int id, String name)
    {
        int index = indexOf(id);
        if (index < 0) return unknownSystem(id);
        String clean = SpaceLimits.sanitizeName(name);
        if (clean.isEmpty()) return SpaceEditResult.refused("Nom vide");
        systems.set(index, systems.get(index).withName(clean));
        return SpaceEditResult.ok();
    }

    /** Deplace un systeme (al) ; position bornee, refusee si non finie. */
    public SpaceEditResult moveSystem(int id, double x, double y, double z)
    {
        int index = indexOf(id);
        if (index < 0) return unknownSystem(id);
        if (!SpaceLimits.finite(x, y, z)) return SpaceEditResult.refused("Position non finie");
        systems.set(index, systems.get(index).withPosition(x, y, z));
        return SpaceEditResult.ok();
    }

    // ---------------------------------------------------------------- operations : corps

    /**
     * Ajoute un corps a la fin du systeme (donc apres son parent), avec les valeurs par defaut de son type : nom
     * "Type id", rayon, couleur, luminosite 1, et pour un corps sur orbite une orbite circulaire hors des orbites
     * existantes autour du meme parent (et a au moins 3 rayons du parent), periode de Kepler, phase repartie.
     *
     * @param parentId {@code 0} = sans parent (seulement une etoile)
     * @return {@link SpaceEditResult#created} avec l'id du corps, ou un refus (systeme ou parent inconnu, type de
     * parent incompatible, systeme plein)
     */
    public SpaceEditResult addBody(int systemId, SpaceBodyKind kind, int parentId)
    {
        int index = indexOf(systemId);
        if (index < 0) return unknownSystem(systemId);
        if (kind == null) return SpaceEditResult.refused("Type de corps manquant");
        SpaceSystem system = systems.get(index);
        if (system.bodies().size() >= SpaceLimits.MAX_BODIES) {
            return SpaceEditResult.refused("Systeme plein (" + SpaceLimits.MAX_BODIES + " corps)");
        }
        SpaceBody parent = null;
        if (parentId != 0) {
            parent = system.body(parentId);
            if (parent == null) return unknownBody(parentId);
        }
        if (!kind.acceptsParent(parent == null ? null : parent.kind())) {
            return SpaceEditResult.refused(parent == null ? kindName(kind) + " : un parent est requis"
                    : kindName(kind) + " ne peut pas orbiter " + kindName(parent.kind()));
        }
        int id = system.nextBodyId();
        SpaceOrbit orbit = parent == null ? null : defaultOrbit(system, kind, parent, id);
        SpaceBody body = new SpaceBody(id, kind, kind.defaultName() + " " + id, parentId, kind.defaultRadius(),
                kind.defaultColor(), 1.0f, orbit);
        systems.set(index, system.withAddedBody(body));
        return SpaceEditResult.created(id);
    }

    /**
     * Remplace les champs editables du corps de meme id ({@code body.id()}) : nom, rayon, couleur, luminosite,
     * orbite. {@code kind} et {@code parentId} de {@code body} sont ignores (immuables : ceux du corps existant sont
     * gardes). Refuse un nom vide, et une orbite absente pour un corps qui a un parent ; l'orbite recue pour un corps
     * sans parent est ignoree. Les valeurs sont deja bornees par le constructeur de {@link SpaceBody} (qui leve sur
     * une valeur non finie : le decodage d'une action invalide echoue avant d'arriver ici).
     */
    public SpaceEditResult updateBody(int systemId, SpaceBody body)
    {
        int index = indexOf(systemId);
        if (index < 0) return unknownSystem(systemId);
        if (body == null) return SpaceEditResult.refused("Corps manquant");
        SpaceSystem system = systems.get(index);
        SpaceBody current = system.body(body.id());
        if (current == null) return unknownBody(body.id());
        if (body.name().isEmpty()) return SpaceEditResult.refused("Nom vide");
        SpaceOrbit orbit = current.isRoot() ? null : body.orbit();
        if (!current.isRoot() && orbit == null) return SpaceEditResult.refused("Orbite manquante");
        SpaceBody updated = new SpaceBody(current.id(), current.kind(), body.name(), current.parentId(), body.radius(),
                body.color(), body.luminosity(), orbit);
        systems.set(index, system.withReplacedBody(updated));
        return SpaceEditResult.ok();
    }

    /**
     * Retire un corps et tous ses descendants. Refuse le dernier corps sans parent du systeme (un systeme garde
     * toujours son etoile principale).
     */
    public SpaceEditResult removeBody(int systemId, int bodyId)
    {
        int index = indexOf(systemId);
        if (index < 0) return unknownSystem(systemId);
        SpaceSystem system = systems.get(index);
        SpaceBody body = system.body(bodyId);
        if (body == null) return unknownBody(bodyId);
        if (body.isRoot() && system.rootCount() <= 1) {
            return SpaceEditResult.refused("Impossible de retirer la derniere etoile centrale du systeme");
        }
        systems.set(index, system.withoutBody(bodyId));
        return SpaceEditResult.ok();
    }

    // ---------------------------------------------------------------- valeurs par defaut

    /** Orbite circulaire d'un corps ajoute : hors des orbites des freres, loin du parent, periode de Kepler. */
    private static SpaceOrbit defaultOrbit(SpaceSystem system, SpaceBodyKind kind, SpaceBody parent, int id)
    {
        double a = Math.max(kind.defaultSemiMajorAxis(), PARENT_CLEARANCE * parent.radius() / AU_KM);
        for (SpaceBody sibling : system.bodies()) {
            if (sibling.parentId() == parent.id()) a = Math.max(a, sibling.orbit().apoapsis() * ORBIT_SPACING);
        }
        a = SpaceLimits.sanitizeSemiMajorAxis(a);
        double period = kind.referencePeriod() * Math.pow(a / kind.defaultSemiMajorAxis(), 1.5);
        return SpaceOrbit.circular(a, SpaceLimits.sanitizePeriod(period), id * GOLDEN_ANGLE);
    }

    private static String kindName(SpaceBodyKind kind) { return kind.defaultName(); }

    private static SpaceEditResult unknownSystem(int id) { return SpaceEditResult.refused("Systeme inconnu : " + id); }

    private static SpaceEditResult unknownBody(int id) { return SpaceEditResult.refused("Corps inconnu : " + id); }

    // ---------------------------------------------------------------- NBT

    /** Format : {@code { next, systems: [ ... ] }} (doc 03 par. 4). */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("next", nextSystemId);
        ListTag list = new ListTag();
        for (SpaceSystem system : systems) list.add(system.save());
        tag.put("systems", list);
        return tag;
    }

    /**
     * Lecture tolerante : un systeme invalide, en double ou au-dela de {@link SpaceLimits#MAX_SYSTEMS} est ignore avec
     * un log ; {@code next} est remonte au-dessus du plus grand id lu. Ne leve jamais ; une galaxie vide en resulte si
     * rien n'est lisible (l'Environment la remplace alors par {@link #createDefault()}).
     */
    public static SpaceGalaxy load(CompoundTag tag)
    {
        SpaceGalaxy galaxy = new SpaceGalaxy();
        int maxId = 0;
        for (Tag entry : tag.getList("systems", Tag.TAG_COMPOUND)) {
            SpaceSystem system = SpaceSystem.load((CompoundTag) entry);
            if (system == null) continue;
            if (galaxy.systems.size() >= SpaceLimits.MAX_SYSTEMS) {
                HoloLib.LOGGER.warn("Space : systeme {} ignore (galaxie pleine)", system.id());
                continue;
            }
            if (galaxy.contains(system.id())) {
                HoloLib.LOGGER.warn("Space : systeme {} ignore (id en double)", system.id());
                continue;
            }
            galaxy.systems.add(system);
            maxId = Math.max(maxId, system.id());
        }
        galaxy.nextSystemId = Math.max(Math.max(1, tag.getInt("next")), maxId + 1);
        return galaxy;
    }

    // ---------------------------------------------------------------- contenu par defaut

    /**
     * Galaxie de premiere installation (doc 03 par. 7) : Sol (id 1) a l'origine, avec le Soleil, une planete tellurique
     * bleue et sa lune, une planete rouge et une geante orangee (orbites proches du systeme solaire reel, phases
     * variees), puis 11 etoiles voisines reelles a leurs directions approchees (ascension droite / declinaison, axe
     * {@code +Y} vers le pole nord celeste), avec un seul corps chacune. Le systeme courant par defaut est Sol (id 1).
     */
    public static SpaceGalaxy createDefault()
    {
        SpaceGalaxy galaxy = new SpaceGalaxy();
        List<SpaceBody> sol = List.of(
                new SpaceBody(1, SpaceBodyKind.STAR, "Soleil", 0, 696_000.0, 0xFFFFE07A, 1.0f, null),
                new SpaceBody(2, SpaceBodyKind.PLANET, "Terre", 1, 6_371.0, 0xFF3A6FD8, 1.0f,
                        new SpaceOrbit(1.0, 0.0167, 102.9, 0.0, 0.0, 365.0, 100.0)),
                new SpaceBody(3, SpaceBodyKind.MOON, "Lune", 2, 1_737.0, 0xFFB4B4B4, 1.0f,
                        new SpaceOrbit(0.00257, 0.0549, 45.0, 5.14, 0.0, 27.3, 220.0)),
                new SpaceBody(4, SpaceBodyKind.PLANET, "Mars", 1, 3_390.0, 0xFFC1440E, 1.0f,
                        new SpaceOrbit(1.52, 0.0934, -23.9, 1.85, 0.0, 687.0, 300.0)),
                new SpaceBody(5, SpaceBodyKind.PLANET, "Jupiter", 1, 69_911.0, 0xFFD8A06A, 1.0f,
                        new SpaceOrbit(5.2, 0.0489, 14.7, 1.3, 0.0, 4_333.0, 40.0)));
        galaxy.systems.add(new SpaceSystem(1, "Sol", 0.0, 0.0, 0.0, sol, 6));
        // Nom, ascension droite (h), declinaison (deg), distance (al), luminosite, couleur, rayon (km).
        galaxy.addNeighbor("Alpha du Centaure", 14.66, -60.83, 4.37, 1.52f, 0xFFFFF1C8, 852_000.0);
        galaxy.addNeighbor("Barnard", 17.96, 4.69, 5.96, 0.0035f, 0xFFFF8A5C, 136_000.0);
        galaxy.addNeighbor("Wolf 359", 10.94, 7.01, 7.86, 0.0011f, 0xFFFF7448, 112_000.0);
        galaxy.addNeighbor("Lalande 21185", 11.06, 35.97, 8.31, 0.02f, 0xFFFF9C66, 274_000.0);
        galaxy.addNeighbor("Sirius", 6.75, -16.72, 8.60, 25.4f, 0xFFCAD8FF, 1_190_000.0);
        galaxy.addNeighbor("Epsilon Eridani", 3.55, -9.46, 10.47, 0.34f, 0xFFFFC27A, 512_000.0);
        galaxy.addNeighbor("61 Cygni", 21.12, 38.75, 11.40, 0.15f, 0xFFFFB46A, 460_000.0);
        galaxy.addNeighbor("Procyon", 7.66, 5.22, 11.46, 6.93f, 0xFFFFF4E8, 1_430_000.0);
        galaxy.addNeighbor("Tau Ceti", 1.73, -15.94, 11.91, 0.52f, 0xFFFFE9B0, 550_000.0);
        galaxy.addNeighbor("Altair", 19.85, 8.87, 16.73, 10.6f, 0xFFF0F4FF, 1_260_000.0);
        galaxy.addNeighbor("Vega", 18.62, 38.78, 25.04, 40.1f, 0xFFD6E2FF, 1_640_000.0);
        galaxy.nextSystemId = galaxy.systems.size() + 1;
        return galaxy;
    }

    /** Ajoute un systeme voisin a une etoile, place par ascension droite (heures) et declinaison (degres). */
    private void addNeighbor(String name, double rightAscension, double declination, double distance,
                             float luminosity, int color, double radius)
    {
        double ra = Math.toRadians(rightAscension * 15.0);
        double dec = Math.toRadians(declination);
        double horizontal = distance * Math.cos(dec);
        int id = systems.size() + 1;
        SpaceBody star = new SpaceBody(1, SpaceBodyKind.STAR, name, 0, radius, color, luminosity, null);
        systems.add(new SpaceSystem(id, name, horizontal * Math.cos(ra), distance * Math.sin(dec),
                horizontal * Math.sin(ra), List.of(star), 2));
    }

    // ---------------------------------------------------------------- etoile de fond

    /**
     * Vue reseau d'un systeme vu de loin : position galactique (al, en {@code float} : precision suffisante pour une
     * direction), couleur et luminosite de l'etoile principale. La direction et l'eclat vus depuis le systeme courant
     * sont calcules par le client.
     */
    public record BackgroundStar(float x, float y, float z, int color, float luminosity)
    {
        public BackgroundStar
        {
            if (!SpaceLimits.finite(x, y, z)) throw new IllegalArgumentException("Position non finie");
            color = SpaceLimits.sanitizeColor(color);
            luminosity = SpaceLimits.sanitizeLuminosity(luminosity);
        }

        /** Etoile de fond d'un systeme (son etoile principale). */
        public static BackgroundStar of(SpaceSystem system)
        {
            SpaceBody star = system.primaryStar();
            return new BackgroundStar((float) system.x(), (float) system.y(), (float) system.z(), star.color(),
                    star.luminosity());
        }

        // Ordre : x, y, z, color, lum.
        public void write(FriendlyByteBuf buf)
        {
            buf.writeFloat(x);
            buf.writeFloat(y);
            buf.writeFloat(z);
            buf.writeInt(color);
            buf.writeFloat(luminosity);
        }

        /** Lecture stricte. */
        public static BackgroundStar read(FriendlyByteBuf buf)
        {
            float x = buf.readFloat();
            float y = buf.readFloat();
            float z = buf.readFloat();
            int color = buf.readInt();
            float luminosity = buf.readFloat();
            return new BackgroundStar(x, y, z, color, luminosity);
        }

        /** Ecrit une liste d'etoiles de fond (nombre en varint, puis chaque etoile). */
        public static void writeList(FriendlyByteBuf buf, List<BackgroundStar> stars)
        {
            buf.writeVarInt(stars.size());
            for (BackgroundStar star : stars) star.write(buf);
        }

        /** Lecture stricte d'une liste : au plus {@link SpaceLimits#MAX_SYSTEMS}. Liste non modifiable. */
        public static List<BackgroundStar> readList(FriendlyByteBuf buf)
        {
            int count = buf.readVarInt();
            if (count < 0 || count > SpaceLimits.MAX_SYSTEMS) {
                throw new IllegalArgumentException("Nombre d'etoiles de fond : " + count);
            }
            List<BackgroundStar> stars = new ArrayList<>(count);
            for (int i = 0; i < count; i++) stars.add(read(buf));
            return List.copyOf(stars);
        }
    }
}
