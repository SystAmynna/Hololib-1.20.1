package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * Calculateur de vitesse de croisiere de Space Pilot (T34, D25, C76 ; {@code docs/space/04-mathematiques.md} par. 11) :
 * fonctions pures, sans allocation, sans etat.
 * <p>
 * <b>Vitesse cible</b> {@code v*} ({@link #targetSpeed}, retouche T34) : consigne pour un pilote <b>humain</b> qui
 * suit la fleche avec le <b>frein normal</b> (Espace seul, ou gaz oppose :
 * {@code k = }{@value SpaceMotion#DECELERATION} par tick) et un <b>temps de reaction</b> ; le frein rapide (Sprint +
 * Espace, C74) n'est plus qu'un rattrapage. La plus grande vitesse nominale {@code V} (km/tick, relative au
 * referentiel) qui respecte deux conditions :
 * <pre>
 * nominale : g_c(V) = (T_c + 1) V + S(V)                &lt;= d - m      (s'arreter a la marge)
 * garantie : g_a(V) = n_1 + ... + n_(T_a+1) + S(n)      &lt;= d - h_min  (jamais la surface)
 *            n_i = min(V_max, max(1, a_b n_(i-1))), n_0 = V,  n = n_(T_a+1)
 * </pre>
 * {@code T_c} = {@value #REACTION_TICKS}, {@code T_a} = {@value #WORST_REACTION_TICKS}, {@code a_b} =
 * {@value SpaceMotion#BOOST_ACCELERATION} (gaz avec boost), {@code S} =
 * {@link SpaceMotion#stoppingDistance}{@code (., false)} (frein normal, exacte), {@code m} = {@link #margin}
 * ({@value #MARGIN_FACTOR} rayons), {@code h_min} = plancher de D9 ({@code 0,01 R}).
 * <ul>
 *     <li><b>Nominale</b> : le pilote voit la fleche passer a la baisse et freine apres {@value #REACTION_TICKS} ticks
 *     (plus le tick de decision) passes a vitesse constante : il s'arrete avant la marge ;</li>
 *     <li><b>Garantie</b> : meme s'il continue d'accelerer <b>avec le boost</b> pendant {@value #WORST_REACTION_TICKS}
 *     ticks (+ 1) apres le changement de fleche (cas du croisement de la consigne pendant l'acceleration, le plus
 *     courant), puis freine normalement, il n'atteint pas le plancher : il entame au pire la marge, ou le freinage
 *     d'approche agit.</li>
 * </ul>
 * {@code g_c} et {@code g_a} sont croissantes : {@code v*} par dichotomie ({@value #BISECTION_STEPS} pas au plus). Loin
 * de la cible, la garantie domine : forme close prudente (sans plafond)
 * {@code v* ~ min((d - m) / 18,5, (d - h_min) / 86,9)}, avec {@code 18,5 = T_c + 1 + k/(1-k)} et
 * {@code 86,9 = a_b (a_b^11 - 1)/(a_b - 1) + a_b^11 k/(1-k)}. {@code v*} est proportionnelle a la distance : elle
 * baisse a mesure qu'on approche, et la suivre en freinage normal amene a l'arret juste avant la marge.
 * <p>
 * Pourquoi : la consigne precedente (frein rapide, reaction d'un tick) etait depassee <b>systematiquement</b> par un
 * joueur (banc T34 bis, Terre -&gt; Lune : arret 86 000 km sous la surface au frein normal sans retard, 243 000 km avec
 * 6 ticks de retard, 1 100 000 km en accelerant avec le boost). Le gaz est multiplicatif : pendant 6 ticks de reaction,
 * le boost multiplie la vitesse par 2,4 (10 ticks : 4,3).
 * <p>
 * <b>Referentiel et interception</b> (T39, C118, remplace l'eloignement de C116) : {@code V} (affichee et comparee a
 * {@code v*}) est relative au referentiel, et le referentiel ne bascule sur la cible qu'a l'entree de sa sphere
 * d'influence (D29). Le mouvement propre {@code u} d'une cible qui n'est pas le referentiel (deplacement par tick,
 * relatif au referentiel, <b>radial et transverse</b>) est donc compte en entier ({@link #interceptSpeed}) : la
 * vitesse conseillee est le vecteur {@code w = u + c n}, ou {@code n} = direction monde -&gt; cible et {@code c} = la
 * vitesse d'approche sure de {@link #targetSpeed} pour la distance a la surface. {@code v* = |w|} (borne a
 * {@code V_max}) et la direction a viser est {@code w / |w|} : le <b>point d'interception</b>, affiche pres de la cible
 * quand il s'en ecarte de facon visible ({@link #interceptShown}). En suivant cette direction a cette vitesse, le monde
 * se rapproche de la cible a {@code c} exactement (la consigne sure de T34) tout en l'accompagnant dans son mouvement :
 * il entre dans sa sphere et y reste le delai de bascule. Une cible referentiel a {@code u = 0} : consigne de T34.
 * Sans ce terme, un pilote qui suit la consigne restait a l'equilibre hors de la sphere des planetes interieures (banc
 * T39 : Mars a 800 000 km) ; avec l'eloignement seul (C116), il atteignait les planetes et les grosses lunes, jamais
 * Phobos (sphere de 451 km, 770 km/tick autour de Mars) ni Deimos.
 * <p>
 * <b>Cible</b> ({@link #pickTarget}) : corps du systeme courant le plus proche du centre de l'ecran, angle entre le
 * regard de la camera et la direction du corps moins son rayon angulaire (viser le disque compte comme viser le
 * centre ; a egalite, le plus proche : celui qui occulte l'autre), au plus {@value #TARGET_ANGLE} degres, avec une
 * hysteresis de {@value #TARGET_HYSTERESIS} degre.
 * <p>
 * <b>Couleur</b> ({@link #redness}) : ecart relatif {@code |v - v*| / v*} ; vert jusqu'a {@value #GREEN_TOLERANCE},
 * rouge au-dela de {@value #RED_TOLERANCE}, degrade lineaire entre (C76).
 * <p>
 * Calcul client seulement (HUD) : rien n'est simule ni envoye, le determinisme bit a bit n'est pas requis ; la
 * trigonometrie passe tout de meme par {@link StrictMath} comme le reste du package.
 */
public final class SpaceCruise
{
    /**
     * Marge avant la surface de la cible, en rayons de la cible : altitude plancher (D9, {@code 0,01 R}) plus zone de
     * freinage d'approche ({@code 2 R}) : le freinage d'approche n'intervient pas encore a cette altitude, la distance
     * d'arret y est donc exacte. Lune 3 492 km, Terre 12 806 km.
     */
    public static final double MARGIN_FACTOR = SpaceMotion.MIN_ALTITUDE_FACTOR + SpaceMotion.APPROACH_FACTOR;
    /** Cible : ecart maximal (degres) entre le centre de l'ecran et le disque d'un corps pour qu'il soit retenu. */
    public static final double TARGET_ANGLE = 10.0;
    /**
     * Cible, hysteresis (degres) : un autre corps ne remplace la cible que s'il est plus pres du centre d'au moins cet
     * angle ; la cible n'est perdue qu'au-dela de {@link #TARGET_ANGLE} plus cet angle.
     */
    public static final double TARGET_HYSTERESIS = 1.0;
    /** Couleur : ecart relatif {@code |v - v*| / v*} jusqu'auquel l'indication est verte. */
    public static final double GREEN_TOLERANCE = 0.10;
    /** Couleur : ecart relatif au-dela duquel l'indication est rouge (degrade entre les deux). */
    public static final double RED_TOLERANCE = 0.30;
    /**
     * Temps de reaction nominal du pilote, en ticks (300 ms) : entre le changement de la fleche et le debut du
     * freinage, passe a vitesse constante (condition nominale, arret a la marge).
     */
    public static final int REACTION_TICKS = 6;
    /**
     * Temps de reaction de la garantie, en ticks (500 ms) : passe a accelerer avec le boost avant de freiner (condition
     * garantie, jamais le plancher).
     */
    public static final int WORST_REACTION_TICKS = 10;
    /**
     * Point d'interception (C118) : ecart minimal (degres) au-dela du bord du disque de la cible pour qu'il soit
     * affiche.
     */
    public static final double INTERCEPT_MARGIN = 0.5;
    /** Pas de dichotomie au plus (resolution {@code V_max / 2^64}, bien sous le km/tick). */
    private static final int BISECTION_STEPS = 64;

    private SpaceCruise() {}

    // ---------------------------------------------------------------- vitesse cible

    /** Marge (km) avant la surface d'une cible de rayon {@code radius} : {@value #MARGIN_FACTOR} rayons. */
    public static double margin(double radius) { return MARGIN_FACTOR * radius; }

    /**
     * Vitesse cible {@code v*} (km/tick, positive, relative au referentiel) : la plus grande vitesse {@code V} de
     * {@code [0, V_max]} qui respecte la condition nominale ({@link #nominalDistance} &lt;= {@code d - marge}) et la
     * condition garantie ({@link #guaranteedDistance} &lt;= {@code d - h_min}). {@code 0} si la cible est deja dans sa
     * marge (ou si meme le depart a 1 km/tick deborderait), {@code V_max} si la distance le permet. Sans allocation, au
     * plus {@value #BISECTION_STEPS} evaluations.
     *
     * @param surfaceDistance distance du monde a la surface de la cible, km ({@code |p - s| - R})
     * @param radius          rayon de la cible, km (marge {@link #margin}, plancher {@code 0,01 R})
     * @param maxSpeed        {@code V_max} (reglage de l'Environment), km/tick
     */
    public static double targetSpeed(double surfaceDistance, double radius, double maxSpeed)
    {
        double vmax = Double.isFinite(maxSpeed) ? Math.max(SpaceMotion.MIN_SPEED, maxSpeed) : SpaceMotion.MIN_SPEED;
        double nominal = surfaceDistance - margin(radius);
        double guaranteed = surfaceDistance - SpaceMotion.MIN_ALTITUDE_FACTOR * radius;
        if (!(nominal > 0.0)) return 0.0;
        if (fits(vmax, vmax, nominal, guaranteed)) return vmax;
        if (!fits(0.0, vmax, nominal, guaranteed)) return 0.0;
        // Invariant : lo respecte les deux conditions, hi non ; les deux distances sont croissantes en V.
        double lo = 0.0;
        double hi = vmax;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = 0.5 * (lo + hi);
            if (mid <= lo || mid >= hi) break;
            if (fits(mid, vmax, nominal, guaranteed)) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /**
     * Vitesse et direction d'interception (T39, C118) : {@code w = u + c n}, {@code c} = {@link #targetSpeed} pour la
     * distance du monde a la surface de la cible, {@code n} = direction monde -&gt; centre de la cible, {@code u} =
     * deplacement de la cible pendant un tick moins celui du referentiel (nul si la cible est le referentiel). Ecrit la
     * direction unitaire a viser dans {@code direction} ({@code n} si {@code w} est nul ou le monde au centre) et rend
     * {@code v* = min(|w|, V_max)}. Sans allocation.
     *
     * @param position  position du monde (km, repere du systeme)
     * @param target    centre de la cible au debut du tick
     * @param motionX   composantes de {@code u}, km/tick
     * @param radius    rayon de la cible, km
     * @param maxSpeed  {@code V_max}, km/tick
     * @param direction direction a viser (resultat, unitaire, axes du systeme)
     */
    public static double interceptSpeed(Vector3dc position, Vector3dc target, double motionX, double motionY,
                                        double motionZ, double radius, double maxSpeed, Vector3d direction)
    {
        double nx = target.x() - position.x();
        double ny = target.y() - position.y();
        double nz = target.z() - position.z();
        double d = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(d > 0.0)) {
            direction.set(0.0, 0.0, 0.0);
            return 0.0;
        }
        nx /= d;
        ny /= d;
        nz /= d;
        double c = targetSpeed(d - radius, radius, maxSpeed);
        double wx = motionX + c * nx;
        double wy = motionY + c * ny;
        double wz = motionZ + c * nz;
        double w = Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (!(w > 0.0) || !Double.isFinite(w)) {
            direction.set(nx, ny, nz);
            return 0.0;
        }
        direction.set(wx / w, wy / w, wz / w);
        double vmax = Double.isFinite(maxSpeed) ? Math.max(SpaceMotion.MIN_SPEED, maxSpeed) : SpaceMotion.MIN_SPEED;
        return Math.min(vmax, w);
    }

    /**
     * Vrai si le point d'interception ({@code direction}, unitaire) s'ecarte de facon visible de la cible : angle entre
     * lui et le centre de la cible superieur au rayon angulaire de la cible plus {@value #INTERCEPT_MARGIN} degre.
     */
    public static boolean interceptShown(Vector3dc position, Vector3dc target, double radius, Vector3dc direction)
    {
        double nx = target.x() - position.x();
        double ny = target.y() - position.y();
        double nz = target.z() - position.z();
        double d = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(d > radius)) return false;
        double cos = (nx * direction.x() + ny * direction.y() + nz * direction.z()) / d;
        double angle = StrictMath.acos(Math.max(-1.0, Math.min(1.0, cos)));
        return angle > StrictMath.asin(radius / d) + Math.toRadians(INTERCEPT_MARGIN);
    }

    /** Vrai si {@code V} respecte les deux conditions de {@link #targetSpeed}. */
    private static boolean fits(double speed, double maxSpeed, double nominal, double guaranteed)
    {
        return nominalDistance(speed) <= nominal && guaranteedDistance(speed, maxSpeed) <= guaranteed;
    }

    /**
     * Distance consommee de la condition nominale {@code g_c(V)} (km) : {@value #REACTION_TICKS} ticks de reaction plus
     * le tick de decision a la vitesse {@code |speed|} constante, puis freinage normal jusqu'a l'arret
     * ({@link SpaceMotion#stoppingDistance}{@code (V, false)}). Croissante en {@code |speed|}. Sans allocation.
     */
    public static double nominalDistance(double speed)
    {
        double v = Double.isFinite(speed) ? Math.abs(speed) : 0.0;
        return (REACTION_TICKS + 1) * v + SpaceMotion.stoppingDistance(v, false);
    }

    /**
     * Distance consommee de la condition garantie {@code g_a(V)} (km) : {@value #WORST_REACTION_TICKS} ticks de
     * reaction plus le tick de decision passes a accelerer avec le boost (vitesse multipliee par
     * {@value SpaceMotion#BOOST_ACCELERATION} a chaque tick, au moins 1 km/tick, au plus {@code V_max}, le monde
     * avancant de la nouvelle vitesse), puis freinage normal jusqu'a l'arret. Croissante en {@code |speed|}. Sans
     * allocation.
     */
    public static double guaranteedDistance(double speed, double maxSpeed)
    {
        double v = Double.isFinite(speed) ? Math.abs(speed) : 0.0;
        double distance = 0.0;
        for (int i = 0; i <= WORST_REACTION_TICKS; i++) {
            v = Math.min(maxSpeed, Math.max(SpaceMotion.MIN_SPEED, v * SpaceMotion.BOOST_ACCELERATION));
            distance += v;
        }
        return distance + SpaceMotion.stoppingDistance(v, false);
    }

    // ---------------------------------------------------------------- couleur

    /**
     * Rougeur de l'indication, {@code [0, 1]} : {@code 0} (vert) si {@code |v - v*| <= }{@value #GREEN_TOLERANCE}
     * {@code v*}, {@code 1} (rouge) au-dela de {@value #RED_TOLERANCE} {@code v*}, lineaire entre (C76). Pour
     * {@code v* = 0}, vert seulement a l'arret. Sans allocation.
     *
     * @param speed  vitesse nominale signee {@code v}, km/tick
     * @param target vitesse cible {@code v* >= 0}, km/tick
     */
    public static double redness(double speed, double target)
    {
        double error = Math.abs(speed - target);
        if (!(target > 0.0)) return error == 0.0 ? 0.0 : 1.0;
        double relative = error / target;
        if (!(relative > GREEN_TOLERANCE)) return 0.0;
        if (!(relative < RED_TOLERANCE)) return 1.0;
        return (relative - GREEN_TOLERANCE) / (RED_TOLERANCE - GREEN_TOLERANCE);
    }

    // ---------------------------------------------------------------- cible

    /**
     * Cible de la vitesse de croisiere : indice (dans {@code system.bodies()}) du corps le plus proche du centre de
     * l'ecran, ou {@code -1}. Ecart d'un corps {@code theta_i = max(0, angle(regard, p_i - s) - alpha_i)} (degres),
     * {@code alpha_i = asin(min(1, R_i / d_i))} : viser son disque vaut viser son centre ; a ecart egal (les deux
     * disques sous le centre), le plus proche, qui occulte l'autre. Hysteresis : la cible courante
     * ({@code currentId}) reste tant que son ecart ne depasse pas {@value #TARGET_ANGLE} + {@value #TARGET_HYSTERESIS}
     * degres, et n'est remplacee que par un corps plus pres du centre d'au moins {@value #TARGET_HYSTERESIS} degre (ou
     * plus proche du monde quand les deux disques couvrent le centre) ; sans cible courante, le meilleur corps s'il est
     * a moins de {@value #TARGET_ANGLE} degres. Sans allocation.
     *
     * @param system     systeme courant ({@code null} : aucune cible)
     * @param positions  positions des corps (km, repere systeme), {@code positions[i]} pour {@code bodies().get(i)}
     * @param count      nombre de positions valables
     * @param position   position du monde, km (repere systeme)
     * @param look       regard de la camera, unitaire, <b>dans les axes du systeme</b> ({@code Q} fois le regard monde)
     * @param currentId  id de la cible courante, {@code 0} sans cible
     */
    public static int pickTarget(SpaceSystem system, Vector3d[] positions, int count, Vector3dc position,
                                 Vector3dc look, int currentId)
    {
        if (system == null) return -1;
        List<SpaceBody> bodies = system.bodies();
        int n = Math.min(count, bodies.size());
        int best = -1;
        double bestTheta = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        int current = -1;
        double currentTheta = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            SpaceBody body = bodies.get(i);
            Vector3d p = positions[i];
            double rx = p.x - position.x();
            double ry = p.y - position.y();
            double rz = p.z - position.z();
            double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (!(d > 0.0)) continue;
            double cos = (rx * look.x() + ry * look.y() + rz * look.z()) / d;
            double angle = StrictMath.toDegrees(StrictMath.acos(Math.max(-1.0, Math.min(1.0, cos))));
            double alpha = StrictMath.toDegrees(StrictMath.asin(Math.min(1.0, body.radius() / d)));
            double theta = Math.max(0.0, angle - alpha);
            if (!Double.isFinite(theta)) continue;
            if (body.id() == currentId) {
                current = i;
                currentTheta = theta;
            }
            if (theta < bestTheta || theta == bestTheta && d < bestDistance) {
                best = i;
                bestTheta = theta;
                bestDistance = d;
            }
        }
        if (current >= 0 && currentTheta <= TARGET_ANGLE + TARGET_HYSTERESIS) {
            if (best != current && (bestTheta + TARGET_HYSTERESIS < currentTheta
                    || bestTheta == 0.0 && currentTheta == 0.0)) {
                return best;
            }
            return current;
        }
        return best >= 0 && bestTheta <= TARGET_ANGLE ? best : -1;
    }
}
