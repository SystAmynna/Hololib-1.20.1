package com.amynna.hololib.client.render.world;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

/** Donnees de la frame pour un {@link WorldRenderer}. Objet reutilise : ne pas le garder au-dela de l'appel. */
public final class WorldRenderContext
{
    private ClientLevel level;
    private Camera camera;
    private Frustum frustum;
    private float partialTick;

    WorldRenderContext() {}

    void set(ClientLevel level, Camera camera, Frustum frustum, float partialTick)
    {
        this.level = level;
        this.camera = camera;
        this.frustum = frustum;
        this.partialTick = partialTick;
    }

    public ClientLevel level() { return level; }

    public Camera camera() { return camera; }

    public Vec3 cameraPosition() { return camera.getPosition(); }

    public Frustum frustum() { return frustum; }

    public float partialTick() { return partialTick; }
}
