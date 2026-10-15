package com.amynna.hololib.client.render.levelview;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Vue secondaire du niveau : le monde rendu depuis une autre camera (position et angles, ou regard d'une entite) dans
 * une texture, avec les reglages graphiques du joueur local (shaderpack compris). Creee par
 * {@link HoloLevelViews#create()}, rendue au debut d'une frame par {@link HoloLevelViews} quand elle a ete vue
 * recemment ({@link #markSeen()}) et que sa cadence le permet ; fermee par {@link #close()}.
 * <p>
 * L'image se lit par {@link #texture()} (enregistree dans le {@code TextureManager}) sur le rectangle
 * {@link #u0()}..{@link #v1()} (deja recadre et retourne : {@code (u0, v0)} = coin haut gauche). Fil de rendu
 * uniquement. Les setters ne font qu'enregistrer les parametres, appliques au prochain rendu.
 */
public final class HoloLevelView
{
    /** Etat de l'image. */
    public enum Status
    {
        /** Jamais rendue (ou parametres changes avant le premier rendu). */
        WAITING,
        /** Image a jour (a la cadence pres). */
        OK,
        /** Camera indisponible : entite absente, retiree ou dans un autre niveau. Derniere image conservee. */
        NO_SIGNAL
    }

    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 1024;
    public static final int MIN_FPS = 1;
    public static final int MAX_FPS = 30;
    public static final float MIN_FOV = 10f;
    public static final float MAX_FOV = 170f;
    /** Distance de vue maximale, en chunks (0 = celle du joueur). */
    public static final int MAX_VIEW_DISTANCE = 32;

    private final ResourceLocation texture;
    private final ViewTexture textureObject = new ViewTexture();

    // ---------------------------------------------------------------- parametres
    private Entity entity;
    private UUID player;
    private boolean entityMode;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float roll;
    private float fov = 70f;
    private int width = 320;
    private int height = 180;
    private int maxFps = 20;
    private int viewDistance;

    // ---------------------------------------------------------------- etat de rendu (package-private)
    /** Cible ou la vue est rendue / copiee, et celle affichee (echangees apres chaque rendu). */
    TextureTarget render;
    TextureTarget display;
    long lastSeen = Long.MIN_VALUE / 2;
    /** Frame de {@link HoloLevelViews} du dernier {@link #markSeen} et plus grande hauteur projetee de cette frame. */
    int seenFrame = -1;
    float seenPixels;
    long lastRender = Long.MIN_VALUE / 2;
    Status status = Status.WAITING;
    boolean closed;
    float u0;
    float v0 = 1f;
    float u1 = 1f;
    float v1;

    HoloLevelView(ResourceLocation texture)
    {
        this.texture = texture;
        Minecraft.getInstance().getTextureManager().register(texture, textureObject);
    }

    // ---------------------------------------------------------------- camera

    /**
     * Camera fixe : oeil en {@code (x, y, z)} (coordonnees du monde), lacet / tangage / roulis en degres (conventions
     * de {@code Entity} : lacet 0 = sud, tangage positif = vers le bas).
     */
    public HoloLevelView setPosition(double x, double y, double z, float yaw, float pitch, float roll)
    {
        this.entityMode = false;
        this.entity = null;
        this.player = null;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = Mth.wrapDegrees(yaw);
        this.pitch = Mth.clamp(pitch, -90f, 90f);
        this.roll = Mth.wrapDegrees(roll);
        return this;
    }

    /**
     * Camera dans le regard d'une entite du niveau client (premiere personne, sans main) ; {@code null} = pas de
     * signal. L'entite doit etre relue par l'appelant si elle peut etre remplacee (reconnexion, changement de
     * dimension) ; pour un joueur, preferer {@link #setPlayer}.
     */
    public HoloLevelView setEntity(Entity entity)
    {
        this.entityMode = true;
        this.entity = entity;
        this.player = null;
        return this;
    }

    /**
     * Camera dans le regard d'un joueur, retrouve par son UUID dans le niveau client a chaque rendu : le signal revient
     * de lui-meme quand le client recree son entite (retour dans la zone suivie, reapparition, dimension).
     */
    public HoloLevelView setPlayer(UUID player)
    {
        if (!entityMode || !player.equals(this.player)) entity = null;
        this.entityMode = true;
        this.player = player;
        return this;
    }

    /** FOV vertical de l'image, en degres (borne). */
    public HoloLevelView setFov(float fov)
    {
        this.fov = Mth.clamp(fov, MIN_FOV, MAX_FOV);
        return this;
    }

    /** Resolution de l'image en pixels (bornee) : fixe aussi le cout du rendu (sauf repli, voir le package). */
    public HoloLevelView setResolution(int width, int height)
    {
        this.width = Mth.clamp(width, MIN_SIZE, MAX_SIZE);
        this.height = Mth.clamp(height, MIN_SIZE, MAX_SIZE);
        return this;
    }

    /**
     * Distance de vue en chunks (0 = celle du joueur, jamais plus) : au-dela, ni terrain, ni block entities, ni
     * entites ne sont dessines (plan lointain), et l'image se fond dans le brouillard. Premier levier de performance.
     */
    public HoloLevelView setViewDistance(int chunks)
    {
        this.viewDistance = Mth.clamp(chunks, 0, MAX_VIEW_DISTANCE);
        return this;
    }

    /** Cadence maximale, en images par seconde (bornee). */
    public HoloLevelView setMaxFps(int maxFps)
    {
        this.maxFps = Mth.clamp(maxFps, MIN_FPS, MAX_FPS);
        return this;
    }

    /** {@link #markSeen(float)} sans taille connue : cadence pleine. */
    public void markSeen() { markSeen(Float.MAX_VALUE); }

    /**
     * L'image vient d'etre dessinee, sur {@code pixels} pixels de haut a l'ecran : une vue n'est rendue que si elle a
     * ete vue recemment, et d'autant moins souvent qu'elle est petite a l'ecran (sous 12 px : plus du tout). Sans effet
     * pendant le rendu d'une vue secondaire (un ecran vu seulement a travers une camera, ou qui se filme, ne se garde
     * pas actif). Sans allocation, appelable a chaque frame depuis un renderer.
     */
    public void markSeen(float pixels)
    {
        if (HoloLevelViews.current() != null) return;
        lastSeen = Util.getNanos();
        int frame = HoloLevelViews.frame();
        if (seenFrame != frame) {
            seenFrame = frame;
            seenPixels = pixels;
        } else if (pixels > seenPixels) {
            seenPixels = pixels;
        }
    }

    /** Ferme la vue : cibles liberees, texture desenregistree. Sans effet si deja fermee. */
    public void close()
    {
        if (closed) return;
        closed = true;
        HoloLevelViews.remove(this);
        if (render != null) render.destroyBuffers();
        if (display != null) display.destroyBuffers();
        render = null;
        display = null;
        Minecraft.getInstance().getTextureManager().release(texture);
    }

    // ---------------------------------------------------------------- lecture

    /** Texture de l'image (noire tant qu'aucune image n'existe, voir {@link #hasImage()}). */
    public ResourceLocation texture() { return texture; }

    /** Vrai si au moins une image a ete rendue. */
    public boolean hasImage() { return display != null; }

    public Status status() { return status; }

    public float u0() { return u0; }

    public float v0() { return v0; }

    public float u1() { return u1; }

    public float v1() { return v1; }

    public int width() { return width; }

    public int height() { return height; }

    public int maxFps() { return maxFps; }

    /** Distance de vue en chunks (0 = celle du joueur). */
    public int viewDistance() { return viewDistance; }

    public float fov() { return fov; }

    public boolean entityMode() { return entityMode; }

    public Entity entity() { return entity; }

    /** Joueur suivi ({@link #setPlayer}), {@code null} sinon. */
    public UUID player() { return player; }

    /** Entite camera resolue par le rendu (joueur retrouve par son UUID). */
    void resolvedEntity(Entity entity) { this.entity = entity; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public float roll() { return roll; }

    public boolean closed() { return closed; }

    /**
     * Texture dont l'id est celui de la couleur de la cible affichee. Rien a charger ni a liberer : les cibles
     * appartiennent a la vue.
     */
    private final class ViewTexture extends AbstractTexture
    {
        @Override
        public void load(ResourceManager manager) {}

        @Override
        public int getId()
        {
            TextureTarget target = display;
            return target != null ? target.getColorTextureId() : 0;
        }

        @Override
        public void releaseId() {}
    }
}
