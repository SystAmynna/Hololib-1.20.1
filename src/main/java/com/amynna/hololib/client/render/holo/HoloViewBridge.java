package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Pont vue : objets ancres {@link HoloAnchor.Local} en premiere personne, sur une sphere autour de l'oeil (direction
 * {@code (x, y)} et distance {@code z} du transform), puis rotation et echelle du transform. Relatif : la sphere est
 * orientee comme le monde (on peut detourner le regard) ; follow : elle tourne avec la camera.
 */
final class HoloViewBridge implements ViewRenderer
{
    private final HoloDrawContext draw = new HoloDrawContext();
    private final Quaternionf rotation = new Quaternionf();

    @Override
    public boolean shouldRender(ViewRenderContext ctx) { return !HoloRouting.view().isEmpty(); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        List<ResolvedHolo> objects = HoloRouting.view();
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!(resolved.object.anchor() instanceof HoloAnchor.Local local)) continue;
            HoloTransform transform = resolved.object.transform();
            pose.pushPose();
            if (local.follow()) {
                // Espace vue (+X a droite, -Z devant) vers les axes camera de Minecraft (+X a gauche, +Z devant).
                pose.mulPose(rotation.rotationY(Mth.PI));
            } else {
                ctx.applyCameraRotation(pose);
            }
            HoloPlacement.onSphere(pose, rotation, transform.x(), transform.y(), transform.z());
            HoloPlacement.applyLocal(pose, rotation, transform);
            draw.set(ctx.partialTick(), HoloAnchor.Family.VIEW, resolved.ownerView);
            HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, draw);
            pose.popPose();
        }
    }
}
