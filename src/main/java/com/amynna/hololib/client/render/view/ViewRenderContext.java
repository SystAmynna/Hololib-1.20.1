package com.amynna.hololib.client.render.view;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Donnees de la frame pour un {@link ViewRenderer}. Objet reutilise : ne pas le garder au-dela de l'appel. */
public final class ViewRenderContext
{
    private Camera camera;
    private float partialTick;
    private final Matrix4f cameraRotation = new Matrix4f();
    private final Matrix3f cameraNormal = new Matrix3f();
    private Matrix4fc projection;
    private float fov;
    private float aspect;

    ViewRenderContext() {}

    void set(Camera camera, float partialTick, Matrix4fc cameraRotation, Matrix3fc cameraNormal, float fov, float aspect)
    {
        this.camera = camera;
        this.partialTick = partialTick;
        this.cameraRotation.set(cameraRotation);
        this.cameraNormal.set(cameraNormal);
        this.fov = fov;
        this.aspect = aspect;
    }

    void projection(Matrix4fc projection) { this.projection = projection; }

    public Camera camera() { return camera; }

    public float partialTick() { return partialTick; }

    /** Rotation monde vers vue de la camera de rendu (roll compris), telle que vanilla l'utilise pour le monde. */
    public Matrix4fc cameraRotation() { return cameraRotation; }

    /** Projection utilisee pour ce renderer (voir {@link ViewRenderer#projection()}). */
    public Matrix4fc projection() { return projection; }

    /** FOV vertical effectif du jeu, en degres (sprint, potions et mods compris). */
    public float fov() { return fov; }

    /** Largeur / hauteur de la fenetre. */
    public float aspect() { return aspect; }

    /** Passe la pile de l'espace vue a un repere oriente comme le monde, centre sur l'oeil. */
    public void applyCameraRotation(PoseStack pose)
    {
        pose.last().pose().mul(cameraRotation);
        pose.last().normal().mul(cameraNormal);
    }
}
