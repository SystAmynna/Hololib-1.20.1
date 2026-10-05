package com.amynna.hololib.client.render.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;

/** Donnees de la frame pour un {@link SkyRenderer}. Objet reutilise : ne pas le garder au-dela de l'appel. */
public final class SkyRenderContext
{
    /** Distance des objets celestes vanilla (soleil, lune), en blocs. */
    public static final float SKY_RADIUS = 100f;

    private ClientLevel level;
    private Camera camera;
    private float partialTick;
    private float timeOfDay;
    private float rainLevel;
    private float starBrightness;
    private boolean skyVisible;
    private float farPlane;

    SkyRenderContext() {}

    void set(ClientLevel level, Camera camera, float partialTick, boolean skyVisible, float farPlane)
    {
        this.level = level;
        this.camera = camera;
        this.partialTick = partialTick;
        this.timeOfDay = level.getTimeOfDay(partialTick);
        this.rainLevel = level.getRainLevel(partialTick);
        this.starBrightness = level.getStarBrightness(partialTick);
        this.skyVisible = skyVisible;
        this.farPlane = farPlane;
    }

    public ClientLevel level() { return level; }

    public Camera camera() { return camera; }

    public float partialTick() { return partialTick; }

    /** Avancement du cycle jour/nuit, 0..1 (0 = midi). */
    public float timeOfDay() { return timeOfDay; }

    /** Intensite de la pluie, 0..1 ; vanilla attenue soleil et lune par {@code 1 - rainLevel}. */
    public float rainLevel() { return rainLevel; }

    /** Luminosite des etoiles vanilla, 0..1 (hors pluie). */
    public float starBrightness() { return starBrightness; }

    /**
     * Faux quand vanilla ne dessine pas le ciel : dimension sans ciel (Nether), camera dans la lave ou la neige
     * poudreuse, cecite ou obscurite, brouillard epais.
     */
    public boolean skyVisible() { return skyVisible; }

    /** Plan lointain de la projection, en blocs (128 au minimum) : rien n'est dessine au-dela. */
    public float farPlane() { return farPlane; }

    /** Rotation celeste vanilla : apres elle, le soleil est en {@code +Y} et la lune en {@code -Y}. */
    public void applyCelestialRotation(PoseStack pose)
    {
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360f));
    }
}
