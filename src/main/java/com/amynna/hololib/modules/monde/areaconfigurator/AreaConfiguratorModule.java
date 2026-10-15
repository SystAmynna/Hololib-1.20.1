package com.amynna.hololib.modules.monde.areaconfigurator;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerCube;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:area_displayer_configurator} (memoire de position, marker), reserve aux operateurs : reglages
 * de l'Area Displayer <b>du meme marker</b> ({@link AreaDisplayerModule#of}, deux cotes ; sans Displayer : « Area
 * Displayer requis sur ce marker », rien d'editable). Il ne porte aucun etat : le Displayer reste la seule autorite,
 * modifiee par son API serveur ; la fenetre ({@link AreaConfiguratorWindow}) relit l'etat du Displayer quand son
 * compteur de changements bouge ({@link AreaDisplayerModule#revision()}).
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}, premier octet = type), verifiees cote serveur : operateur d'abord
 * (refus silencieux), source = la fenetre, Displayer present :
 * <pre>
 * 0 CORNER   + boolean B + 3 int x, y, z   coin A ou B (hauteur bornee a celle du monde)
 * 1 HERE     + boolean B                   coin A ou B = bloc du joueur
 * 2 FLAG     + byte option + boolean       option du Displayer (AreaDisplayerModule.FLAG_*)
 * 3 INTERVAL + varint secondes             intervalle de l'Auto Update (borne)
 * 4 REFRESH                                « Actualiser » : nouveau scan complet
 * 5 RESOLUTION + varint pas               resolution (un palier de AreaGrid.LEVELS)
 * 6 BLOCK_ADD + utf id                   ajoute un bloc a la liste noire (revalide ; rescan complet)
 * 7 BLOCK_REMOVE + utf id                retire un bloc de la liste noire (rescan complet)
 * 8 TRANSPARENCY + byte pourcent         transparence du terrain (0 opaque, 100 invisible)
 * </pre>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur et
 * reaffecte seulement s'il change ; fenetre dessinee seulement chez un operateur ({@code shown()}, backend).
 */
public final class AreaConfiguratorModule extends HoloModule
{
    public static final int ACTION_CORNER = 0;
    public static final int ACTION_HERE = 1;
    public static final int ACTION_FLAG = 2;
    public static final int ACTION_INTERVAL = 3;
    public static final int ACTION_REFRESH = 4;
    public static final int ACTION_RESOLUTION = 5;
    public static final int ACTION_BLOCK_ADD = 6;
    public static final int ACTION_BLOCK_REMOVE = 7;
    public static final int ACTION_TRANSPARENCY = 8;
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;
    /** Ecart entre le pave par defaut et la fenetre (placement par defaut), en blocs. */
    public static final float WINDOW_GAP = 0.3f;
    private static final Component NO_DISPLAYER = Component.literal("Area Displayer requis sur ce marker");

    private static Backend backend;

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local est operateur (niveau {@value AreaConfiguratorModule#OP_LEVEL}). */
        boolean localOp();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        AreaConfiguratorModule.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    static boolean localOp() { return backend != null && backend.localOp(); }

    private AreaConfiguratorWindow window;
    private final Set<UUID> ops = new HashSet<>();
    /** Client : Displayer et revision affiches par la fenetre. */
    private AreaDisplayerModule shown;
    private int shownRevision = -1;

    /**
     * Placement par defaut de la fenetre (aussi son RESET) : a droite du pave par defaut vu de face ({@code +X}),
     * centree sur sa hauteur. Constant (les operateurs placent ensuite la fenetre au {@code world_editor}).
     */
    public static HoloTransform defaultWindowTransform()
    {
        float x = AreaDisplayerCube.DEFAULT_SCALE + WINDOW_GAP
                + AreaConfiguratorWindow.WIDTH * AreaConfiguratorWindow.PIXEL_SCALE * 0.5f;
        return HoloTransform.translation(x, AreaDisplayerCube.DEFAULT_HEIGHT, 0f);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new AreaConfiguratorWindow("window", this, defaultWindowTransform()));
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

    /** Operateurs seulement (verifie en premier), source = la fenetre, Displayer du marker requis. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(OP_LEVEL)) return;
        if (source == null || source != window) return;
        AreaDisplayerModule displayer = AreaDisplayerModule.of(this);
        if (displayer == null) {
            player.displayClientMessage(NO_DISPLAYER, true);
            return;
        }
        PositionHoloContainer container = HoloContainers.position(displayer.markerId());
        if (container == null) return;
        ServerLevel level = container.level();
        int action = buf.readByte();
        switch (action) {
            case ACTION_CORNER -> {
                boolean second = buf.readBoolean();
                BlockPos pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                displayer.setCorner(second, pos, level);
            }
            case ACTION_HERE -> {
                boolean second = buf.readBoolean();
                if (player.level() == level) displayer.setCorner(second, player.blockPosition(), level);
            }
            case ACTION_FLAG -> {
                int flag = buf.readByte();
                displayer.setFlag(flag, buf.readBoolean());
            }
            case ACTION_INTERVAL -> displayer.setInterval(buf.readVarInt());
            case ACTION_REFRESH -> displayer.refresh();
            case ACTION_RESOLUTION -> displayer.setResolution(buf.readVarInt());
            case ACTION_BLOCK_ADD -> displayer.addBlocked(buf.readUtf(AreaDisplayerModule.MAX_BLOCK_ID));
            case ACTION_BLOCK_REMOVE -> displayer.removeBlocked(buf.readUtf(AreaDisplayerModule.MAX_BLOCK_ID));
            case ACTION_TRANSPARENCY -> displayer.setTransparency(buf.readUnsignedByte());
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- client

    /** Fenetre rafraichie quand le Displayer du marker ou son etat changent. */
    @Override
    public void clientTick()
    {
        refresh();
    }

    private void refresh()
    {
        if (window == null) return;
        AreaDisplayerModule displayer = AreaDisplayerModule.of(this);
        int revision = displayer != null ? displayer.revision() : -1;
        if (displayer == shown && revision == shownRevision) return;
        shown = displayer;
        shownRevision = revision;
        window.showState(displayer);
    }

    // ---------------------------------------------------------------- demandes du client

    /** Client : coin A ({@code second} faux) ou B aux coordonnees donnees. */
    void requestCorner(boolean second, int x, int y, int z)
    {
        sendTyped(ACTION_CORNER, buf -> {
            buf.writeBoolean(second);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
        });
    }

    /** Client : coin A ou B = bloc du joueur. */
    void requestHere(boolean second) { sendTyped(ACTION_HERE, buf -> buf.writeBoolean(second)); }

    /** Client : option {@code flag} du Displayer. */
    void requestFlag(int flag, boolean on)
    {
        sendTyped(ACTION_FLAG, buf -> {
            buf.writeByte(flag);
            buf.writeBoolean(on);
        });
    }

    /** Client : intervalle de l'Auto Update, en secondes. */
    void requestInterval(int seconds) { sendTyped(ACTION_INTERVAL, buf -> buf.writeVarInt(seconds)); }

    /** Client : resolution (un palier de {@code AreaGrid.LEVELS}). */
    void requestResolution(int step) { sendTyped(ACTION_RESOLUTION, buf -> buf.writeVarInt(step)); }

    /** Client : ajoute un bloc a la liste noire. */
    void requestBlockAdd(String id) { sendTyped(ACTION_BLOCK_ADD, buf -> buf.writeUtf(id, AreaDisplayerModule.MAX_BLOCK_ID)); }

    /** Client : retire un bloc de la liste noire. */
    void requestBlockRemove(String id)
    {
        sendTyped(ACTION_BLOCK_REMOVE, buf -> buf.writeUtf(id, AreaDisplayerModule.MAX_BLOCK_ID));
    }

    /** Client : transparence du terrain, en pour cent. */
    void requestTransparency(int percent) { sendTyped(ACTION_TRANSPARENCY, buf -> buf.writeByte(percent)); }

    /** Client : « Actualiser ». */
    void requestRefresh() { sendTyped(ACTION_REFRESH, null); }

    private void sendTyped(int action, Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            if (content != null) content.accept(buf);
        });
    }
}
