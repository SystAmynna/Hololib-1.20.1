package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

import java.util.List;

/**
 * Echelle du mode systeme du Configurator ({@code docs/space/04-mathematiques.md} par. 7), partagee par le module
 * (placement et lecture de la poignee d'orbite, deux cotes) et le renderer du cube : memes calculs, memes nombres.
 * <b>Tout est a l'echelle</b> (D13, D17, T25) : plus de loupe des lunes, seules les tailles des corps sont exagerees.
 * <ul>
 *     <li><b>vue du systeme entier</b> : echelle lineaire {@code q = p / H_sys} (unites du cube, demi-arete
 *     {@link SpaceMapCube#HALF_SIZE}), avec {@code H_sys = nice(1,1 x portee max)} en UA ; la portee d'un corps est la
 *     somme des apoapses de sa chaine de parents (une planete d'une etoile compagne compte l'orbite de la compagne).
 *     Les lunes sont a leur vraie distance : dans Sol, la Lune est collee a la Terre (attendu, D13) ;</li>
 *     <li><b>zoom sur un corps</b> (D17) : la vue est centree sur le corps {@code c} au temps affiche,
 *     {@code q = (p - p_c) / H_zoom}, avec {@code H_zoom = nice(1,1 x max(portee de ses descendants mesuree depuis
 *     lui, ZOOM_MIN_RADII x R_c))} : ses lunes (et leurs lunes) tiennent dans le cube, a l'echelle ;</li>
 *     <li>rayon affiche d'un corps : {@code clamp(0,015 + 0,01 . log10(R_km / 1000), 0,012, 0,08)} unites du cube, ou
 *     son rayon reel a l'echelle de la vue s'il est plus grand (zoom serre : Mars plus grosse que la formule).</li>
 * </ul>
 * Sans allocation.
 */
public final class SpaceSystemScale
{
    /** Marge de l'echelle : {@code H = nice(MARGIN x portee)}. */
    public static final double MARGIN = 1.1;
    /**
     * Portee minimale d'un zoom, en rayons du corps zoome : un corps sans lune (ou aux lunes tres proches) reste
     * entoure de son voisinage immediat.
     */
    public static final double ZOOM_MIN_RADII = 5.0;
    /** Bornes du rayon affiche (formule exageree), en unites du cube. */
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

    /**
     * Demi-etendue d'un zoom sur le corps d'indice {@code center}, en UA : {@code nice(1,1 x max(portee, 5 R))}, ou la
     * portee est la plus grande somme d'apoapses d'un descendant jusqu'au corps zoome (non compris).
     */
    public static double zoomHalfExtent(SpaceSystem system, int center)
    {
        List<SpaceBody> bodies = system.bodies();
        double max = ZOOM_MIN_RADII * bodies.get(center).radius() / SpaceUnits.AU_KM;
        for (int i = center + 1; i < bodies.size(); i++) {
            double reach = 0.0;
            int index = i;
            while (index > center) {
                SpaceBody body = bodies.get(index);
                if (body.orbit() != null) reach += body.orbit().apoapsis();
                index = system.parentIndex(index);
            }
            // Descendant seulement si la chaine des parents passe par le corps zoome (parents avant enfants).
            if (index == center) max = Math.max(max, reach);
        }
        return SpaceConfiguratorModule.nice(MARGIN * max);
    }

    /** Demi-etendue de la vue, en UA : {@link #halfExtent} sans zoom ({@code center < 0}), sinon {@link #zoomHalfExtent}. */
    public static double viewHalf(SpaceSystem system, int center)
    {
        return center < 0 ? halfExtent(system) : zoomHalfExtent(system, center);
    }

    /** Unites du cube par km pour une vue de demi-etendue {@code half} (UA). */
    public static double unit(double half)
    {
        return SpaceMapCube.HALF_SIZE / (SpaceUnits.AU_KM * half);
    }

    /**
     * Vrai si le corps d'indice {@code index} est dans le voisinage d'un zoom sur {@code center} : lui-meme ou l'un de
     * ses descendants ; toujours vrai sans zoom ({@code center < 0}). Sans allocation.
     */
    public static boolean inZoom(SpaceSystem system, int center, int index)
    {
        if (center < 0) return true;
        while (index > center) index = system.parentIndex(index);
        return index == center;
    }

    /**
     * Vrai si l'orbite du corps d'indice {@code index} est dessinee a l'echelle de la vue : corps a orbite dont le
     * parent est dans le voisinage du zoom (toujours sans zoom). L'orbite du corps zoome lui-meme est bien plus grande
     * que la vue : seul un arc local en est dessine (renderer), et elle ne s'edite pas dans le zoom.
     */
    public static boolean orbitInView(SpaceSystem system, int center, int index)
    {
        int parent = system.parentIndex(index);
        return parent >= 0 && inZoom(system, center, parent);
    }

    /**
     * Rayon affiche d'un corps de rayon {@code radiusKm} dans une vue de demi-etendue {@code half} (UA), en unites du
     * cube : la formule exageree, ou le rayon reel a l'echelle s'il est plus grand.
     */
    public static double displayRadius(double radiusKm, double half)
    {
        double r = 0.015 + 0.01 * Math.log10(Math.max(1e-9, radiusKm) / 1000.0);
        double exaggerated = Mth.clamp(r, MIN_DISPLAY_RADIUS, MAX_DISPLAY_RADIUS) * SpaceMapCube.HALF_SIZE;
        return Math.max(exaggerated, radiusKm * unit(half));
    }

    /**
     * Positions affichees de tous les corps au temps {@code tDays}, en unites du cube : {@code dest[i]} pour
     * {@code system.bodies().get(i)}. Positions de Kepler absolues ({@link SpaceOrbits#positions}, km), moins celle du
     * corps zoome ({@code center >= 0}), fois {@link #unit}. Sans allocation.
     *
     * @param dest au moins {@code system.bodies().size()} vecteurs ({@code SpaceOrbits.newPositionArray()})
     */
    public static void positions(SpaceSystem system, double tDays, double half, int center, Vector3d[] dest)
    {
        SpaceOrbits.positions(system, tDays, dest);
        int count = system.bodies().size();
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        if (center >= 0 && center < count) {
            cx = dest[center].x;
            cy = dest[center].y;
            cz = dest[center].z;
        }
        double k = unit(half);
        for (int i = 0; i < count; i++) {
            Vector3d p = dest[i];
            p.set((p.x - cx) * k, (p.y - cy) * k, (p.z - cz) * k);
        }
    }
}
