package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.hud.HudRenderContext;
import com.amynna.hololib.client.render.hud.HudRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Pont HUD : objets ancres {@link HoloAnchor.Hud}, a plat sur l'ecran, dans l'ordre de leur {@code z} (trie par
 * {@link HoloRouting}). Origine au point d'alignement plus {@code (x, y)} en pixels GUI ; {@code z} sert aussi de
 * profondeur GUI (plus grand = devant), pour que des objets epais se recouvrent dans le meme ordre. Passe du repere du
 * GUI ({@code y} vers le bas) au repere local de l'objet ({@code y} vers le haut) par {@code (1, -1, 1)}, puis rotation
 * et echelle du transform (pixels GUI par unite).
 */
final class HoloHudBridge implements HudRenderer
{
    private final HoloDrawContext draw = new HoloDrawContext();
    private final Quaternionf rotation = new Quaternionf();

    @Override
    public boolean shouldRender(HudRenderContext ctx) { return !HoloRouting.hud().isEmpty(); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, HudRenderContext ctx)
    {
        List<ResolvedHolo> objects = HoloRouting.hud();
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!(resolved.object.anchor() instanceof HoloAnchor.Hud hud)) continue;
            HoloTransform transform = resolved.object.transform();
            pose.pushPose();
            pose.translate(hud.alignX() * ctx.width() + transform.x(), hud.alignY() * ctx.height() + transform.y(),
                    transform.z());
            pose.scale(1f, -1f, 1f);
            HoloPlacement.applyLocal(pose, rotation, transform);
            draw.set(ctx.partialTick(), HoloAnchor.Family.HUD, resolved.ownerView);
            HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, draw);
            pose.popPose();
        }
    }
}
