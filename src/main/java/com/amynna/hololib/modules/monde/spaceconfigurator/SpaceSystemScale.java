package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

import java.util.List;

/**
 * Echelle du mode systeme du Configurator ({@code docs/space/04-mathematiques.md} par. 7), partagee par le module
 * (placement et lecture de la poignee d'orbite, deux cotes) et le renderer du cube : memes calculs, memes nombres.
 * <ul>
 *     <li>echelle lineaire {@code q = p / H_sys} (unites du cube, demi-arete {@link SpaceMapCube#HALF_SIZE}), avec
 *     {@code H_sys = nice(1,1 x portee max)} en UA ; la portee d'un corps est la somme des apoapses de sa chaine de
 *     parents (une planete d'une etoile compagne compte l'orbite de la compagne) ;</li>
 *     <li><b>loupe des lunes</b> : les distances lune -> planete sont multipliees par
 *     {@code m = max(1, arrondi_1-2-5(0,06 . H_sys / a_min_lune))}, arrondi a la valeur 1-2-5 la plus proche en
 *     echelle logarithmique (ecart au doc 04) : sans cela, changer l'orbite de la plus petite lune changerait
 *     {@code m} dans la meme proportion et son orbite affichee ne bougerait pas. La plus petite lune est ainsi
 *     affichee entre {@code 0,038} et {@code 0,095 H_sys} de sa planete ;</li>
 *     <li>rayon affiche d'un corps : {@code clamp(0,015 + 0,01 . log10(R_km / 1000), 0,012, 0,08)} unites du cube.</li>
 * </ul>
 * Sans allocation (sauf mention).
 */
public final class SpaceSystemScale
{
    /** Marge de l'echelle : {@code H_sys = nice(MARGIN x portee max)}. */
    public static final double MARGIN = 1.1;
    /** Distance affichee minimale d'une lune a sa planete, en fraction de {@code H_sys}. */
    public static final double MOON_SPACING = 0.06;
    /** Bornes du rayon affiche, en unites du cube. */
    public static final double MIN_DISPLAY_RADIUS = 0.012;
    public static final double MAX_DISPLAY_RADIUS = 0.08;

    private SpaceSystemScale() {}

    /**
     * Demi-etendue {@code H_sys} en UA : {@code nice(1,1 x portee max)} ; {@code 1} UA pour un systeme sans orbite.
     */
    public static double halfExtent(SpaceSystem system)
    {
        List<SpaceBody> bodies = system.bodies();
        double max = 0.0;
        for (int i = 0; i < bodies.size(); i++) {
            double reach = 0.0;
            for (int index = i; index >= 0; index = system.parentIndex(index)) {
                SpaceBody body = bodies.get(index);
                if (body.orbit() != null) reach += body.orbit().apoapsis();
            }
            max = Math.max(max, reach);
        }
        return SpaceConfiguratorModule.nice(MARGIN * max);
    }

    /** Facteur de la loupe des lunes {@code m >= 1} (valeur 1-2-5 la plus proche) ; {@code 1} sans lune. */
    public static double moonMagnifier(SpaceSystem system, double half)
    {
        double min = Double.POSITIVE_INFINITY;
        for (SpaceBody body : system.bodies()) {
            if (body.kind() == SpaceBodyKind.MOON && body.orbit() != null) {
                min = Math.min(min, body.orbit().semiMajorAxis());
            }
        }
        if (!(min < Double.POSITIVE_INFINITY) || !(min > 0.0)) return 1.0;
        double raw = MOON_SPACING * half / min;
        return raw <= 1.0 ? 1.0 : Math.max(1.0, nearestStep(raw));
    }

    /** Valeur {@code 1, 2 ou 5 x 10^k} la plus proche de {@code value > 0} en echelle logarithmique. */
    static double nearestStep(double value)
    {
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(value)));
        double n = value / magnitude;
        // Milieux geometriques : sqrt(2), sqrt(10), sqrt(50).
        double step = n < Math.sqrt(2.0) ? 1.0 : n < Math.sqrt(10.0) ? 2.0 : n < Math.sqrt(50.0) ? 5.0 : 10.0;
        return step * magnitude;
    }

    /** Facteur des distances au parent d'un corps : la loupe pour une lune, {@code 1} sinon. */
    public static double factor(SpaceBody body, double magnifier)
    {
        return body.kind() == SpaceBodyKind.MOON ? magnifier : 1.0;
    }

    /** Rayon affiche d'un corps de rayon {@code radiusKm}, en unites du cube. */
    public static double displayRadius(double radiusKm)
    {
        double r = 0.015 + 0.01 * Math.log10(Math.max(1e-9, radiusKm) / 1000.0);
        return Mth.clamp(r, MIN_DISPLAY_RADIUS, MAX_DISPLAY_RADIUS) * SpaceMapCube.HALF_SIZE;
    }

    /**
     * Positions affichees de tous les corps au temps {@code tDays}, en unites du cube : {@code dest[i]} pour
     * {@code system.bodies().get(i)}. Corps sans parent au centre ; sinon position affichee du parent plus la position
     * relative de Kepler (km) multipliee par {@code facteur / (UA_KM . H_sys)}. Sans allocation.
     *
     * @param dest au moins {@code system.bodies().size()} vecteurs ({@code SpaceOrbits.newPositionArray()})
     * @param tmp  vecteur de travail
     */
    public static void positions(SpaceSystem system, double tDays, double half, double magnifier, Vector3d[] dest,
                                 Vector3d tmp)
    {
        List<SpaceBody> bodies = system.bodies();
        double unit = SpaceMapCube.HALF_SIZE / (SpaceUnits.AU_KM * half);
        for (int i = 0; i < bodies.size(); i++) {
            SpaceBody body = bodies.get(i);
            Vector3d p = dest[i];
            int parent = system.parentIndex(i);
            if (parent < 0 || body.orbit() == null) {
                p.set(0.0, 0.0, 0.0);
                continue;
            }
            SpaceOrbits.localPosition(body.orbit(), tDays, tmp);
            double k = factor(body, magnifier) * unit;
            Vector3d q = dest[parent];
            p.set(q.x + tmp.x * k, q.y + tmp.y * k, q.z + tmp.z * k);
        }
    }
}
