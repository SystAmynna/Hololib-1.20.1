package com.amynna.hololib.client.render.modules.monde.screen;

import com.amynna.hololib.client.modules.monde.screen.ScreenClient;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.levelview.HoloLevelView;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.modules.monde.screen.ScreenDisplay;
import com.amynna.hololib.modules.monde.screen.ScreenModule;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Dessin de l'ecran du Screen ({@link ScreenDisplay}) : plaque de fond (double face, visible de dos), image de la vue
 * du niveau ({@link ScreenClient}, face avant, pleine lumiere), cadre, et etat du signal quand il n'y a pas d'image a
 * jour. Signale la vue comme vue a chaque dessin ({@code markSeen}, avec la hauteur de l'ecran a l'ecran en pixels) :
 * une vue n'est rendue que si son ecran est dessine dans le rendu principal, d'autant moins souvent qu'il y est petit.
 */
final class ScreenDisplayRenderer implements HoloObjectRenderer<ScreenDisplay>
{
    private static final HoloColor BACK = HoloColor.secondary(0xE6).darker(0.6f);
    private static final HoloColor FRAME = HoloColor.primary(0xFF);
    private static final HoloColor TEXT = HoloColor.special(0xFF);
    private static final HoloColor TEXT_BACKGROUND = HoloColor.secondary(0xC0).darker(0.7f);
    /** Epaisseur du cadre, en fraction de la hauteur de l'ecran (exterieur a l'image). */
    private static final float FRAME_THICKNESS = 0.015f;
    /** Hauteur d'une ligne de texte, en fraction de la hauteur de l'ecran. */
    private static final float TEXT_HEIGHT = 0.07f;
    /** Ecart de profondeur entre plaque, image et cadre (local). */
    private static final float DEPTH = 0.002f;

    private static final String WAITING = "En attente du signal";
    private static final String NO_SIGNAL = "Pas de signal";

    private final Matrix4f textMatrix = new Matrix4f();

    @Override
    public void render(ScreenDisplay display, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        ScreenModule module = display.screen();
        if (module == null) return;
        HoloTheme theme = HoloThemes.resolve(display);
        HoloLevelView view = ScreenClient.view(module);
        Matrix4f m = pose.last().pose();
        float hw = display.halfWidth();
        float hh = display.halfHeight();
        if (view != null) view.markSeen(projectedHeight(m, hh));
        int light = LightTexture.FULL_BRIGHT;

        // Plaque de fond, double face : de dos, l'ecran est une plaque sombre.
        VertexConsumer quads = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float f = FRAME_THICKNESS;
        rect(quads, m, -hw - f, -hh - f, hw + f, hh + f, -DEPTH, BACK.resolve(theme), light);

        boolean image = view != null && view.hasImage();
        if (image) {
            VertexConsumer c = buffers.getBuffer(RenderType.text(view.texture()));
            // Face avant seulement (type texte : faces arriere eliminees), sens trigonometrique vu de +Z.
            c.vertex(m, -hw, hh, 0f).color(0xFF, 0xFF, 0xFF, 0xFF).uv(view.u0(), view.v0()).uv2(light).endVertex();
            c.vertex(m, -hw, -hh, 0f).color(0xFF, 0xFF, 0xFF, 0xFF).uv(view.u0(), view.v1()).uv2(light).endVertex();
            c.vertex(m, hw, -hh, 0f).color(0xFF, 0xFF, 0xFF, 0xFF).uv(view.u1(), view.v1()).uv2(light).endVertex();
            c.vertex(m, hw, hh, 0f).color(0xFF, 0xFF, 0xFF, 0xFF).uv(view.u1(), view.v0()).uv2(light).endVertex();
        }

        // Cadre exterieur a l'image.
        quads = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        int frame = FRAME.resolve(theme);
        rect(quads, m, -hw - f, hh, hw + f, hh + f, DEPTH, frame, light);
        rect(quads, m, -hw - f, -hh - f, hw + f, -hh, DEPTH, frame, light);
        rect(quads, m, -hw - f, -hh, -hw, hh, DEPTH, frame, light);
        rect(quads, m, hw, -hh, hw + f, hh, DEPTH, frame, light);

        String status = status(view, image);
        if (status != null) label(status, m, buffers, TEXT.resolve(theme), TEXT_BACKGROUND.resolve(theme), light);
    }

    /**
     * Hauteur approchee de l'ecran a l'ecran, en pixels : hauteur dans le repere de la vue (la pile contient la
     * rotation de la camera, la translation et l'echelle) projetee a la distance de son centre.
     */
    private static float projectedHeight(Matrix4f m, float halfHeight)
    {
        float distance = (float) Math.sqrt(m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32());
        float height = 2f * halfHeight * (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
        float focal = RenderSystem.getProjectionMatrix().m11();
        return height * focal / Math.max(distance, 0.05f) * 0.5f * Minecraft.getInstance().getWindow().getHeight();
    }

    /** Texte d'etat, {@code null} quand l'image est a jour. */
    private static String status(HoloLevelView view, boolean image)
    {
        if (view == null) return WAITING;
        return switch (view.status()) {
            case OK -> null;
            case WAITING -> image ? null : WAITING;
            case NO_SIGNAL -> NO_SIGNAL;
        };
    }

    /** Texte centre, sur fond sombre, devant l'image. */
    private void label(String text, Matrix4f m, MultiBufferSource buffers, int color, int background, int light)
    {
        Font font = Minecraft.getInstance().font;
        float scale = TEXT_HEIGHT / font.lineHeight;
        textMatrix.set(m).translate(0f, 0f, 2f * DEPTH).scale(scale, -scale, scale);
        font.drawInBatch(text, -0.5f * font.width(text), -0.5f * font.lineHeight, color, false, textMatrix, buffers,
                Font.DisplayMode.NORMAL, background, light);
    }

    /** Rectangle du plan {@code z} (double face avec {@code TRANSLUCENT_QUADS}). */
    private static void rect(VertexConsumer c, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb,
                             int light)
    {
        c.vertex(m, x0, y1, z).color(argb).uv2(light).endVertex();
        c.vertex(m, x0, y0, z).color(argb).uv2(light).endVertex();
        c.vertex(m, x1, y0, z).color(argb).uv2(light).endVertex();
        c.vertex(m, x1, y1, z).color(argb).uv2(light).endVertex();
    }
}
