package com.amynna.hololib.modules.monde.screen;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module {@code hololib:screen} (memoire de position, marker) : un ecran holographique ({@link ScreenDisplay}) montre
 * le rendu d'une zone comme une camera de surveillance, rendu par chaque client qui le regarde avec ses propres
 * reglages graphiques (vue secondaire du framework, {@code client.render.levelview}). Visible par tous.
 * <p>
 * <b>Etat</b> (sauvegarde et synchronise) : mode ({@link Mode#POSITION} camera fixe : position, lacet, tangage,
 * roulis ; {@link Mode#PLAYER} regard d'un joueur : UUID et nom), FOV vertical, resolution de l'image, cadence maximale
 * et distance de vue (chunks, 0 = celle du spectateur) : les trois fixent le cout du rendu chez chaque spectateur, la
 * distance en premier. Modifie seulement par l'API serveur publique ({@link #setMode}, {@link #setPosition},
 * {@link #setAngles}, {@link #setFov}, {@link #setTarget}, {@link #setResolution}, {@link #setMaxFps},
 * {@link #setViewDistance}), appelee par le Screen Configurator du meme marker (operateurs) ; le module n'a aucune
 * action. Nouvelle installation : camera fixe sur le marker, regard vers le sud.
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}) :
 * <pre>
 * byte mode, 3 double position, 3 float lacet / tangage / roulis, float FOV, boolean cible [+ UUID + utf nom],
 * varint largeur, varint hauteur, varint images/s, varint distance de vue (chunks)
 * </pre>
 * Cote client, le {@link Backend} cree la vue a la creation du miroir, la tient a jour a chaque tick et la ferme a
 * son retrait.
 */
public final class ScreenModule extends HoloModule
{
    /** Mode de la camera. */
    public enum Mode
    {
        /** Camera fixe : position, orientation, FOV. */
        POSITION,
        /** Camera dans le regard d'un joueur. */
        PLAYER
    }

    public static final float MIN_FOV = 30f;
    public static final float MAX_FOV = 110f;
    public static final float DEFAULT_FOV = 70f;
    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 1024;
    public static final int DEFAULT_WIDTH = 320;
    public static final int DEFAULT_HEIGHT = 180;
    public static final int MIN_FPS = 1;
    public static final int MAX_FPS = 30;
    public static final int DEFAULT_FPS = 20;
    /** Distance de vue en chunks : 0 = celle du spectateur (jamais plus). */
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int DEFAULT_VIEW_DISTANCE = 4;
    /** Borne horizontale de la position (bord du monde). */
    public static final double MAX_HORIZONTAL = 30_000_000.0;
    /** Borne verticale de la position (large : la camera peut etre hors de la hauteur du monde). */
    public static final double MAX_VERTICAL = 20_000.0;
    /** Longueur maximale d'un nom de joueur. */
    public static final int MAX_NAME = 16;
    /** Hauteur des yeux de la camera posee sur le marker a l'installation. */
    private static final double DEFAULT_EYE = 1.6;
    /** Ecart relatif tolere entre les echelles X et Y de l'ecran avant de les uniformiser. */
    private static final float UNIFORM_EPSILON = 1e-4f;

    private static final Map<HoloScope, ScreenModule> SERVER_SCREENS = new ConcurrentHashMap<>();
    private static final Map<HoloScope, ScreenModule> CLIENT_SCREENS = new ConcurrentHashMap<>();

    private static Backend backend;

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Miroir cree : vue du niveau ouverte. */
        void attach(ScreenModule module);

        /** Chaque tick client : parametres de la vue relus (revision), joueur suivi. */
        void tick(ScreenModule module);

        /** Miroir retire : vue fermee. */
        void detach(ScreenModule module);
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        ScreenModule.backend = Objects.requireNonNull(backend);
    }

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    private Mode mode = Mode.POSITION;
    /** Faux tant que la camera n'a pas ete posee (installation : placee sur le marker au premier tick serveur). */
    private boolean placed;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float roll;
    private float fov = DEFAULT_FOV;
    private UUID target;
    private String targetName = "";
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private int maxFps = DEFAULT_FPS;
    private int viewDistance = DEFAULT_VIEW_DISTANCE;

    // ---------------------------------------------------------------- deux cotes

    private ScreenDisplay display;
    /** Compteur de changements d'etat (serveur : a chaque modification ; client : a chaque reception). */
    private int revision;
    private final HoloTransform lastDisplay = new HoloTransform();
    private boolean displayKnown;

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        display = addObject(new ScreenDisplay("display"));
        screens(host()).put(host().scope(), this);
        if (host().clientSide() && backend != null) backend.attach(this);
    }

    @Override
    public void onUnload()
    {
        screens(host()).remove(host().scope(), this);
        if (host().clientSide() && backend != null) backend.detach(this);
    }

    @Override
    public void clientTick()
    {
        if (backend != null) backend.tick(this);
    }

    private static Map<HoloScope, ScreenModule> screens(HoloModuleHost host)
    {
        return host.clientSide() ? CLIENT_SCREENS : SERVER_SCREENS;
    }

    /**
     * Screen de la meme memoire de position que {@code module} (meme marker, meme cote), {@code null} s'il n'y en a
     * pas (utilise par le Screen Configurator, deux cotes). Serveur : confirme par le conteneur du marker. Sans
     * allocation.
     */
    public static ScreenModule of(HoloModule module)
    {
        HoloModuleHost host = module.host();
        if (host == null || !(host.scope() instanceof HoloScope.Position position)) return null;
        ScreenModule screen = screens(host).get(position);
        if (screen == null || screen.host().clientSide()) return screen;
        PositionHoloContainer container = HoloContainers.position(position.markerId());
        return container != null && container.module(screen.instanceId()) == screen ? screen : null;
    }

    /** Identifiant du marker qui porte le module. */
    public String markerId() { return ((HoloScope.Position) host().scope()).markerId(); }

    // ---------------------------------------------------------------- sauvegarde

    @Override
    public void save(CompoundTag tag)
    {
        tag.putString("mode", mode.name());
        tag.putBoolean("placed", placed);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putFloat("roll", roll);
        tag.putFloat("fov", fov);
        if (target != null) {
            tag.putUUID("target", target);
            tag.putString("targetName", targetName);
        }
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putInt("fps", maxFps);
        tag.putInt("viewDistance", viewDistance);
    }

    /** Lecture tolerante : valeur invalide = defaut. */
    @Override
    public void load(CompoundTag tag)
    {
        mode = "PLAYER".equals(tag.getString("mode")) ? Mode.PLAYER : Mode.POSITION;
        placed = tag.getBoolean("placed");
        x = clampHorizontal(tag.getDouble("x"));
        y = clampVertical(tag.getDouble("y"));
        z = clampHorizontal(tag.getDouble("z"));
        yaw = Mth.wrapDegrees(finite(tag.getFloat("yaw")));
        pitch = clampPitch(tag.getFloat("pitch"));
        roll = Mth.wrapDegrees(finite(tag.getFloat("roll")));
        fov = tag.contains("fov", Tag.TAG_FLOAT) ? clampFov(tag.getFloat("fov")) : DEFAULT_FOV;
        target = tag.hasUUID("target") ? tag.getUUID("target") : null;
        targetName = target != null ? clampName(tag.getString("targetName")) : "";
        width = tag.contains("width", Tag.TAG_INT) ? clampSize(tag.getInt("width")) : DEFAULT_WIDTH;
        height = tag.contains("height", Tag.TAG_INT) ? clampSize(tag.getInt("height")) : DEFAULT_HEIGHT;
        maxFps = tag.contains("fps", Tag.TAG_INT) ? Mth.clamp(tag.getInt("fps"), MIN_FPS, MAX_FPS) : DEFAULT_FPS;
        viewDistance = tag.contains("viewDistance", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("viewDistance"), 0, MAX_VIEW_DISTANCE) : DEFAULT_VIEW_DISTANCE;
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(mode.ordinal());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(roll);
        buf.writeFloat(fov);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target);
            buf.writeUtf(targetName, MAX_NAME);
        }
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeVarInt(maxFps);
        buf.writeVarInt(viewDistance);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        mode = buf.readByte() == Mode.PLAYER.ordinal() ? Mode.PLAYER : Mode.POSITION;
        x = clampHorizontal(buf.readDouble());
        y = clampVertical(buf.readDouble());
        z = clampHorizontal(buf.readDouble());
        yaw = Mth.wrapDegrees(finite(buf.readFloat()));
        pitch = clampPitch(buf.readFloat());
        roll = Mth.wrapDegrees(finite(buf.readFloat()));
        fov = clampFov(buf.readFloat());
        if (buf.readBoolean()) {
            target = buf.readUUID();
            targetName = clampName(buf.readUtf(MAX_NAME));
        } else {
            target = null;
            targetName = "";
        }
        width = clampSize(buf.readVarInt());
        height = clampSize(buf.readVarInt());
        maxFps = Mth.clamp(buf.readVarInt(), MIN_FPS, MAX_FPS);
        viewDistance = Mth.clamp(buf.readVarInt(), 0, MAX_VIEW_DISTANCE);
        revision++;
    }

    // ---------------------------------------------------------------- serveur

    /** Pose de la camera a l'installation, echelle de l'ecran gardee uniforme. */
    @Override
    public void serverTick(ServerLevel level)
    {
        if (!placed) {
            PositionHoloContainer container = HoloContainers.position(markerId());
            if (container != null && container.entity() != null) {
                Vec3 pos = container.entity().position();
                placed = true;
                x = clampHorizontal(pos.x);
                y = clampVertical(pos.y + DEFAULT_EYE);
                z = clampHorizontal(pos.z);
                changed();
            }
        }
        pollDisplay();
    }

    /** Echelles X et Y de l'ecran ramenees a leur moyenne si une edition les a separees (image non deformee). */
    private void pollDisplay()
    {
        HoloTransform t = display.transform();
        if (displayKnown && sameTransform(t, lastDisplay)) return;
        float mean = (t.scaleX() + t.scaleY()) / 2f;
        float tolerance = UNIFORM_EPSILON * mean;
        if (Math.abs(t.scaleX() - mean) > tolerance || Math.abs(t.scaleY() - mean) > tolerance
                || Math.abs(t.scaleZ() - mean) > tolerance) {
            t.setScale(mean);
        }
        lastDisplay.set(t);
        displayKnown = true;
    }

    private static boolean sameTransform(HoloTransform a, HoloTransform b)
    {
        return a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.yaw() == b.yaw() && a.pitch() == b.pitch()
                && a.roll() == b.roll() && a.scaleX() == b.scaleX() && a.scaleY() == b.scaleY()
                && a.scaleZ() == b.scaleZ();
    }

    private void changed()
    {
        revision++;
        markDirty();
    }

    // ---------------------------------------------------------------- API serveur (Configurator)

    /** Serveur : mode de la camera. */
    public void setMode(Mode mode)
    {
        if (mode == null || mode == this.mode) return;
        this.mode = mode;
        changed();
    }

    /** Serveur : position de la camera fixe (oeil), bornee. */
    public void setPosition(double x, double y, double z)
    {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        x = clampHorizontal(x);
        y = clampVertical(y);
        z = clampHorizontal(z);
        placed = true;
        if (x == this.x && y == this.y && z == this.z) return;
        this.x = x;
        this.y = y;
        this.z = z;
        changed();
    }

    /** Serveur : orientation de la camera fixe, en degres (tangage borne a +-90). */
    public void setAngles(float yaw, float pitch, float roll)
    {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) return;
        yaw = Mth.wrapDegrees(yaw);
        pitch = clampPitch(pitch);
        roll = Mth.wrapDegrees(roll);
        if (yaw == this.yaw && pitch == this.pitch && roll == this.roll) return;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        changed();
    }

    /** Serveur : FOV vertical, borne. */
    public void setFov(float fov)
    {
        if (!Float.isFinite(fov)) return;
        fov = clampFov(fov);
        if (fov == this.fov) return;
        this.fov = fov;
        changed();
    }

    /** Serveur : joueur suivi en mode {@link Mode#PLAYER} ; {@code null} = aucun. */
    public void setTarget(UUID target, String name)
    {
        String clamped = target != null ? clampName(name) : "";
        if (Objects.equals(target, this.target) && clamped.equals(targetName)) return;
        this.target = target;
        this.targetName = clamped;
        changed();
    }

    /** Serveur : resolution de l'image, bornee. */
    public void setResolution(int width, int height)
    {
        width = clampSize(width);
        height = clampSize(height);
        if (width == this.width && height == this.height) return;
        this.width = width;
        this.height = height;
        changed();
    }

    /** Serveur : cadence maximale, bornee. */
    public void setMaxFps(int fps)
    {
        fps = Mth.clamp(fps, MIN_FPS, MAX_FPS);
        if (fps == maxFps) return;
        maxFps = fps;
        changed();
    }

    /** Serveur : distance de vue en chunks (0 = celle du spectateur), bornee. */
    public void setViewDistance(int chunks)
    {
        chunks = Mth.clamp(chunks, 0, MAX_VIEW_DISTANCE);
        if (chunks == viewDistance) return;
        viewDistance = chunks;
        changed();
    }

    // ---------------------------------------------------------------- lecture

    public Mode mode() { return mode; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public float roll() { return roll; }

    public float fov() { return fov; }

    /** Joueur suivi, {@code null} sans cible. */
    public UUID target() { return target; }

    /** Nom du joueur suivi (vide sans cible). */
    public String targetName() { return targetName; }

    public int width() { return width; }

    public int height() { return height; }

    public int maxFps() { return maxFps; }

    /** Distance de vue en chunks, 0 = celle du spectateur. */
    public int viewDistance() { return viewDistance; }

    /** Compteur de changements d'etat : a comparer pour relire l'etat. */
    public int revision() { return revision; }

    /** Objet ecran du module. */
    public ScreenDisplay display() { return display; }

    // ---------------------------------------------------------------- bornes

    private static float finite(float value) { return Float.isFinite(value) ? value : 0f; }

    private static double clampHorizontal(double value)
    {
        return Double.isFinite(value) ? Mth.clamp(value, -MAX_HORIZONTAL, MAX_HORIZONTAL) : 0.0;
    }

    private static double clampVertical(double value)
    {
        return Double.isFinite(value) ? Mth.clamp(value, -MAX_VERTICAL, MAX_VERTICAL) : 0.0;
    }

    private static float clampPitch(float value) { return Mth.clamp(finite(value), -90f, 90f); }

    private static float clampFov(float value)
    {
        return Float.isFinite(value) ? Mth.clamp(value, MIN_FOV, MAX_FOV) : DEFAULT_FOV;
    }

    private static int clampSize(int value) { return Mth.clamp(value, MIN_SIZE, MAX_SIZE); }

    private static String clampName(String name)
    {
        if (name == null) return "";
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }
}
