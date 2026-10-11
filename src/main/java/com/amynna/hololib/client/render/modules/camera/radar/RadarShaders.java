package com.amynna.hololib.client.render.modules.camera.radar;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * Shader de composition du radar ({@code assets/hololib/shaders/core/radar_composite.*}), charge (et recharge avec les
 * ressources) par {@code RegisterShadersEvent} sur le bus du mod. Format {@code POSITION} : quad plein ecran en
 * coordonnees de clip.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RadarShaders
{
    private static ShaderInstance composite;

    private RadarShaders() {}

    /** Shader de composition, ou {@code null} tant qu'il n'est pas charge. */
    static ShaderInstance composite() { return composite; }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException
    {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "radar_composite"), DefaultVertexFormat.POSITION),
                shader -> composite = shader);
    }
}
