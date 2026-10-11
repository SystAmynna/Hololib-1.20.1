package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient;
import com.amynna.hololib.client.render.StableProjection;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceOverlay;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpTimeline;
import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
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
import java.util.Objects;

/**
 * Cible du vaisseau cote pilote (T43, D33 ; {@code docs/space/04-mathematiques.md} par. 9) : <b>anneau vert</b> autour
 * de l'astre cible et <b>fleche verte</b> sur le bord exterieur du cercle avant du joystick, tournee vers lui. La cible
 * est tenue par l'Environment ({@link SpaceEnvironmentModule#targetSystemId()}, {@link SpaceEnvironmentModule#targetBodyId()})
 * et posee depuis la Space Map ; elle ne change ni la cible de la vitesse de croisiere ni celle du saut.
 * <p>
 * <b>Affichage</b> : memes conditions que le saut ({@link SpaceJumpRenderer}) : <b>pilote local aux commandes</b>
 * ({@link SpacePilotPad#localPilot()}), module Space Pilot dans sa memoire active, Environment synchronise avec un
 * systeme courant et une cible ; rien pendant le voyage d'un saut ni pendant le reste de la distorsion
 * ({@link SpaceWarpTimeline#active}). Un autre joueur ne voit jamais rien (seul le joueur local est lu). Ne depend pas de
 * Space Knowledge ni du reglage « Lien referentiel ».
 * <ul>
 *     <li><b>cible dans le systeme courant</b> (corps, ou systeme entier : son etoile principale) : anneau au rayon de
 *     {@link SpaceOverlay#bodyRing} (celui de Space Knowledge et de l'anneau du referentiel), un peu plus epais
 *     ({@value #RING_THICKNESS_PX} px au lieu de {@value SpaceOverlay#BODY_RING_THICKNESS_PX}) : il les recouvre ;</li>
 *     <li><b>cible dans un autre systeme</b> (systeme ou un de ses corps) : anneau de {@value #STAR_RING_PX} px autour
 *     de l'<b>etoile de fond</b> de ce systeme (direction calculee comme {@link SpaceJumpRenderer}), au-dela du double
 *     anneau de l'aide a la visee du saut (6 / 9 px) qui reste visible dedans ; apres le saut vers ce systeme, l'anneau
 *     passe sur l'astre lui-meme (cas precedent) ;</li>
 *     <li><b>fleche</b> (premiere personne seulement, comme le lien du referentiel : en F5 le cercle avant est dessine
 *     autour de l'oeil du joueur) : triangle plein, base posee a {@value #ARROW_GAP_PX} px au-dela du bord exterieur du
 *     cercle avant, pointe {@value #ARROW_LENGTH_PX} px plus loin, largeur {@value #ARROW_WIDTH_PX} px, dans le plan de
 *     l'ecran (celui du cercle, objet {@code Local} vu de face), tournee selon l'angle de la cible autour de l'axe avant
 *     du monde (valable sur le cote et derriere) ; masquee quand la cible est dans le cercle avant.</li>
 * </ul>
 * <b>Conversion {@code STABLE -> WORLD}</b> de la fleche : celle du lien du referentiel
 * ({@link SpaceReferenceLinkRenderer}) ; le cercle avant est dessine en projection {@code STABLE} : un point de
 * direction vue {@code v} y tombe a l'ecran la ou tombe, en projection {@code WORLD}, {@code M v},
 * {@code M = diag(k, k, 1)}, {@code k = m11_stable / m11_world}. La base de la fleche est {@code M c},
 * {@code c = cos(rho) F + sin(rho) T} ({@code T} tangente en {@code F} vers {@code M^-1 R}) : le point ou le lien vers
 * la cible quitterait le cercle ; la fleche suit le grand cercle {@code F' -> R} ({@code F' = M F}) au-dela.
 * <p>
 * <b>Ordre de dessin</b> (deux instances, {@link Part}) : la fleche est enregistree <b>avant</b> le lien du referentiel
 * (ses tirets passent par-dessus quand la cible est le referentiel : la fleche ne le masque pas) et l'anneau
 * <b>apres</b> (il recouvre l'anneau du referentiel du meme corps). {@code RenderType} des surcouches
 * ({@link SpaceOverlay#quads()}, sans ecriture de profondeur, devant le terrain) ; dessin dans l'espace vue sur une
 * sphere de {@value SpaceReferenceLinkRenderer#RADIUS} bloc autour de l'oeil, pile a l'identite ; tailles constantes a
 * l'ecran (pixels de reference, {@link SpaceOverlay#pixelTan}) ; aucun etat GL modifie a la main, aucune allocation
 * par frame. Fil de rendu.
 */
public final class SpaceTargetRenderer implements ViewRenderer
{
    /** Partie dessinee par une instance (ordre d'enregistrement autour du lien du referentiel). */
    public enum Part
    {
        /** Fleche sur le cercle avant : enregistree avant {@link SpaceReferenceLinkRenderer}. */
        ARROW,
        /** Anneau autour de la cible : enregistre apres {@link SpaceReferenceLinkRenderer}. */
        RING
    }

    /** Rayon de la sphere de dessin, en blocs (celle du lien du referentiel et du saut). */
    private static final float RADIUS = SpaceReferenceLinkRenderer.RADIUS;

    /** Epaisseur de l'anneau vert (pixels de reference) : un peu plus que l'anneau d'un corps, qu'il recouvre. */
    private static final double RING_THICKNESS_PX = 2.0;
    /** Rayon de l'anneau autour d'une etoile de fond (pixels de reference) ; rayon minimal (radians). */
    private static final double STAR_RING_PX = 12.0;
    private static final double STAR_RING_MIN_ANGLE = Math.toRadians(0.5);
    private static final float RING_ALPHA = 0.95f;

    /** Fleche : ecart au bord exterieur du cercle avant, longueur et largeur de base (pixels de reference). */
    private static final double ARROW_GAP_PX = 1.0;
    private static final double ARROW_LENGTH_PX = 8.0;
    private static final double ARROW_WIDTH_PX = 9.0;
    private static final float ARROW_ALPHA = 0.95f;

    /** Rayon angulaire du bord exterieur du cercle avant (projection {@code STABLE}), comme le lien du referentiel. */
    private static final double FORWARD_EDGE_ANGLE =
            Math.atan(((double) SpaceJoystickForward.RADIUS + SpaceJoystickRenderer.THICKNESS)
                    / SpaceJoystickForward.DISTANCE);
    private static final float COS_FORWARD_EDGE = (float) Math.cos(FORWARD_EDGE_ANGLE);
    private static final float SIN_FORWARD_EDGE = (float) Math.sin(FORWARD_EDGE_ANGLE);

    private static final int RING_SEGMENTS = SpaceOverlay.BODY_RING_SEGMENTS;
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final float RED = SpaceOverlay.red(SpaceOverlay.TARGET_COLOR);
    private static final float GREEN = SpaceOverlay.green(SpaceOverlay.TARGET_COLOR);
    private static final float BLUE = SpaceOverlay.blue(SpaceOverlay.TARGET_COLOR);

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

    private final Part part;

    // ---------------------------------------------------------------- etat de la frame (reutilise)

    /** Trouve par {@link #shouldRender}, utilise par {@link #render} de la meme frame. */
    private SpaceEnvironmentModule environment;

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    private final Vector3d forwardAxis = new Vector3d();
    /** Espace vue : cible {@code R}, axe avant {@code F}, son image {@code F'}, preimage {@code M^-1 R}. */
    private final Vector3f target = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f forwardImage = new Vector3f();
    private final Vector3f preimage = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    /** Base de la fleche, direction vers l'exterieur et cote (plan tangent), sommets. */
    private final Vector3f base = new Vector3f();
    private final Vector3f outward = new Vector3f();
    private final Vector3f side = new Vector3f();
    private final Vector3f corner = new Vector3f();
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    /** Tangente d'un pixel de reference au centre de l'ecran (projection {@code WORLD}), pour la frame. */
    private double pixelTan;

    public SpaceTargetRenderer(Part part) { this.part = Objects.requireNonNull(part); }

    /** Projection du monde : l'anneau reste exactement autour de l'astre du ciel (FOV effectif, longue-vue). */
    @Override
    public Projection projection() { return Projection.WORLD; }

    /**
     * Pilote local en capture avec un module Space Pilot, Environment synchronise avec un systeme courant et une cible,
     * pas de voyage affiche ni de distorsion ; fleche : premiere personne seulement.
     */
    @Override
    public boolean shouldRender(ViewRenderContext ctx)
    {
        environment = null;
        if (SpacePilotPad.localPilot() == null || SpacePilotClient.module() == null) return false;
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced() || env.currentSystem() == null || !env.hasTarget()) return false;
        float partialTick = ctx.partialTick();
        if (env.jumpAt(partialTick).phase() == SpaceJump.Phase.TRAVEL) return false;
        if (SpaceWarpTimeline.active(env, partialTick)) return false;
        if (part == Part.ARROW && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return false;
        environment = env;
        return true;
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        environment = null;
        if (env == null) return;
        SpaceSystem current = env.currentSystem();
        if (current == null) return;
        float partialTick = ctx.partialTick();
        double m11 = Math.abs(ctx.projection().m11());
        pixelTan = SpaceOverlay.pixelTan(m11);
        frame.set(env.pose(partialTick, pose));
        double ring = targetDirection(env, current, partialTick);
        if (!(ring > 0.0)) return;
        Matrix4fc rotation = ctx.cameraRotation();
        rotation.transformDirection(target).normalize();

        Matrix4f m = stack.last().pose();
        VertexConsumer vc = buffers.getBuffer(SpaceOverlay.quads());
        if (part == Part.RING) {
            drawRing(vc, m, ring, Math.atan(0.5 * RING_THICKNESS_PX * pixelTan));
        } else {
            drawArrow(vc, m, env, rotation, m11);
        }
    }

    // ---------------------------------------------------------------- cible

    /**
     * Direction de la cible dans les axes du monde, ecrite dans {@link #target} : corps du systeme courant (systeme
     * entier : etoile principale) ou etoile de fond d'un autre systeme.
     *
     * @return le rayon angulaire de l'anneau (radians, milieu du trait), {@code 0} si la cible est introuvable ou
     * confondue avec le monde
     */
    private double targetDirection(SpaceEnvironmentModule env, SpaceSystem current, float partialTick)
    {
        int systemId = env.targetSystemId();
        if (systemId == current.id()) {
            int bodyId = env.targetBodyId();
            int index = bodyId == 0 ? 0 : current.indexOf(bodyId);
            if (index < 0) return 0.0;
            int count = Math.min(env.bodyPositions(partialTick, positions), current.bodies().size());
            if (index >= count) return 0.0;
            double distance = frame.direction(positions[index], target);
            if (!(distance > 0.0)) return 0.0;
            return SpaceOverlay.bodyRing(current.bodies().get(index), distance, SpaceOverlay.bodyRingMin(pixelTan));
        }
        BackgroundStar star = env.backgroundStar(systemId);
        if (star == null || !(frame.galacticDirection(current, star, target) > 0.0)) return 0.0;
        return Math.max(STAR_RING_MIN_ANGLE, Math.atan(STAR_RING_PX * pixelTan));
    }

    // ---------------------------------------------------------------- fleche

    /**
     * Fleche sur le bord exterieur du cercle avant, vers {@link #target} (espace vue) ; rien si la cible est dans le
     * cercle, quasi opposee a l'axe avant, ou si la base tombe derriere l'oeil.
     */
    private void drawArrow(VertexConsumer vc, Matrix4f m, SpaceEnvironmentModule env, Matrix4fc rotation,
                           double m11World)
    {
        if (!Double.isFinite(m11World) || !(m11World > 0.0)) return;
        double stableTan = Math.tan(Math.toRadians(StableProjection.configuredFov()) * 0.5);
        if (!(stableTan > 0.0) || !Double.isFinite(stableTan)) return;
        float k = (float) (1.0 / stableTan / m11World);
        if (!(k > 0f) || !Float.isFinite(k)) return;

        env.settings().forward(forwardAxis);
        forward.set((float) forwardAxis.x, (float) forwardAxis.y, (float) forwardAxis.z);
        rotation.transformDirection(forward).normalize();
        forwardImage.set(k * forward.x, k * forward.y, forward.z).normalize();
        // Preimage de la cible (M^-1 R) : son angle a F est celui de la cible au centre du cercle tel qu'il est vu.
        preimage.set(target.x / k, target.y / k, target.z).normalize();
        float along = preimage.dot(forward);
        tangent.set(preimage).fma(-along, forward);
        float sine = tangent.length();
        if (!(sine > 1.0e-5f)) return;
        // Cible dans le cercle avant : pas de fleche.
        if (Math.atan2(sine, along) <= FORWARD_EDGE_ANGLE) return;
        tangent.mul(1f / sine);
        // Base : bord du cone du cercle avant sur l'arc F -> M^-1 R, ramene en projection WORLD.
        base.set(forward).mul(COS_FORWARD_EDGE).fma(SIN_FORWARD_EDGE, tangent);
        base.set(k * base.x, k * base.y, base.z).normalize();
        if (!(base.z < 0f)) return;
        // Vers l'exterieur : tangente en la base du grand cercle F' -> base (celui du lien vers la cible).
        outward.set(base).mul(forwardImage.dot(base)).sub(forwardImage);
        float length = outward.length();
        if (!(length > 1.0e-6f)) return;
        outward.mul(1f / length);
        base.cross(outward, side).normalize();

        float gap = (float) (ARROW_GAP_PX * pixelTan);
        float tip = (float) ((ARROW_GAP_PX + ARROW_LENGTH_PX) * pixelTan);
        float half = (float) (0.5 * ARROW_WIDTH_PX * pixelTan);
        // Triangle (quad dont les deux derniers sommets sont la pointe), dans le plan tangent a la base.
        arrowVertex(vc, m, gap, -half);
        arrowVertex(vc, m, gap, half);
        arrowVertex(vc, m, tip, 0f);
        arrowVertex(vc, m, tip, 0f);
    }

    /** Sommet {@code RADIUS (base + a outward + s side)} de la fleche ({@code a}, {@code s} : tangentes). */
    private void arrowVertex(VertexConsumer vc, Matrix4f m, float a, float s)
    {
        corner.set(base).fma(a, outward).fma(s, side).mul(RADIUS);
        vertex(vc, m, corner.x, corner.y, corner.z, ARROW_ALPHA);
    }

    // ---------------------------------------------------------------- anneau

    /** Anneau vert autour de {@link #target} : rayon angulaire {@code ring} (milieu du trait), demi-epaisseur {@code half}. */
    private void drawRing(VertexConsumer vc, Matrix4f m, double ring, double half)
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
            ringVertex(vc, m, w, ic, is, RING_COS[j], RING_SIN[j]);
            ringVertex(vc, m, w, oc, os, RING_COS[j], RING_SIN[j]);
            ringVertex(vc, m, w, oc, os, RING_COS[j + 1], RING_SIN[j + 1]);
            ringVertex(vc, m, w, ic, is, RING_COS[j + 1], RING_SIN[j + 1]);
        }
    }

    /** Sommet {@code c w + s (ct bu + st bv)} ({@code c}, {@code s} deja multiplies par le rayon). */
    private void ringVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float c, float s, float ct, float st)
    {
        float u = ct * s;
        float v = st * s;
        vertex(vc, m, c * w.x + u * bu.x + v * bv.x, c * w.y + u * bu.y + v * bv.y, c * w.z + u * bu.z + v * bv.z,
                RING_ALPHA);
    }

    /**
     * Base perpendiculaire a {@code w} (espace vue) : {@code bv} = haut de l'ecran projete, {@code bu = w x bv} ; si
     * {@code w} est presque vertical, a partir de la droite de l'ecran (copie de {@link SpaceReferenceLinkRenderer}).
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

    /** Sommet vert transforme sans allocation ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur). */
    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(RED, GREEN, BLUE, a).uv2(LIGHT).endVertex();
    }
}
