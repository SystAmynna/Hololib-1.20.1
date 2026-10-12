package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Demonstration de la compatibilite avec Config ({@link Configurable}) : une jauge au HUD, en haut a gauche, reglee
 * depuis la fenetre de configuration du module (interrupteur d'activation et niveau). L'etat est sauvegarde et
 * synchronise ; les widgets l'envoient au serveur ({@code sendAction}) et le relisent dans {@link #onSync}.
 */
public final class DemoConfigurableModule extends HoloModule implements Configurable
{
    private static final int GAUGE_WIDTH = 120;
    private static final int BAR_WIDTH = GAUGE_WIDTH - 8;
    private static final int CONFIG_WIDTH = 150;
    private static final byte ACTION_ENABLED = 0;
    private static final byte ACTION_LEVEL = 1;

    private boolean enabled = true;
    private float level = 0.5f;
    private ConfigWindow config;
    private HoloWidgetSwitch enabledSwitch;
    private HoloWidgetValueBar levelBar;
    private HoloLabel gaugeLabel;
    private HoloPanel gaugeFill;

    @Override
    public void onLoad()
    {
        DemoEditableWindow gauge = addObject(new DemoEditableWindow("gauge", new HoloAnchor.Hud(0f, 0f),
                HoloTransform.translation(70f, 24f, 0f).setScale(160f), GAUGE_WIDTH, 30, 1f / 160f));
        gauge.root().setPattern(0x80).setBorder(HoloColor.action(0xFF), 1f);
        gaugeLabel = gauge.root().add(new HoloLabel(4, 3, GAUGE_WIDTH - 8, 12, Component.empty()));
        gauge.root().add(new HoloPanel(4, 18, BAR_WIDTH, 7).setBackground(HoloColor.secondary(0x60).darker(0.75f)));
        gaugeFill = gauge.root().add(new HoloPanel(4, 18, 0, 7).setBackground(HoloColor.action(0xC0)));

        config = addObject(new ConfigWindow("config", Component.literal("Demo configurable"),
                HoloTransform.translation(26f, 0f, 0.7f), CONFIG_WIDTH, 50, 1f / 400f));
        enabledSwitch = config.content().add(new HoloWidgetSwitch(6, 6, CONFIG_WIDTH - 12, 16,
                Component.literal("Jauge active"), (widget, on) -> requestEnabled(on)));
        levelBar = config.content().add(new HoloWidgetValueBar(6, 28, CONFIG_WIDTH - 12, 14,
                Component.literal("Niveau"), (widget, value) -> requestLevel(value)));
        refresh();
    }

    @Override
    public Component configName() { return Component.literal("Demo configurable"); }

    @Override
    public ConfigWindow configWindow() { return config; }

    private void requestEnabled(boolean on)
    {
        enabled = on;
        refresh();
        sendAction(buf -> buf.writeByte(ACTION_ENABLED).writeBoolean(on));
    }

    private void requestLevel(float value)
    {
        level = value;
        refresh();
        sendAction(buf -> buf.writeByte(ACTION_LEVEL).writeFloat(value));
    }

    /** Widgets d'apres l'etat (deux cotes). */
    private void refresh()
    {
        enabledSwitch.setOn(enabled);
        levelBar.setValue(level);
        gaugeLabel.setText(Component.literal(enabled ? "Niveau : " + Math.round(level * 100f) + " %" : "Desactivee"));
        gaugeLabel.setColor(enabled ? HoloColor.special(0xFF) : HoloColor.special(0xFF).darker(0.5f));
        gaugeFill.setBounds(gaugeFill.x(), gaugeFill.y(), enabled ? BAR_WIDTH * level : 0f, gaugeFill.height());
    }

    @Override
    public void onSync() { refresh(); }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        switch (buf.readByte()) {
            case ACTION_ENABLED -> enabled = buf.readBoolean();
            case ACTION_LEVEL -> {
                float value = buf.readFloat();
                if (!Float.isFinite(value)) return;
                level = Mth.clamp(value, 0f, 1f);
            }
            default -> {
                return;
            }
        }
        markDirty();
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putBoolean("enabled", enabled);
        tag.putFloat("level", level);
    }

    @Override
    public void load(CompoundTag tag)
    {
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        level = tag.contains("level") ? Mth.clamp(tag.getFloat("level"), 0f, 1f) : 0.5f;
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeBoolean(enabled);
        buf.writeFloat(level);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        enabled = buf.readBoolean();
        level = buf.readFloat();
    }
}
