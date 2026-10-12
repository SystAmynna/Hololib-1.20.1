package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.window.HoloWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Renderer par defaut des {@link HoloWindow} : passe du repere local de l'objet (blocs, {@code y} vers le haut, centre)
 * au repere du GUI (pixels, {@code y} vers le bas, origine en haut a gauche), puis dessine l'arborescence de widgets
 * via un {@link BufferHoloCanvas}, qui porte le theme de la fenetre ({@link HoloThemes#resolve}).
 * <p>
 * L'echelle {@code (s, -s, s)} reproduit l'inversion de {@code y} de la projection du GUI : les quads du texte, ecrits
 * pour le GUI, restent tournes vers {@code +Z} (le texte est elimine vu de dos, comme sur un panneau).
 */
public final class HoloWindowRenderer implements HoloObjectRenderer<HoloWindow>
{
    private final BufferHoloCanvas canvas = new BufferHoloCanvas();

    @Override
    public void render(HoloWindow window, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        float s = window.pixelScale();
        pose.pushPose();
        pose.translate(-0.5f * window.width() * s, 0.5f * window.height() * s, 0f);
        pose.scale(s, -s, s);
        canvas.begin(pose.last(), buffers, packedLight, ctx.partialTick(), HoloThemes.resolve(window));
        try {
            window.root().render(canvas);
        } finally {
            canvas.end();
            pose.popPose();
        }
    }
}
