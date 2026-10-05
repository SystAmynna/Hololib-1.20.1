package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Objet holographique sous le centre de l'ecran (le reticule), pour le systeme d'interaction. Pas un renderer : partage
 * avec les ponts les calculs de placement ({@link HoloPlacement}, {@link ResolvedHolo}) et les listes de
 * {@link HoloRouting}, d'ou sa place dans ce package. Fil client, hors du rendu.
 * <p>
 * Pour chaque objet route, la matrice repere local -> repere de la famille est reconstruite comme dans son pont, puis
 * le rayon est intersecte avec le plan {@code z = 0} de l'objet, vu de face seulement ; l'objet decide ensuite s'il
 * arrete le regard ({@link HoloObject#interactibleAt}). Seuls les objets dont l'acces autorise le joueur local sont
 * candidats. Priorite dans l'ordre de dessin : HUD (le plus en avant), vue, monde, ciel ; le plus proche dans une
 * famille. Monde : a moins de {@link #WORLD_REACH} blocs et non cache par un bloc. Ciel : seulement si le regard ne
 * touche rien du jeu ({@code hitResult} {@code MISS}).
 * <p>
 * Rayon : en premiere personne, depuis l'oeil selon le regard interpole (la passe vue n'a pas de bobbing) ; en F5,
 * depuis la camera de la frame precedente. Aucune allocation sans candidat du monde.
 */
public final class HoloPicking
{
    /** Distance maximale d'interaction avec un objet dessine dans le monde, en blocs. */
    public static final double WORLD_REACH = 16.0;

    /** Resultat, objet reutilise : valable jusqu'au prochain {@link #pick}. */
    public static final class Hit
    {
        private HoloObject object;
        private HoloInteractible target;
        private HoloAnchor.Family family;
        private float localX;
        private float localY;
        private float distance;
        private float objectYaw;
        private float objectPitch;

        private Hit() {}

        public HoloObject object() { return object; }

        /** Element interactible le plus profond sous le regard. */
        public HoloInteractible target() { return target; }

        public HoloAnchor.Family family() { return family; }

        /** Point vise dans le plan local {@code z = 0} de l'objet. */
        public float localX() { return localX; }

        public float localY() { return localY; }

        /** Distance depuis l'origine du rayon (blocs ; pixels GUI en HUD). */
        public float distance() { return distance; }

        /** Direction de l'oeil vers le centre de l'objet (regard courant en HUD). */
        public float objectYaw() { return objectYaw; }

        public float objectPitch() { return objectPitch; }
    }

    private static final Hit HIT = new Hit();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Quaternionf ORIENTATION = new Quaternionf();
    private static final Quaternionf TMP = new Quaternionf();
    private static final Vector3f OFFSET = new Vector3f();
    private static final Vector3f LOCAL_ORIGIN = new Vector3f();
    private static final Vector3f LOCAL_DIR = new Vector3f();
    private static final Vector3f CENTER = new Vector3f();

    // Rayon courant, repere centre sur l'oeil (axes du monde), sauf en HUD (pixels GUI).
    private static float ox, oy, oz, dx, dy, dz;
    // Derniere intersection reussie.
    private static float hitX, hitY, hitT;
    // Oeil interpole (sceneRay), taille du GUI (hudRay), resultat de project.
    private static double eyeX, eyeY, eyeZ;
    private static float guiWidth, guiHeight;
    private static float projectedX, projectedY;

    private HoloPicking() {}

    /** Element vise par le joueur local, ou {@code null}. */
    public static Hit pick(Minecraft mc, float partialTick)
    {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        UUID local = player.getUUID();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (firstPerson && pickHud(mc, local, player.getViewYRot(partialTick), player.getViewXRot(partialTick))) {
            return HIT;
        }
        sceneRay(mc, player, partialTick);
        if (firstPerson && pickView(local, player, partialTick)) return HIT;
        if (pickWorld(mc, local, partialTick)) return HIT;
        HitResult hitResult = mc.hitResult;
        if ((hitResult == null || hitResult.getType() == HitResult.Type.MISS) && pickSky(mc, local, partialTick)) {
            return HIT;
        }
        return null;
    }

    /**
     * Regard projete sur le plan {@code z = 0} d'un objet route, meme hors de ses bords (survol avec marge, glisser) :
     * {@link #projectedX()}, {@link #projectedY()} dans son repere local. Faux si l'objet n'est pas route, pas
     * disponible ou vu de dos.
     */
    public static boolean project(Minecraft mc, HoloObject object, float partialTick)
    {
        LocalPlayer player = mc.player;
        ResolvedHolo resolved = HoloRouting.resolved(object);
        if (player == null || mc.level == null || resolved == null) return false;
        boolean placed;
        if (resolved.family == HoloAnchor.Family.HUD) {
            hudRay(mc);
            placed = hudMatrix(resolved);
        } else {
            sceneRay(mc, player, partialTick);
            placed = switch (resolved.family) {
                case VIEW -> viewMatrix(resolved, player, partialTick);
                case WORLD -> worldMatrix(resolved, partialTick);
                case SKY -> skyMatrix(resolved, mc, partialTick);
                case HUD -> false;
            };
            if (resolved.family == HoloAnchor.Family.SKY) ox = oy = oz = 0f;
        }
        if (!placed || !intersect()) return false;
        projectedX = hitX;
        projectedY = hitY;
        return true;
    }

    /** Resultat de {@link #project}, repere local de l'objet. */
    public static float projectedX() { return projectedX; }

    public static float projectedY() { return projectedY; }

    // ---------------------------------------------------------------- familles

    private static boolean pickHud(Minecraft mc, UUID local, float yaw, float pitch)
    {
        List<ResolvedHolo> objects = HoloRouting.hud();
        if (objects.isEmpty()) return false;
        hudRay(mc);
        // Trie par z croissant : le dernier est devant.
        for (int i = objects.size() - 1; i >= 0; i--) {
            ResolvedHolo resolved = objects.get(i);
            if (!candidate(resolved, local) || !hudMatrix(resolved)) continue;
            if (accept(resolved, HoloAnchor.Family.HUD)) {
                HIT.objectYaw = yaw;
                HIT.objectPitch = pitch;
                return true;
            }
        }
        return false;
    }

    private static boolean pickView(UUID local, LocalPlayer player, float partialTick)
    {
        List<ResolvedHolo> objects = HoloRouting.view();
        boolean found = false;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!candidate(resolved, local) || !viewMatrix(resolved, player, partialTick)) continue;
            if (accept(resolved, HoloAnchor.Family.VIEW, best)) {
                best = HIT.distance;
                found = true;
            }
        }
        return found;
    }

    private static boolean pickWorld(Minecraft mc, UUID local, float partialTick)
    {
        List<ResolvedHolo> objects = HoloRouting.world();
        boolean found = false;
        float best = (float) WORLD_REACH;
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!candidate(resolved, local) || !worldMatrix(resolved, partialTick)) continue;
            if (accept(resolved, HoloAnchor.Family.WORLD, best)) {
                best = HIT.distance;
                found = true;
            }
        }
        if (!found) return false;
        // Le plus proche cache par un bloc : tout ce qui est derriere l'est aussi.
        // Un peu avant l'objet, pour qu'un hologramme pose contre un bloc reste utilisable.
        float reach = Math.max(0f, best - 0.05f);
        Vec3 from = new Vec3(eyeX + ox, eyeY + oy, eyeZ + oz);
        Vec3 to = from.add(dx * reach, dy * reach, dz * reach);
        HitResult blocks = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                mc.player));
        return blocks.getType() == HitResult.Type.MISS;
    }

    private static boolean pickSky(Minecraft mc, UUID local, float partialTick)
    {
        List<ResolvedHolo> objects = HoloRouting.sky();
        boolean found = false;
        float best = Float.MAX_VALUE;
        // Le ciel est centre sur la camera : rayon depuis son origine.
        float sx = ox, sy = oy, sz = oz;
        ox = oy = oz = 0f;
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!candidate(resolved, local) || !skyMatrix(resolved, mc, partialTick)) continue;
            if (accept(resolved, HoloAnchor.Family.SKY, best)) {
                best = HIT.distance;
                found = true;
            }
        }
        ox = sx;
        oy = sy;
        oz = sz;
        return found;
    }

    // ---------------------------------------------------------------- rayons et matrices (comme les ponts)

    /** Centre de l'ecran en pixels GUI, rayon vers l'interieur de l'ecran ({@code +Z} vers l'observateur). */
    private static void hudRay(Minecraft mc)
    {
        guiWidth = mc.getWindow().getGuiScaledWidth();
        guiHeight = mc.getWindow().getGuiScaledHeight();
        ox = 0.5f * guiWidth;
        oy = 0.5f * guiHeight;
        oz = 10000f;
        dx = 0f;
        dy = 0f;
        dz = -1f;
    }

    /** Oeil interpole ; rayon depuis l'oeil selon le regard (1re personne) ou depuis la camera (F5). */
    private static void sceneRay(Minecraft mc, LocalPlayer player, float partialTick)
    {
        eyeX = Mth.lerp(partialTick, player.xo, player.getX());
        eyeY = Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight();
        eyeZ = Mth.lerp(partialTick, player.zo, player.getZ());
        if (mc.options.getCameraType().isFirstPerson()) {
            ox = oy = oz = 0f;
            direction(player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        } else {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 position = camera.getPosition();
            ox = (float) (position.x - eyeX);
            oy = (float) (position.y - eyeY);
            oz = (float) (position.z - eyeZ);
            Vector3f look = camera.getLookVector();
            dx = look.x;
            dy = look.y;
            dz = look.z;
        }
    }

    private static boolean hudMatrix(ResolvedHolo resolved)
    {
        if (!(resolved.object.anchor() instanceof HoloAnchor.Hud hud)) return false;
        HoloTransform transform = resolved.object.transform();
        MATRIX.translation(hud.alignX() * guiWidth + transform.x(), hud.alignY() * guiHeight + transform.y(),
                transform.z()).scale(1f, -1f, 1f);
        applyLocal(transform);
        return true;
    }

    private static boolean viewMatrix(ResolvedHolo resolved, LocalPlayer player, float partialTick)
    {
        if (!(resolved.object.anchor() instanceof HoloAnchor.Local anchor)) return false;
        HoloTransform transform = resolved.object.transform();
        if (anchor.follow()) {
            HoloPlacement.yawPitch(ORIENTATION, player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        } else {
            ORIENTATION.identity();
        }
        HoloPlacement.onSphere(ORIENTATION, OFFSET, TMP, transform);
        MATRIX.translation(OFFSET).rotate(ORIENTATION)
                .scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
        return true;
    }

    private static boolean worldMatrix(ResolvedHolo resolved, float partialTick)
    {
        Vec3 position = resolved.position(partialTick);
        if (!resolved.available()) return false;
        HoloTransform transform = resolved.object.transform();
        MATRIX.translation((float) (position.x - eyeX), (float) (position.y - eyeY), (float) (position.z - eyeZ))
                .rotate(resolved.orientation())
                .scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
        return true;
    }

    private static boolean skyMatrix(ResolvedHolo resolved, Minecraft mc, float partialTick)
    {
        if (!(resolved.object.anchor() instanceof HoloAnchor.Sky sky)) return false;
        HoloTransform transform = resolved.object.transform();
        MATRIX.identity();
        if (sky.celestial()) {
            // Comme SkyRenderContext.applyCelestialRotation.
            MATRIX.rotateY(-90f * Mth.DEG_TO_RAD).rotateX(mc.level.getTimeOfDay(partialTick) * 360f * Mth.DEG_TO_RAD);
        }
        MATRIX.translate(transform.x(), transform.y(), transform.z());
        applyLocal(transform);
        return true;
    }

    /** Objet dessinable par le joueur local et qu'il a le droit d'utiliser. */
    private static boolean candidate(ResolvedHolo resolved, UUID local)
    {
        return resolved.object.access().allows(local, resolved.container.scope().owner());
    }

    private static void applyLocal(HoloTransform transform)
    {
        MATRIX.rotate(HoloPlacement.rotation(TMP, transform))
                .scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
    }

    private static boolean accept(ResolvedHolo resolved, HoloAnchor.Family family)
    {
        return accept(resolved, family, Float.MAX_VALUE);
    }

    /** Intersecte {@link #MATRIX} avec le rayon ; remplit {@link #HIT} si l'objet vise est plus proche que max. */
    private static boolean accept(ResolvedHolo resolved, HoloAnchor.Family family, float max)
    {
        if (!intersect() || hitT >= max) return false;
        HoloInteractible target = resolved.object.interactibleAt(hitX, hitY);
        if (target == null) return false;
        HIT.object = resolved.object;
        HIT.target = target;
        HIT.family = family;
        HIT.localX = hitX;
        HIT.localY = hitY;
        HIT.distance = hitT;
        if (family != HoloAnchor.Family.HUD) {
            MATRIX.getTranslation(CENTER);
            HIT.objectYaw = (float) Mth.atan2(-CENTER.x, CENTER.z) * Mth.RAD_TO_DEG;
            HIT.objectPitch = (float) Mth.atan2(-CENTER.y, Math.sqrt(CENTER.x * CENTER.x + CENTER.z * CENTER.z))
                    * Mth.RAD_TO_DEG;
        }
        return true;
    }

    /** Rayon contre le plan {@code z = 0} du repere {@link #MATRIX}, vu de face ({@code +Z}). */
    private static boolean intersect()
    {
        if (Math.abs(MATRIX.determinant()) < 1e-12f) return false;
        MATRIX.invert(INVERSE);
        INVERSE.transformPosition(ox, oy, oz, LOCAL_ORIGIN);
        INVERSE.transformDirection(dx, dy, dz, LOCAL_DIR);
        if (LOCAL_ORIGIN.z <= 0f || LOCAL_DIR.z >= 0f) return false;
        // Transformation affine : le parametre du rayon est le meme dans les deux reperes (direction unitaire).
        float t = -LOCAL_ORIGIN.z / LOCAL_DIR.z;
        hitX = LOCAL_ORIGIN.x + LOCAL_DIR.x * t;
        hitY = LOCAL_ORIGIN.y + LOCAL_DIR.y * t;
        hitT = t;
        return true;
    }

    /** Direction de regard {@code (yaw, pitch)} de Minecraft, unitaire. */
    private static void direction(float yaw, float pitch)
    {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        dx = -Mth.sin(yawRad) * cosPitch;
        dy = -Mth.sin(pitchRad);
        dz = Mth.cos(yawRad) * cosPitch;
    }
}
