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
 * <b>Referentiel</b> (doc 04 par. 5 point 0, decisions D7, D8) : la vitesse nominale {@code V} est relative a un
 * <b>corps de reference</b>, designe par son id (stable), qui entraine le monde dans son mouvement orbital (a
 * {@code V = 0}, le monde reste immobile par rapport a lui). Il se choisit par un score <b>proximite x regard</b>
 * {@code S_i = P_i G_i} ({@link #score}) : {@code P_i = 1 / max(h_i, 1 km)}, {@code G_i = 1 / (1 + (theta_i /}
 * {@value #AIM_HALF_ANGLE}{@code deg)^2)}, {@code theta_i} = angle entre la visee du pilote (ou l'axe avant) et le
 * corps, moins son rayon angulaire. Tres pres d'un corps, la proximite domine ; loin de tout, le corps vise gagne.
 * Bascule vers le meilleur score s'il depasse {@value #SWITCH_MARGIN} fois celui du referentiel pendant
 * {@value #SWITCH_DELAY} ticks consecutifs, au plus une fois tous les {@value #SWITCH_COOLDOWN} ticks ; le mouvement
 * d'entrainement passe de l'ancien au nouveau referentiel en {@value #BLEND_TICKS} ticks (smoothstep). L'etat
 * ({@link SpaceFrameState}) entre dans {@link #step} et y est mis a jour en place.
 * <p>
 * {@link #step} est <b>pure et deterministe bit a bit</b> : memes entrees -> memes sorties sur le serveur et sur les
 * clients (dead reckoning, {@code docs/space/05-synchronisation.md} par. 2). Trigonometrie par {@link StrictMath},
 * arithmetique ecrite ici ({@code SpaceRotations}), corps parcourus dans l'ordre du systeme.
 * <p>
 * <b>Instance</b> : elle porte son espace de travail (positions des corps au debut et a la fin du pas, scores, axes,
 * quaternion, referentiel), donc aucune allocation par appel ; une instance par fil (serveur, client, affichage),
 * jamais partagee. Apres {@link #step}, {@link #defaultPose}, {@link #refreshReference} ou
 * {@link #nearestBody(SpacePose, SpaceSystem, double)}, {@link #bodyPositions()} contient les positions des corps au
 * temps demande (fin du pas pour {@link #step}).
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
    /** Vitesse minimale, km par tick : depart du gaz, et arret net en dessous au freinage. */
    public static final double MIN_SPEED = 1.0;
    /**
     * Referentiel, regard : ecart angulaire {@code theta_0} (degres, hors du disque du corps) auquel le facteur de
     * regard {@code G} vaut 1/2 ; a 3 {@code theta_0}, il vaut 1/10.
     */
    public static final double AIM_HALF_ANGLE = 20.0;
    /** Referentiel, proximite : altitude plancher (km) du facteur {@code P = 1 / max(h, plancher)}. */
    public static final double MIN_SCORE_ALTITUDE = 1.0;
    /** Referentiel : un autre corps doit avoir un score superieur a ce facteur fois celui du referentiel. */
    public static final double SWITCH_MARGIN = 1.25;
    /** Referentiel, delai : ticks consecutifs de candidature avant la bascule (0,5 s). */
    public static final int SWITCH_DELAY = 10;
    /** Referentiel, cooldown : ticks minimum entre deux bascules (3 s). */
    public static final int SWITCH_COOLDOWN = 60;
    /** Referentiel, fondu : ticks pour passer du mouvement d'entrainement de l'ancien au nouveau (2 s). */
    public static final int BLEND_TICKS = 40;
    /** Pose par defaut : distance au parent de la premiere planete, en demi-grands axes de son orbite. */
    public static final double DEFAULT_ORBIT_FACTOR = 1.02;
    /** Pose par defaut sans planete : distance a l'etoile principale, en UA (au moins hors de la marge). */
    public static final double DEFAULT_DISTANCE_AU = 1.0;
    /** Pose par defaut : altitude minimale au-dessus de chaque corps, en rayons du corps. */
    public static final double DEFAULT_CLEARANCE = 20.0;
    /** Pose par defaut : nombre de passes d'ecartement des corps (borne fixe : deterministe). */
    private static final int CLEARANCE_PASSES = 4;
    /** {@link #AIM_HALF_ANGLE} en radians. */
    private static final double AIM_HALF_ANGLE_RAD = Math.toRadians(AIM_HALF_ANGLE);

    /** Positions des corps a la fin du pas (ou au temps demande hors pas). */
    private final Vector3d[] bodies = SpaceOrbits.newPositionArray();
    /** Positions des corps au debut du pas ({@link #step}, {@link #interpolate}). */
    private final Vector3d[] startBodies = SpaceOrbits.newPositionArray();
    /** Positions des corps au temps d'affichage ({@link #interpolate} seulement). */
    private final Vector3d[] displayBodies = SpaceOrbits.newPositionArray();
    /** Scores du referentiel par corps ({@link #score}). */
    private final double[] scores = new double[SpaceLimits.MAX_BODIES];
    private final Vector3d forward = new Vector3d();
    private final Vector3d right = new Vector3d();
    private final Vector3d up = new Vector3d();
    private final Vector3d direction = new Vector3d();
    private final Vector3d aim = new Vector3d();
    private final Quaterniond rotation = new Quaterniond();
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
     *     <li>referentiel, pose et positions au debut du pas ({@link #selectReference}) : score {@code P G} de
     *     chaque corps, visee de {@code input} ; {@code ref} absent -> meilleur score, sans delai ni fondu ; sinon
     *     bascule vers le meilleur score {@code c} s'il depasse {@value #SWITCH_MARGIN} fois celui de {@code ref}
     *     pendant {@value #SWITCH_DELAY} pas consecutifs et que {@value #SWITCH_COOLDOWN} pas ont passe depuis la
     *     bascule precedente. Ne modifie ni la pose ni {@code V} ;</li>
     *     <li>gaz, vitesse <b>signee</b> {@code V} dans {@code [-V_max, V_max]} ({@code V < 0} : le monde part en
     *     arriere) : frein ({@code brake}) -> {@code V = 0,92 V}, puis {@code 0} sous 1 km/tick en valeur absolue,
     *     quel que soit {@code throttle} (le frein prime) ; sinon gaz {@code s = throttle != 0} de signe oppose a
     *     {@code V} -> meme deceleration jusqu'a 0 ; sinon {@code V = s min(V_max, max(1, 1,05 |V|))} (accelere
     *     dans le sens {@code s}, en partant de 1 km/tick) ; sans touche, {@code V} inchangee (l'elan est garde) ;</li>
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
     * @param input     entree du pilote, visee comprise ({@link SpacePilotInput#ZERO} sans pilote : visee sur l'axe
     *                  avant)
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
        int ref = bodyCount > 0 ? selectReference(pose, input, system, startBodies, settings, frame) : -1;
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

        // 1. Gaz, vitesse signee : le frein prime ; un gaz oppose au sens de V freine d'abord jusqu'a 0.
        int throttle = input.throttleSign();
        if (input.brake() || throttle > 0 && v < 0.0 || throttle < 0 && v > 0.0) {
            v *= DECELERATION;
            if (Math.abs(v) < MIN_SPEED) v = 0.0;
        } else if (throttle != 0) {
            v = throttle * Math.min(maxSpeed, Math.max(MIN_SPEED, Math.abs(v) * ACCELERATION));
        }

        // 2. Rotation, dans les axes du monde, composee a droite (Rot(axe, 0) = identite exacte : sautee).
        settings.forward(forward);
        settings.right(right);
        settings.up(up);
        rotation.set(pose.orientation());
        double turn = Math.toRadians(settings.turnRate());
        if (input.yaw() != 0) {
            SpaceRotations.multiplyAxisAngle(rotation, up.x, up.y, up.z, -input.yawRate() * turn);
        }
        if (input.pitch() != 0) {
            SpaceRotations.multiplyAxisAngle(rotation, right.x, right.y, right.z, input.pitchRate() * turn);
        }
        int roll = input.rollSign();
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
     * Pose par defaut d'un systeme (doc 04 par. 5) au temps {@code tDays} : sur la droite parent -> premiere planete
     * (premier corps {@code PLANET} de la liste), a {@code 1,02 a} de son parent ; sans planete, a 1 UA de l'etoile
     * principale sur {@code +X}. Puis ecartee de chaque corps a une altitude d'au moins 20 rayons (4 passes
     * radiales, dans l'ordre des corps). Orientation : avant du monde {@code Q F_W} tourne vers l'etoile principale,
     * haut du monde au plus pres de {@code +Y} du systeme (sinon de {@code -Z} si l'etoile est a la verticale).
     * La vitesse associee est {@code 0} (a poser par l'appelant). Fixe aussi le referentiel
     * ({@link #referenceBodyId()}, a poser par l'appelant dans son {@link SpaceFrameState#reset}) : le meilleur score
     * proximite x regard a la pose par defaut, visee sur l'axe avant ({@link #score}). Sans systeme : origine,
     * identite, aucun referentiel.
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
        int planet = -1;
        for (int i = 0; i < bodyCount && planet < 0; i++) if (list.get(i).kind() == SpaceBodyKind.PLANET) planet = i;
        if (planet >= 0) {
            Vector3d parent = bodies[system.parentIndex(planet)];
            Vector3d p = bodies[planet];
            double dx = p.x - parent.x;
            double dy = p.y - parent.y;
            double dz = p.z - parent.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double distance = DEFAULT_ORBIT_FACTOR * list.get(planet).orbit().semiMajorAxis() * SpaceUnits.AU_KM;
            if (d > 0.0) {
                sx = parent.x + dx / d * distance;
                sy = parent.y + dy / d * distance;
                sz = parent.z + dz / d * distance;
            } else {
                sx = parent.x + distance;
                sy = parent.y;
                sz = parent.z;
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

        // Orientation : f_S = direction vers l'etoile principale, r_S = f_S x haut, u_S = r_S x f_S ; Q envoie
        // (R_W, U_W, F_W) sur (r_S, u_S, f_S) : Q = r_S R_W^T + u_S U_W^T + f_S F_W^T.
        double fx = star.x - sx;
        double fy = star.y - sy;
        double fz = star.z - sz;
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
                dest.orientation());
        dest.position().set(sx, sy, sz);
        setReference(dest.position(), system, score(dest, SpacePilotInput.ZERO, system, bodies, settings), bodies);
        return dest;
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

    // ---------------------------------------------------------------- referentiel

    /**
     * Regle de bascule du referentiel (doc 04 par. 5 point 0) au debut d'un pas, positions des corps deja calculees ;
     * met {@code frame} a jour (candidature, bascule) sans avancer son compteur de pas. {@code frame.reference()}
     * absent du systeme ({@code 0}, corps retire, systeme change) -> meilleur score aussitot, sans delai ni fondu ;
     * sinon le meilleur score {@code c} est candidat s'il depasse {@value #SWITCH_MARGIN} fois celui du referentiel,
     * et le devient apres {@value #SWITCH_DELAY} candidatures consecutives si {@value #SWITCH_COOLDOWN} pas ont passe
     * depuis la bascule precedente. Sans allocation.
     *
     * @return indice du referentiel (apres la regle) dans {@code system.bodies()}, {@code -1} si le systeme n'a pas de
     * corps
     */
    private int selectReference(SpacePose pose, SpacePilotInput input, SpaceSystem system, Vector3d[] positions,
                                SpaceMotionSettings settings, SpaceFrameState frame)
    {
        int best = score(pose, input, system, positions, settings);
        if (best < 0) {
            frame.reset(0);
            return -1;
        }
        int current = system.indexOf(frame.reference());
        if (current < 0) {
            frame.reset(system.bodies().get(best).id());
            return best;
        }
        if (best == current || !(scores[best] > SWITCH_MARGIN * scores[current])) {
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
     * Scores du referentiel vus de {@code pose}, positions des corps deja calculees, rendus dans {@link #scores} :
     * {@code S_i = P_i G_i} avec {@code P_i = 1 / max(h_i, 1 km)} et {@code G_i = 1 / (1 + (theta_i / theta_0)^2)},
     * {@code theta_i = max(0, angle(a, w_i) - alpha_i)} : angle entre la direction de visee {@code a} et la direction
     * du corps {@code w_i}, moins son rayon angulaire {@code alpha_i = asin(min(1, R_i / d_i))} (viser n'importe ou
     * sur le disque vaut viser le centre). {@code a = Q} x regard {@code (yaw_F + aimYaw, pitch_F - aimPitch)} (axes
     * du monde -> systeme) : visee du pilote, ou axe avant sans visee. Monde au centre d'un corps : {@code theta = 0}.
     * Deterministe ({@link StrictMath}), sans allocation.
     *
     * @return indice du meilleur score (le premier en cas d'egalite), {@code -1} si le systeme n'a pas de corps
     */
    private int score(SpacePose pose, SpacePilotInput input, SpaceSystem system, Vector3d[] positions,
                      SpaceMotionSettings settings)
    {
        List<SpaceBody> list = system.bodies();
        double yaw = Math.toRadians(settings.forwardYaw() + input.aimYawDegrees());
        double pitch = Math.toRadians(settings.forwardPitch() - input.aimPitchDegrees());
        double horizontal = StrictMath.cos(pitch);
        SpaceRotations.rotate(pose.orientation(), -StrictMath.sin(yaw) * horizontal, -StrictMath.sin(pitch),
                StrictMath.cos(yaw) * horizontal, aim);
        Vector3dc s = pose.position();
        int best = -1;
        double bestScore = 0.0;
        for (int i = 0; i < list.size(); i++) {
            double radius = list.get(i).radius();
            Vector3d p = positions[i];
            double dx = p.x - s.x();
            double dy = p.y - s.y();
            double dz = p.z - s.z();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double theta = 0.0;
            if (d > 0.0) {
                double cx = aim.y * dz - aim.z * dy;
                double cy = aim.z * dx - aim.x * dz;
                double cz = aim.x * dy - aim.y * dx;
                double angle = StrictMath.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz),
                        aim.x * dx + aim.y * dy + aim.z * dz);
                theta = Math.max(0.0, angle - StrictMath.asin(Math.min(1.0, radius / d)));
            }
            double ratio = theta / AIM_HALF_ANGLE_RAD;
            double value = 1.0 / (Math.max(d - radius, MIN_SCORE_ALTITUDE) * (1.0 + ratio * ratio));
            scores[i] = value;
            if (best < 0 || value > bestScore) {
                best = i;
                bestScore = value;
            }
        }
        return best;
    }

    /**
     * Referentiel au temps {@code tDays} (serveur, chaque tick, et apres une edition) : positions calculees, puis
     * {@code frame.reference()} absent du systeme -> meilleur score aussitot ({@link #score}, sans delai ni fondu) ;
     * sinon {@code frame} inchange (les bascules ne se font que dans {@link #step}). Rend l'indice du referentiel et
     * fixe {@link #referenceBodyId()}, {@link #referenceAltitude()}. Sans allocation.
     *
     * @return indice du referentiel dans {@code system.bodies()}, {@code -1} sans corps
     */
    public int refreshReference(SpacePose pose, SpacePilotInput input, SpaceSystem system, double tDays,
                                SpaceMotionSettings settings, SpaceFrameState frame)
    {
        computePositions(system, tDays);
        int index = -1;
        if (bodyCount > 0) {
            index = system.indexOf(frame.reference());
            if (index < 0) {
                index = score(pose, input, system, bodies, settings);
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
