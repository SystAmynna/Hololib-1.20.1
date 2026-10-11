package com.amynna.hololib.client.render.cancel;

import com.amynna.hololib.holo.render.HoloRenderElement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Enveloppe des effets d'une dimension, mise dans {@code ClientLevel.effects} tant que le ciel, les nuages ou la meteo
 * sont annules ({@link RenderCancels}). Delegue tout a l'original ; les hooks Forge de {@code LevelRenderer}
 * ({@code renderSky}, {@code renderClouds}, {@code renderSnowAndRain}) renvoient vrai pour un element annule (etat lu
 * a chaque appel) ; ciel annule, {@code getSunriseColor} ne renvoie aucune lueur. Usage interne.
 */
final class CancelingEffects extends DimensionSpecialEffects
{
    private final DimensionSpecialEffects original;

    CancelingEffects(DimensionSpecialEffects original)
    {
        super(original.getCloudHeight(), original.hasGround(), original.skyType(), original.forceBrightLightmap(),
                original.constantAmbientLight());
        this.original = original;
    }

    DimensionSpecialEffects original() { return original; }

    /**
     * Ciel annule : aucune lueur du lever et du coucher. {@code FogRenderer.setupColor} la melange sinon a la couleur du
     * brouillard selon le regard (vers le soleil), donc au fond de l'ecran qui remplace le ciel.
     */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick)
    {
        if (RenderCancels.cancelled(HoloRenderElement.SKY)) return null;
        return original.getSunriseColor(timeOfDay, partialTick);
    }

    @Override
    public float getCloudHeight() { return original.getCloudHeight(); }

    @Override
    public boolean hasGround() { return original.hasGround(); }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness)
    {
        return original.getBrightnessDependentFogColor(color, brightness);
    }

    @Override
    public boolean isFoggyAt(int x, int y) { return original.isFoggyAt(x, y); }

    @Override
    public SkyType skyType() { return original.skyType(); }

    @Override
    public boolean forceBrightLightmap() { return original.forceBrightLightmap(); }

    @Override
    public boolean constantAmbientLight() { return original.constantAmbientLight(); }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX,
                                double camY, double camZ, Matrix4f projectionMatrix)
    {
        return RenderCancels.cancelled(HoloRenderElement.CLOUDS)
                || original.renderClouds(level, ticks, partialTick, poseStack, camX, camY, camZ, projectionMatrix);
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
    {
        return RenderCancels.cancelled(HoloRenderElement.SKY)
                || original.renderSky(level, ticks, partialTick, poseStack, camera, projectionMatrix, isFoggy,
                setupFog);
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
                                     double camX, double camY, double camZ)
    {
        return RenderCancels.cancelled(HoloRenderElement.WEATHER)
                || original.renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ);
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera)
    {
        return original.tickRain(level, ticks, camera);
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors)
    {
        original.adjustLightmapColors(level, partialTicks, skyDarken, blockLightRedFlicker, skyLight, pixelX, pixelY,
                colors);
    }
}
