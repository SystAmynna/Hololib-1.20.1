package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient;
import com.amynna.hololib.client.render.StableProjection;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceOverlay;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpTimeline;
import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Lien vers le referentiel de Space Pilot (T33, livrable 3 bis ; {@code docs/space/04-mathematiques.md} par. 9, C75,
 * C98) : arc pointille du <b>bord exterieur du cercle avant</b> du joystick jusqu'au <b>bord exterieur de l'anneau du
 * corps de reference</b>, sans ecart, et cet anneau lui-meme (Space Pilot le dessine aussi, pour qu'il existe sans
 * Space Knowledge ; meme rayon et meme couleur que l'anneau de la surcouche : {@link SpaceOverlay#bodyRing},
 * {@link SpaceOverlay#kindColor}, les deux se confondent).
 * <p>
 * <b>Affichage</b> : seulement pour le <b>pilote local</b> ({@link SpacePilotPad#localPilot()}, meme condition que le
 * joystick et {@link SpaceJumpRenderer}), avec le module Space Pilot dans sa memoire active et le reglage « Lien
 * referentiel » ({@link SpacePilotModule#refLink()}) ; rien pendant le voyage d'un saut ni pendant le reste de la
 * distorsion ({@link SpaceWarpTimeline#active}) ; rien hors pilotage. Ne depend pas de Space Knowledge. Le lien
 * (pas l'anneau) n'est dessine qu'en premiere personne : en F5, le cercle avant est dessine autour de l'oeil du
 * joueur, pas de la camera.
 * <p>
 * <b>Famille vue</b> ({@link ViewRenderer}, projection {@code WORLD} : meme FOV effectif que le ciel, donc l'anneau
 * reste exactement sur celui du ciel) : lien et anneau passent devant le terrain, comme le joystick. Tout est calcule
 * dans l'<b>espace vue</b> (directions du monde tournees par {@link ViewRenderContext#cameraRotation()}, la rotation
 * qu'utilise le pont des objets {@code Local}) et pose sur une sphere de {@value #RADIUS} bloc autour de l'oeil, la
 * pile restant a l'identite.
 * <p>
 * <b>Conversion {@code STABLE} vers {@code WORLD}</b> : le cercle avant est un objet {@code Local} dessine par le pont
 * vue en projection {@code STABLE} (FOV des options, sans modificateur). Un point de direction vue {@code v} y tombe a
 * l'ecran la ou tombe, en projection {@code WORLD}, la direction {@code M v} avec
 * {@code M = diag(k, k, 1)}, {@code k = m11_stable / m11_world} (tangentes {@code x / z} et {@code y / z}
 * multipliees par {@code k} ; meme rapport d'aspect). {@code M} est lineaire : elle envoie un grand cercle sur un grand
 * cercle. Le centre du cercle avant (axe avant {@code F}) est vu en {@code F' = M F} ; l'arc {@code F' -> R} (corps de
 * reference, deja en projection {@code WORLD}) est l'image de l'arc {@code F -> M^-1 R}, qui coupe le cone du cercle
 * avant (rayon {@code rho = atan((RADIUS + THICKNESS) / DISTANCE)}) en
 * {@code c = cos(rho) F + sin(rho) T} ({@code T} tangente en {@code F} vers {@code M^-1 R}) : le depart est
 * {@code M c}, exactement sur le bord du cercle tel qu'il est vu, quel que soit l'ecart des deux FOV (vol creatif,
 * longue-vue, Rapidite, {@code fovEffectScale}). Pas de bobbing pendant le pilotage (joueur immobile).
 * <p>
 * Regles du lien inchangees (par. 9) : arc de grand cercle le plus court, tirets de {@value #LINK_DASH_PX} px et
 * espaces de {@value #LINK_SPACE_PX} px de reference, un tiret partant du bord du cercle avant (motif fixe par rapport
 * a l'axe avant : il ne defile pas quand le referentiel bouge), epaisseur {@value #LINK_THICKNESS_PX} px, opacite
 * {@value #LINK_ALPHA}, couleur du referentiel ; rien si l'axe avant est dans l'anneau ou si les deux directions sont
 * quasi opposees. Le dernier tiret est coupe au bord de l'anneau : le lien le touche quand ce bord tombe dans un tiret.
 * <p>
 * {@code RenderType} des surcouches ({@link SpaceOverlay#quads()}, sans ecriture de profondeur) ; aucun etat GL modifie
 * a la main, aucune allocation par frame. Fil de rendu.
 */
public final class SpaceReferenceLinkRenderer implements ViewRenderer
{
    /** Rayon de la sphere de dessin, en blocs : devant les cercles du joystick (2 blocs), comme le saut. */
    static final float RADIUS = 1.5f;

    /** Tangente du rayon angulaire du bord exterieur du cercle avant (vu de l'oeil, projection {@code STABLE}). */
    private static final double FORWARD_EDGE_TAN =
            ((double) SpaceJoystickForward.RADIUS + SpaceJoystickRenderer.THICKNESS) / SpaceJoystickForward.DISTANCE;
    private static final double FORWARD_EDGE_ANGLE = Math.atan(FORWARD_EDGE_TAN);

    /** Tiret et espace du pointille, epaisseur : pixels de reference (constants a l'ecran). */
    private static final double LINK_DASH_PX = 6.0;
    private static final double LINK_SPACE_PX = 4.0;
    private static final double LINK_THICKNESS_PX = 1.5;
    /** Plafond du nombre de tirets (longue-vue sur un lien de 180 degres) : au-dela, les tirets s'allongent. */
    private static final int MAX_LINK_DASHES = 512;
    /** Opacite du lien (couleur du corps de reference). */
    private static final float LINK_ALPHA = 0.8f;

    /** Corde la plus longue d'un arc dessine (au-dela : subdivise). */
    private static final double MAX_CHORD_ANGLE = Math.toRadians(3.0);
    private static final float COS_MAX_CHORD = (float) Math.cos(MAX_CHORD_ANGLE);
    private static final int MAX_SUBDIVISIONS = 64;
    /** Directions quasi opposees (moins de 1,8 degre) : grand cercle indefini, rien n'est dessine. */
    private static final float COS_THROUGH_EYE = -0.9995f;

    private static final int RING_SEGMENTS = SpaceOverlay.BODY_RING_SEGMENTS;
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    static
    {
        for (int j = 0; j <= RING_SEGMENTS; j++) {
            double angle = 2.0 * Math.PI * j / RING_SEGMENTS;
            RING_COS[j] = (float) Math.cos(angle);
            RING_SIN[j] = (float) Math.sin(angle);
        }
        RING_COS[RING_SEGMENTS] = RING_COS[0];
        RING_SIN[RING_SEGMENTS] = RING_SIN[0];
    }

    // ---------------------------------------------------------------- etat de la frame (reutilise)

    /** Trouve par {@link #shouldRender}, utilise par {@link #render} de la meme frame. */
    private SpaceEnvironmentModule environment;

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    private final Vector3d forwardAxis = new Vector3d();
    /** Espace vue : corps de reference {@code R}, axe avant {@code F}, son image {@code F'}, preimage {@code M^-1 R}. */
    private final Vector3f target = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f forwardImage = new Vector3f();
    private final Vector3f preimage = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    private final Vector3f start = new Vector3f();
    /** Tangente du grand cercle {@code F' -> R} en {@code F'}, extremites d'un tiret, d'une corde. */
    private final Vector3f linkTangent = new Vector3f();
    private final Vector3f dashA = new Vector3f();
    private final Vector3f dashB = new Vector3f();
    private final Vector3f arcA = new Vector3f();
    private final Vector3f arcB = new Vector3f();
    private final Vector3f cross = new Vector3f();
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    /** Tangente d'un pixel de reference au centre de l'ecran (projection {@code WORLD}), pour la frame. */
    private double pixelTan;

    /** Projection du monde : l'anneau reste exactement sur celui du ciel (FOV effectif, longue-vue). */
    @Override
    public Projection projection() { return Projection.WORLD; }

    /**
     * Pilote local en capture avec un module Space Pilot dont le lien est active, Environment synchronise avec un
     * systeme courant, pas de voyage affiche ni de distorsion ({@link SpaceWarpTimeline#active}).
     */
    @Override
    public boolean shouldRender(ViewRenderContext ctx)
    {
        environment = null;
        if (SpacePilotPad.localPilot() == null) return false;
        SpacePilotModule module = SpacePilotClient.module();
        if (module == null || !module.refLink()) return false;
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced() || env.currentSystem() == null) return false;
        float partialTick = ctx.partialTick();
        if (env.jumpAt(partialTick).phase() == SpaceJump.Phase.TRAVEL) return false;
        if (SpaceWarpTimeline.active(env, partialTick)) return false;
        environment = env;
        return true;
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        environment = null;
        if (env == null) return;
        SpaceSystem system = env.currentSystem();
        if (system == null) return;
        int index = system.indexOf(env.referenceBodyId());
        if (index < 0) return;
        float partialTick = ctx.partialTick();
        frame.set(env.pose(partialTick, pose));
        int count = Math.min(env.bodyPositions(partialTick, positions), system.bodies().size());
        if (index >= count) return;
        double distance = frame.direction(positions[index], target);
        if (!(distance > 0.0)) return;
        Matrix4fc rotation = ctx.cameraRotation();
        rotation.transformDirection(target).normalize();

        double m11 = Math.abs(ctx.projection().m11());
        pixelTan = SpaceOverlay.pixelTan(m11);
        SpaceBody body = system.bodies().get(index);
        double ring = SpaceOverlay.bodyRing(body, distance, SpaceOverlay.bodyRingMin(pixelTan));
        double half = SpaceOverlay.bodyRingHalf(pixelTan);
        int color = SpaceOverlay.kindColor(body.kind());
        float r = SpaceOverlay.red(color);
        float g = SpaceOverlay.green(color);
        float b = SpaceOverlay.blue(color);

        Matrix4f m = stack.last().pose();
        VertexConsumer vc = buffers.getBuffer(SpaceOverlay.quads());
        drawRing(vc, m, ring, half, r, g, b, SpaceOverlay.BODY_RING_ALPHA);
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            drawLink(vc, m, env, rotation, m11, ring + half, r, g, b);
        }
    }

    // ---------------------------------------------------------------- lien

    /**
     * Lien pointille du bord du cercle avant (image {@code STABLE -> WORLD}, voir la doc de la classe) au bord exterieur
     * de l'anneau du referentiel ({@code ringEdge}, radians, depuis {@link #target}).
     */
    private void drawLink(VertexConsumer vc, Matrix4f m, SpaceEnvironmentModule env, Matrix4fc rotation, double m11World,
                          double ringEdge, float r, float g, float b)
    {
        if (!Double.isFinite(m11World) || !(m11World > 0.0)) return;
        double stableTan = Math.tan(Math.toRadians(StableProjection.configuredFov()) * 0.5);
        if (!(stableTan > 0.0) || !Double.isFinite(stableTan)) return;
        float k = (float) (1.0 / stableTan / m11World);
        if (!(k > 0f) || !Float.isFinite(k)) return;

        env.settings().forward(forwardAxis);
        forward.set((float) forwardAxis.x, (float) forwardAxis.y, (float) forwardAxis.z);
        rotation.transformDirection(forward).normalize();
        // Image du centre du cercle avant (F' = M F) et preimage du referentiel (M^-1 R).
        forwardImage.set(k * forward.x, k * forward.y, forward.z).normalize();
        preimage.set(target.x / k, target.y / k, target.z);
        // Depart : bord du cone du cercle avant sur l'arc F -> M^-1 R, ramene en projection WORLD.
        tangent.set(preimage).fma(-preimage.dot(forward), forward);
        float tangentLength = tangent.length();
        if (!(tangentLength > 1.0e-6f)) return;
        tangent.mul(1f / tangentLength);
        start.set(forward).mul((float) Math.cos(FORWARD_EDGE_ANGLE)).fma((float) Math.sin(FORWARD_EDGE_ANGLE), tangent);
        start.set(k * start.x, k * start.y, start.z).normalize();

        float dot = forwardImage.dot(target);
        if (dot < COS_THROUGH_EYE) return;
        linkTangent.set(target).fma(-dot, forwardImage);
        float length = linkTangent.length();
        if (!(length > 1.0e-6f)) return;
        linkTangent.mul(1f / length);
        double from = angleBetween(forwardImage, start);
        double end = angle(dot) - ringEdge;
        if (!(end > from)) return;

        double dash = pixels(LINK_DASH_PX);
        double period = dash + pixels(LINK_SPACE_PX);
        int dashes = (int) Math.ceil((end - from) / period);
        if (dashes > MAX_LINK_DASHES) {
            double stretch = (end - from) / (MAX_LINK_DASHES * period);
            dash *= stretch;
            period *= stretch;
            dashes = MAX_LINK_DASHES;
        }
        float halfWidth = (float) (RADIUS * 0.5 * LINK_THICKNESS_PX * pixelTan);
        for (int i = 0; i < dashes; i++) {
            double a0 = from + i * period;
            double a1 = Math.min(end, a0 + dash);
            if (!(a1 > a0)) break;
            greatCircle(a0, dashA);
            greatCircle(a1, dashB);
            arc(vc, m, dashA, dashB, halfWidth, r, g, b, LINK_ALPHA);
        }
    }

    /** Point du grand cercle du lien a l'angle {@code angle} (radians) de {@code F'}, vers le referentiel. */
    private void greatCircle(double angle, Vector3f dest)
    {
        dest.set(forwardImage).mul((float) Math.cos(angle)).fma((float) Math.sin(angle), linkTangent);
    }

    /** Angle d'un nombre de pixels de reference compte depuis le centre de l'ecran. */
    private double pixels(double px) { return Math.atan(px * pixelTan); }

    /** Angle entre deux directions unitaires de produit scalaire {@code dot}. */
    private static double angle(float dot) { return Math.acos(Math.max(-1f, Math.min(1f, dot))); }

    /** Angle entre deux directions unitaires (precis aux petits angles). */
    private static double angleBetween(Vector3f a, Vector3f b)
    {
        double cx = (double) a.y * b.z - (double) a.z * b.y;
        double cy = (double) a.z * b.x - (double) a.x * b.z;
        double cz = (double) a.x * b.y - (double) a.y * b.x;
        double dot = (double) a.x * b.x + (double) a.y * b.y + (double) a.z * b.z;
        return Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), dot);
    }

    /**
     * Arc de grand cercle le plus court entre deux directions unitaires, decoupe en cordes d'au plus
     * {@link #MAX_CHORD_ANGLE} ; rien si elles sont quasi opposees.
     */
    private void arc(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                     float b, float a)
    {
        float dot = from.dot(to);
        if (dot >= COS_MAX_CHORD) {
            strip(vc, m, from, to, halfWidth, r, g, b, a);
            return;
        }
        if (dot < COS_THROUGH_EYE) return;
        double theta = angle(dot);
        double sinTheta = Math.sin(theta);
        int n = Math.min(MAX_SUBDIVISIONS, (int) Math.ceil(theta / MAX_CHORD_ANGLE));
        arcA.set(from);
        for (int i = 1; i <= n; i++) {
            if (i == n) {
                arcB.set(to);
            } else {
                double t = i / (double) n;
                float wa = (float) (Math.sin((1.0 - t) * theta) / sinTheta);
                float wb = (float) (Math.sin(t * theta) / sinTheta);
                arcB.set(from).mul(wa).fma(wb, to).normalize();
            }
            strip(vc, m, arcA, arcB, halfWidth, r, g, b, a);
            arcA.set(arcB);
        }
    }

    /**
     * Trait fin entre deux directions proches, sur la sphere de dessin : la corde se projette comme le segment de
     * l'espace (meme plan par l'oeil) et le quad, decale selon la normale de ce plan, est vu de face. Ignore si les
     * deux extremites sont derriere l'oeil.
     */
    private void strip(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                       float b, float a)
    {
        if (from.z >= 0f && to.z >= 0f) return;
        cross.set(from).cross(to);
        float length = cross.length();
        if (!(length > 1.0e-7f)) return;
        cross.mul(halfWidth / length);
        float k = RADIUS;
        vertex(vc, m, k * from.x - cross.x, k * from.y - cross.y, k * from.z - cross.z, r, g, b, a);
        vertex(vc, m, k * from.x + cross.x, k * from.y + cross.y, k * from.z + cross.z, r, g, b, a);
        vertex(vc, m, k * to.x + cross.x, k * to.y + cross.y, k * to.z + cross.z, r, g, b, a);
        vertex(vc, m, k * to.x - cross.x, k * to.y - cross.y, k * to.z - cross.z, r, g, b, a);
    }

    // ---------------------------------------------------------------- anneau du referentiel

    /**
     * Anneau du referentiel autour de {@link #target} : rayon angulaire {@code ring} (milieu du trait) et
     * demi-epaisseur {@code half} (radians), ceux de la surcouche de Space Knowledge.
     */
    private void drawRing(VertexConsumer vc, Matrix4f m, double ring, double half, float r, float g, float b, float a)
    {
        Vector3f w = target;
        // Anneau entierement derriere l'oeil : rien a dessiner.
        if (-w.z < Math.cos(Math.min(Math.PI, 0.5 * Math.PI + ring + half))) return;
        basis(w);
        double innerAngle = Math.max(0.0, ring - half);
        double outerAngle = ring + half;
        float ic = (float) Math.cos(innerAngle) * RADIUS;
        float is = (float) Math.sin(innerAngle) * RADIUS;
        float oc = (float) Math.cos(outerAngle) * RADIUS;
        float os = (float) Math.sin(outerAngle) * RADIUS;
        for (int j = 0; j < RING_SEGMENTS; j++) {
            ringVertex(vc, m, w, ic, is, RING_COS[j], RING_SIN[j], r, g, b, a);
            ringVertex(vc, m, w, oc, os, RING_COS[j], RING_SIN[j], r, g, b, a);
            ringVertex(vc, m, w, oc, os, RING_COS[j + 1], RING_SIN[j + 1], r, g, b, a);
            ringVertex(vc, m, w, ic, is, RING_COS[j + 1], RING_SIN[j + 1], r, g, b, a);
        }
    }

    /** Sommet {@code c w + s (ct bu + st bv)} ({@code c}, {@code s} deja multiplies par le rayon). */
    private void ringVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float c, float s, float ct, float st, float r,
                            float g, float b, float a)
    {
        float u = ct * s;
        float v = st * s;
        vertex(vc, m, c * w.x + u * bu.x + v * bv.x, c * w.y + u * bu.y + v * bv.y, c * w.z + u * bu.z + v * bv.z,
                r, g, b, a);
    }

    /**
     * Base perpendiculaire a {@code w} (espace vue) : {@code bv} = haut de l'ecran projete, {@code bu = w x bv} ; si
     * {@code w} est presque vertical, a partir de la droite de l'ecran.
     */
    private void basis(Vector3f w)
    {
        bv.set(0f, 1f, 0f).fma(-w.y, w);
        float length = bv.length();
        if (length > 1.0e-3f) {
            bv.mul(1f / length);
            w.cross(bv, bu);
            return;
        }
        bu.set(1f, 0f, 0f).fma(-w.x, w).normalize();
        bu.cross(w, bv);
    }

    /** Sommet transforme sans allocation ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur). */
    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }
}
