package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.modules.camera.quickaction.QuickAction;
import com.amynna.hololib.modules.camera.quickaction.QuickActionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Demonstration de la compatibilite avec Quick Action ({@link QuickActionProvider}) : trois actions dans la roue,
 * chacune avec une icone dessinee en quadrilateres. Le clic envoie l'indice de l'action au module serveur, qui
 * diffuse le message dans le chat comme si le joueur l'avait ecrit : "bonjour", son pseudo, ou "au revoir".
 */
public final class DemoQuickActionsModule extends HoloModule implements QuickActionProvider
{
    private static final HoloColor FACE = HoloColor.action(0xFF);
    private static final HoloColor FEATURES = HoloColor.action(0xFF).darker(0.82f);
    private static final HoloColor PERSON = HoloColor.primary(0xFF).lighter(0.51f);
    private static final HoloColor DOOR = HoloColor.action(0xFF).darker(0.3f);
    private static final HoloColor ARROW = HoloColor.critical(0xFF).lighter(0.05f);

    private static final byte HELLO = 0;
    private static final byte TEST = 1;
    private static final byte BYE = 2;
    private static final int SEGMENTS = 16;

    private List<QuickAction> actions = List.of();

    /** Envoye aux observateurs : ses actions apparaissent dans la roue vue par un joueur qui inspecte. */
    @Override
    public boolean sharedWithObservers() { return true; }

    @Override
    public void onLoad()
    {
        actions = List.of(
                QuickAction.of("hello", Component.literal("[DEMO] Bonjour"), DemoQuickActionsModule::drawSmile,
                        () -> say(HELLO)),
                QuickAction.of("test", Component.literal("[DEMO] Test"), DemoQuickActionsModule::drawPerson,
                        () -> say(TEST)),
                QuickAction.of("bye", Component.literal("[DEMO] Au revoir"), DemoQuickActionsModule::drawExit,
                        () -> say(BYE)));
    }

    @Override
    public List<QuickAction> quickActions() { return actions; }

    private void say(byte message) { sendAction(buf -> buf.writeByte(message)); }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        String text = switch (buf.readByte()) {
            case HELLO -> "bonjour";
            case TEST -> player.getGameProfile().getName();
            case BYE -> "au revoir";
            default -> null;
        };
        if (text == null) return;
        player.server.getPlayerList().broadcastChatMessage(PlayerChatMessage.unsigned(player.getUUID(), text), player,
                ChatType.bind(ChatType.CHAT, player));
    }

    // ---------------------------------------------------------------- icones (carre size x size)

    /** Visage souriant. */
    private static void drawSmile(HoloCanvas canvas, float size)
    {
        float c = size / 2f;
        disk(canvas, c, c, 0.48f * size, canvas.color(FACE));
        canvas.fill(0.32f * size, 0.3f * size, 0.1f * size, 0.14f * size, canvas.color(FEATURES));
        canvas.fill(0.58f * size, 0.3f * size, 0.1f * size, 0.14f * size, canvas.color(FEATURES));
        arc(canvas, c, 0.5f * size, 0.22f * size, 0.3f * size, 0.15f * Mth.PI, 0.85f * Mth.PI, canvas.color(FEATURES));
    }

    /** Silhouette : tete et epaules. */
    private static void drawPerson(HoloCanvas canvas, float size)
    {
        float c = size / 2f;
        disk(canvas, c, 0.3f * size, 0.2f * size, canvas.color(PERSON));
        arc(canvas, c, size, 0f, 0.42f * size, Mth.PI, Mth.TWO_PI, canvas.color(PERSON));
    }

    /** Porte et fleche de sortie. */
    private static void drawExit(HoloCanvas canvas, float size)
    {
        canvas.outline(0.08f * size, 0.08f * size, 0.42f * size, 0.84f * size, Math.max(1f, 0.07f * size),
                canvas.color(DOOR));
        float y = size / 2f;
        float shaft = 0.08f * size;
        canvas.fill(0.3f * size, y - shaft, 0.4f * size, 2f * shaft, canvas.color(ARROW));
        canvas.quad(0.68f * size, y - 0.22f * size, 0.95f * size, y, 0.95f * size, y, 0.68f * size, y + 0.22f * size,
                canvas.color(ARROW));
    }

    private static void disk(HoloCanvas canvas, float cx, float cy, float r, int color)
    {
        arc(canvas, cx, cy, 0f, r, 0f, Mth.TWO_PI, color);
    }

    /** Couronne entre les rayons {@code r0} et {@code r1}, des angles {@code a0} a {@code a1} ({@code y} vers le bas). */
    private static void arc(HoloCanvas canvas, float cx, float cy, float r0, float r1, float a0, float a1, int color)
    {
        for (int i = 0; i < SEGMENTS; i++) {
            float b0 = a0 + (a1 - a0) * i / SEGMENTS;
            float b1 = a0 + (a1 - a0) * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(b0);
            float s0 = Mth.sin(b0);
            float c1 = Mth.cos(b1);
            float s1 = Mth.sin(b1);
            canvas.quad(cx + r0 * c0, cy + r0 * s0, cx + r1 * c0, cy + r1 * s0, cx + r1 * c1, cy + r1 * s1,
                    cx + r0 * c1, cy + r0 * s1, color);
        }
    }
}
