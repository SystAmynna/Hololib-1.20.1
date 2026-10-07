package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.sky.SkyRenderContext;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Pont ciel : objets ancres {@link HoloAnchor.Sky}. Le transform est applique tel quel dans le repere du ciel (centre
 * sur l'oeil, axes du monde ou celestes) ; les astres vanilla sont a {@link SkyRenderContext#SKY_RADIUS} blocs. Un
 * overlay a son propre placement ({@link HoloAttachment#place}), dans les axes du monde.
 */
final class HoloSkyBridge implements SkyRenderer
{
    private final HoloDrawContext draw = new HoloDrawContext();
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf tmp = new Quaternionf();
    private final Quaternionf overlayRotation = new Quaternionf();
    private final Vector3f center = new Vector3f();
    private final Vector3f offset = new Vector3f();

    @Override
    public boolean shouldRender(SkyRenderContext ctx) { return !HoloRouting.sky().isEmpty(); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, SkyRenderContext ctx)
    {
        List<ResolvedHolo> objects = HoloRouting.sky();
        ResolvedHolo focused = HoloPicking.focused();
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            // Fenetre d'outils visee : dessinee au-dessus de tout par HoloFocusBridge.
            if (resolved == focused) continue;
            HoloObject placed = resolved.placed();
            if (!(placed.anchor() instanceof HoloAnchor.Sky sky)) continue;
            HoloTransform transform = placed.transform();
            pose.pushPose();
            if (resolved.attachment == null) {
                if (sky.celestial()) ctx.applyCelestialRotation(pose);
                pose.translate(transform.x(), transform.y(), transform.z());
                HoloPlacement.applyLocal(pose, rotation, transform);
            } else {
                // Overlay : placement propre dans les axes du monde, sans la rotation celeste de son hote.
                float scale = HoloPlacement.eyeOverlay(resolved.attachment, ctx.camera(), ctx.timeOfDay(), rotation,
                        center, tmp, offset, overlayRotation);
                HoloPlacement.overlay(pose, offset, overlayRotation, scale);
            }
            draw.set(ctx.partialTick(), HoloAnchor.Family.SKY, resolved.ownerView);
            HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, draw);
            pose.popPose();
        }
    }
}
