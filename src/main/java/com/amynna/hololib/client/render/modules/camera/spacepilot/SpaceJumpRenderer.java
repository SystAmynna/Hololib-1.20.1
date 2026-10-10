package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceWarpEffect;
import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Saut interstellaire cote pilote (T22 ; {@code docs/space/04-mathematiques.md} par. 8 et 9, C41, C44) : reperage de
 * l'etoile cible et <b>roue de charge</b>, pour le <b>pilote local</b> seulement (pad du Space Controller en capture,
 * {@link SpacePilotPad#localPilot()}, et module Space Pilot dans sa memoire active, D11). Un autre joueur ne voit
 * jamais rien : le renderer ne lit que le joueur local.
 * <ul>
 *     <li><b>charge</b> ({@code jumpAt(partialTick)} en phase {@code CHARGE}) : double anneau en couleur de selection
 *     autour de l'etoile cible et roue autour d'elle, remplie depuis le haut dans le sens horaire selon
 *     {@code jumpChargeProgress(partialTick)} (interpolee) ; direction calculee depuis les positions figees du saut
 *     (celles de l'alignement : la roue arrive exactement sur l'axe avant) ;</li>
 *     <li><b>touche tenue avant la charge</b> (cible choisie par le pad, {@link SpacePilotPad#jumpTarget()}, pas encore
 *     de charge affichee) : double anneau de selection autour de l'etoile cible ;</li>
 *     <li><b>aide a la visee</b> (pilotage sans la touche du saut, ni saut en cours) : double anneau fin et pale autour
 *     de l'etoile qui serait choisie a l'appui ({@link SpaceJump#pickTarget}, meme regle que le pad, regard de la
 *     camera) ; coupee par l'interrupteur general de Space Pilot ({@code enabled}), comme la surcouche ;</li>
 *     <li>rien pendant le voyage ni pendant le fondu de sortie de la distorsion ({@code SpaceWarpEffect}, T23).</li>
 * </ul>
 * Reperage et roue ne dependent pas de {@code enabled} (C44 : necessaires au pilotage).
 * <p>
 * <b>Famille vue</b> ({@link ViewRenderer}, projection {@code WORLD} : meme FOV et meme bobbing que le ciel) plutot que
 * ciel : le reperage et la roue passent devant le terrain et la coque, comme le joystick (la cible finit sur l'axe
 * avant, au centre du cercle avant). Directions calculees comme le ciel ({@code pose(partialTick)},
 * {@link SpaceSkyFrame#galacticDirection}) dans un repere oriente comme le monde
 * ({@link ViewRenderContext#applyCameraRotation}), posees sur une sphere de {@value #RADIUS} bloc autour de l'oeil
 * (devant les cercles du joystick, a 2 blocs). Tailles constantes a l'ecran (pixels de reference, comme la
 * surcouche : un pixel jusqu'a 1 080 pixels de haut, proportionnel au-dela). {@code RenderType} de la surcouche, sans
 * ecriture de profondeur ; aucun etat GL modifie a la main, aucune allocation par frame. Fil de rendu.
 */
public final class SpaceJumpRenderer implements ViewRenderer
{
    /** Rayon de la sphere de dessin, en blocs : devant les cercles du joystick (2 blocs). */
    static final float RADIUS = 1.5f;
    /** Hauteur d'ecran jusqu'a laquelle un pixel de reference vaut un pixel (au-dela : proportionnel). */
    private static final double REFERENCE_HEIGHT = 1080.0;
    /** Echelle verticale de la projection a defaut ({@code 1 / tan(35 deg)}, FOV 70). */
    private static final double DEFAULT_PROJECTION_SCALE = 1.0 / Math.tan(Math.toRadians(35.0));

    /** Couleur de selection des modules Space (RGB), comme la selection du Configurator. */
    private static final int SELECTION = 0xFFE040;

    /**
     * Double anneau de la cible : rayons interieur et exterieur (pixels de reference), memes que celui des etoiles de
     * fond de la surcouche (il le recouvre), epaisseur ; rayon interieur minimal (radians) hors du carre de l'etoile.
     */
    private static final double TARGET_INNER_PX = 6.0;
    private static final double TARGET_OUTER_PX = 9.0;
    private static final double TARGET_MIN_ANGLE = Math.toRadians(0.3);
    private static final double TARGET_THICKNESS_PX = 1.5;
    private static final float TARGET_ALPHA = 0.95f;
    /** Aide a la visee : meme double anneau, plus fin et pale. */
    private static final double AID_THICKNESS_PX = 1.0;
    private static final float AID_ALPHA = 0.4f;
    private static final int TARGET_SEGMENTS = 24;

    /**
     * Roue de charge : rayon (pixels de reference, au moins {@link #WHEEL_GAP_PX} au-dela du double anneau),
     * epaisseur ; partie remplie et reste du tour.
     */
    private static final double WHEEL_PX = 20.0;
    private static final double WHEEL_GAP_PX = 5.0;
    private static final double WHEEL_THICKNESS_PX = 3.0;
    private static final float WHEEL_ALPHA = 0.95f;
    private static final float WHEEL_TRACK_ALPHA = 0.25f;
    private static final int WHEEL_SEGMENTS = 64;

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final float RED = (SELECTION >> 16 & 0xFF) / 255f;
    private static final float GREEN = (SELECTION >> 8 & 0xFF) / 255f;
    private static final float BLUE = (SELECTION & 0xFF) / 255f;

    // ---------------------------------------------------------------- etat de la frame (reutilise)

    /** Trouve par {@link #shouldRender}, utilise par {@link #render} de la meme frame. */
    private SpaceEnvironmentModule environment;
    private boolean aid;

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    private final Vector3f direction = new Vector3f();
    private final Vector3f look = new Vector3f();
    /** Base de l'anneau en cours : haut de l'ecran ({@code bv}) et droite ({@code bu = w x bv}), perpendiculaires. */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    /** Tangente d'un pixel de reference au centre de l'ecran, pour la frame. */
    private double pixelTan;

    /** Projection du monde : l'anneau reste exactement autour de l'etoile du ciel (FOV effectif, longue-vue). */
    @Override
    public Projection projection() { return Projection.WORLD; }

    /**
     * Pilote local en capture avec un module Space Pilot, Environment synchronise avec un systeme courant, pas de
     * voyage affiche ni de distorsion en fondu ({@link SpaceWarpEffect#strength}, T23).
     */
    @Override
    public boolean shouldRender(ViewRenderContext ctx)
    {
        environment = null;
        if (SpacePilotPad.localPilot() == null) return false;
        SpacePilotModule module = SpacePilotClient.module();
        if (module == null) return false;
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced() || env.currentSystem() == null) return false;
        if (env.jumpAt(ctx.partialTick()).phase() == SpaceJump.Phase.TRAVEL) return false;
        // Fondu de sortie de la distorsion (T23) : rien tant que le ciel n'est pas revenu.
        if (SpaceWarpEffect.strength(env, ctx.partialTick()) > 0f) return false;
        environment = env;
        aid = module.enabled();
        return true;
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        environment = null;
        SpacePilotPad pad = SpacePilotPad.localPilot();
        if (env == null || pad == null) return;
        SpaceSystem current = env.currentSystem();
        if (current == null) return;
        float partialTick = ctx.partialTick();
        Camera camera = ctx.camera();
        frame.set(env.pose(partialTick, pose));
        screen(ctx);
        look.set(camera.getLookVector());
        ctx.applyCameraRotation(stack);
        Matrix4f m = stack.last().pose();
        VertexConsumer vc = buffers.getBuffer(SpacePilotSkyRenderer.OverlayType.OVERLAY_QUADS);

        SpaceJump jump = env.jumpAt(partialTick);
        if (jump.phase() == SpaceJump.Phase.CHARGE) {
            Vector3dc from = jump.origin();
            Vector3dc to = jump.destination();
            if (frame.galacticDirection(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), direction) > 0.0
                    && visible(direction)) {
                basis(direction, camera);
                double outer = drawTarget(vc, m, direction, TARGET_THICKNESS_PX, TARGET_ALPHA);
                drawWheel(vc, m, direction, outer, env.jumpChargeProgress(partialTick));
            }
            return;
        }
        int target = pad.jumpTarget();
        float thickness;
        float alpha;
        if (target > 0) {
            thickness = (float) TARGET_THICKNESS_PX;
            alpha = TARGET_ALPHA;
        } else {
            if (!aid || pad.jumpHeld() || pad.jumpLocked()) return;
            target = SpaceJump.pickTarget(frame, current, env.backgroundStars(), look.x, look.y, look.z, direction);
            thickness = (float) AID_THICKNESS_PX;
            alpha = AID_ALPHA;
        }
        BackgroundStar star = target > 0 ? env.backgroundStar(target) : null;
        if (star == null || !(frame.galacticDirection(current, star, direction) > 0.0) || !visible(direction)) return;
        basis(direction, camera);
        drawTarget(vc, m, direction, thickness, alpha);
    }

    // ---------------------------------------------------------------- frame

    /** Echelle de l'ecran de la frame : tangente d'un pixel de reference (projection de ce renderer, fenetre). */
    private void screen(ViewRenderContext ctx)
    {
        double m11 = Math.abs(ctx.projection().m11());
        if (!Double.isFinite(m11) || !(m11 > 0.0)) m11 = DEFAULT_PROJECTION_SCALE;
        double height = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        pixelTan = 2.0 * Math.max(1.0, height / REFERENCE_HEIGHT) / (m11 * height);
    }

    /** Angle d'un nombre de pixels de reference compte depuis le centre de l'ecran. */
    private double pixels(double px) { return Math.atan(px * pixelTan); }

    /** Faux si la direction est a plus de 90 degres du regard (hors de tout champ de vision). */
    private boolean visible(Vector3f w) { return w.dot(look) > 0f; }

    // ---------------------------------------------------------------- dessin

    /**
     * Double anneau en couleur de selection autour de {@code w} (base {@link #basis} faite).
     *
     * @return le rayon angulaire de l'anneau exterieur, bord compris (radians)
     */
    private double drawTarget(VertexConsumer vc, Matrix4f m, Vector3f w, double thicknessPx, float alpha)
    {
        double inner = Math.max(TARGET_MIN_ANGLE, pixels(TARGET_INNER_PX));
        double outer = inner + pixels(TARGET_OUTER_PX - TARGET_INNER_PX);
        double half = pixels(0.5 * thicknessPx);
        arc(vc, m, w, inner, half, 0.0, 1.0, TARGET_SEGMENTS, alpha);
        arc(vc, m, w, outer, half, 0.0, 1.0, TARGET_SEGMENTS, alpha);
        return outer + half;
    }

    /**
     * Roue de charge (par. 9) : anneau autour de {@code w}, au-dela du double anneau ({@code inside}, radians), rempli
     * depuis le haut de l'ecran dans le sens horaire jusqu'a {@code progress} ({@code [0, 1]}), reste du tour pale.
     */
    private void drawWheel(VertexConsumer vc, Matrix4f m, Vector3f w, double inside, double progress)
    {
        double p = Math.max(0.0, Math.min(1.0, progress));
        double half = pixels(0.5 * WHEEL_THICKNESS_PX);
        double radius = Math.max(pixels(WHEEL_PX), inside + pixels(WHEEL_GAP_PX) + half);
        arc(vc, m, w, radius, half, 0.0, p, WHEEL_SEGMENTS, WHEEL_ALPHA);
        arc(vc, m, w, radius, half, p, 1.0, WHEEL_SEGMENTS, WHEEL_TRACK_ALPHA);
    }

    /**
     * Arc d'anneau de rayon angulaire {@code angle} et de demi-epaisseur {@code half} (radians) autour de {@code w},
     * de la fraction de tour {@code from} a {@code to} ({@code [0, 1]}, 0 = haut de l'ecran, sens horaire), sur la
     * sphere de dessin ; {@code segments} quads pour un tour entier (au moins un).
     */
    private void arc(VertexConsumer vc, Matrix4f m, Vector3f w, double angle, double half, double from, double to,
                     int segments, float alpha)
    {
        if (!(to > from)) return;
        double innerAngle = Math.max(0.0, angle - half);
        double outerAngle = angle + half;
        float ic = (float) Math.cos(innerAngle) * RADIUS;
        float is = (float) Math.sin(innerAngle) * RADIUS;
        float oc = (float) Math.cos(outerAngle) * RADIUS;
        float os = (float) Math.sin(outerAngle) * RADIUS;
        int n = Math.max(1, (int) Math.ceil((to - from) * segments));
        double step = 2.0 * Math.PI * (to - from) / n;
        double theta = 2.0 * Math.PI * from;
        float c0 = (float) Math.cos(theta);
        float s0 = (float) Math.sin(theta);
        for (int i = 0; i < n; i++) {
            theta += step;
            float c1 = (float) Math.cos(theta);
            float s1 = (float) Math.sin(theta);
            ringVertex(vc, m, w, ic, is, c0, s0, alpha);
            ringVertex(vc, m, w, oc, os, c0, s0, alpha);
            ringVertex(vc, m, w, oc, os, c1, s1, alpha);
            ringVertex(vc, m, w, ic, is, c1, s1, alpha);
            c0 = c1;
            s0 = s1;
        }
    }

    /** Sommet {@code c w + s (ct bv + st bu)} ({@code c}, {@code s} deja multiplies par le rayon). */
    private void ringVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float c, float s, float ct, float st, float a)
    {
        float v = ct * s;
        float u = st * s;
        float x = c * w.x + v * bv.x + u * bu.x;
        float y = c * w.y + v * bv.y + u * bu.y;
        float z = c * w.z + v * bv.z + u * bu.z;
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(RED, GREEN, BLUE, a).uv2(LIGHT).endVertex();
    }

    /**
     * Base perpendiculaire a {@code w} alignee sur l'ecran : {@code bv} = haut de la camera projete,
     * {@code bu = w x bv} (droite de l'ecran) ; si {@code w} est presque le haut de la camera, a partir de sa droite.
     */
    private void basis(Vector3f w, Camera camera)
    {
        bv.set(camera.getUpVector());
        bv.fma(-bv.dot(w), w);
        float length = bv.length();
        if (length > 1.0e-3f) {
            bv.mul(1f / length);
            w.cross(bv, bu);
            return;
        }
        bu.set(camera.getLeftVector()).negate();
        bu.fma(-bu.dot(w), w).normalize();
        bu.cross(w, bv);
    }
}
