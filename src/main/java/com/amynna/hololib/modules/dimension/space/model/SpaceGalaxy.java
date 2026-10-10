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
     * Galaxie de premiere installation (doc 03 par. 7, refaite en T16 : decision D15). Toutes les valeurs sont
     * recopiees de {@code docs/space/donnees-systemes.md} (sources, conventions, objets non retenus et motifs) :
     * <ul>
     *     <li><b>Sol</b> (id 1, systeme courant par defaut) a l'origine galactique, 34 corps : le Soleil, les 8
     *     planetes, 5 objets mineurs ({@link SpaceBodyKind#MINOR} : Ceres, Pluton, Eris, Haumea, Makemake) et
     *     20 lunes majeures (dont Charon autour de Pluton) ;</li>
     *     <li>les <b>11 systemes voisins</b> du lot 1, aux memes positions (ascension droite / declinaison, axe
     *     {@code +Y} vers le pole nord celeste), avec leurs etoiles compagnes (orbites {@code STAR -> STAR}) et leurs
     *     planetes confirmees au 2026-10-09.</li>
     * </ul>
     * Conventions de l'annexe : orientation d'une orbite = {@code yaw} longitude du periastre (inconnue : 0),
     * {@code pitch} inclinaison, {@code roll} 0 (noeud ascendant non modelise) ; pour une lune, {@code pitch} =
     * obliquite du parent + inclinaison sur son equateur (lunes d'Uranus presque verticales, Triton retrograde) ; pour
     * une compagne ou une exoplanete, inclinaison sur le plan du ciel (0 si inconnue). Phases : anomalie moyenne J2000
     * approchee pour les planetes de Sol et Pluton, arbitraires ailleurs. Rayons d'exoplanetes sans transit
     * <b>estimes</b> (relation masse-rayon de l'archive NASA, a partir de la masse minimale). Noms sans accents (C15).
     * Ids des corps dans l'ordre de l'annexe (l'etoile principale en premier, id 1), ids des systemes de 1 a 12.
     * <p>
     * Une galaxie deja sauvegardee n'est jamais remplacee (C51) : seule une nouvelle installation sans archive
     * (T14) utilise ce contenu.
     */
    public static SpaceGalaxy createDefault()
    {
        SpaceGalaxy galaxy = new SpaceGalaxy();
        galaxy.systems.add(sol().build(1, 0.0, 0.0, 0.0));
        // Ascension droite (h), declinaison (deg), distance (al) : positions du lot 1, inchangees.
        galaxy.addNeighbor(alphaCentauri(), 14.66, -60.83, 4.37);
        galaxy.addNeighbor(barnard(), 17.96, 4.69, 5.96);
        galaxy.addNeighbor(wolf359(), 10.94, 7.01, 7.86);
        galaxy.addNeighbor(lalande21185(), 11.06, 35.97, 8.31);
        galaxy.addNeighbor(sirius(), 6.75, -16.72, 8.60);
        galaxy.addNeighbor(epsilonEridani(), 3.55, -9.46, 10.47);
        galaxy.addNeighbor(cygni61(), 21.12, 38.75, 11.40);
        galaxy.addNeighbor(procyon(), 7.66, 5.22, 11.46);
        galaxy.addNeighbor(tauCeti(), 1.73, -15.94, 11.91);
        galaxy.addNeighbor(altair(), 19.85, 8.87, 16.73);
        galaxy.addNeighbor(vega(), 18.62, 38.78, 25.04);
        galaxy.nextSystemId = galaxy.systems.size() + 1;
        return galaxy;
    }

    /** Ajoute un systeme voisin, place par ascension droite (heures) et declinaison (degres) a {@code distance} al. */
    private void addNeighbor(DefaultSystem system, double rightAscension, double declination, double distance)
    {
        double ra = Math.toRadians(rightAscension * 15.0);
        double dec = Math.toRadians(declination);
        double horizontal = distance * Math.cos(dec);
        systems.add(system.build(systems.size() + 1, horizontal * Math.cos(ra), distance * Math.sin(dec),
                horizontal * Math.sin(ra)));
    }

    /**
     * Sol, 34 corps. Sources : NASA Planetary Fact Sheets (https://nssdc.gsfc.nasa.gov/planetary/factsheet/), JPL SSD
     * (elements moyens J2000, https://ssd.jpl.nasa.gov/planets/approx_pos.html), JPL Small-Body Database (Eris,
     * Haumea, Makemake), JPL Planetary Satellite Mean Elements (https://ssd.jpl.nasa.gov/sats/elem/). Objets mineurs :
     * {@code yaw = Omega + omega}. Lunes : obliquites des parents Mars 25,19, Jupiter 3,13, Saturne 26,73, Uranus
     * 97,77, Neptune 28,32, Pluton 119,6 (la Lune est donnee par rapport a l'ecliptique) ; demi-grands axes recopies
     * en UA ({@code a_km / 149 597 870,7}).
     */
    private static DefaultSystem sol()
    {
        DefaultSystem s = new DefaultSystem("Sol");
        // Etoile : nom, rayon (km), couleur, luminosite.
        int soleil = s.star("Soleil", 695_700.0, 0xFFFFE07A, 1.0f);
        // Planetes, puis objets mineurs : nom, parent, rayon (km), couleur, a (UA), e, yaw, pitch, periode (j), phase.
        s.planet("Mercure", soleil, 2_439.7, 0xFF9A9A9A, 0.3871, 0.2056, 77.46, 7.00, 87.969, 174.8);
        s.planet("Venus", soleil, 6_051.8, 0xFFE6C88A, 0.7233, 0.0068, 131.53, 3.39, 224.701, 50.1);
        int terre = s.planet("Terre", soleil, 6_371.0, 0xFF3A6FD8, 1.0000, 0.0167, 102.94, 0.00, 365.256, 357.5);
        int mars = s.planet("Mars", soleil, 3_389.5, 0xFFC1440E, 1.5237, 0.0934, 336.04, 1.85, 686.980, 19.4);
        int jupiter = s.planet("Jupiter", soleil, 69_911.0, 0xFFD8A06A, 5.2029, 0.0489, 14.75, 1.30, 4_332.59, 20.0);
        int saturne = s.planet("Saturne", soleil, 58_232.0, 0xFFE3CB8F, 9.5367, 0.0565, 92.43, 2.49, 10_759.22, 317.0);
        int uranus = s.planet("Uranus", soleil, 25_362.0, 0xFF9FD8E0, 19.189, 0.0457, 170.96, 0.77, 30_688.5, 142.2);
        int neptune = s.planet("Neptune", soleil, 24_622.0, 0xFF3E66D6, 30.070, 0.0113, 44.97, 1.77, 60_182.0, 256.2);

        s.minor("Ceres", soleil, 469.7, 0xFF8C8C88, 2.767, 0.0758, 153.9, 10.59, 1_680.0, 95.0);
        int pluton = s.minor("Pluton", soleil, 1_188.3, 0xFFD9B58F, 39.48, 0.2488, 224.07, 17.16, 90_560.0, 14.5);
        s.minor("Eris", soleil, 1_163.0, 0xFFE8E8E8, 67.86, 0.4407, 187.6, 44.04, 203_830.0, 205.0);
        // Haumea : rayon moyen (corps tres allonge).
        s.minor("Haumea", soleil, 780.0, 0xFFDADADA, 43.22, 0.191, 0.9, 28.2, 103_774.0, 218.0);
        s.minor("Makemake", soleil, 715.0, 0xFFC98E6B, 45.43, 0.159, 16.6, 28.98, 111_845.0, 165.0);

        // Lunes : nom, parent, rayon (km), couleur, a (UA), e, pitch, periode (j), phase (arbitraire).
        s.moon("Lune", terre, 1_737.4, 0xFFB4B4B4, 0.0025696, 0.0549, 5.145, 27.322, 220.0);
        s.moon("Phobos", mars, 11.27, 0xFF7A6A5A, 0.00006268, 0.0151, 26.27, 0.3189, 10.0);
        s.moon("Deimos", mars, 6.2, 0xFF8A7A6A, 0.00015684, 0.0003, 26.98, 1.2624, 190.0);
        s.moon("Io", jupiter, 1_821.6, 0xFFE8D85A, 0.0028189, 0.0041, 3.18, 1.7691, 30.0);
        s.moon("Europe", jupiter, 1_560.8, 0xFFD9CBB0, 0.0044856, 0.009, 3.60, 3.5512, 120.0);
        s.moon("Ganymede", jupiter, 2_634.1, 0xFF9C8F80, 0.0071553, 0.0013, 3.33, 7.1546, 250.0);
        s.moon("Callisto", jupiter, 2_410.3, 0xFF6E6358, 0.0125851, 0.0074, 3.32, 16.689, 330.0);
        s.moon("Encelade", saturne, 252.1, 0xFFF4F8FF, 0.0015906, 0.0047, 26.74, 1.3702, 60.0);
        s.moon("Tethys", saturne, 531.1, 0xFFDCDCDC, 0.0019694, 0.0001, 27.85, 1.8878, 150.0);
        s.moon("Dione", saturne, 561.4, 0xFFC8C8C8, 0.0025227, 0.0022, 26.76, 2.7369, 240.0);
        s.moon("Rhea", saturne, 763.8, 0xFFBDBDBD, 0.0035235, 0.0013, 27.08, 4.5182, 300.0);
        s.moon("Titan", saturne, 2_574.7, 0xFFD9A74A, 0.0081677, 0.0288, 27.08, 15.945, 20.0);
        s.moon("Japet", saturne, 734.5, 0xFF8F8270, 0.0238026, 0.0286, 42.20, 79.321, 110.0);
        s.moon("Miranda", uranus, 235.8, 0xFFB0B0B0, 0.0008649, 0.0013, 102.00, 1.4135, 200.0);
        s.moon("Ariel", uranus, 578.9, 0xFFBEBEBE, 0.0012769, 0.0012, 98.03, 2.5204, 280.0);
        s.moon("Umbriel", uranus, 584.7, 0xFF707070, 0.0017801, 0.0039, 97.90, 4.1442, 40.0);
        s.moon("Titania", uranus, 788.4, 0xFFB4A89C, 0.0029139, 0.0011, 98.11, 8.7059, 160.0);
        s.moon("Oberon", uranus, 761.4, 0xFF9E9086, 0.0039006, 0.0014, 97.83, 13.463, 260.0);
        // Triton : retrograde (inclinaison 156,885 sur l'equateur de Neptune).
        s.moon("Triton", neptune, 1_353.4, 0xFFE0C8C0, 0.0023714, 0.000016, -174.80, 5.8769, 70.0);
        s.moon("Charon", pluton, 606.0, 0xFF9A9090, 0.00013096, 0.0002, 119.68, 6.3872, 0.0);
        return s;
    }

    /**
     * Alpha du Centaure, 5 corps. Sources : https://en.wikipedia.org/wiki/Alpha_Centauri,
     * https://en.wikipedia.org/wiki/Proxima_Centauri, NASA Exoplanet Archive (Proxima b ; Proxima d, confirmee par
     * NIRPS en 2025). Rayons de Proxima b et d estimes (1,02 et 0,692 rayon terrestre).
     */
    private static DefaultSystem alphaCentauri()
    {
        DefaultSystem s = new DefaultSystem("Alpha du Centaure");
        int a = s.star("Alpha du Centaure A", 851_100.0, 0xFFFFF1C8, 1.52f);
        // Compagnes : nom, parent, rayon (km), couleur, luminosite, a (UA), e, pitch, periode (j), phase (arbitraire).
        s.companion("Alpha du Centaure B", a, 600_500.0, 0xFFFFD8A0, 0.50f, 23.3, 0.5195, 79.2, 29_133.0, 0.0);
        int proxima = s.companion("Proxima du Centaure", a, 107_300.0, 0xFFFF6A3C, 0.0016f, 8_700.0, 0.50, 107.6,
                199_790_000.0, 180.0);
        // Planetes : nom, parent, rayon (km), couleur, a (UA), e, yaw, pitch, periode (j), phase (arbitraire).
        s.planet("Proxima b", proxima, 6_500.0, 0xFF9C7A5A, 0.04848, 0.0, 0.0, 0.0, 11.18465, 40.0);
        s.planet("Proxima d", proxima, 4_410.0, 0xFF8A7060, 0.02881, 0.0, 0.0, 0.0, 5.12338, 200.0);
        return s;
    }

    /**
     * Barnard, 5 corps. Sources : NASA Exoplanet Archive (b : 2024, ESPRESSO ; c, d, e : Basant et al. 2025, ApJL),
     * https://www.astronomy.com/science/three-new-planets-found-around-barnards-star. Rayons des planetes estimes ;
     * inclinaisons inconnues (0).
     */
    private static DefaultSystem barnard()
    {
        DefaultSystem s = new DefaultSystem("Barnard");
        int star = s.star("Barnard", 130_100.0, 0xFFFF8A5C, 0.0035f);
        s.planet("Barnard d", star, 4_420.0, 0xFF8C6E5A, 0.0188, 0.04, 0.0, 0.0, 2.3402, 0.0);
        s.planet("Barnard b", star, 4_590.0, 0xFF9A7A62, 0.0229, 0.03, 0.0, 0.0, 3.1542, 90.0);
        s.planet("Barnard c", star, 4_730.0, 0xFF86705E, 0.0274, 0.08, 0.0, 0.0, 4.1244, 180.0);
        s.planet("Barnard e", star, 4_060.0, 0xFF7E6C60, 0.0381, 0.04, 0.0, 0.0, 6.7392, 270.0);
        return s;
    }

    /** Wolf 359, 1 corps (aucune planete confirmee). Source : https://en.wikipedia.org/wiki/Wolf_359. */
    private static DefaultSystem wolf359()
    {
        DefaultSystem s = new DefaultSystem("Wolf 359");
        s.star("Wolf 359", 100_200.0, 0xFFFF7448, 0.0011f);
        return s;
    }

    /**
     * Lalande 21185, 3 corps. Sources : NASA Exoplanet Archive (GJ 411 b, 2019 ; HD 95735 c, 2021),
     * https://en.wikipedia.org/wiki/Lalande_21185. Rayons des planetes estimes ; inclinaisons inconnues (0).
     */
    private static DefaultSystem lalande21185()
    {
        DefaultSystem s = new DefaultSystem("Lalande 21185");
        int star = s.star("Lalande 21185", 272_700.0, 0xFFFF9C66, 0.021f);
        s.planet("Lalande 21185 b", star, 9_240.0, 0xFFA08A70, 0.07879, 0.063, 0.0, 0.0, 12.9394, 60.0);
        s.planet("Lalande 21185 c", star, 23_950.0, 0xFF7FA6C8, 2.94, 0.132, 0.0, 0.0, 2_946.0, 230.0);
        return s;
    }

    /** Sirius, 2 corps. Sources : https://en.wikipedia.org/wiki/Sirius, https://en.wikipedia.org/wiki/Sirius_B. */
    private static DefaultSystem sirius()
    {
        DefaultSystem s = new DefaultSystem("Sirius");
        int a = s.star("Sirius A", 1_190_000.0, 0xFFCAD8FF, 25.4f);
        // Sirius B : naine blanche.
        s.companion("Sirius B", a, 5_634.0, 0xFFE8EEFF, 0.056f, 19.8, 0.5914, 136.3, 18_309.0, 120.0);
        return s;
    }

    /**
     * Epsilon Eridani, 2 corps. Sources : NASA Exoplanet Archive (eps Eri b : a, e, i, periode ; rayon estime),
     * https://en.wikipedia.org/wiki/Epsilon_Eridani_b (orbite revisee en 2025).
     */
    private static DefaultSystem epsilonEridani()
    {
        DefaultSystem s = new DefaultSystem("Epsilon Eridani");
        int star = s.star("Epsilon Eridani", 511_300.0, 0xFFFFC27A, 0.34f);
        s.planet("Epsilon Eridani b", star, 87_900.0, 0xFFC8A47A, 3.53, 0.06, 0.0, 40.0, 2_680.0, 300.0);
        return s;
    }

    /** 61 Cygni, 2 corps. Source : https://en.wikipedia.org/wiki/61_Cygni (ajustement le plus recent de l'orbite). */
    private static DefaultSystem cygni61()
    {
        DefaultSystem s = new DefaultSystem("61 Cygni");
        int a = s.star("61 Cygni A", 462_600.0, 0xFFFFB46A, 0.153f);
        s.companion("61 Cygni B", a, 413_900.0, 0xFFFFA860, 0.085f, 86.8, 0.442, 53.0, 258_260.0, 45.0);
        return s;
    }

    /**
     * Procyon, 2 corps. Sources : Bond et al. 2015 (ApJ 813, 106 : periode, masses),
     * https://en.wikipedia.org/wiki/Procyon (a, e, rayon de B).
     */
    private static DefaultSystem procyon()
    {
        DefaultSystem s = new DefaultSystem("Procyon");
        int a = s.star("Procyon A", 1_424_800.0, 0xFFFFF4E8, 6.93f);
        // Procyon B : naine blanche.
        s.companion("Procyon B", a, 8_585.0, 0xFFF0F0FF, 0.00049f, 15.14, 0.3979, 31.1, 14_917.0, 210.0);
        return s;
    }

    /**
     * Tau Ceti, 4 corps. Source : NASA Exoplanet Archive, table {@code pscomppars} (tau Cet g, h, f ; Feng et al.
     * 2017 ; tau Cet e n'y figure plus). Rayons des planetes estimes ; inclinaisons inconnues (0).
     */
    private static DefaultSystem tauCeti()
    {
        DefaultSystem s = new DefaultSystem("Tau Ceti");
        int star = s.star("Tau Ceti", 551_700.0, 0xFFFFE9B0, 0.52f);
        s.planet("Tau Ceti g", star, 7_520.0, 0xFFB08A6A, 0.133, 0.06, 0.0, 0.0, 20.00, 15.0);
        s.planet("Tau Ceti h", star, 7_580.0, 0xFF9C8C78, 0.243, 0.23, 0.0, 0.0, 49.41, 130.0);
        s.planet("Tau Ceti f", star, 11_530.0, 0xFF8AA0B8, 1.334, 0.16, 0.0, 0.0, 636.13, 250.0);
        return s;
    }

    /** Altair, 1 corps (aucune planete connue ; etoile du lot 1 inchangee). */
    private static DefaultSystem altair()
    {
        DefaultSystem s = new DefaultSystem("Altair");
        s.star("Altair", 1_260_000.0, 0xFFF0F4FF, 10.6f);
        return s;
    }

    /** Vega, 1 corps (aucune planete confirmee ; etoile du lot 1 inchangee). */
    private static DefaultSystem vega()
    {
        DefaultSystem s = new DefaultSystem("Vega");
        s.star("Vega", 1_640_000.0, 0xFFD6E2FF, 40.1f);
        return s;
    }

    /**
     * Systeme par defaut en construction : un appel par corps, dans l'ordre de l'annexe ; chaque appel rend l'id du
     * corps cree (1, 2, 3... dans l'ordre des appels), a passer comme parent aux corps suivants. Les angles sont en
     * degres, les rayons en km, les demi-grands axes en UA, les periodes en jours. Les planetes n'utilisent pas la
     * luminosite (1).
     */
    private static final class DefaultSystem
    {
        private final String name;
        private final List<SpaceBody> bodies = new ArrayList<>();

        DefaultSystem(String name) { this.name = name; }

        /** Etoile principale, au centre du systeme (premier appel). */
        int star(String name, double radius, int color, float luminosity)
        {
            return add(SpaceBodyKind.STAR, name, 0, radius, color, luminosity, null);
        }

        /** Etoile compagne en orbite autour de l'etoile {@code parent} ({@code yaw} et {@code roll} inconnus : 0). */
        int companion(String name, int parent, double radius, int color, float luminosity, double a, double e,
                      double pitch, double period, double phase)
        {
            return add(SpaceBodyKind.STAR, name, parent, radius, color, luminosity,
                    new SpaceOrbit(a, e, 0.0, pitch, 0.0, period, phase));
        }

        /** Planete autour de l'etoile {@code parent} ; {@code yaw} = longitude du periastre. */
        int planet(String name, int parent, double radius, int color, double a, double e, double yaw, double pitch,
                   double period, double phase)
        {
            return add(SpaceBodyKind.PLANET, name, parent, radius, color, 1.0f,
                    new SpaceOrbit(a, e, yaw, pitch, 0.0, period, phase));
        }

        /** Objet mineur autour de l'etoile {@code parent} ; {@code yaw} = longitude du perihelie. */
        int minor(String name, int parent, double radius, int color, double a, double e, double yaw, double pitch,
                  double period, double phase)
        {
            return add(SpaceBodyKind.MINOR, name, parent, radius, color, 1.0f,
                    new SpaceOrbit(a, e, yaw, pitch, 0.0, period, phase));
        }

        /**
         * Lune autour de la planete ou de l'objet mineur {@code parent} ; {@code pitch} = obliquite du parent +
         * inclinaison sur son equateur ; {@code yaw} inconnu : 0.
         */
        int moon(String name, int parent, double radius, int color, double a, double e, double pitch, double period,
                 double phase)
        {
            return add(SpaceBodyKind.MOON, name, parent, radius, color, 1.0f,
                    new SpaceOrbit(a, e, 0.0, pitch, 0.0, period, phase));
        }

        private int add(SpaceBodyKind kind, String name, int parent, double radius, int color, float luminosity,
                        SpaceOrbit orbit)
        {
            int id = bodies.size() + 1;
            bodies.add(new SpaceBody(id, kind, name, parent, radius, color, luminosity, orbit));
            return id;
        }

        /** Systeme d'id {@code id} a la position galactique donnee (al) ; prochain id de corps = corps + 1. */
        SpaceSystem build(int id, double x, double y, double z)
        {
            return new SpaceSystem(id, name, x, y, z, bodies, bodies.size() + 1);
        }
    }

    // ---------------------------------------------------------------- etoile de fond

    /**
     * Vue reseau d'un systeme vu de loin : identite du systeme (id et nom, T15 : surcouche du pilote, cible du saut),
     * position galactique (al, en {@code float} : precision suffisante pour une direction), couleur et luminosite de
     * l'etoile principale. La direction et l'eclat vus depuis le systeme courant sont calcules par le client.
     *
     * @param id   id du systeme ({@code > 0}), unique dans la galaxie
     * @param name nom du systeme, nettoye ({@link SpaceLimits#sanitizeName})
     */
    public record BackgroundStar(int id, String name, float x, float y, float z, int color, float luminosity)
    {
        public BackgroundStar
        {
            if (id <= 0) throw new IllegalArgumentException("Id de systeme invalide : " + id);
            name = SpaceLimits.sanitizeName(name);
            if (!SpaceLimits.finite(x, y, z)) throw new IllegalArgumentException("Position non finie");
            color = SpaceLimits.sanitizeColor(color);
            luminosity = SpaceLimits.sanitizeLuminosity(luminosity);
        }

        /** Etoile de fond d'un systeme (son id, son nom, son etoile principale). */
        public static BackgroundStar of(SpaceSystem system)
        {
            SpaceBody star = system.primaryStar();
            return new BackgroundStar(system.id(), system.name(), (float) system.x(), (float) system.y(),
                    (float) system.z(), star.color(), star.luminosity());
        }

        /**
         * Ordre : id (varint), name (UTF, au plus {@link SpaceLimits#MAX_NAME_LENGTH} caracteres), x, y, z (float),
         * color (int), lum (float). Taille : 20 octets + id (1 a 2 octets en pratique) + nom (1 octet de longueur +
         * octets UTF-8 du nom).
         */
        public void write(FriendlyByteBuf buf)
        {
            buf.writeVarInt(id);
            buf.writeUtf(name, SpaceLimits.MAX_NAME_LENGTH);
            buf.writeFloat(x);
            buf.writeFloat(y);
            buf.writeFloat(z);
            buf.writeInt(color);
            buf.writeFloat(luminosity);
        }

        /** Lecture stricte (meme ordre que {@link #write}) : leve sur un id invalide ou une position non finie. */
        public static BackgroundStar read(FriendlyByteBuf buf)
        {
            int id = buf.readVarInt();
            String name = buf.readUtf(SpaceLimits.MAX_NAME_LENGTH);
            float x = buf.readFloat();
            float y = buf.readFloat();
            float z = buf.readFloat();
            int color = buf.readInt();
            float luminosity = buf.readFloat();
            return new BackgroundStar(id, name, x, y, z, color, luminosity);
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
