package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.world.WorldRenderContext;
import com.amynna.hololib.client.render.world.WorldRenderer;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Pont monde : objets ancres {@code World} (depuis l'origine de leur hote), et ancres {@code Local} vus dans le monde
 * (objet d'un autre joueur, ou vue a la troisieme personne), places sur leur sphere autour de l'oeil du joueur de
 * reference. Position et orientation (celles de l'overlay, face a la camera) sont calculees par
 * {@link ResolvedHolo} ; l'echelle du transform (ou de l'overlay) est appliquee ici.
 */
final class HoloWorldBridge extends WorldRenderer<ResolvedHolo>
{
    private final HoloDrawContext draw = new HoloDrawContext();

    @Override
    protected List<? extends ResolvedHolo> instances() { return HoloRouting.world(); }

    @Override
    protected double maxDistance(ResolvedHolo object) { return 96.0; }

    @Override
    protected boolean shouldRender(ResolvedHolo object, Vec3 position, WorldRenderContext ctx)
    {
        // Fenetre d'outils visee : dessinee au-dessus de tout par HoloFocusBridge.
        return object != HoloPicking.focused() && object.available() && super.shouldRender(object, position, ctx);
    }

    @Override
    protected int packedLight(ResolvedHolo object, Vec3 position, WorldRenderContext ctx)
    {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    protected void render(ResolvedHolo object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                          WorldRenderContext ctx)
    {
        pose.mulPose(object.orientation());
        if (object.attachment == null) {
            HoloTransform transform = object.object.transform();
            pose.scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
        } else {
            float scale = object.overlayScale();
            pose.scale(scale, scale, scale);
        }
        draw.set(ctx.partialTick(), HoloAnchor.Family.WORLD, object.ownerView);
        HoloObjectRenderers.render(object.object, pose, buffers, packedLight, draw);
    }
}
