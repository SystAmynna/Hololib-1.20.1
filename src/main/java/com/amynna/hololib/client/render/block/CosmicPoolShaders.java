package com.amynna.hololib.client.render.block;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.block.HoloLibBlocks;
import com.amynna.hololib.client.render.ShaderPacks;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Shader du bassin cosmique ({@code assets/hololib/shaders/core/cosmic_pool.*}), charge (et recharge avec les
 * ressources, F3+T) par {@code RegisterShadersEvent} sur le bus du mod ; enregistre aussi le renderer du bloc.
 * Format {@code POSITION_COLOR_TEX_LIGHTMAP} (donnees de surface, voir {@link CosmicPoolRenderer}).
 * <p>
 * Uniformes mis a jour a chaque dessin : {@code u_time} et {@code u_bob}, le bobbing de la marche que vanilla met dans
 * la projection du monde ({@code GameRenderer.bobView}, recopie ici) : le shader en deduit le rayon d'une camera sans
 * bobbing, l'interieur du bassin reste fixe a l'ecran pendant la marche.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CosmicPoolShaders
{
    /** Periode de {@code u_time} en ticks : garde la precision du float (une discontinuite toutes les ~14 h). */
    private static final long TIME_PERIOD = 1_000_000L;

    private static final Matrix4f BOB = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static ShaderInstance shader;

    private CosmicPoolShaders() {}

    /** Shader du bassin dans le monde : bobbing de la marche de la frame dans {@code u_bob}. */
    static ShaderInstance worldShader()
    {
        return update(walkBobbing(BOB));
    }

    /** Shader du bassin hors du monde (GUI, main en premiere personne) : {@code u_bob} = identite. */
    static ShaderInstance stillShader()
    {
        return update(IDENTITY);
    }

    /**
     * Shader, {@code u_time} (secondes de jeu, fige en pause) et {@code u_bob} mis a jour. Appele par l'etat du
     * {@code RenderType} a chaque dessin, sur le fil de rendu.
     */
    private static ShaderInstance update(Matrix4f bob)
    {
        if (shader != null)
        {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            float ticks = level == null ? 0.0F : (level.getGameTime() % TIME_PERIOD) + minecraft.getFrameTime();
            shader.safeGetUniform("u_time").set(ticks / 20.0F);
            shader.safeGetUniform("u_bob").set(bob);
        }
        return shader;
    }

    /**
     * Ecrit dans {@code dest} le bobbing de la marche de la frame, tel que {@code GameRenderer.bobView} l'applique a la
     * projection du monde ; identite sans bobbing, ou avec un shaderpack (Oculus le deplace dans le model-view).
     */
    private static Matrix4f walkBobbing(Matrix4f dest)
    {
        dest.identity();
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.bobView().get() || !(minecraft.getCameraEntity() instanceof Player player)
                || ShaderPacks.inUse())
        {
            return dest;
        }
        float partialTick = minecraft.getFrameTime();
        float step = player.walkDist - player.walkDistO;
        float walk = -(player.walkDist + step * partialTick);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        return dest.translate(Mth.sin(walk * Mth.PI) * bob * 0.5F, -Math.abs(Mth.cos(walk * Mth.PI) * bob), 0.0F)
                .rotateZ(Mth.sin(walk * Mth.PI) * bob * 3.0F * Mth.DEG_TO_RAD)
                .rotateX(Math.abs(Mth.cos(walk * Mth.PI - 0.2F) * bob) * 5.0F * Mth.DEG_TO_RAD);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException
    {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "cosmic_pool"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                loaded -> shader = loaded);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(HoloLibBlocks.COSMIC_POOL_ENTITY.get(), context -> new CosmicPoolRenderer());
    }
}
