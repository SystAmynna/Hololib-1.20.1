package com.amynna.hololib.modules.monde.screenconfigurator;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.screen.ScreenDisplay;
import com.amynna.hololib.modules.monde.screen.ScreenModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:screen_configurator} (memoire de position, marker), reserve aux operateurs : reglages du
 * Screen <b>du meme marker</b> ({@link ScreenModule#of}, deux cotes ; sans Screen : « Screen requis sur ce marker »,
 * rien d'editable). Il ne porte aucun etat : le Screen reste la seule autorite, modifiee par son API serveur ; la
 * fenetre ({@link ScreenConfiguratorWindow}) relit l'etat du Screen quand son compteur de changements bouge
 * ({@link ScreenModule#revision()}).
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}, premier octet = type), verifiees cote serveur : operateur d'abord
 * (refus silencieux), source = la fenetre, Screen present :
 * <pre>
 * 0 MODE        + byte mode                 camera fixe (0) ou regard d'un joueur (1)
 * 1 TARGET      + utf nom                   joueur suivi, cherche parmi les joueurs connectes (sinon message)
 * 2 TARGET_SELF                             joueur suivi = l'operateur
 * 3 POSITION    + 3 double x, y, z          position de la camera fixe (bornee)
 * 4 ANGLES      + 3 float lacet, tangage, roulis
 * 5 FOV         + float degres              FOV vertical (borne)
 * 6 HERE                                    camera fixe = oeil et regard de l'operateur, sans roulis (meme niveau)
 * 7 RESOLUTION  + varint largeur, hauteur   resolution de l'image (bornee)
 * 8 FPS         + varint images/s           cadence maximale (bornee)
 * 9 DISTANCE    + varint chunks             distance de vue (0 = celle du spectateur)
 * </pre>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur et
 * reaffecte seulement s'il change ; fenetre dessinee seulement chez un operateur ({@code shown()}, backend).
 */
public final class ScreenConfiguratorModule extends HoloModule
{
    public static final int ACTION_MODE = 0;
    public static final int ACTION_TARGET = 1;
    public static final int ACTION_TARGET_SELF = 2;
    public static final int ACTION_POSITION = 3;
    public static final int ACTION_ANGLES = 4;
    public static final int ACTION_FOV = 5;
    public static final int ACTION_HERE = 6;
    public static final int ACTION_RESOLUTION = 7;
    public static final int ACTION_FPS = 8;
    public static final int ACTION_DISTANCE = 9;
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;
    /** Ecart entre l'ecran par defaut et la fenetre (placement par defaut), en blocs. */
    public static final float WINDOW_GAP = 0.3f;
    private static final Component NO_SCREEN = Component.literal("Screen requis sur ce marker");

    private static Backend backend;

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local est operateur (niveau {@value ScreenConfiguratorModule#OP_LEVEL}). */
        boolean localOp();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        ScreenConfiguratorModule.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    static boolean localOp() { return backend != null && backend.localOp(); }

    private ScreenConfiguratorWindow window;
    private final Set<UUID> ops = new HashSet<>();
    /** Client : Screen et revision affiches par la fenetre. */
    private ScreenModule shown;
    private int shownRevision = -1;

    /**
     * Placement par defaut de la fenetre (aussi son RESET) : a droite de l'ecran par defaut (16:9) vu de face
     * ({@code +X}), centree sur sa hauteur. Constant (les operateurs placent ensuite la fenetre au
     * {@code world_editor}).
     */
    public static HoloTransform defaultWindowTransform()
    {
        float screenHalfWidth = 0.5f * ScreenModule.DEFAULT_WIDTH / ScreenModule.DEFAULT_HEIGHT
                * ScreenDisplay.DEFAULT_SCALE;
        float x = screenHalfWidth + WINDOW_GAP
                + ScreenConfiguratorWindow.WIDTH * ScreenConfiguratorWindow.PIXEL_SCALE * 0.5f;
        return HoloTransform.translation(x, ScreenDisplay.DEFAULT_HEIGHT, 0f);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new ScreenConfiguratorWindow("window", this, defaultWindowTransform()));
        refresh();
    }

    @Override
    public void onUnload()
    {
        shown = null;
        shownRevision = -1;
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void serverTick(ServerLevel level)
    {
        updateAccess(level.getServer());
    }

    /** Acces de la fenetre : operateurs connectes ; reaffecte seulement si l'ensemble change. */
    private void updateAccess(MinecraftServer server)
    {
        ops.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(OP_LEVEL)) ops.add(player.getUUID());
        }
        HoloAccess access = window.access();
        Set<UUID> granted = access.mode() == HoloAccess.Mode.PLAYERS ? access.players() : Set.of();
        if (!granted.equals(ops)) window.setAccess(HoloAccess.players(ops));
    }

    /** Operateurs seulement (verifie en premier), source = la fenetre, Screen du marker requis. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(OP_LEVEL)) return;
        if (source == null || source != window) return;
        ScreenModule screen = ScreenModule.of(this);
        if (screen == null) {
            player.displayClientMessage(NO_SCREEN, true);
            return;
        }
        int action = buf.readByte();
        switch (action) {
            case ACTION_MODE -> screen.setMode(buf.readByte() == ScreenModule.Mode.PLAYER.ordinal()
                    ? ScreenModule.Mode.PLAYER : ScreenModule.Mode.POSITION);
            case ACTION_TARGET -> target(player, screen, buf.readUtf(ScreenModule.MAX_NAME));
            case ACTION_TARGET_SELF -> screen.setTarget(player.getUUID(), player.getGameProfile().getName());
            case ACTION_POSITION -> screen.setPosition(buf.readDouble(), buf.readDouble(), buf.readDouble());
            case ACTION_ANGLES -> screen.setAngles(buf.readFloat(), buf.readFloat(), buf.readFloat());
            case ACTION_FOV -> screen.setFov(buf.readFloat());
            case ACTION_HERE -> here(player, screen);
            case ACTION_RESOLUTION -> screen.setResolution(buf.readVarInt(), buf.readVarInt());
            case ACTION_FPS -> screen.setMaxFps(buf.readVarInt());
            case ACTION_DISTANCE -> screen.setViewDistance(buf.readVarInt());
            default -> {
            }
        }
    }

    /** Joueur connecte de ce nom (casse ignoree) ; sinon message a l'operateur. Vide = aucune cible. */
    private static void target(ServerPlayer player, ScreenModule screen, String name)
    {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            screen.setTarget(null, "");
            return;
        }
        for (ServerPlayer candidate : player.server.getPlayerList().getPlayers()) {
            if (candidate.getGameProfile().getName().equalsIgnoreCase(trimmed)) {
                screen.setTarget(candidate.getUUID(), candidate.getGameProfile().getName());
                return;
            }
        }
        player.displayClientMessage(Component.literal("Joueur inconnu : " + trimmed), true);
    }

    /** « Ici » : oeil et regard de l'operateur, s'il est dans le niveau du marker. */
    private void here(ServerPlayer player, ScreenModule screen)
    {
        PositionHoloContainer container = HoloContainers.position(screen.markerId());
        if (container == null || player.level() != container.level()) return;
        Vec3 eye = player.getEyePosition();
        screen.setPosition(eye.x, eye.y, eye.z);
        screen.setAngles(player.getYRot(), player.getXRot(), 0f);
    }

    // ---------------------------------------------------------------- client

    /** Fenetre rafraichie quand le Screen du marker ou son etat changent. */
    @Override
    public void clientTick()
    {
        refresh();
    }

    private void refresh()
    {
        if (window == null) return;
        ScreenModule screen = ScreenModule.of(this);
        int revision = screen != null ? screen.revision() : -1;
        if (screen == shown && revision == shownRevision) return;
        shown = screen;
        shownRevision = revision;
        window.showState(screen);
    }

    // ---------------------------------------------------------------- demandes du client

    void requestMode(ScreenModule.Mode mode) { sendTyped(ACTION_MODE, buf -> buf.writeByte(mode.ordinal())); }

    void requestTarget(String name) { sendTyped(ACTION_TARGET, buf -> buf.writeUtf(name, ScreenModule.MAX_NAME)); }

    void requestTargetSelf() { sendTyped(ACTION_TARGET_SELF, null); }

    void requestPosition(double x, double y, double z)
    {
        sendTyped(ACTION_POSITION, buf -> {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        });
    }

    void requestAngles(float yaw, float pitch, float roll)
    {
        sendTyped(ACTION_ANGLES, buf -> {
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
            buf.writeFloat(roll);
        });
    }

    void requestFov(float fov) { sendTyped(ACTION_FOV, buf -> buf.writeFloat(fov)); }

    void requestHere() { sendTyped(ACTION_HERE, null); }

    void requestResolution(int width, int height)
    {
        sendTyped(ACTION_RESOLUTION, buf -> {
            buf.writeVarInt(width);
            buf.writeVarInt(height);
        });
    }

    void requestFps(int fps) { sendTyped(ACTION_FPS, buf -> buf.writeVarInt(fps)); }

    void requestViewDistance(int chunks) { sendTyped(ACTION_DISTANCE, buf -> buf.writeVarInt(chunks)); }

    private void sendTyped(int action, Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            if (content != null) content.accept(buf);
        });
    }
}
