package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.sky.SkyRenderContext;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Pont ciel : objets ancres {@link HoloAnchor.Sky}. Le transform est applique tel quel dans le repere du ciel (centre
 * sur l'oeil, axes du monde ou celestes) ; les astres vanilla sont a {@link SkyRenderContext#SKY_RADIUS} blocs.
 */
final class HoloSkyBridge implements SkyRenderer
{
    private final HoloDrawContext draw = new HoloDrawContext();
    private final Quaternionf rotation = new Quaternionf();

    @Override
    public boolean shouldRender(SkyRenderContext ctx) { return !HoloRouting.sky().isEmpty(); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, SkyRenderContext ctx)
    {
        List<ResolvedHolo> objects = HoloRouting.sky();
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!(resolved.object.anchor() instanceof HoloAnchor.Sky sky)) continue;
            HoloTransform transform = resolved.object.transform();
            pose.pushPose();
            if (sky.celestial()) ctx.applyCelestialRotation(pose);
            pose.translate(transform.x(), transform.y(), transform.z());
            HoloPlacement.applyLocal(pose, rotation, transform);
            draw.set(ctx.partialTick(), HoloAnchor.Family.SKY, resolved.ownerView);
            HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, draw);
            pose.popPose();
        }
    }
}
