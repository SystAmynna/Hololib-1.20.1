package com.amynna.hololib.client.render.modules.camera.targeting;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.client.modules.camera.targeting.TargetingClient;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Segment vert du <b>centre de l'ecran</b> (point sur l'axe de la camera, a {@value #START} bloc) jusqu'au centre de
 * la cible du joueur local ({@link TargetingClient}), dessine quand le regard ne passe plus par la cible (rayon de la
 * camera hors de sa boite : boite de l'entite, ou du bloc). Famille vue ({@link ViewRenderer}, projection
 * {@code WORLD} : le bout reste sur la cible), devant le terrain. Calcul en espace vue (directions du monde tournees
 * par {@link ViewRenderContext#cameraRotation()}), pile a l'identite ; bout coupe au plan {@code z = -}{@value #NEAR}
 * si la cible est derriere la camera. Ruban d'epaisseur constante a l'ecran ({@value #HALF_WIDTH} en tangente, de
 * part et d'autre). Aucune allocation par frame.
 */
final class TargetLinkRenderer implements ViewRenderer
{
    private static final float START = 1f;
    private static final float NEAR = 0.05f;
    private static final float HALF_WIDTH = 0.0016f;
    private static final HoloColor COLOR = HoloColor.selection(0xD0);

    // Couleurs du theme de la frame en cours (fil de rendu), posees par {@link #applyTheme}.
    private static int color;

    /** Resout les couleurs dans le theme de l'objet (debut de frame). */
    private static void applyTheme(HoloTheme theme)
    {
        color = COLOR.resolve(theme);
    }

    private final Vector3f end = new Vector3f();
    private final Vector3f forward = new Vector3f();

    // Boite de la cible relative a la camera (remplie par target()).
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;
    // Intervalle du rayon dans la boite (rayHitsBox).
    private double near;
    private double far;

    @Override
    public Projection projection() { return Projection.WORLD; }

    @Override
    public boolean shouldRender(ViewRenderContext ctx)
    {
        return TargetingClient.entity() != null || TargetingClient.block() != null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        applyTheme(HoloThemes.clientStandard());
        Vec3 camera = ctx.camera().getPosition();
        if (!target(camera, ctx.partialTick())) return;
        forward.set(ctx.camera().getLookVector());
        if (rayHitsBox(forward.x, forward.y, forward.z)) return;
        ctx.cameraRotation().transformDirection(end.set((float) ((minX + maxX) / 2), (float) ((minY + maxY) / 2),
                (float) ((minZ + maxZ) / 2)));
        float ax = 0f;
        float ay = 0f;
        float az = -START;
        if (end.z > -NEAR) {
            // Cible derriere (ou trop pres) : bout ramene sur le plan proche, le long du segment.
            float t = (-NEAR - az) / (end.z - az);
            end.set(ax + t * (end.x - ax), ay + t * (end.y - ay), -NEAR);
        }
        // Direction a l'ecran (tangentes), perpendiculaire.
        float dx = end.x / -end.z - ax / -az;
        float dy = end.y / -end.z - ay / -az;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-4f) return;
        float px = -dy / length * HALF_WIDTH;
        float py = dx / length * HALF_WIDTH;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer c = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float sa = -az;
        float sb = -end.z;
        vertex(c, matrix, ax - px * sa, ay - py * sa, az);
        vertex(c, matrix, end.x - px * sb, end.y - py * sb, end.z);
        vertex(c, matrix, end.x + px * sb, end.y + py * sb, end.z);
        vertex(c, matrix, ax + px * sa, ay + py * sa, az);
    }

    private static void vertex(VertexConsumer c, Matrix4f matrix, float x, float y, float z)
    {
        c.vertex(matrix, x, y, z).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    /** Boite de la cible relative a la camera ; faux sans cible. */
    private boolean target(Vec3 camera, float partialTick)
    {
        Entity entity = TargetingClient.entity();
        if (entity != null) {
            double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.x;
            double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - camera.y;
            double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.z;
            double half = entity.getBbWidth() / 2.0;
            minX = x - half;
            minY = y;
            minZ = z - half;
            maxX = x + half;
            maxY = y + entity.getBbHeight();
            maxZ = z + half;
            return true;
        }
        BlockPos block = TargetingClient.block();
        if (block == null) return false;
        minX = block.getX() - camera.x;
        minY = block.getY() - camera.y;
        minZ = block.getZ() - camera.z;
        maxX = minX + 1.0;
        maxY = minY + 1.0;
        maxZ = minZ + 1.0;
        return true;
    }

    /** Vrai si le rayon de la camera (origine, direction {@code d}) coupe la boite de la cible (methode des dalles). */
    private boolean rayHitsBox(double dx, double dy, double dz)
    {
        near = 0.0;
        far = Double.MAX_VALUE;
        return slab(dx, minX, maxX) && slab(dy, minY, maxY) && slab(dz, minZ, maxZ);
    }

    /** Resserre {@code [near, far]} sur une dalle ; faux si l'intervalle devient vide. */
    private boolean slab(double d, double lo, double hi)
    {
        if (Math.abs(d) < 1.0e-9) return lo <= 0.0 && hi >= 0.0;
        double t0 = lo / d;
        double t1 = hi / d;
        near = Math.max(near, Math.min(t0, t1));
        far = Math.min(far, Math.max(t0, t1));
        return near <= far;
    }
}
