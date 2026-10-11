package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * Mouvement du monde dans le systeme, un pas par tick (voir {@code docs/space/04-mathematiques.md} par. 5) ; pose par
 * defaut d'un systeme ; corps le plus proche ; corps de reference ; interpolation d'affichage dans le repere du
 * referentiel.
 * <p>
 * <b>Referentiel</b> (doc 04 par. 5 point 0, decisions D7, D29 ; D8 remplacee en T39) : la vitesse nominale {@code V}
 * est relative a un <b>corps de reference</b>, designe par son id (stable), qui entraine le monde dans son mouvement
 * orbital (a {@code V = 0}, le monde reste immobile par rapport a lui). Il se choisit par les <b>spheres
 * d'influence</b> (style Kerbal Space Program, {@link #influenceRadius}) : sphere d'un corps de parent {@code p}
 * {@code r_i = a_i (m_i / m_p)^}{@value #INFLUENCE_EXPONENT} ({@code a_i} demi-grand axe en km), au moins
 * {@value #INFLUENCE_FLOOR} rayons du corps, puis rayon "de jeu" : x {@value #MOON_INFLUENCE_FACTOR} pour une lune, x
 * {@value #PLANET_INFLUENCE_FACTOR} pour une planete ou un objet mineur, au plus {@value #INFLUENCE_MAX_ORBIT_FRACTION}
 * {@code a_i} (etoile compagne : rayon de base) ; sphere infinie pour un corps sans parent. Le referentiel vise est le
 * corps <b>le plus profond</b> de la hierarchie dont la sphere contient le monde ({@link #influenceTarget} : on descend
 * de l'etoile vers ses enfants, entre deux freres qui contiennent le monde le plus attirant {@code m / d^2}) ; on ne
 * quitte la sphere du referentiel (et de ses ancetres) qu'au-dela de {@value #INFLUENCE_HYSTERESIS} fois son rayon. Le
 * regard n'y entre plus. Bascule vers le corps vise apres {@value #SWITCH_DELAY} ticks consecutifs de candidature, au
 * plus une fois tous les {@value #SWITCH_COOLDOWN} ticks ; le mouvement d'entrainement passe de l'ancien au nouveau
 * referentiel en {@value #BLEND_TICKS} ticks (smoothstep). La masse ne sert qu'a ce choix : <b>aucune gravite</b> n'est
 * appliquee au monde et les orbites n'en dependent pas. L'etat ({@link SpaceFrameState}) entre dans {@link #step} et y
 * est mis a jour en place.
 * <p>
 * {@link #step} est <b>pure et deterministe bit a bit</b> : memes entrees -> memes sorties sur le serveur et sur les
 * clients (dead reckoning, {@code docs/space/05-synchronisation.md} par. 2). Trigonometrie par {@link StrictMath},
 * arithmetique ecrite ici ({@code SpaceRotations}), corps parcourus dans l'ordre du systeme.
 * <p>
 * <b>Instance</b> : elle porte son espace de travail (positions des corps au debut et a la fin du pas, spheres
 * d'influence du dernier systeme vu, axes, quaternion, referentiel), donc aucune allocation par appel ; une instance par fil (serveur, client, affichage),
 * jamais partagee. Apres {@link #step}, {@link #defaultPose}, {@link #refreshReference} ou
 * {@link #nearestBody(SpacePose, SpaceSystem, double)}, {@link #bodyPositions()} contient les positions des corps au
 * temps demande (fin du pas pour {@link #step}).
 * <p>
 * <b>Saut</b> (T21, doc 04 par. 8) : la variante de {@link #step} qui recoit un {@link SpaceJump} demarre, avance ou
 * annule la charge, fige la pose pendant le voyage ; meme fonction sur le serveur et sur les clients. L'arrivee
 * (changement de systeme) n'est pas dans le pas : elle est decidee par le serveur.
 */
public final class SpaceMotion
{
    /**
     * Facteur de la zone de freinage d'approche : on freine a moins de {@code K} rayons au-dessus du plancher
     * ({@code f = (h - h_min) / (K R)}, D9 : 2 au lieu de 10).
     */
    public static final double APPROACH_FACTOR = 2.0;
    /**
     * Altitude plancher d'un corps, en rayons du corps : {@code h_min = 0,01 R} (D9 ; Terre 64 km, Lune 17 km, Soleil
     * 6 960 km). Le freinage d'approche s'annule a cette altitude et le garde-fou n'y descend jamais.
     */
    public static final double MIN_ALTITUDE_FACTOR = 0.01;
    /** Gaz : vitesse nominale multipliee par ce facteur a chaque tick d'acceleration. */
    public static final double ACCELERATION = 1.05;
    /** Frein : vitesse nominale multipliee par ce facteur a chaque tick de deceleration. */
    public static final double DECELERATION = 0.92;
    /**
     * Gaz avec boost (T32, touche Sprint maintenue, C74) : facteur d'acceleration par tick, {@code 1,05^3} (pente
     * logarithmique triplee : de 1 km/tick a {@code V_max} = 10^7 en 111 ticks au lieu de 331).
     */
    public static final double BOOST_ACCELERATION = 1.157625;
    /**
     * Frein, ou gaz oppose au sens de la vitesse, avec boost (T32, C74) : facteur de deceleration par tick,
     * {@code 0,92^3} (pente logarithmique triplee : de 10^7 km/tick a l'arret en 65 ticks au lieu de 194).
     */
    public static final double BOOST_DECELERATION = 0.778688;
    /** Vitesse minimale, km par tick : depart du gaz, et arret net en dessous au freinage. */
    public static final double MIN_SPEED = 1.0;
    /**
     * Referentiel (T39, D29), sphere d'influence : exposant de {@code r = a (m / m_parent)^0,4} (Laplace). Terre
     * 0,92 M km, Lune 66 000 km, Jupiter 48 M km.
     */
    public static final double INFLUENCE_EXPONENT = 0.4;
    /**
     * Referentiel, sphere d'influence : rayon minimal, en rayons du corps (10 depuis la retouche de T39, 2 avant) : une
     * petite lune rapide (Phobos, Io, Europe, Rhea...) a une sphere assez grande pour que le monde y reste le delai de
     * bascule et qu'elle devienne son referentiel.
     */
    public static final double INFLUENCE_FLOOR = 10.0;
    /**
     * Referentiel, rayon "de jeu" (retouche de T39 apres essai en jeu : lunes trop difficiles a atteindre sans les
     * manquer) : facteur applique au rayon de base d'une lune ({@code MOON}, y compris autour d'un objet mineur).
     */
    public static final double MOON_INFLUENCE_FACTOR = 4.0;
    /**
     * Referentiel, rayon de jeu : facteur d'une planete ou d'un objet mineur (une etoile compagne garde son rayon de
     * base, sans plafond).
     */
    public static final double PLANET_INFLUENCE_FACTOR = 1.5;
    /**
     * Referentiel, rayon de jeu : plafond d'une planete, d'un objet mineur ou d'une lune, en demi-grands axes de son
     * orbite (la sphere d'une lune ne contient jamais le centre de son parent ; Lune : 4 x 66 184 km plafonne a
     * 192 203 km). Les etoiles compagnes n'ont pas de plafond.
     */
    public static final double INFLUENCE_MAX_ORBIT_FRACTION = 0.5;
    /**
     * Referentiel, hysteresis : le monde ne quitte la sphere du referentiel (ou d'un de ses ancetres) qu'au-dela de ce
     * facteur fois son rayon (remplace la marge de score de D8).
     */
    public static final double INFLUENCE_HYSTERESIS = 1.05;
    /** Referentiel, delai : ticks consecutifs de candidature avant la bascule (0,5 s). */
    public static final int SWITCH_DELAY = 10;
    /** Referentiel, cooldown : ticks minimum entre deux bascules (3 s). */
    public static final int SWITCH_COOLDOWN = 60;
    /** Referentiel, fondu : ticks pour passer du mouvement d'entrainement de l'ancien au nouveau (2 s). */
    public static final int BLEND_TICKS = 40;
    /**
     * Pose par defaut : ecart maximal a la planete de depart, vers l'exterieur de son orbite, en demi-grands axes
     * (0,02 a : l'ancienne pose a 1,02 a du parent).
     */
    public static final double DEFAULT_PLANET_GAP = 0.02;
    /**
     * Pose par defaut (T39, C115) : ecart maximal a la planete de depart, en rayons de <b>base</b> de sa sphere
     * d'influence ({@link #baseInfluenceRadius}, sans le facteur de jeu) : le monde y part dans la sphere de la
     * planete, qui est son referentiel et l'entraine (Terre : 739 720 km, hors de la sphere de jeu de la Lune).
     */
    public static final double DEFAULT_INFLUENCE_FACTOR = 0.8;
    /**
     * Pose par defaut : demi-grand axe vise pour la planete de depart, en UA (la Terre dans Sol) ; la planete retenue
     * est celle dont le demi-grand axe en est le plus proche en echelle logarithmique ({@link #startPlanet}).
     */
    public static final double START_SEMI_MAJOR_AXIS = 1.0;
    /** Pose par defaut sans planete : distance a l'etoile principale, en UA (au moins hors de la marge). */
    public static final double DEFAULT_DISTANCE_AU = 1.0;
    /** Pose par defaut : altitude minimale au-dessus de chaque corps, en rayons du corps. */
    public static final double DEFAULT_CLEARANCE = 20.0;
    /** Pose par defaut : nombre de passes d'ecartement des corps (borne fixe : deterministe). */
    private static final int CLEARANCE_PASSES = 4;
    /** Teleportation (T44, D34) : distance au centre du corps, en rayons du corps (comme l'arrivee d'un saut). */
    public static final double TELEPORT_RADII = 10.0;
    /** Teleportation : distance au plus cette part du rayon de jeu de la sphere d'influence du corps. */
    public static final double TELEPORT_INFLUENCE_FRACTION = 0.5;
    /** Teleportation : distance au moins ce nombre de rayons du corps (sphere d'influence minuscule). */
    public static final double TELEPORT_MIN_RADII = 1.5;

    /** Positions des corps a la fin du pas (ou au temps demande hors pas). */
    private final Vector3d[] bodies = SpaceOrbits.newPositionArray();
    /** Positions des corps au debut du pas ({@link #step}, {@link #interpolate}). */
    private final Vector3d[] startBodies = SpaceOrbits.newPositionArray();
    /** Positions des corps au temps d'affichage ({@link #interpolate} seulement). */
    private final Vector3d[] displayBodies = SpaceOrbits.newPositionArray();
    /** Rayons des spheres d'influence (km) des corps de {@link #influenceSystem} ({@link #prepareInfluence}). */
    private final double[] influence = new double[SpaceLimits.MAX_BODIES];
    /** Indices des parents des corps de {@link #influenceSystem} ({@code -1} : sans parent). */
    private final int[] parents = new int[SpaceLimits.MAX_BODIES];
    /** Corps du referentiel courant et ses ancetres ({@link #influenceTarget}, hysteresis). */
    private final boolean[] chain = new boolean[SpaceLimits.MAX_BODIES];
    /** Systeme dont {@link #influence} et {@link #parents} sont calcules (systemes immuables : identite). */
    private SpaceSystem influenceSystem;
    private final Vector3d forward = new Vector3d();
    private final Vector3d right = new Vector3d();
    private final Vector3d up = new Vector3d();
    private final Vector3d direction = new Vector3d();
    private final Quaterniond rotation = new Quaterniond();
    /** Position galactique d'une cible de saut ({@link SpaceJump.Targets}). */
    private final Vector3d jumpTarget = new Vector3d();
    /** Vecteur de travail des calculs du saut. */
    private final Vector3d jumpWork = new Vector3d();
    private int bodyCount;
    private double nearestAltitude = Double.POSITIVE_INFINITY;
    private int referenceId;
    private int referenceIndex = -1;
    private double referenceAltitude = Double.POSITIVE_INFINITY;

    /**
     * Positions des corps (km, repere systeme) calculees par le dernier appel, pour les {@link #bodyCount()} premiers
     * elements. Tableau interne : lecture seule, valable jusqu'au prochain appel.
     */
    public Vector3d[] bodyPositions() { return bodies; }

    /** Nombre de positions valables dans {@link #bodyPositions()}. */
    public int bodyCount() { return bodyCount; }

    /**
     * Id du corps de reference rendu par le dernier {@link #step}, {@link #defaultPose} ou {@link #refreshReference}
     * ({@code 0} sans corps) : celui de l'etat du referentiel apres l'appel.
     */
    public int referenceBodyId() { return referenceId; }

    /** Indice de {@link #referenceBodyId()} dans le systeme du dernier appel, {@code -1} sans corps. */
    public int referenceIndex() { return referenceIndex; }

    /** Altitude (km) au-dessus de {@link #referenceBodyId()} a la fin du dernier pas (ou au temps demande). */
    public double referenceAltitude() { return referenceAltitude; }

    // ---------------------------------------------------------------- pas de simulation

    /**
     * Un pas de tick (doc 04 par. 5) :
     * <ol start="0">
     *     <li>referentiel, pose et positions au debut du pas ({@link #selectReference}) : corps le plus profond dont
     *     la sphere d'influence contient le monde ({@link #influenceTarget}, hysteresis
     *     {@value #INFLUENCE_HYSTERESIS} sur le referentiel et ses ancetres ; la visee de {@code input} ne compte
     *     pas) ; {@code ref} absent -> ce corps, sans delai ni fondu ; sinon bascule vers lui s'il reste le corps vise
     *     pendant {@value #SWITCH_DELAY} pas consecutifs et que {@value #SWITCH_COOLDOWN} pas ont passe depuis la
     *     bascule precedente. Ne modifie ni la pose ni {@code V} ;</li>
     *     <li>gaz, vitesse <b>signee</b> {@code V} dans {@code [-V_max, V_max]} ({@code V < 0} : le monde part en
     *     arriere) : frein ({@code brake}) -> {@code V = 0,92 V}, puis {@code 0} sous 1 km/tick en valeur absolue,
     *     quel que soit {@code throttle} (le frein prime) ; sinon gaz {@code s = throttle != 0} de signe oppose a
     *     {@code V} -> meme deceleration jusqu'a 0 ; sinon {@code V = s min(V_max, max(1, 1,05 |V|))} (accelere
     *     dans le sens {@code s}, en partant de 1 km/tick) ; sans touche, {@code V} inchangee (l'elan est garde).
     *     Avec {@code boost} (T32), facteurs {@value #BOOST_DECELERATION} et {@value #BOOST_ACCELERATION} au lieu de
     *     {@value #DECELERATION} et {@value #ACCELERATION} ; seul, le boost ne change rien ;</li>
     *     <li>rotation dans les axes du monde, composee a droite :
     *     {@code Q <- Q Rot(U, -yawRate turn) Rot(R, pitchRate turn) Rot(F, roll rollSpeed)}, normalisee ;</li>
     *     <li>vitesse effective {@code v = V Q F}, relative au referentiel (vers l'arriere du monde si {@code V < 0}) ;
     *     deplacement dans le repere du systeme {@code D = v + dp_frame} : {@code dp_frame = p_ref(t + 1) - p_ref(t)}
     *     (nul pour un corps sans parent), ou pendant un fondu
     *     {@code (1 - sigma) dp_prev + sigma dp_ref}, {@code sigma = smoothstep(pas depuis la bascule / }
     *     {@value #BLEND_TICKS}{@code )}. Freinage d'approche, dans le repere de chaque corps {@code i} (monde au debut
     *     du pas {@code o_i = s - p_i(t)}, {@code h_i = |o_i| - R_i}, {@code n_i = -o_i / |o_i|}) : approche
     *     {@code a_i = v . n_i} pour le referentiel hors fondu ; sinon vitesse relative
     *     {@code a_i = min(v . n_i, (D - dp_i) . n_i)} (le freinage ne retire que de la vitesse commandee : il ne
     *     pousse jamais le monde, qui a {@code V = 0} reste lie au referentiel ; il freine moins si le corps
     *     s'eloigne). Si {@code a_i > 0}, {@code v <- v - (1 - f) a_i n_i} avec
     *     {@code f = clamp((h_i - h_min,i) / (K R_i), 0, 1)} ({@code K = }{@value #APPROACH_FACTOR},
     *     {@code h_min,i = }{@value #MIN_ALTITUDE_FACTOR}{@code  R_i}) ; {@code V} n'est jamais modifiee par ce
     *     freinage ;</li>
     *     <li>garde-fou sur {@code D}, dans le repere de chaque corps : le monde va de {@code o_i} a
     *     {@code o_i + D - dp_i} ; si ce <b>segment</b> passe a moins de {@code R + max(h / 2, h_min)} du corps
     *     ({@code h} d'avant le pas), le mouvement s'arrete a l'entree de cette sphere et le reste du pas, prive de sa
     *     composante radiale, la longe : l'altitude est au plus divisee par deux par tick jusqu'au plancher
     *     {@code h_min}, jamais en dessous ; un pas plus long que la distance au centre ne traverse pas le corps, et un
     *     corps qui vient sur le monde le repousse. Un monde sous le plancher au debut du pas ({@code h < h_min}, apres
     *     une edition, ou d'un arrondi apres un glissement) est d'abord ramene radialement a {@code R + h_min}, puis
     *     le pas part de la : il s'eloigne librement, ou longe la sphere du plancher ;</li>
     *     <li>{@code s = s'} ; un pas de plus depuis la bascule ({@code frame}).</li>
     * </ol>
     * Avec un corps sans parent pour referentiel (hors fondu), le pas est identique bit a bit a celui d'avant le
     * referentiel tant qu'aucun corps mobile n'est dans sa zone de freinage ou de garde-fou. Sans allocation.
     *
     * @param pose      pose au debut du pas (non modifiee, sauf si c'est {@code dest})
     * @param speed     vitesse nominale signee {@code V} au debut du pas, km/tick, relative au referentiel (non finie =
     *                  0 ; bornee a {@code [-V_max, V_max]})
     * @param frame     etat du referentiel au debut du pas, <b>mis a jour en place</b> (etat de fin de pas, a passer
     *                  au pas suivant)
     * @param input     entree du pilote ({@link SpacePilotInput#ZERO} sans pilote ; sa visee n'est plus lue depuis
     *                  T39)
     * @param system    systeme courant ({@code null} = espace vide, aucun freinage, aucun referentiel)
     * @param startDays temps simule au <b>debut</b> du pas (jours), {@code settings.days(gameTime, 0)}
     * @param endDays   temps simule a la <b>fin</b> du pas (jours), {@code settings.days(gameTime + 1, 0)} ; la fin
     *                  d'un pas et le debut du suivant doivent etre calcules par la meme expression (positions
     *                  identiques bit a bit)
     * @param settings  reglages (axe avant, {@code V_max}, taux)
     * @param dest      pose a la fin du pas ; peut etre {@code pose}
     * @return la vitesse nominale signee {@code V} a la fin du pas, dans {@code [-V_max, V_max]}
     */
    public double step(SpacePose pose, double speed, SpaceFrameState frame, SpacePilotInput input, SpaceSystem system,
                       double startDays, double endDays, SpaceMotionSettings settings, SpacePose dest)
    {
        return move(pose, speed, frame, input, system, startDays, endDays, settings, dest, false);
    }

    /**
     * Un pas de tick avec le <b>saut</b> (doc 04 par. 8, T21), etat {@code jump} mis a jour en place ; meme suite
     * d'operations sur le serveur et sur les clients :
     * <ul>
     *     <li>{@code NONE} : drapeau du saut dans {@code input} et cible valide ({@code targets}, id different du
     *     systeme courant) -> debut de la charge ({@link SpaceJump#begin}, {@code Q0} = orientation au debut du pas),
     *     puis pas de charge ci-dessous ; sinon pas normal ({@link #step(SpacePose, double, SpaceFrameState,
     *     SpacePilotInput, SpaceSystem, double, double, SpaceMotionSettings, SpacePose) step}) ;</li>
     *     <li>{@code CHARGE} (la cible reste celle du debut, meme si l'entree en designe une autre) : drapeau perdu ->
     *     {@code NONE} (orientation atteinte gardee) et pas normal ; sinon pas
     *     <b>verrouille</b> (frein force, gaz / roulis / taux ignores ; referentiel, entrainement, freinage
     *     d'approche et garde-fou comme d'habitude, avec l'orientation du debut du pas ; frein a
     *     {@value #DECELERATION}, boost ignore), puis orientation de fin de pas
     *     {@link SpaceJump#chargeOrientation} au pas {@code n} de la charge ; au pas {@value SpaceJump#CHARGE_TICKS}
     *     -> {@code TRAVEL}, {@code V = 0} ;</li>
     *     <li>{@code TRAVEL} : pose figee ({@code dest = pose}), referentiel inchange, {@code V = 0}, compteur borne a
     *     la duree. L'arrivee ({@link SpaceJump#arrived()}) est faite par le serveur <b>hors</b> du pas ; un client
     *     reste au bout du compte. {@link #bodyPositions()} n'est pas recalcule pendant le voyage.</li>
     * </ul>
     * Sans allocation.
     *
     * @param jump    etat du saut au debut du pas, <b>mis a jour en place</b>
     * @param targets recherche de la cible ({@code null} : aucun saut ne peut commencer)
     * @return la vitesse nominale signee {@code V} a la fin du pas
     */
    public double step(SpacePose pose, double speed, SpaceFrameState frame, SpaceJump jump, SpaceJump.Targets targets,
                       SpacePilotInput input, SpaceSystem system, double startDays, double endDays,
                       SpaceMotionSettings settings, SpacePose dest)
    {
        if (jump.phase() == SpaceJump.Phase.NONE) {
            int target = input.jumpTarget();
            if (target > 0 && system != null && target != system.id() && targets != null
                    && targets.position(target, jumpTarget)) {
                jump.begin(target, pose.orientation(), system.x(), system.y(), system.z(), jumpTarget,
                        settings.warpSpeed());
            }
        }
        switch (jump.phase()) {
            case CHARGE -> {
                if (input.jumpTarget() <= 0) {
                    jump.reset();
                    return move(pose, speed, frame, input, system, startDays, endDays, settings, dest, false);
                }
                double v = move(pose, speed, frame, input, system, startDays, endDays, settings, dest, true);
                int n = jump.advanceCharge();
                jump.chargeOrientation(n, settings, jumpWork, dest.orientation());
                return jump.phase() == SpaceJump.Phase.TRAVEL ? 0.0 : v;
            }
            case TRAVEL -> {
                if (dest != pose) dest.set(pose);
                jump.advanceTravel();
                return 0.0;
            }
            default -> {
                return move(pose, speed, frame, input, system, startDays, endDays, settings, dest, false);
            }
        }
    }

    /**
     * Corps du pas (voir {@link #step(SpacePose, double, SpaceFrameState, SpacePilotInput, SpaceSystem, double,
     * double, SpaceMotionSettings, SpacePose) step}) ; {@code locked} (charge du saut) : frein force (sans boost), gaz
     * et rotations ignores (le referentiel est choisi comme d'habitude).
     */
    private double move(SpacePose pose, double speed, SpaceFrameState frame, SpacePilotInput input, SpaceSystem system,
                        double startDays, double endDays, SpaceMotionSettings settings, SpacePose dest,
                        boolean locked)
    {
        double maxSpeed = settings.maxSpeed();
        double v = Double.isFinite(speed) ? Math.max(-maxSpeed, Math.min(speed, maxSpeed)) : 0.0;
        Vector3dc start = pose.position();
        double sx = start.x();
        double sy = start.y();
        double sz = start.z();

        // 0. Referentiel au debut du pas ; deplacement du repere pendant le pas (nul pour un corps sans parent, melange
        // de l'ancien et du nouveau referentiel pendant un fondu).
        List<SpaceBody> list = computePositions(system, endDays);
        if (bodyCount > 0) SpaceOrbits.positions(system, startDays, startBodies);
        int ref = bodyCount > 0 ? selectReference(pose, system, startBodies, frame) : -1;
        if (ref < 0) frame.reset(0);
        int from = ref >= 0 && frame.blending() ? system.indexOf(frame.previous()) : -1;
        boolean blending = from >= 0;
        boolean refMoving = ref >= 0 && !list.get(ref).isRoot();
        double rdx = refMoving ? bodies[ref].x - startBodies[ref].x : 0.0;
        double rdy = refMoving ? bodies[ref].y - startBodies[ref].y : 0.0;
        double rdz = refMoving ? bodies[ref].z - startBodies[ref].z : 0.0;
        if (blending) {
            double sigma = frame.blend();
            double rest = 1.0 - sigma;
            rdx = rest * (bodies[from].x - startBodies[from].x) + sigma * rdx;
            rdy = rest * (bodies[from].y - startBodies[from].y) + sigma * rdy;
            rdz = rest * (bodies[from].z - startBodies[from].z) + sigma * rdz;
        }
        boolean frameMoving = refMoving || blending;

        // 1. Gaz, vitesse signee : le frein prime ; un gaz oppose au sens de V freine d'abord jusqu'a 0. Le boost
        // (Sprint, T32) renforce les deux pentes ; ignore pendant la charge du saut.
        int throttle = locked ? 0 : input.throttleSign();
        boolean boost = !locked && input.boost();
        if (locked || input.brake() || throttle > 0 && v < 0.0 || throttle < 0 && v > 0.0) {
            v *= boost ? BOOST_DECELERATION : DECELERATION;
            if (Math.abs(v) < MIN_SPEED) v = 0.0;
        } else if (throttle != 0) {
            v = throttle * Math.min(maxSpeed, Math.max(MIN_SPEED, Math.abs(v) * (boost ? BOOST_ACCELERATION
                    : ACCELERATION)));
        }

        // 2. Rotation, dans les axes du monde, composee a droite (Rot(axe, 0) = identite exacte : sautee).
        settings.forward(forward);
        settings.right(right);
        settings.up(up);
        rotation.set(pose.orientation());
        double turn = Math.toRadians(settings.turnRate());
        if (!locked && input.yaw() != 0) {
            SpaceRotations.multiplyAxisAngle(rotation, up.x, up.y, up.z, -input.yawRate() * turn);
        }
        if (!locked && input.pitch() != 0) {
            SpaceRotations.multiplyAxisAngle(rotation, right.x, right.y, right.z, input.pitchRate() * turn);
        }
        int roll = locked ? 0 : input.rollSign();
        if (roll != 0) {
            SpaceRotations.multiplyAxisAngle(rotation, forward.x, forward.y, forward.z,
                    roll * Math.toRadians(settings.rollSpeed()));
        }
        SpaceRotations.normalize(rotation);

        // 3. Vitesse effective (relative au referentiel), freinage d'approche dans le repere de chaque corps.
        SpaceRotations.rotate(rotation, forward.x, forward.y, forward.z, direction);
        double vx = direction.x * v;
        double vy = direction.y * v;
        double vz = direction.z * v;
        for (int i = 0; i < bodyCount; i++) {
            Vector3d p0 = startBodies[i];
            double dx = p0.x - sx;
            double dy = p0.y - sy;
            double dz = p0.z - sz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(d > 0.0)) continue;
            double nx = dx / d;
            double ny = dy / d;
            double nz = dz / d;
            double approach = vx * nx + vy * ny + vz * nz;
            if (approach <= 0.0) continue;
            if (i != ref || blending) {
                // Vitesse relative au corps D - dp_i = v + dp_frame - dp_i ; jamais plus que l'approche commandee.
                Vector3d p1 = bodies[i];
                double relative = (vx + rdx - (p1.x - p0.x)) * nx + (vy + rdy - (p1.y - p0.y)) * ny
                        + (vz + rdz - (p1.z - p0.z)) * nz;
                if (relative < approach) approach = relative;
                if (approach <= 0.0) continue;
            }
            double radius = list.get(i).radius();
            double f = clamp01((d - radius - MIN_ALTITUDE_FACTOR * radius) / (APPROACH_FACTOR * radius));
            double cut = (1.0 - f) * approach;
            vx -= cut * nx;
            vy -= cut * ny;
            vz -= cut * nz;
        }

        // 4. Garde-fou sur D = v + dp_frame : jamais plus pres que R + max(h / 2, h_min) (h d'avant le pas). Dans le
        // repere de chaque corps, le monde va de o = s - p(t) a e - p(t + 1) ; teste sur le segment (pas seulement son
        // extremite) : un pas plus long que la distance au centre traverserait le corps.
        double ex = sx + (frameMoving ? vx + rdx : vx);
        double ey = sy + (frameMoving ? vy + rdy : vy);
        double ez = sz + (frameMoving ? vz + rdz : vz);
        for (int i = 0; i < bodyCount; i++) {
            Vector3d p0 = startBodies[i];
            Vector3d p = bodies[i];
            double radius = list.get(i).radius();
            double ox = sx - p0.x;
            double oy = sy - p0.y;
            double oz = sz - p0.z;
            double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
            double limit = radius + Math.max((d - radius) * 0.5, MIN_ALTITUDE_FACTOR * radius);
            // Segment o + tau w, tau dans [0, 1] (relatif au corps) : w = (e - s) - dp_i.
            double wx = (ex - sx) - (p.x - p0.x);
            double wy = (ey - sy) - (p.y - p0.y);
            double wz = (ez - sz) - (p.z - p0.z);
            boolean below = !(d >= limit);
            if (below) {
                // Sous le plancher au debut du pas (edition, ou arrondi apres un glissement) : depart ramene
                // radialement sur la sphere du plancher (centre : vers +X), puis le pas part de la.
                if (d > 0.0) {
                    double k = limit / d;
                    ox *= k;
                    oy *= k;
                    oz *= k;
                } else {
                    ox = limit;
                    oy = 0.0;
                    oz = 0.0;
                }
            }
            double a = wx * wx + wy * wy + wz * wz;
            double b = ox * wx + oy * wy + oz * wz;
            if (below && !(b < 0.0)) {
                // Depuis la sphere vers l'exterieur (ou sur place) : le segment ne rentre jamais dans la sphere.
                ex = p.x + ox + wx;
                ey = p.y + oy + wy;
                ez = p.z + oz + wz;
                continue;
            }
            if (!(a > 0.0)) continue;
            double closest = Math.max(0.0, Math.min(1.0, -b / a));
            double cx = ox + closest * wx;
            double cy = oy + closest * wy;
            double cz = oz + closest * wz;
            double limit2 = limit * limit;
            if (!below && cx * cx + cy * cy + cz * cz >= limit2) continue;
            // Entree dans la sphere de rayon limit (premiere racine de |o + tau w| = limit ; 0 depuis la sphere du
            // plancher), puis glissement : le reste du pas, prive de sa composante radiale, longe la sphere ; resultat
            // relatif au corps en fin de pas.
            double c = ox * ox + oy * oy + oz * oz - limit2;
            double entry = below ? 0.0 : Math.max(0.0, (-b - Math.sqrt(Math.max(0.0, b * b - a * c))) / a);
            double nx = ox + entry * wx;
            double ny = oy + entry * wy;
            double nz = oz + entry * wz;
            double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= n;
            ny /= n;
            nz /= n;
            double rest = 1.0 - entry;
            double rx = wx * rest;
            double ry = wy * rest;
            double rz = wz * rest;
            double radial = rx * nx + ry * ny + rz * nz;
            double tx = nx * limit + rx - radial * nx;
            double ty = ny * limit + ry - radial * ny;
            double tz = nz * limit + rz - radial * nz;
            double t = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double k = t < limit ? limit / t : 1.0;
            ex = p.x + tx * k;
            ey = p.y + ty * k;
            ez = p.z + tz * k;
        }
        if (!Double.isFinite(ex) || !Double.isFinite(ey) || !Double.isFinite(ez)) {
            ex = sx;
            ey = sy;
            ez = sz;
        }

        // 5. Resultat (dest peut etre pose : tout a ete lu) ; un pas de plus depuis la bascule.
        dest.position().set(ex, ey, ez);
        dest.orientation().set(rotation);
        frame.advance();
        setReference(dest.position(), system, ref, bodies);
        return v;
    }

    // ---------------------------------------------------------------- pose par defaut

    /**
     * Pose par defaut d'un systeme (doc 04 par. 5) au temps {@code tDays} : sur la droite parent -> planete de depart
     * ({@link #startPlanet} : la planete dont le demi-grand axe est le plus proche de 1 UA, la Terre dans Sol, quel que
     * soit son rang dans la liste), au-dela de la planete, a {@code min(0,02 a, 0,8 r)} d'elle ({@code r} = rayon de
     * <b>base</b> de sa sphere d'influence, {@link #baseInfluenceRadius}, sans le facteur de jeu, T39, C115 : le monde
     * part dans la sphere de la planete) ; sans planete, a 1 UA de
     * l'etoile principale sur {@code +X}. Puis ecartee de chaque corps a une altitude d'au moins 20 rayons (4 passes
     * radiales, dans l'ordre des corps). Orientation : avant du monde {@code Q F_W} tourne vers l'etoile principale,
     * haut du monde au plus pres de {@code +Y} du systeme (sinon de {@code -Z} si l'etoile est a la verticale).
     * La vitesse associee est {@code 0} (a poser par l'appelant). Fixe aussi le referentiel
     * ({@link #referenceBodyId()}, a poser par l'appelant dans son {@link SpaceFrameState#reset}) : le corps le plus
     * profond dont la sphere d'influence contient la pose par defaut ({@link #influenceTarget}, sans hysteresis).
     * Sans systeme : origine, identite, aucun referentiel.
     *
     * @return {@code dest}
     */
    public SpacePose defaultPose(SpaceSystem system, double tDays, SpaceMotionSettings settings, SpacePose dest)
    {
        if (system == null) {
            computePositions(null, tDays);
            setReference(dest.reset().position(), null, -1, bodies);
            return dest;
        }
        List<SpaceBody> list = computePositions(system, tDays);
        Vector3d star = bodies[0];
        double sx;
        double sy;
        double sz;
        int planet = startPlanet(list, bodyCount);
        if (planet >= 0) {
            Vector3d parent = bodies[system.parentIndex(planet)];
            Vector3d p = bodies[planet];
            double dx = p.x - parent.x;
            double dy = p.y - parent.y;
            double dz = p.z - parent.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double gap = Math.min(DEFAULT_PLANET_GAP * list.get(planet).orbit().semiMajorAxis() * SpaceUnits.AU_KM,
                    DEFAULT_INFLUENCE_FACTOR * baseInfluenceRadius(list.get(planet),
                            list.get(system.parentIndex(planet))));
            if (d > 0.0) {
                sx = p.x + dx / d * gap;
                sy = p.y + dy / d * gap;
                sz = p.z + dz / d * gap;
            } else {
                sx = p.x + gap;
                sy = p.y;
                sz = p.z;
            }
        } else {
            sx = star.x + DEFAULT_DISTANCE_AU * SpaceUnits.AU_KM;
            sy = star.y;
            sz = star.z;
        }

        // Ecartement : altitude >= 20 rayons pour chaque corps.
        for (int pass = 0; pass < CLEARANCE_PASSES; pass++) {
            for (int i = 0; i < bodyCount; i++) {
                Vector3d p = bodies[i];
                double min = list.get(i).radius() * (1.0 + DEFAULT_CLEARANCE);
                double ox = sx - p.x;
                double oy = sy - p.y;
                double oz = sz - p.z;
                double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (d >= min) continue;
                if (d > 0.0) {
                    sx = p.x + ox / d * min;
                    sy = p.y + oy / d * min;
                    sz = p.z + oz / d * min;
                } else {
                    sx = p.x + min;
                }
            }
        }

        lookAt(sx, sy, sz, star.x, star.y, star.z, settings, dest.orientation());
        dest.position().set(sx, sy, sz);
        setReference(dest.position(), system, influenceTarget(dest.position(), system, bodies, -1), bodies);
        return dest;
    }

    /**
     * Pose de <b>teleportation</b> pres du corps d'indice {@code index} de {@code system} au temps {@code tDays} (T44,
     * D34, doc 04 par. 5 bis), « comme en orbite » : a la distance
     * {@code d = max(}{@value #TELEPORT_MIN_RADII}{@code  R, min(}{@value #TELEPORT_RADII}{@code  R,
     * }{@value #TELEPORT_INFLUENCE_FRACTION}{@code  r))} de son centre ({@code R} rayon du corps, {@code r} rayon de
     * jeu de sa sphere d'influence, {@link #influenceRadius} ; infini pour un corps sans parent : 10 rayons, comme
     * l'arrivee d'un saut). Cote : vers l'etoile la plus proche dans sa parente (face eclairee) ; pour une etoile, vers
     * {@code approach} (direction d'ou l'on vient, depuis le corps ; nulle : vers son parent, sinon {@code +X}). Si ce
     * point n'a pas le corps pour cible de la regle des spheres d'influence (avec l'hysteresis du referentiel : la
     * sphere d'un enfant ou d'un voisin le contient) ou tombe dans un autre corps (altitude sous le plancher), les six
     * directions des axes sont essayees dans l'ordre {@code +X, -X, +Y, -Y, +Z, -Z} ; aucune ne convient : le premier
     * point est garde. Orientation : avant du monde tourne vers le corps ({@link #lookAt}). Vitesse associee nulle (a
     * poser par l'appelant) ; referentiel a poser par l'appelant = le corps ({@link #referenceBodyId()} le donne).
     * Deterministe, sans allocation.
     *
     * @param approach direction (depuis le corps) d'ou arrive le vaisseau, pour une etoile ; peut etre nulle
     * @return {@code dest}
     */
    public SpacePose orbitPose(SpaceSystem system, int index, double tDays, SpaceMotionSettings settings,
                               Vector3dc approach, SpacePose dest)
    {
        List<SpaceBody> list = computePositions(system, tDays);
        SpaceBody body = list.get(index);
        Vector3d p = bodies[index];
        int parent = system.parentIndex(index);
        double radius = body.radius();
        double influenceRadius = influenceRadius(body, parent < 0 ? null : list.get(parent));
        double d = Math.max(TELEPORT_MIN_RADII * radius,
                Math.min(TELEPORT_RADII * radius, TELEPORT_INFLUENCE_FRACTION * influenceRadius));

        // Direction principale : vers l'etoile ancetre la plus proche, ou (etoile) d'ou l'on vient.
        int star = parent;
        while (star >= 0 && list.get(star).kind() != SpaceBodyKind.STAR) star = system.parentIndex(star);
        if (body.kind() != SpaceBodyKind.STAR && star >= 0) {
            direction.set(bodies[star]).sub(p);
        } else if (approach != null && approach.lengthSquared() > 0.0) {
            direction.set(approach);
        } else if (parent >= 0) {
            direction.set(bodies[parent]).sub(p);
        } else {
            direction.set(1.0, 0.0, 0.0);
        }
        if (!(direction.lengthSquared() > 0.0) || !direction.isFinite()) direction.set(1.0, 0.0, 0.0);
        direction.normalize();
        double fx = direction.x;
        double fy = direction.y;
        double fz = direction.z;

        Vector3d s = dest.position();
        boolean found = false;
        for (int attempt = 0; attempt <= 6 && !found; attempt++) {
            if (attempt > 0) {
                int axis = (attempt - 1) / 2;
                double sign = (attempt - 1) % 2 == 0 ? 1.0 : -1.0;
                direction.set(axis == 0 ? sign : 0.0, axis == 1 ? sign : 0.0, axis == 2 ? sign : 0.0);
            }
            s.set(p).fma(d, direction);
            found = clearOfBodies(s, list) && influenceTarget(s, system, bodies, index) == index;
        }
        if (!found) s.set(p).fma(d, direction.set(fx, fy, fz));
        lookAt(s.x, s.y, s.z, p.x, p.y, p.z, settings, dest.orientation());
        setReference(s, system, index, bodies);
        return dest;
    }

    /** Vrai si {@code position} est au-dessus de l'altitude plancher de chaque corps (positions deja calculees). */
    private boolean clearOfBodies(Vector3dc position, List<SpaceBody> list)
    {
        for (int i = 0; i < bodyCount; i++) {
            if (altitude(position, list.get(i), bodies[i]) <= MIN_ALTITUDE_FACTOR * list.get(i).radius()) return false;
        }
        return true;
    }

    /**
     * Orientation du monde dont l'avant {@code Q F_W} vise le point {@code (tx, ty, tz)} depuis {@code (sx, sy, sz)},
     * haut du monde au plus pres de {@code +Y} du systeme (sinon de {@code -Z} si la cible est a la verticale) ; cible
     * confondue : avant sur {@code -Z}. Pose par defaut et teleportation ({@link #orbitPose}). Deterministe, sans
     * allocation.
     */
    private void lookAt(double sx, double sy, double sz, double tx, double ty, double tz,
                        SpaceMotionSettings settings, Quaterniond dest)
    {
        // f_S = direction vers la cible, r_S = f_S x haut, u_S = r_S x f_S ; Q envoie
        // (R_W, U_W, F_W) sur (r_S, u_S, f_S) : Q = r_S R_W^T + u_S U_W^T + f_S F_W^T.
        double fx = tx - sx;
        double fy = ty - sy;
        double fz = tz - sz;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl > 0.0) {
            fx /= fl;
            fy /= fl;
            fz /= fl;
        } else {
            fx = 0.0;
            fy = 0.0;
            fz = -1.0;
        }
        // f x (0, 1, 0) = (-fz, 0, fx) ; a la verticale : f x (0, 0, -1) = (-fy, fx, 0).
        double rx = -fz;
        double ry = 0.0;
        double rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0e-9) {
            rx = -fy;
            ry = fx;
            rz = 0.0;
            rl = Math.sqrt(rx * rx + ry * ry);
        }
        rx /= rl;
        ry /= rl;
        rz /= rl;
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        settings.forward(forward);
        settings.right(right);
        settings.up(up);
        SpaceRotations.fromMatrix(
                rx * right.x + ux * up.x + fx * forward.x,
                rx * right.y + ux * up.y + fx * forward.y,
                rx * right.z + ux * up.z + fx * forward.z,
                ry * right.x + uy * up.x + fy * forward.x,
                ry * right.y + uy * up.y + fy * forward.y,
                ry * right.z + uy * up.z + fy * forward.z,
                rz * right.x + uz * up.x + fz * forward.x,
                rz * right.y + uz * up.y + fz * forward.y,
                rz * right.z + uz * up.z + fz * forward.z,
                dest);
    }

    /**
     * Indice de la planete de depart d'une pose par defaut : parmi les corps {@code PLANET} (ni etoile, ni lune, ni
     * objet mineur), celui dont le demi-grand axe {@code a} est le plus proche de {@link #START_SEMI_MAJOR_AXIS} en
     * echelle logarithmique ({@code |ln a|} minimal), le premier dans l'ordre du systeme en cas d'egalite ;
     * {@code -1} sans planete. Ne depend ni du rang de la planete ni de son nom : la Terre reste la planete de depart
     * de Sol meme avec Mercure et Venus avant elle. Deterministe ({@link StrictMath}).
     */
    private static int startPlanet(List<SpaceBody> list, int count)
    {
        int best = -1;
        double bestGap = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            SpaceBody body = list.get(i);
            if (body.kind() != SpaceBodyKind.PLANET || body.orbit() == null) continue;
            double gap = StrictMath.abs(StrictMath.log(body.orbit().semiMajorAxis() / START_SEMI_MAJOR_AXIS));
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- corps le plus proche

    /**
     * Indice (dans {@code system.bodies()}) du corps de plus petite altitude {@code h = d - R} au temps
     * {@code tDays}, {@code -1} sans systeme ; l'altitude est ensuite lue par {@link #nearestAltitude()}.
     */
    public int nearestBody(SpacePose pose, SpaceSystem system, double tDays)
    {
        computePositions(system, tDays);
        int index = system == null ? -1 : nearestBody(pose.position(), system, bodies);
        nearestAltitude = index < 0 ? Double.POSITIVE_INFINITY
                : altitude(pose.position(), system.bodies().get(index), bodies[index]);
        return index;
    }

    /** Altitude (km) du corps rendu par le dernier {@link #nearestBody(SpacePose, SpaceSystem, double)}. */
    public double nearestAltitude() { return nearestAltitude; }

    /**
     * Indice du corps de plus petite altitude vu de {@code position}, positions des corps deja calculees
     * ({@link SpaceOrbits#positions}) ; {@code -1} si le systeme n'a pas de corps. Sans allocation.
     */
    public static int nearestBody(Vector3dc position, SpaceSystem system, Vector3d[] positions)
    {
        List<SpaceBody> list = system.bodies();
        int best = -1;
        double bestAltitude = Double.POSITIVE_INFINITY;
        for (int i = 0; i < list.size(); i++) {
            double h = altitude(position, list.get(i), positions[i]);
            if (h < bestAltitude) {
                bestAltitude = h;
                best = i;
            }
        }
        return best;
    }

    /** Altitude de {@code position} au-dessus du corps, en km : {@code |position - p| - R} (negative a l'interieur). */
    public static double altitude(Vector3dc position, SpaceBody body, Vector3dc bodyPosition)
    {
        double dx = position.x() - bodyPosition.x();
        double dy = position.y() - bodyPosition.y();
        double dz = position.z() - bodyPosition.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - body.radius();
    }

    /**
     * Distance d'arret au frein (doc 04 par. 5, T32 ; pour la vitesse de croisiere de T34), en km, relative au
     * referentiel : somme des deplacements nominaux {@code |V| k^n} des pas du freinage,
     * {@code k} = {@value #DECELERATION} ou, avec {@code boost}, {@value #BOOST_DECELERATION}, jusqu'au pas ou la
     * vitesse passe sous
     * {@value #MIN_SPEED} km/tick (arret net, deplacement nul). Meme suite de multiplications que {@link #step} :
     * resultat exact pour un pas sans freinage d'approche. Majoree par {@code k |V| / (1 - k)} (11,5 |V| sans boost,
     * 3,52 |V| avec). Le freinage d'approche ({@value #APPROACH_FACTOR} rayons) ne fait que ralentir davantage : la
     * distance reelle est plus courte. Sans allocation, au plus quelques centaines d'iterations pour
     * {@code |V| <= V_max}.
     *
     * @param speed vitesse nominale signee au debut du freinage, km/tick (non finie = 0)
     * @param boost frein renforce (Sprint + Saut)
     */
    public static double stoppingDistance(double speed, boolean boost)
    {
        double k = boost ? BOOST_DECELERATION : DECELERATION;
        double v = Double.isFinite(speed) ? Math.abs(speed) : 0.0;
        double distance = 0.0;
        while (true) {
            v *= k;
            if (v < MIN_SPEED) return distance;
            distance += v;
        }
    }

    // ---------------------------------------------------------------- referentiel

    /**
     * Rayon "de jeu" de la sphere d'influence d'un corps (T39, D29, doc 04 par. 5 point 0), en km, celui qui choisit le
     * referentiel : rayon de base ({@link #baseInfluenceRadius}) fois un facteur du type
     * ({@value #MOON_INFLUENCE_FACTOR} pour une lune, {@value #PLANET_INFLUENCE_FACTOR} pour une planete ou un objet
     * mineur), au plus {@value #INFLUENCE_MAX_ORBIT_FRACTION} fois le demi-grand axe de son orbite (km) ; une etoile
     * compagne garde son rayon de base (ni facteur, ni plafond) ; infini pour un corps sans parent. Deterministe
     * ({@link StrictMath}).
     *
     * @param parent parent du corps ({@code null} = sans parent)
     */
    public static double influenceRadius(SpaceBody body, SpaceBody parent)
    {
        double base = baseInfluenceRadius(body, parent);
        if (base == Double.POSITIVE_INFINITY) return base;
        double factor = switch (body.kind()) {
            case MOON -> MOON_INFLUENCE_FACTOR;
            case PLANET, MINOR -> PLANET_INFLUENCE_FACTOR;
            default -> 1.0;
        };
        // Etoile compagne : rayon de base, sans facteur ni plafond.
        if (body.kind() == SpaceBodyKind.STAR) return base;
        double cap = INFLUENCE_MAX_ORBIT_FRACTION * body.orbit().semiMajorAxis() * SpaceUnits.AU_KM;
        return Math.min(base * factor, cap);
    }

    /**
     * Rayon de base de la sphere d'influence d'un corps, en km :
     * {@code r = a (m / m_parent)^}{@value #INFLUENCE_EXPONENT} (Laplace), {@code a} = demi-grand axe de son orbite en
     * km, au moins {@value #INFLUENCE_FLOOR} rayons du corps ; infini pour un corps sans parent. Sert a la pose par
     * defaut ({@link #defaultPose}) ; le choix du referentiel utilise le rayon de jeu ({@link #influenceRadius}).
     * Deterministe ({@link StrictMath}).
     *
     * @param parent parent du corps ({@code null} = sans parent)
     */
    public static double baseInfluenceRadius(SpaceBody body, SpaceBody parent)
    {
        if (parent == null || body.orbit() == null) return Double.POSITIVE_INFINITY;
        double a = body.orbit().semiMajorAxis() * SpaceUnits.AU_KM;
        double r = a * StrictMath.pow(body.mass() / parent.mass(), INFLUENCE_EXPONENT);
        return Math.max(r, INFLUENCE_FLOOR * body.radius());
    }

    /**
     * Regle de bascule du referentiel (doc 04 par. 5 point 0) au debut d'un pas, positions des corps deja calculees ;
     * met {@code frame} a jour (candidature, bascule) sans avancer son compteur de pas. Corps vise {@code c} =
     * {@link #influenceTarget} (hysteresis sur le referentiel courant). {@code frame.reference()} absent du systeme
     * ({@code 0}, corps retire, systeme change) -> {@code c} aussitot, sans delai ni fondu ; sinon {@code c} different
     * du referentiel est candidat, et le devient apres {@value #SWITCH_DELAY} candidatures consecutives si
     * {@value #SWITCH_COOLDOWN} pas ont passe depuis la bascule precedente. Sans allocation.
     *
     * @return indice du referentiel (apres la regle) dans {@code system.bodies()}, {@code -1} si le systeme n'a pas de
     * corps
     */
    private int selectReference(SpacePose pose, SpaceSystem system, Vector3d[] positions, SpaceFrameState frame)
    {
        int current = system.indexOf(frame.reference());
        int best = influenceTarget(pose.position(), system, positions, current);
        if (best < 0) {
            frame.reset(0);
            return -1;
        }
        if (current < 0) {
            frame.reset(system.bodies().get(best).id());
            return best;
        }
        if (best == current) {
            frame.clearCandidate();
            return current;
        }
        int id = system.bodies().get(best).id();
        frame.nominate(id);
        if (frame.candidateTicks() >= SWITCH_DELAY && frame.sinceSwitch() >= SWITCH_COOLDOWN) {
            frame.switchTo(id);
            return best;
        }
        return current;
    }

    /**
     * Corps vise par la regle des spheres d'influence (T39, D29) vu de {@code position}, positions des corps deja
     * calculees : on part des corps sans parent (spheres infinies ; plusieurs : le plus attirant), puis on descend
     * tant qu'un enfant du corps retenu contient le monde (distance au centre {@code d <= r}) ; entre plusieurs enfants
     * qui le contiennent (spheres qui se recouvrent), le plus attirant {@code m / d^2} ({@code d^2} au moins 1 km^2) ;
     * a egalite, le premier dans l'ordre du systeme. <b>Hysteresis</b> : le corps d'indice {@code current} et ses
     * ancetres ont une sphere agrandie de {@value #INFLUENCE_HYSTERESIS} (on ne la quitte qu'au-dela). Deterministe,
     * sans allocation (spheres calculees une fois par systeme, {@link #prepareInfluence}).
     *
     * @param current indice du referentiel courant, {@code -1} sans hysteresis (pose par defaut, referentiel absent)
     * @return indice du corps vise, {@code -1} si le systeme n'a pas de corps
     */
    private int influenceTarget(Vector3dc position, SpaceSystem system, Vector3d[] positions, int current)
    {
        List<SpaceBody> list = system.bodies();
        int count = list.size();
        if (count == 0) return -1;
        prepareInfluence(system);
        for (int i = 0; i < count; i++) chain[i] = false;
        for (int i = current; i >= 0; i = parents[i]) chain[i] = true;
        double sx = position.x();
        double sy = position.y();
        double sz = position.z();
        int node = -1;
        while (true) {
            int best = -1;
            double bestPull = 0.0;
            for (int i = 0; i < count; i++) {
                if (parents[i] != node) continue;
                Vector3d p = positions[i];
                double dx = p.x - sx;
                double dy = p.y - sy;
                double dz = p.z - sz;
                double d2 = dx * dx + dy * dy + dz * dz;
                double limit = chain[i] ? influence[i] * INFLUENCE_HYSTERESIS : influence[i];
                if (!(d2 <= limit * limit)) continue;
                double pull = list.get(i).mass() / Math.max(d2, 1.0);
                if (best < 0 || pull > bestPull) {
                    best = i;
                    bestPull = pull;
                }
            }
            if (best < 0) return node;
            node = best;
        }
    }

    /**
     * Spheres d'influence et parents des corps de {@code system} ({@link #influence}, {@link #parents}), recalcules
     * seulement quand le systeme change (instance immuable : une edition ou un changement de systeme en donne une
     * autre). Sans allocation.
     */
    private void prepareInfluence(SpaceSystem system)
    {
        if (system == influenceSystem) return;
        List<SpaceBody> list = system.bodies();
        for (int i = 0; i < list.size(); i++) {
            int parent = system.parentIndex(i);
            parents[i] = parent;
            influence[i] = influenceRadius(list.get(i), parent < 0 ? null : list.get(parent));
        }
        influenceSystem = system;
    }

    /**
     * Referentiel au temps {@code tDays} (serveur, chaque tick, et apres une edition) : positions calculees, puis
     * {@code frame.reference()} absent du systeme -> corps vise aussitot ({@link #influenceTarget}, sans delai ni
     * fondu) ; sinon {@code frame} inchange (les bascules ne se font que dans {@link #step}). Rend l'indice du
     * referentiel et fixe {@link #referenceBodyId()}, {@link #referenceAltitude()}. Sans allocation.
     *
     * @return indice du referentiel dans {@code system.bodies()}, {@code -1} sans corps
     */
    public int refreshReference(SpacePose pose, SpaceSystem system, double tDays, SpaceFrameState frame)
    {
        computePositions(system, tDays);
        int index = -1;
        if (bodyCount > 0) {
            index = system.indexOf(frame.reference());
            if (index < 0) {
                index = influenceTarget(pose.position(), system, bodies, -1);
                frame.reset(system.bodies().get(index).id());
            }
        } else {
            frame.reset(0);
        }
        setReference(pose.position(), system, index, bodies);
        return index;
    }

    /** Poids smoothstep {@code x^2 (3 - 2x)} de {@code x} borne a {@code [0, 1]} (fondu du referentiel). */
    public static double smoothstep(double x)
    {
        double t = clamp01(x);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Referentiel rendu par l'appel : id, indice et altitude vue de {@code position}. */
    private void setReference(Vector3dc position, SpaceSystem system, int index, Vector3d[] positions)
    {
        referenceIndex = index;
        if (index < 0) {
            referenceId = 0;
            referenceAltitude = Double.POSITIVE_INFINITY;
        } else {
            SpaceBody body = system.bodies().get(index);
            referenceId = body.id();
            referenceAltitude = altitude(position, body, positions[index]);
        }
    }

    // ---------------------------------------------------------------- affichage

    /**
     * Pose d'affichage entre deux ticks <b>dans le repere du referentiel</b> (doc 05 par. 2, D8, leve L10) :
     * {@code s(tau) = lerp(s_k, s_k+1, f) - f dF + (F(tau) - F(t_k))}, ou {@code F} est le repere du pas qui a mene de
     * {@code previous} a {@code current} ({@code F = (1 - sigma) p_prev + sigma p_ref}, {@code sigma} de ce pas,
     * {@link SpaceFrameState#lastStepBlend()}) et {@code dF = F(t_k+1) - F(t_k)}. Revient a interpoler
     * {@code s - F} en ligne droite puis a rajouter {@code F} au temps d'affichage : le monde suit l'arc du
     * referentiel au lieu de sa corde ; exact aux deux ticks ({@code f = 0} et {@code 1}). Orientation :
     * {@code slerp}. Referentiel sans parent (ou aucun) : simple {@link SpacePose#lerp}.
     * <p>
     * Ecrase {@link #bodyPositions()} : a appeler sur une instance dediee a l'affichage (fil de rendu). Sans
     * allocation ; non deterministe bit a bit (JOML), reserve au rendu.
     *
     * @param previous     pose affichee au tick {@code k} (temps {@code previousDays})
     * @param current      pose affichee au tick {@code k + 1} (temps {@code currentDays})
     * @param frame        etat du referentiel apres le pas {@code k -> k + 1}
     * @param system       systeme courant ({@code null} : simple interpolation)
     * @param displayDays  temps d'affichage, entre {@code previousDays} et {@code currentDays}
     * @param partial      fraction {@code f} du tick, {@code [0, 1]}
     * @param dest         resultat ; peut etre {@code previous} ou {@code current}
     * @return {@code dest}
     */
    public SpacePose interpolate(SpacePose previous, SpacePose current, SpaceFrameState frame, SpaceSystem system,
                                 double previousDays, double currentDays, double displayDays, double partial,
                                 SpacePose dest)
    {
        SpacePose.lerp(previous, current, partial, dest);
        if (system == null) return dest;
        List<SpaceBody> list = system.bodies();
        int ref = system.indexOf(frame.reference());
        if (ref < 0) return dest;
        double sigma = frame.lastStepBlend();
        int from = sigma < 1.0 ? system.indexOf(frame.previous()) : -1;
        if (from < 0) sigma = 1.0;
        boolean refMoving = !list.get(ref).isRoot();
        boolean fromMoving = from >= 0 && !list.get(from).isRoot();
        if (!refMoving && !fromMoving) return dest;
        SpaceOrbits.positions(system, previousDays, startBodies);
        computePositions(system, currentDays);
        SpaceOrbits.positions(system, displayDays, displayBodies);
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        if (refMoving) {
            cx += sigma * arc(startBodies[ref].x, bodies[ref].x, displayBodies[ref].x, partial);
            cy += sigma * arc(startBodies[ref].y, bodies[ref].y, displayBodies[ref].y, partial);
            cz += sigma * arc(startBodies[ref].z, bodies[ref].z, displayBodies[ref].z, partial);
        }
        if (fromMoving) {
            double rest = 1.0 - sigma;
            cx += rest * arc(startBodies[from].x, bodies[from].x, displayBodies[from].x, partial);
            cy += rest * arc(startBodies[from].y, bodies[from].y, displayBodies[from].y, partial);
            cz += rest * arc(startBodies[from].z, bodies[from].z, displayBodies[from].z, partial);
        }
        dest.position().add(cx, cy, cz);
        return dest;
    }

    /**
     * Ecart entre l'arc d'un corps et sa corde au temps d'affichage, une composante :
     * {@code (p(tau) - p(t_k)) - f (p(t_k+1) - p(t_k))}.
     */
    private static double arc(double start, double end, double display, double partial)
    {
        return (display - start) - partial * (end - start);
    }

    // ---------------------------------------------------------------- interne

    /** Remplit {@link #bodies} au temps {@code tDays} ; rend la liste des corps (vide sans systeme). */
    private List<SpaceBody> computePositions(SpaceSystem system, double tDays)
    {
        if (system == null) {
            bodyCount = 0;
            return List.of();
        }
        SpaceOrbits.positions(system, tDays, bodies);
        bodyCount = system.bodies().size();
        return system.bodies();
    }

    private static double clamp01(double value)
    {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }
}
