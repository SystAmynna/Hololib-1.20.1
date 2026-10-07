package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.view.ViewRenderContext;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Pont de la fenetre d'outils visee ({@link HoloPicking#focused()}) : dessinee dans la passe vue, apres les autres
 * objets de la vue et profondeur videe, donc au-dessus de tout (hologrammes, terrain, son hote), sous la main et le GUI.
 * Son pont habituel la saute. Meme placement que dans sa famille, et meme projection (celle du monde pour un hote
 * dessine dans le monde, stable sinon) : la fenetre ne bouge pas en prenant le focus.
 */
final class HoloFocusBridge implements ViewRenderer
{
    private final HoloDrawContext draw = new HoloDrawContext();
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf tmp = new Quaternionf();
    private final Quaternionf overlayRotation = new Quaternionf();
    private final Vector3f center = new Vector3f();
    private final Vector3f offset = new Vector3f();

    @Override
    public boolean hiddenWithGui()
    {
        // Comme sa famille : seule la vue est masquee avec le GUI.
        ResolvedHolo focused = HoloPicking.focused();
        return focused == null || focused.family == HoloAnchor.Family.VIEW;
    }

    @Override
    public Projection projection()
    {
        ResolvedHolo focused = HoloPicking.focused();
        return focused != null && focused.family == HoloAnchor.Family.WORLD ? Projection.WORLD : Projection.STABLE;
    }

    @Override
    public boolean shouldRender(ViewRenderContext ctx) { return HoloPicking.focused() != null; }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, ViewRenderContext ctx)
    {
        ResolvedHolo resolved = HoloPicking.focused();
        Minecraft mc = Minecraft.getInstance();
        if (resolved == null || resolved.attachment == null || mc.level == null) return;
        float partialTick = ctx.partialTick();
        pose.pushPose();
        ctx.applyCameraRotation(pose);
        if (resolved.family == HoloAnchor.Family.WORLD) {
            Vec3 position = resolved.position(partialTick);
            if (!resolved.available()) {
                pose.popPose();
                return;
            }
            Vec3 camera = ctx.camera().getPosition();
            float scale = resolved.overlayScale();
            pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            pose.mulPose(resolved.orientation());
            pose.scale(scale, scale, scale);
        } else {
            float scale = HoloPlacement.eyeOverlay(resolved.attachment, ctx.camera(),
                    mc.level.getTimeOfDay(partialTick), rotation, center, tmp, offset, overlayRotation);
            if (Float.isNaN(scale)) {
                pose.popPose();
                return;
            }
            HoloPlacement.overlay(pose, offset, overlayRotation, scale);
        }
        // Au-dessus des objets de la vue deja dessines (le dispatcher a vide les buffers apres leur pont).
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        draw.set(partialTick, resolved.family, resolved.ownerView);
        HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, draw);
        pose.popPose();
    }
}
