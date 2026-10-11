package com.amynna.hololib.client.render.outline;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Contour facon glowing d'un {@link HoloOutlineMask} : shader {@code hololib:outline_composite}
 * ({@code assets/hololib/shaders/core/}), charge (et recharge avec les ressources) par {@code RegisterShadersEvent}.
 * Quad plein ecran sans test de profondeur : les pixels hors masque voisins du masque (jusqu'a 2 px) prennent la
 * couleur ; le contour se voit donc a travers les murs, comme le glowing vanilla.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoloOutlineShader
{
    private static ShaderInstance shader;

    private HoloOutlineShader() {}

    /**
     * Dessine le contour du masque sur la cible principale (liee a la fin), couleur {@code 0xRRGGBB} et opacite
     * {@code alpha}. Envoyer avant les lots en attente qui doivent passer dessous. Sans effet tant que le shader n'est
     * pas charge. Etat restaure : cible principale liee, profondeur, melange, cull, texture 0 du shader.
     */
    public static void compose(HoloOutlineMask mask, int rgb, float alpha)
    {
        ShaderInstance current = shader;
        if (current == null) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int texture0 = RenderSystem.getShaderTexture(0);
        try
        {
            main.bindWrite(false);
            current.safeGetUniform("TexelSize").set(1f / mask.width(), 1f / mask.height());
            current.safeGetUniform("OutlineColor").set((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f, alpha);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, mask.maskDepthTexture());
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1f, -1f, 0f).endVertex();
            builder.vertex(1f, -1f, 0f).endVertex();
            builder.vertex(1f, 1f, 0f).endVertex();
            builder.vertex(-1f, 1f, 0f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        }
        finally
        {
            main.bindWrite(false);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException
    {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "outline_composite"),
                DefaultVertexFormat.POSITION), loaded -> shader = loaded);
    }
}
