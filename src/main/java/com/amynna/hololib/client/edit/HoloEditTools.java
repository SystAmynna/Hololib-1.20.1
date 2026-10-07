package com.amynna.hololib.client.edit;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.capture.HoloControlCapture;
import com.amynna.hololib.client.render.holo.HoloPicking;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.client.render.holo.ResolvedHolo;
import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.control.HoloClickable;
import com.amynna.hololib.holo.widget.control.HoloScrollable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outil actif du mode edition (un seul a la fois, sur un objet) et ses manipulations. Fil client.
 * <ul>
 *     <li>{@code Local} / {@code Sky} : l'objet entier est la poignee ({@link #handleFor}). Translation : clic
 *     maintenu, l'objet suit le regard ; rotation : clic maintenu, la souris (camera figee par une capture) le fait
 *     basculer autour des axes de la vue ; molette : distance, roll ou echelle selon l'outil.</li>
 *     <li>{@code World} : gizmos ({@link #pickGizmo}) : segments X / Y / Z (translation le long de l'axe, regard
 *     projete sur la droite) ou cercles (rotation autour de l'axe, regard projete dans le plan du cercle).</li>
 *     <li>{@code Hud} (cadre de l'editor) : molette sur l'objet selectionne.</li>
 * </ul>
 * Une modification est signalee a {@link HoloEditSession} au relachement (ou apres quelques ticks sans molette).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class HoloEditTools
{
    private static final float SCALE_STEP = 1.1f;
    private static final float DISTANCE_STEP = 1.05f;
    private static final float ROLL_STEP = 5f;
    /** Pas de la portee d'edition a la molette, en blocs. */
    private static final float RANGE_STEP = 0.5f;
    private static final float SKY_MIN_DISTANCE = 2f;
    /** Sous le plan lointain (128 blocs au minimum). */
    private static final float SKY_MAX_DISTANCE = 120f;
    private static final float MIN_SCALE = 1e-3f;
    /** Ticks sans molette avant l'envoi au serveur. */
    private static final int SEND_DELAY = 5;
    private static final float GIZMO_FACTOR = 1.3f;
    private static final float GIZMO_MIN = 0.5f;

    private static final HoloEditMath MATH = new HoloEditMath();
    private static final CameraHandle CAMERA = new CameraHandle();
    private static final AxisHandle[] AXES = {new AxisHandle(0), new AxisHandle(1), new AxisHandle(2)};
    private static final RotationCaptor ROTATION = new RotationCaptor();
    private static final Vector3f[] AXIS = {new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
            new Vector3f(0f, 0f, 1f)};
    private static final List<ResolvedHolo> GIZMO = new ArrayList<>(1);
    private static final List<ResolvedHolo> GIZMO_VIEW = Collections.unmodifiableList(GIZMO);

    private static HoloEditTarget target;
    private static HoloEditTool tool;
    /** Clic maintenu sur la poignee d'un objet {@code Local} / {@code Sky}. */
    private static boolean dragging;
    private static int heldAxis = -1;
    private static int hoverAxis = -1;
    /** Objet HUD tenu dans le cadre de l'editor. */
    private static HoloObject grabbedHud;
    private static HoloObject pending;
    private static int pendingTicks;

    // Temporaires.
    private static final Vector3d ORIGIN = new Vector3d();
    private static final Vector3f DIR = new Vector3f();
    private static final Vector3f VEC = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf Q2 = new Quaternionf();
    private static final Quaternionf FACING = new Quaternionf();
    private static final Quaternionf CELESTIAL = new Quaternionf();
    // Prise.
    private static float grabYaw;
    private static float grabPitch;
    private static float grabParam;
    private static final Quaternionf GRAB_GAZE = new Quaternionf();
    private static final Quaternionf GRAB_ROTATION = new Quaternionf();
    private static final Vector3f GRAB_POSITION = new Vector3f();
    private static final Vector3d GRAB_CENTER = new Vector3d();

    private HoloEditTools() {}

    // ---------------------------------------------------------------- selection

    static HoloEditTarget target() { return target; }

    static HoloEditTool tool() { return tool; }

    /** Bouton d'outil : active l'outil sur la cible, ou le desactive s'il l'etait deja. */
    static void toggle(HoloEditTarget target, HoloEditTool tool)
    {
        if (HoloEditTools.target == target && HoloEditTools.tool == tool) {
            select(null, null);
        } else {
            select(target, tool);
        }
    }

    /** Outil actif ({@code null} = aucun) ; termine la manipulation en cours. */
    static void select(HoloEditTarget target, HoloEditTool tool)
    {
        endDrag();
        HoloEditTools.target = tool != null ? target : null;
        HoloEditTools.tool = target != null ? tool : null;
    }

    /** Fin de session : manipulation terminee, envoi en attente fait, aucun outil. */
    static void reset()
    {
        select(null, null);
        grabbedHud = null;
        flushPending();
        GIZMO.clear();
    }

    /**
     * Vrai si l'objet porte l'outil actif : il reste visable et manipulable sans droit d'usage ({@code HoloAccess}),
     * l'edition ayant ses propres regles.
     */
    public static boolean editing(HoloObject object)
    {
        return target != null && tool != null && target.object() == object;
    }

    /** Vrai si l'objet est en cours de manipulation ou attend son envoi. */
    static boolean manipulating(HoloObject object)
    {
        if (object == null) return false;
        if (object == pending || object == grabbedHud) return true;
        return (dragging || heldAxis >= 0) && target != null && target.object() == object;
    }

    /** Envoi differe (molette) : fait apres quelques ticks sans nouveau changement. */
    static void schedule(HoloObject object)
    {
        if (pending != null && pending != object) HoloEditSession.send(pending);
        pending = object;
        pendingTicks = SEND_DELAY;
    }

    private static void flushPending()
    {
        if (pending != null) HoloEditSession.send(pending);
        pending = null;
    }

    // ---------------------------------------------------------------- picking (HoloPicking)

    /**
     * Poignee de l'outil actif si {@code object} le porte, sinon {@code null} : l'objet est alors vise comme un tout.
     * Un objet {@code World} n'en a que pour la molette (echelle, portee) : translation et rotation passent par les
     * gizmos.
     */
    public static HoloInteractible handleFor(HoloObject object)
    {
        if (target == null || tool == null || target.hud() || target.object() != object) return null;
        if (object.anchor() instanceof HoloAnchor.World && tool != HoloEditTool.SCALE && tool != HoloEditTool.RANGE) {
            return null;
        }
        return CAMERA;
    }

    /** Debut du picking d'une frame. */
    public static void clearHover() { hoverAxis = -1; }

    /**
     * Gizmo de l'outil actif sous le rayon ({@code origin} dans le monde, {@code direction} unitaire) : distance, ou
     * -1. Retient l'axe vise ({@link #gizmoHandle()}).
     */
    public static float pickGizmo(double ox, double oy, double oz, float dx, float dy, float dz, float max)
    {
        hoverAxis = -1;
        ResolvedHolo resolved = gizmoResolved();
        if (resolved == null) return -1f;
        Vec3 center = resolved.position(Minecraft.getInstance().getFrameTime());
        if (!resolved.available()) return -1f;
        float wx = (float) (center.x - ox);
        float wy = (float) (center.y - oy);
        float wz = (float) (center.z - oz);
        float size = gizmoSize(resolved.object());
        float best = max;
        for (int i = 0; i < 3; i++) {
            float t = tool == HoloEditTool.TRANSLATE ? raySegment(wx, wy, wz, dx, dy, dz, AXIS[i], size)
                    : rayRing(wx, wy, wz, dx, dy, dz, AXIS[i], size);
            if (t >= 0f && t < best) {
                best = t;
                hoverAxis = i;
            }
        }
        return hoverAxis >= 0 ? best : -1f;
    }

    public static HoloObject gizmoHost() { return target != null ? target.object() : null; }

    /** Poignee de l'axe vise au dernier {@link #pickGizmo}. */
    public static HoloInteractible gizmoHandle() { return hoverAxis >= 0 ? AXES[hoverAxis] : null; }

    // ---------------------------------------------------------------- gizmos (renderer)

    /** Objet du monde portant les gizmos (0 ou 1 element), mis a jour chaque tick. */
    public static List<ResolvedHolo> gizmo() { return GIZMO_VIEW; }

    /** Vrai : segments de translation ; faux : cercles de rotation. */
    public static boolean gizmoTranslate() { return tool == HoloEditTool.TRANSLATE; }

    /** Demi-longueur des segments, rayon des cercles. */
    public static float gizmoSize(HoloObject object)
    {
        return Math.max(GIZMO_MIN, object.boundingRadius() * object.transform().maxScale() * GIZMO_FACTOR);
    }

    /** Axe tenu, sinon vise ; -1 = aucun. */
    public static int highlightedAxis() { return heldAxis >= 0 ? heldAxis : hoverAxis; }

    private static ResolvedHolo gizmoResolved()
    {
        if (target == null || target.hud() || (tool != HoloEditTool.TRANSLATE && tool != HoloEditTool.ROTATE)) {
            return null;
        }
        if (!(target.object().anchor() instanceof HoloAnchor.World)) return null;
        ResolvedHolo resolved = HoloRouting.resolved(target.object());
        return resolved != null && resolved.family() == HoloAnchor.Family.WORLD ? resolved : null;
    }

    // ---------------------------------------------------------------- cadre HUD

    /** Glisser d'un objet HUD dans le cadre de l'editor : debut ({@code object}) ou fin ({@code null}). */
    public static void grabHud(HoloObject object)
    {
        if (grabbedHud != null && object == null) HoloEditSession.changed(grabbedHud, HoloEditTool.TRANSLATE, true);
        grabbedHud = object;
    }

    /** Molette sur le cadre : outil actif sur l'objet HUD selectionne. */
    public static void scrollHud(HoloObject object, int steps)
    {
        if (target != null && target.hud() && target.object() == object) scroll(object, steps);
    }

    // ---------------------------------------------------------------- tick et frame

    static void tick()
    {
        if (pending != null && --pendingTicks <= 0) flushPending();
        GIZMO.clear();
        ResolvedHolo resolved = gizmoResolved();
        if (resolved != null) GIZMO.add(resolved);
    }

    /** Glisser en cours, apres le picking de la frame. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!HoloControlCapture.gameInputActive(mc)) return;
        float partialTick = event.renderTickTime;
        if (dragging && tool == HoloEditTool.TRANSLATE) dragCamera(mc, partialTick);
        if (heldAxis >= 0) dragGizmo(mc, partialTick);
    }

    // ---------------------------------------------------------------- manipulations camera

    private static void beginCamera(HoloInteraction ctx)
    {
        Minecraft mc = Minecraft.getInstance();
        HoloObject object = target.object();
        HoloTransform t = object.transform();
        if (tool == HoloEditTool.TRANSLATE) {
            if (!HoloPicking.ray(mc, mc.getFrameTime(), ORIGIN, DIR)) return;
            float yaw = HoloEditMath.yawOf(DIR);
            float pitch = HoloEditMath.pitchOf(DIR);
            if (object.anchor() instanceof HoloAnchor.Local) {
                grabYaw = t.x() - yaw;
                grabPitch = t.y() - pitch;
            } else {
                HoloEditMath.yawPitch(GRAB_GAZE, yaw, pitch);
                GRAB_POSITION.set(t.x(), t.y(), t.z());
                HoloEditMath.rotation(GRAB_ROTATION, t);
            }
            dragging = true;
        } else if (tool == HoloEditTool.ROTATE) {
            dragging = ctx.capture(ROTATION);
        }
    }

    /**
     * Translation au regard : {@code Local} garde l'ecart angulaire pris au clic ; {@code Sky} tourne autour de
     * l'oeil.
     */
    private static void dragCamera(Minecraft mc, float partialTick)
    {
        if (!HoloPicking.ray(mc, partialTick, ORIGIN, DIR)) return;
        float yaw = HoloEditMath.yawOf(DIR);
        float pitch = HoloEditMath.pitchOf(DIR);
        HoloObject object = target.object();
        HoloTransform t = object.transform();
        if (object.anchor() instanceof HoloAnchor.Local) {
            t.setTranslation(Mth.wrapDegrees(yaw + grabYaw), Mth.clamp(pitch + grabPitch, -90f, 90f), t.z());
        } else if (object.anchor() instanceof HoloAnchor.Sky sky) {
            // Rotation du regard depuis la prise, dans le repere du ciel (celeste : conjugue par sa rotation).
            HoloEditMath.yawPitch(Q, yaw, pitch).mul(Q2.set(GRAB_GAZE).conjugate());
            if (sky.celestial()) {
                celestial(mc, partialTick);
                Q2.set(CELESTIAL).conjugate().mul(Q).mul(CELESTIAL);
                Q.set(Q2);
            }
            VEC.set(GRAB_POSITION).rotate(Q);
            t.setTranslation(VEC.x, VEC.y, VEC.z);
            MATH.setRotation(t, Q2.set(Q).mul(GRAB_ROTATION));
        }
    }

    /**
     * Rotation autour des axes de la vue (degres) : {@code dYaw} autour de la verticale de l'objet vu de face,
     * {@code dPitch} autour de son horizontale, {@code dRoll} autour de l'axe du regard.
     */
    private static void rotateView(HoloObject object, float dYaw, float dPitch, float dRoll)
    {
        HoloTransform t = object.transform();
        if (!facing(object)) return;
        // q' = F * D * F^-1 * q : D exprime dans le repere de face F.
        Q.rotationYXZ(dYaw * Mth.DEG_TO_RAD, dPitch * Mth.DEG_TO_RAD, dRoll * Mth.DEG_TO_RAD);
        Q2.set(FACING).mul(Q);
        Q.set(FACING).conjugate();
        Q2.mul(Q).mul(HoloEditMath.rotation(Q, t));
        MATH.setRotation(t, Q2);
    }

    /**
     * Repere de face de l'objet dans le repere de son transform : identite pour un {@code Local} (sa rotation est deja
     * relative a sa face, tournee vers l'oeil) et un {@code Hud} ; pour un {@code Sky}, face tournee vers la camera.
     * Faux pour un ancrage {@code World} (cercles).
     */
    private static boolean facing(HoloObject object)
    {
        HoloAnchor anchor = object.anchor();
        if (anchor instanceof HoloAnchor.World) return false;
        FACING.identity();
        if (anchor instanceof HoloAnchor.Sky) {
            HoloTransform t = object.transform();
            VEC.set(t.x(), t.y(), t.z());
            if (VEC.lengthSquared() > 1e-6f) {
                VEC.normalize();
                HoloEditMath.yawPitch(FACING, HoloEditMath.yawOf(VEC), HoloEditMath.pitchOf(VEC)).rotateY(Mth.PI);
            }
        }
        return true;
    }

    /** Molette : distance (translation), roll (rotation), echelle ou portee, selon l'outil et l'ancrage. */
    private static void scroll(HoloObject object, int steps)
    {
        HoloTransform t = object.transform();
        HoloAnchor anchor = object.anchor();
        switch (tool) {
            case TRANSLATE -> {
                if (anchor instanceof HoloAnchor.Local) {
                    float z = t.z() * (float) Math.pow(DISTANCE_STEP, steps);
                    t.setTranslation(t.x(), t.y(), Mth.clamp(z, HoloAnchor.Local.MIN_DISTANCE,
                            HoloAnchor.Local.MAX_DISTANCE));
                } else if (anchor instanceof HoloAnchor.Sky) {
                    VEC.set(t.x(), t.y(), t.z());
                    float length = VEC.length();
                    if (length < 1e-3f) return;
                    float distance = Mth.clamp(length * (float) Math.pow(DISTANCE_STEP, steps), SKY_MIN_DISTANCE,
                            SKY_MAX_DISTANCE);
                    VEC.mul(distance / length);
                    t.setTranslation(VEC.x, VEC.y, VEC.z);
                } else if (anchor instanceof HoloAnchor.Hud) {
                    t.setTranslation(t.x(), t.y(), Mth.clamp(t.z() + steps, -HoloAnchor.Hud.MAX_ORDER,
                            HoloAnchor.Hud.MAX_ORDER));
                } else {
                    return;
                }
            }
            case ROTATE -> {
                if (anchor instanceof HoloAnchor.World) return;
                rotateView(object, 0f, 0f, steps * ROLL_STEP);
            }
            case SCALE -> {
                float factor = (float) Math.pow(SCALE_STEP, steps);
                if (target.scaleSync()) {
                    t.setScale(clampScale(t.scaleX() * factor), clampScale(t.scaleY() * factor),
                            clampScale(t.scaleZ() * factor));
                } else {
                    int axis = target.scaleAxis();
                    t.setScale(axis == 0 ? clampScale(t.scaleX() * factor) : t.scaleX(),
                            axis == 1 ? clampScale(t.scaleY() * factor) : t.scaleY(),
                            axis == 2 ? clampScale(t.scaleZ() * factor) : t.scaleZ());
                }
            }
            case RANGE -> {
                if (!(anchor instanceof HoloAnchor.World)) return;
                object.setEditRange(object.editRange() + RANGE_STEP * steps);
            }
        }
        HoloEditSession.changed(object, tool, false);
    }

    private static float clampScale(float scale) { return Mth.clamp(scale, MIN_SCALE, HoloTransform.MAX_SCALE); }

    /** Fin d'un glisser (relachement, changement d'outil) : capture terminee, modification signalee. */
    private static void endDrag()
    {
        boolean was = dragging || heldAxis >= 0;
        dragging = false;
        heldAxis = -1;
        if (HoloCaptures.isCapturing(ROTATION)) HoloCaptures.end(ROTATION);
        if (was && target != null) HoloEditSession.changed(target.object(), tool, true);
    }

    /** Rotation celeste du ciel ({@code SkyRenderContext.applyCelestialRotation}). */
    private static void celestial(Minecraft mc, float partialTick)
    {
        CELESTIAL.rotationY(-90f * Mth.DEG_TO_RAD)
                .rotateX(mc.level.getTimeOfDay(partialTick) * 360f * Mth.DEG_TO_RAD);
    }

    // ---------------------------------------------------------------- gizmos

    private static void beginAxis(int axis)
    {
        Minecraft mc = Minecraft.getInstance();
        ResolvedHolo resolved = gizmoResolved();
        float partialTick = mc.getFrameTime();
        if (resolved == null || !HoloPicking.ray(mc, partialTick, ORIGIN, DIR)) return;
        Vec3 center = resolved.position(partialTick);
        if (!resolved.available()) return;
        GRAB_CENTER.set(center.x, center.y, center.z);
        HoloTransform t = target.object().transform();
        float param = tool == HoloEditTool.TRANSLATE ? lineParam(axis) : ringAngle(axis);
        if (Float.isNaN(param)) return;
        grabParam = param;
        GRAB_POSITION.set(t.x(), t.y(), t.z());
        HoloEditMath.rotation(GRAB_ROTATION, t);
        heldAxis = axis;
    }

    private static void dragGizmo(Minecraft mc, float partialTick)
    {
        if (!HoloPicking.ray(mc, partialTick, ORIGIN, DIR)) return;
        HoloObject object = target.object();
        HoloTransform t = object.transform();
        Vector3f axis = AXIS[heldAxis];
        if (tool == HoloEditTool.TRANSLATE) {
            float s = lineParam(heldAxis);
            if (Float.isNaN(s)) return;
            VEC.set(axis).mul(s - grabParam).add(GRAB_POSITION);
            float px = t.x();
            float py = t.y();
            float pz = t.z();
            t.setTranslation(VEC.x, VEC.y, VEC.z);
            if (!object.anchor().acceptable(t)) t.setTranslation(px, py, pz);
        } else {
            float angle = ringAngle(heldAxis);
            if (Float.isNaN(angle)) return;
            Q.rotationAxis(angle - grabParam, axis.x, axis.y, axis.z).mul(GRAB_ROTATION);
            MATH.setRotation(t, Q);
        }
    }

    /** Parametre sur l'axe (depuis le centre pris) du point le plus proche du rayon ; NaN si paralleles. */
    private static float lineParam(int axis)
    {
        Vector3f a = AXIS[axis];
        float wx = (float) (GRAB_CENTER.x - ORIGIN.x);
        float wy = (float) (GRAB_CENTER.y - ORIGIN.y);
        float wz = (float) (GRAB_CENTER.z - ORIGIN.z);
        float b = a.dot(DIR);
        float d = a.x * wx + a.y * wy + a.z * wz;
        float e = DIR.x * wx + DIR.y * wy + DIR.z * wz;
        float denom = 1f - b * b;
        if (denom < 1e-4f) return Float.NaN;
        return (b * e - d) / denom;
    }

    /** Angle du point vise dans le plan du cercle de l'axe, autour du centre pris ; NaN si le plan est rase. */
    private static float ringAngle(int axis)
    {
        Vector3f n = AXIS[axis];
        float wx = (float) (GRAB_CENTER.x - ORIGIN.x);
        float wy = (float) (GRAB_CENTER.y - ORIGIN.y);
        float wz = (float) (GRAB_CENTER.z - ORIGIN.z);
        float dn = n.dot(DIR);
        if (Math.abs(dn) < 1e-4f) return Float.NaN;
        float t = (n.x * wx + n.y * wy + n.z * wz) / dn;
        if (t < 0f) return Float.NaN;
        float rx = t * DIR.x - wx;
        float ry = t * DIR.y - wy;
        float rz = t * DIR.z - wz;
        Vector3f u = AXIS[(axis + 1) % 3];
        Vector3f v = AXIS[(axis + 2) % 3];
        return (float) Math.atan2(v.x * rx + v.y * ry + v.z * rz, u.x * rx + u.y * ry + u.z * rz);
    }

    /** Distance le long du rayon au segment {@code centre +- size * axe} s'il passe assez pres, sinon -1. */
    private static float raySegment(float wx, float wy, float wz, float dx, float dy, float dz, Vector3f a,
                                    float size)
    {
        float b = a.x * dx + a.y * dy + a.z * dz;
        float d = a.x * wx + a.y * wy + a.z * wz;
        float e = dx * wx + dy * wy + dz * wz;
        float denom = 1f - b * b;
        float s = denom < 1e-6f ? 0f : Mth.clamp((b * e - d) / denom, -size, size);
        float t = e + b * s;
        if (t < 0f) return -1f;
        float px = wx + s * a.x - t * dx;
        float py = wy + s * a.y - t * dy;
        float pz = wz + s * a.z - t * dz;
        float tolerance = Math.max(0.04f, 0.02f * t);
        return px * px + py * py + pz * pz <= tolerance * tolerance ? t : -1f;
    }

    /** Distance le long du rayon au cercle de rayon {@code size} autour de l'axe s'il passe assez pres, sinon -1. */
    private static float rayRing(float wx, float wy, float wz, float dx, float dy, float dz, Vector3f n,
                                 float size)
    {
        float dn = n.x * dx + n.y * dy + n.z * dz;
        if (Math.abs(dn) < 1e-4f) return -1f;
        float t = (n.x * wx + n.y * wy + n.z * wz) / dn;
        if (t < 0f) return -1f;
        float rx = t * dx - wx;
        float ry = t * dy - wy;
        float rz = t * dz - wz;
        float r = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float tolerance = Math.max(0.05f, 0.02f * t);
        return Math.abs(r - size) <= tolerance ? t : -1f;
    }

    // ---------------------------------------------------------------- poignees

    /** Poignee d'un objet {@code Local} / {@code Sky} (et molette de l'echelle d'un objet {@code World}). */
    private static final class CameraHandle implements HoloClickable, HoloScrollable
    {
        @Override
        public boolean clickableBy(HoloControl button)
        {
            return button == HoloControl.LEFT_CLICK && target != null
                    && (tool == HoloEditTool.TRANSLATE || tool == HoloEditTool.ROTATE)
                    && !(target.object().anchor() instanceof HoloAnchor.World);
        }

        @Override
        public void onClick(HoloInteraction ctx, HoloControl button)
        {
            if (target != null) beginCamera(ctx);
        }

        @Override
        public void onClickRelease(HoloInteraction ctx, HoloControl button) { endDrag(); }

        @Override
        public void onScrolled(HoloInteraction ctx, int steps)
        {
            if (target != null && tool != null) scroll(target.object(), steps);
        }
    }

    /** Segment ou cercle d'un axe (gizmo d'un objet {@code World}). */
    private static final class AxisHandle implements HoloClickable
    {
        private final int axis;

        private AxisHandle(int axis)
        {
            this.axis = axis;
        }

        @Override
        public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

        @Override
        public void onClick(HoloInteraction ctx, HoloControl button)
        {
            if (target != null) beginAxis(axis);
        }

        @Override
        public void onClickRelease(HoloInteraction ctx, HoloControl button) { endDrag(); }
    }

    /** Rotation au clic maintenu : souris (camera figee) et molette (roll). */
    private static final class RotationCaptor implements HoloCaptor
    {
        @Override
        public boolean captures(HoloControl control)
        {
            return control.equals(HoloControl.MOUSE_MOVE) || control.equals(HoloControl.SCROLL);
        }

        @Override
        public void onMouseMove(HoloCapture ctx, float dYaw, float dPitch)
        {
            if (target != null && (dYaw != 0f || dPitch != 0f)) rotateView(target.object(), dYaw, dPitch, 0f);
        }

        @Override
        public void onScroll(HoloCapture ctx, double delta)
        {
            if (target == null) return;
            int steps = (int) Math.round(delta);
            if (steps == 0) steps = delta > 0 ? 1 : -1;
            rotateView(target.object(), 0f, 0f, steps * ROLL_STEP);
        }
    }
}
