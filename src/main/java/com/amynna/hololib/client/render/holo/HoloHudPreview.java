package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Objets HUD retires du HUD par une session d'edition ({@link HoloRouting#hudPreview()}), dessines en miniature dans
 * un cadre (fenetre de l'editor) : meme placement que {@link HoloHudBridge}, profondeur ecrasee pour rester dans
 * l'epaisseur d'une fenetre. Fil de rendu ; aucune allocation.
 */
public final class HoloHudPreview
{
    /** Ecart en profondeur entre deux objets, en pixels GUI. */
    private static final float Z_STEP = 0.5f;
    /** Ecrasement de la profondeur propre des objets (objets epais). */
    private static final float Z_SCALE = 0.01f;
    private static final int HIGHLIGHT = 0x50FFE040;

    private static final HoloDrawContext DRAW = new HoloDrawContext();
    private static final Quaternionf ROTATION = new Quaternionf();

    private HoloHudPreview() {}

    /**
     * Dessine les objets ; la pile est placee au coin haut gauche du cadre, en pixels GUI ({@code y} vers le bas, face
     * {@code +Z}), a l'echelle du cadre. {@code highlighted} (peut etre nul) est surligne.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, float guiWidth, float guiHeight,
                              float partialTick, HoloObject highlighted)
    {
        List<ResolvedHolo> objects = HoloRouting.hudPreview();
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            if (!(resolved.object.anchor() instanceof HoloAnchor.Hud hud)) continue;
            HoloTransform transform = resolved.object.transform();
            pose.pushPose();
            pose.translate(hud.alignX() * guiWidth + transform.x(), hud.alignY() * guiHeight + transform.y(),
                    (i + 1) * Z_STEP);
            pose.scale(1f, -1f, Z_SCALE);
            HoloPlacement.applyLocal(pose, ROTATION, transform);
            if (resolved.object == highlighted) highlight(pose, buffers, resolved.object);
            DRAW.set(partialTick, HoloAnchor.Family.HUD, resolved.ownerView);
            HoloObjectRenderers.render(resolved.object, pose, buffers, LightTexture.FULL_BRIGHT, DRAW);
            pose.popPose();
        }
    }

    /** Fond translucide sur les bornes de l'objet, juste derriere lui. */
    private static void highlight(PoseStack pose, MultiBufferSource buffers, HoloObject object)
    {
        VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        Matrix4f matrix = pose.last().pose();
        float w = object.halfWidth() * 1.05f;
        float h = object.halfHeight() * 1.05f;
        int r = HIGHLIGHT >> 16 & 0xFF;
        int g = HIGHLIGHT >> 8 & 0xFF;
        int b = HIGHLIGHT & 0xFF;
        int a = HIGHLIGHT >>> 24;
        int light = LightTexture.FULL_BRIGHT;
        consumer.vertex(matrix, -w, -h, -0.01f).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(matrix, w, -h, -0.01f).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(matrix, w, h, -0.01f).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(matrix, -w, h, -0.01f).color(r, g, b, a).uv2(light).endVertex();
    }
}
