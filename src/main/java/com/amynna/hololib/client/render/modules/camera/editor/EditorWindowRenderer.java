package com.amynna.hololib.client.render.modules.camera.editor;

import com.amynna.hololib.client.edit.HoloEditSession;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloHudPreview;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.holo.HoloWindowRenderer;
import com.amynna.hololib.modules.camera.editor.EditorHudFrame;
import com.amynna.hololib.modules.camera.editor.EditorWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Fenetre de l'editor : la fenetre et ses widgets comme toute {@code HoloWindow}, puis, si elle est l'hote du cadre HUD
 * de la session, les objets HUD en miniature dans son cadre (l'objet selectionne surligne).
 */
final class EditorWindowRenderer implements HoloObjectRenderer<EditorWindow>
{
    /** Les objets HUD passent devant les dessins du cadre (chaque dessin du canvas avance de 0.1 pixel). */
    private static final float FRAME_DEPTH = 3f;

    private final HoloWindowRenderer window = new HoloWindowRenderer();

    @Override
    public void render(EditorWindow object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        window.render(object, pose, buffers, packedLight, ctx);
        if (HoloEditSession.hudHost() != object) return;
        Minecraft mc = Minecraft.getInstance();
        float guiWidth = mc.getWindow().getGuiScaledWidth();
        float guiHeight = mc.getWindow().getGuiScaledHeight();
        EditorHudFrame frame = object.frame();
        float s = object.pixelScale();
        pose.pushPose();
        // Repere de la fenetre (pixels, y vers le bas), comme HoloWindowRenderer, puis coin du cadre.
        pose.translate(-0.5f * object.width() * s, 0.5f * object.height() * s, 0f);
        pose.scale(s, -s, s);
        pose.translate(frame.absoluteX(), frame.absoluteY(), FRAME_DEPTH);
        float k = frame.scale();
        pose.scale(k, k, k);
        HoloHudPreview.render(pose, buffers, guiWidth, guiHeight, ctx.partialTick(), HoloEditSession.selectedHud());
        pose.popPose();
    }
}
