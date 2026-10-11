package com.amynna.hololib.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceTextureRef;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Textures integrees des astres (T29, decisions D21, C69, C70) : liste des ids, table <b>nom -> id</b> des corps de Sol,
 * regle <b>auto</b> (texture d'un corps sans texture explicite) et resolution d'une {@link SpaceTextureRef} en
 * texture. Code commun, sans classe client : le chargement et le dessin sont faits par le client (T30).
 * <p>
 * Fichiers : {@code assets/hololib/textures/space/<dossier>/<id>.png} (dossier {@code sol} ou {@code generic}),
 * credits dans {@code assets/hololib/textures/space/CREDITS.md}, affectation des corps par defaut et justification
 * dans {@code docs/space/donnees-systemes.md} (section "Textures"). Images equirectangulaires 2:1, RGB sans alpha :
 * 1024 x 512 (etoiles, planetes), 512 x 256 (lunes, objets mineurs). <b>Convention</b> : nord en haut, est a droite,
 * <b>centre de l'image = longitude 0</b> (meridien origine ; {@code docs/space/04-mathematiques.md}, rotation propre :
 * longitude 0 sur {@code +X}), donc {@code u = 0,5 + longitude_est / 360}. Une texture {@link Builtin#tinted() teintee}
 * (etoiles) est une luminance a multiplier par la couleur du corps.
 * <p>
 * Regle auto ({@link #auto}) : d'abord le <b>nom</b> (corps de Sol, nom exact sans accents <b>et</b> meme type que dans
 * Sol, quel que soit le systeme), sinon une <b>categorie generique</b> ({@link Category}) selon le type, le rayon et la
 * temperature d'equilibre ({@link #equilibriumTemperature}), puis une variante tiree des ids du systeme et du corps
 * (deterministe : meme texture chez tous les clients et a chaque chargement). Une galaxie sauvegardee n'est jamais
 * modifiee : la regle est evaluee a l'affichage.
 * <p>
 * Aucune allocation dans {@link #auto}, {@link #builtinFor} et {@link #customHash} (instances et
 * {@link ResourceLocation} construites une fois) : appelables par frame, meme si le client met le resultat en cache.
 */
public final class SpaceBodyTextures
{
    /** Rayon terrestre, en km (borne des categories, comme {@code docs/space/donnees-systemes.md}). */
    public static final double EARTH_RADIUS_KM = 6_371.0;
    /** Rayon maximal d'une planete rocheuse, en rayons terrestres (au-dela : enveloppe de gaz, Fulton 2017). */
    public static final double ROCKY_MAX_EARTH_RADII = 1.6;
    /** Rayon minimal d'une geante gazeuse, en rayons terrestres (en dessous : geante de glace / mini-Neptune). */
    public static final double GAS_GIANT_MIN_EARTH_RADII = 6.0;
    /** Temperature d'equilibre (K, albedo nul) a 1 UA d'une etoile de luminosite solaire. */
    public static final double EQUILIBRIUM_AT_1_AU = 278.6;
    /** Ligne des glaces (K) : en dessous, rocheuse ou lune glacee. */
    public static final double FROST_LINE_K = 170.0;
    /** Rocheuse brulante a partir de cette temperature (K). */
    public static final double ROCKY_HOT_K = 360.0;
    /** Geante gazeuse temperee a partir de cette temperature (K) : nuages d'eau (classe II de Sudarsky). */
    public static final double GAS_TEMPERATE_K = 170.0;
    /** Geante gazeuse chaude a partir de cette temperature (K) : nuages absents ou de silicates (classes IV-V). */
    public static final double GAS_HOT_K = 700.0;

    private static final String SOL = "sol";
    private static final String GENERIC = "generic";

    /**
     * Texture integree.
     *
     * @param id          id de la reference {@code builtin:<id>} ({@code [a-z0-9_]{1,32}})
     * @param displayName nom lisible, sans accents (selecteur du Configurator, T44)
     * @param tinted      vrai si l'image est une luminance a teinter par la couleur du corps (etoiles)
     * @param location    fichier PNG
     */
    public record Builtin(String id, String displayName, boolean tinted, ResourceLocation location)
    {
    }

    /** Categorie generique d'un corps sans texture propre ; chaque categorie a une ou plusieurs variantes. */
    public enum Category
    {
        /** Etoile : une seule image, teintee par la couleur du corps (C70). */
        STAR("Etoile (teintee)"),
        /** Geante gazeuse chaude (Jupiter chaud) : sombre, peu de nuages. */
        GAS_HOT("Geante gazeuse chaude"),
        /** Geante gazeuse temperee : nuages d'eau clairs. */
        GAS_TEMPERATE("Geante gazeuse temperee"),
        /** Geante gazeuse froide (type Jupiter / Saturne) : bandes beiges et brunes d'ammoniac. */
        GAS_COLD("Geante gazeuse froide"),
        /** Geante de glace ou mini-Neptune : methane, teintes cyan a bleues. */
        ICE_GIANT("Geante de glace"),
        /** Rocheuse brulante : surface sombre, roches nues. */
        ROCKY_HOT("Rocheuse brulante"),
        /** Rocheuse temperee : deserts, eventuellement mers. */
        ROCKY_TEMPERATE("Rocheuse temperee"),
        /** Rocheuse glacee : banquise, givre. */
        ROCKY_ICY("Rocheuse glacee"),
        /** Lune ou objet mineur glace : croute claire crateree. */
        MOON_ICY("Lune glacee"),
        /** Lune ou objet mineur rocheux : regolithe gris cratere. */
        MOON_ROCKY("Lune rocheuse");

        private final String displayName;
        private final List<Builtin> variants = new ArrayList<>();
        private final List<Builtin> variantsView = Collections.unmodifiableList(variants);

        Category(String displayName) { this.displayName = displayName; }

        /** Nom lisible de la categorie, sans accents. */
        public String displayName() { return displayName; }

        /** Variantes (au moins une), dans l'ordre des ids ; liste non modifiable. */
        public List<Builtin> variants() { return variantsView; }
    }

    private static final List<Builtin> ALL = new ArrayList<>();
    private static final List<Builtin> SOL_LIST = new ArrayList<>();
    private static final List<Builtin> ALL_VIEW = Collections.unmodifiableList(ALL);
    private static final Map<String, Builtin> BY_ID = new HashMap<>();
    /** Corps de Sol : nom exact -> texture ; type attendu dans {@link #SOL_KINDS}. */
    private static final Map<String, Builtin> SOL_BY_NAME = new HashMap<>();
    private static final Map<String, SpaceBodyKind> SOL_KINDS = new HashMap<>();

    static {
        // Generiques (C69). Etoile : une seule image (C70).
        generic(Category.STAR, "star");
        generic(Category.GAS_HOT, "gas_hot_1", "gas_hot_2");
        generic(Category.GAS_TEMPERATE, "gas_temperate_1", "gas_temperate_2");
        generic(Category.GAS_COLD, "gas_cold_1", "gas_cold_2");
        generic(Category.ICE_GIANT, "ice_giant_1", "ice_giant_2");
        generic(Category.ROCKY_HOT, "rocky_hot_1", "rocky_hot_2");
        generic(Category.ROCKY_TEMPERATE, "rocky_temperate_1", "rocky_temperate_2", "rocky_temperate_3");
        generic(Category.ROCKY_ICY, "rocky_icy_1", "rocky_icy_2");
        generic(Category.MOON_ICY, "moon_icy_1", "moon_icy_2");
        generic(Category.MOON_ROCKY, "moon_rocky_1", "moon_rocky_2");

        // Sol : nom du corps (galaxie par defaut, sans accents) -> id. Le Soleil n'y est pas : texture d'etoile teintee.
        sol("Mercure", SpaceBodyKind.PLANET, "mercure");
        sol("Venus", SpaceBodyKind.PLANET, "venus");
        sol("Terre", SpaceBodyKind.PLANET, "terre");
        sol("Mars", SpaceBodyKind.PLANET, "mars");
        sol("Jupiter", SpaceBodyKind.PLANET, "jupiter");
        sol("Saturne", SpaceBodyKind.PLANET, "saturne");
        sol("Uranus", SpaceBodyKind.PLANET, "uranus");
        sol("Neptune", SpaceBodyKind.PLANET, "neptune");
        sol("Ceres", SpaceBodyKind.MINOR, "ceres");
        sol("Pluton", SpaceBodyKind.MINOR, "pluton");
        sol("Eris", SpaceBodyKind.MINOR, "eris");
        sol("Haumea", SpaceBodyKind.MINOR, "haumea");
        sol("Makemake", SpaceBodyKind.MINOR, "makemake");
        sol("Lune", SpaceBodyKind.MOON, "lune");
        sol("Phobos", SpaceBodyKind.MOON, "phobos");
        sol("Deimos", SpaceBodyKind.MOON, "deimos");
        sol("Io", SpaceBodyKind.MOON, "io");
        sol("Europe", SpaceBodyKind.MOON, "europe");
        sol("Ganymede", SpaceBodyKind.MOON, "ganymede");
        sol("Callisto", SpaceBodyKind.MOON, "callisto");
        sol("Encelade", SpaceBodyKind.MOON, "encelade");
        sol("Tethys", SpaceBodyKind.MOON, "tethys");
        sol("Dione", SpaceBodyKind.MOON, "dione");
        sol("Rhea", SpaceBodyKind.MOON, "rhea");
        sol("Titan", SpaceBodyKind.MOON, "titan");
        sol("Japet", SpaceBodyKind.MOON, "japet");
        sol("Miranda", SpaceBodyKind.MOON, "miranda");
        sol("Ariel", SpaceBodyKind.MOON, "ariel");
        sol("Umbriel", SpaceBodyKind.MOON, "umbriel");
        sol("Titania", SpaceBodyKind.MOON, "titania");
        sol("Oberon", SpaceBodyKind.MOON, "oberon");
        sol("Triton", SpaceBodyKind.MOON, "triton");
        sol("Charon", SpaceBodyKind.MOON, "charon");

        // Liste complete : Sol (ordre de la galaxie par defaut) puis generiques (ordre des categories).
        ALL.addAll(SOL_LIST);
        for (Category category : Category.values()) ALL.addAll(category.variants);
    }

    private SpaceBodyTextures() {}

    // ---------------------------------------------------------------- liste

    /** Toutes les textures integrees : Sol (ordre de la galaxie par defaut) puis generiques ; liste non modifiable. */
    public static List<Builtin> all() { return ALL_VIEW; }

    /** Texture integree d'id {@code id}, {@code null} si inconnue. */
    public static Builtin builtin(String id) { return id == null ? null : BY_ID.get(id); }

    /** Vrai si {@code id} designe une texture integree. */
    public static boolean exists(String id) { return builtin(id) != null; }

    /** Texture de Sol du corps nomme exactement {@code name} et de type {@code kind}, {@code null} sinon. */
    public static Builtin solTexture(String name, SpaceBodyKind kind)
    {
        Builtin builtin = name == null ? null : SOL_BY_NAME.get(name);
        return builtin != null && SOL_KINDS.get(name) == kind ? builtin : null;
    }

    // ---------------------------------------------------------------- resolution

    /**
     * Texture integree a afficher pour le corps d'indice {@code index} de {@code system} : sa reference
     * {@code builtin:<id>} si l'id est connue, sinon la regle auto ({@link #auto}) ; pour une reference
     * {@code custom:<sha1>}, c'est le <b>repli</b> a afficher tant que l'image importee manque (T30 / T45, voir
     * {@link #customHash}). Jamais {@code null}. Sans allocation.
     */
    public static Builtin builtinFor(SpaceSystem system, int index)
    {
        SpaceTextureRef texture = system.bodies().get(index).texture();
        if (texture.source() == SpaceTextureRef.Source.BUILTIN) {
            Builtin builtin = BY_ID.get(texture.id());
            if (builtin != null) return builtin;
        }
        return auto(system, index);
    }

    /** {@link #builtinFor(SpaceSystem, int)} par corps ; {@code body} doit appartenir a {@code system}. */
    public static Builtin builtinFor(SpaceSystem system, SpaceBody body)
    {
        return builtinFor(system, requireIndex(system, body));
    }

    /** Fichier de la texture integree a afficher ({@link #builtinFor(SpaceSystem, int)}). */
    public static ResourceLocation location(SpaceSystem system, int index)
    {
        return builtinFor(system, index).location();
    }

    /**
     * Empreinte SHA-1 (40 hexadecimaux minuscules) de la texture importee du corps, {@code null} si sa reference n'est
     * pas {@code custom}. Le client affiche l'image importee si elle est disponible, sinon
     * {@link #builtinFor(SpaceSystem, int)}.
     */
    public static String customHash(SpaceBody body)
    {
        SpaceTextureRef texture = body.texture();
        return texture.source() == SpaceTextureRef.Source.CUSTOM ? texture.id() : null;
    }

    // ---------------------------------------------------------------- regle auto (C69)

    /**
     * Texture "auto" du corps d'indice {@code index} (sa reference est ignoree) : texture de Sol par nom et type
     * ({@link #solTexture}), sinon variante de sa {@link #category categorie} tiree des ids du systeme et du corps.
     * Jamais {@code null}. Sans allocation.
     */
    public static Builtin auto(SpaceSystem system, int index)
    {
        SpaceBody body = system.bodies().get(index);
        Builtin sol = solTexture(body.name(), body.kind());
        if (sol != null) return sol;
        List<Builtin> variants = category(system, index).variants();
        return variants.get(variant(system.id(), body.id(), variants.size()));
    }

    /**
     * Categorie generique du corps d'indice {@code index} (avant variante) :
     * <ul>
     *     <li>{@code STAR} : etoile ;</li>
     *     <li>{@code MOON} et {@code MINOR} : glace si la temperature d'equilibre est sous la ligne des glaces
     *     ({@link #FROST_LINE_K}), sinon rocheuse ;</li>
     *     <li>{@code PLANET} : rocheuse jusqu'a {@link #ROCKY_MAX_EARTH_RADII} rayons terrestres (brulante a partir de
     *     {@link #ROCKY_HOT_K}, glacee sous {@link #FROST_LINE_K}, temperee entre les deux), geante de glace jusqu'a
     *     {@link #GAS_GIANT_MIN_EARTH_RADII}, geante gazeuse au-dela (chaude a partir de {@link #GAS_HOT_K}, temperee a
     *     partir de {@link #GAS_TEMPERATE_K}, froide sinon).</li>
     * </ul>
     */
    public static Category category(SpaceSystem system, int index)
    {
        SpaceBody body = system.bodies().get(index);
        if (body.kind() == SpaceBodyKind.STAR) return Category.STAR;
        double temperature = equilibriumTemperature(system, index);
        if (body.kind() == SpaceBodyKind.MOON || body.kind() == SpaceBodyKind.MINOR) {
            return temperature < FROST_LINE_K ? Category.MOON_ICY : Category.MOON_ROCKY;
        }
        double earthRadii = body.radius() / EARTH_RADIUS_KM;
        if (earthRadii <= ROCKY_MAX_EARTH_RADII) {
            if (temperature >= ROCKY_HOT_K) return Category.ROCKY_HOT;
            return temperature < FROST_LINE_K ? Category.ROCKY_ICY : Category.ROCKY_TEMPERATE;
        }
        if (earthRadii < GAS_GIANT_MIN_EARTH_RADII) return Category.ICE_GIANT;
        if (temperature >= GAS_HOT_K) return Category.GAS_HOT;
        return temperature >= GAS_TEMPERATE_K ? Category.GAS_TEMPERATE : Category.GAS_COLD;
    }

    /**
     * Temperature d'equilibre du corps d'indice {@code index}, en kelvins (albedo nul, redistribution complete) :
     * {@code T = 278,6 * L^(1/4) / sqrt(a)}, avec {@code L} la luminosite (relative au Soleil) de l'etoile la plus
     * proche dans sa parente et {@code a} (UA) le demi-grand axe du corps de la chaine qui orbite directement cette
     * etoile (la planete pour une lune). Les autres etoiles du systeme (binaires) sont ignorees. 0 pour une etoile,
     * un corps sans etoile parente, une luminosite nulle ou un demi-grand axe nul. Sans allocation.
     */
    public static double equilibriumTemperature(SpaceSystem system, int index)
    {
        List<SpaceBody> bodies = system.bodies();
        if (bodies.get(index).kind() == SpaceBodyKind.STAR) return 0.0;
        int child = index;
        int parent = system.parentIndex(index);
        while (parent >= 0 && bodies.get(parent).kind() != SpaceBodyKind.STAR) {
            child = parent;
            parent = system.parentIndex(parent);
        }
        if (parent < 0) return 0.0;
        SpaceBody orbiting = bodies.get(child);
        double luminosity = bodies.get(parent).luminosity();
        if (orbiting.orbit() == null || luminosity <= 0.0) return 0.0;
        double a = orbiting.orbit().semiMajorAxis();
        if (!(a > 0.0)) return 0.0;
        return EQUILIBRIUM_AT_1_AU * StrictMath.sqrt(StrictMath.sqrt(luminosity)) / StrictMath.sqrt(a);
    }

    /** Variante parmi {@code count}, tiree des ids (melange entier : deux voisins n'ont pas la meme suite). */
    static int variant(int systemId, int bodyId, int count)
    {
        if (count <= 1) return 0;
        int h = systemId * 0x9E3779B1 + bodyId * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0xC2B2AE3D;
        h ^= h >>> 13;
        return Math.floorMod(h, count);
    }

    // ---------------------------------------------------------------- construction des tables

    private static int requireIndex(SpaceSystem system, SpaceBody body)
    {
        int index = system.indexOf(body.id());
        if (index < 0) throw new IllegalArgumentException("Corps " + body.id() + " absent du systeme " + system.id());
        return index;
    }

    private static void generic(Category category, String... ids)
    {
        for (int i = 0; i < ids.length; i++) {
            String name = ids.length == 1 ? category.displayName() : category.displayName() + " " + (i + 1);
            category.variants.add(register(ids[i], name, category == Category.STAR, GENERIC));
        }
    }

    private static void sol(String name, SpaceBodyKind kind, String id)
    {
        Builtin builtin = register(id, name, false, SOL);
        SOL_LIST.add(builtin);
        SOL_BY_NAME.put(name, builtin);
        SOL_KINDS.put(name, kind);
    }

    private static Builtin register(String id, String displayName, boolean tinted, String folder)
    {
        if (!SpaceTextureRef.validBuiltinId(id) || BY_ID.containsKey(id)) {
            throw new IllegalStateException("Id de texture integree invalide ou en double : " + id);
        }
        Builtin builtin = new Builtin(id, displayName, tinted,
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "textures/space/" + folder + "/" + id + ".png"));
        BY_ID.put(id, builtin);
        return builtin;
    }
}
