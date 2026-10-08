package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Quick Action : roue d'actions au centre de l'ecran a l'ouverture du mode focus. Les actions viennent des modules
 * {@link QuickActionProvider} de la memoire du joueur ; on vise un secteur en tournant la camera, un clic gauche ou
 * droit l'execute, puis la roue se masque (et le focus se ferme si "Laisser le focus active" est desactive).
 * <p>
 * Configurable ({@link Configurable}) : roue affichee, masquage si ignoree (le regard s'eloigne sans rien choisir),
 * focus laisse actif, taille de la roue et de la zone morte, et la liste des actions (activables, reordonnables au
 * glisser). Etat sauvegarde, synchronise au proprietaire ; les widgets l'envoient au serveur ({@code sendAction}) et
 * le relisent dans {@link #onSync}. Ordre et activation sont gardes par cle d'action
 * ({@code <type du module>/<id>}), meme pour un module retire puis remis.
 */
public final class QuickActionModule extends HoloModule implements Configurable
{
    private static final int MAX_KEYS = 128;
    private static final int MAX_KEY_LENGTH = 256;
    private static final float MIN_DEAD_RATIO = 0.05f;
    private static final float MAX_DEAD_RATIO = 0.7f;
    private static final byte ACTION_SIZE = 0;
    private static final byte ACTION_DEAD_ZONE = 1;
    private static final byte ACTION_WHEEL = 2;
    private static final byte ACTION_DISMISS = 3;
    private static final byte ACTION_KEEP_FOCUS = 4;
    private static final byte ACTION_ORDER = 5;
    private static final byte ACTION_TOGGLE = 6;
    private static final int CONFIG_WIDTH = 170;
    private static final int MARGIN = 6;
    private static final int LIST_Y = 112;
    private static final int LIST_HEIGHT = 96;
    private static final float ROW_HEIGHT = 18f;

    private static Backend backend;

    // Etat (deux cotes).
    private float wheelSize = 0.5f;
    private float deadZone = 0.4f;
    private boolean wheelEnabled = true;
    private boolean dismissIfIgnored = true;
    private boolean keepFocus = true;
    private final List<String> order = new ArrayList<>();
    private final Set<String> disabled = new HashSet<>();

    // Objets et widgets.
    private QuickActionWheelWindow wheel;
    private ConfigWindow config;
    private HoloWidgetSwitch wheelSwitch;
    private HoloWidgetSwitch dismissSwitch;
    private HoloWidgetSwitch keepFocusSwitch;
    private HoloWidgetValueBar sizeBar;
    private HoloWidgetValueBar deadZoneBar;
    private HoloWidgetList list;

    // Cote client : actions decouvertes, puis rangees selon l'ordre du joueur (lignes paralleles).
    private final List<QuickAction> scratch = new ArrayList<>();
    private final List<QuickAction> discovered = new ArrayList<>();
    private final List<String> discoveredKeys = new ArrayList<>();
    private final List<QuickAction> actions = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<QuickActionRow> rows = new ArrayList<>();
    private final List<QuickAction> enabled = new ArrayList<>();
    private boolean orderChanged = true;
    private boolean enabledChanged = true;

    /** Partie client : fermeture du mode focus. */
    public interface Backend
    {
        /** Ferme le mode focus du joueur local. */
        void closeFocus();
    }

    /** Usage interne : branche la partie client, au setup client. */
    public static void install(Backend backend) { QuickActionModule.backend = Objects.requireNonNull(backend); }

    @Override
    public void onLoad()
    {
        wheel = addObject(new QuickActionWheelWindow("wheel", this::perform));
        config = addObject(new ConfigWindow("config", Component.literal("Quick Action"),
                HoloTransform.translation(-28f, 0f, 0.7f), CONFIG_WIDTH, LIST_Y + LIST_HEIGHT + MARGIN, 1f / 400f));
        float width = CONFIG_WIDTH - 2 * MARGIN;
        wheelSwitch = config.content().add(new HoloWidgetSwitch(MARGIN, 4, width, 16,
                Component.literal("Roue affichee"), (widget, on) -> requestFlag(ACTION_WHEEL, on)));
        dismissSwitch = config.content().add(new HoloWidgetSwitch(MARGIN, 22, width, 16,
                Component.literal("Masquer si ignoree"), (widget, on) -> requestFlag(ACTION_DISMISS, on)));
        keepFocusSwitch = config.content().add(new HoloWidgetSwitch(MARGIN, 40, width, 16,
                Component.literal("Laisser le focus active"), (widget, on) -> requestFlag(ACTION_KEEP_FOCUS, on)));
        sizeBar = config.content().add(new HoloWidgetValueBar(MARGIN, 60, width, 14,
                Component.literal("Taille de la roue"), (widget, value) -> requestValue(ACTION_SIZE, value)));
        deadZoneBar = config.content().add(new HoloWidgetValueBar(MARGIN, 78, width, 14,
                Component.literal("Zone morte"), (widget, value) -> requestValue(ACTION_DEAD_ZONE, value)));
        config.content().add(new HoloLabel(MARGIN, 96, width, 14, Component.literal("Actions (glisser pour trier)"))
                .setColor(ConfigWindow.ACCENT));
        list = config.content().add(new HoloWidgetList(MARGIN, LIST_Y, width, LIST_HEIGHT, ROW_HEIGHT));
        refresh();
    }

    @Override
    public Component configName() { return Component.literal("Quick Action"); }

    @Override
    public ConfigWindow configWindow() { return config; }

    public QuickActionWheelWindow wheel() { return wheel; }

    public boolean wheelEnabled() { return wheelEnabled; }

    public boolean dismissIfIgnored() { return dismissIfIgnored; }

    public boolean keepFocus() { return keepFocus; }

    /** Rayon de la roue, en pixels de sa fenetre. */
    private float radius()
    {
        return Mth.lerp(wheelSize, QuickActionWheelWindow.MIN_RADIUS, QuickActionWheelWindow.MAX_RADIUS);
    }

    private float deadRadius() { return radius() * Mth.lerp(deadZone, MIN_DEAD_RATIO, MAX_DEAD_RATIO); }

    // ---------------------------------------------------------------- client

    /**
     * Cote client, chaque tick : actions des modules {@link QuickActionProvider} parmi {@code modules} (memoire du
     * joueur). Lignes et secteurs ne sont reconstruits que si les actions, leur ordre ou leur activation ont change.
     */
    public void sync(Collection<HoloModule> modules)
    {
        scratch.clear();
        for (HoloModule module : modules) {
            if (!(module instanceof QuickActionProvider provider) || provider.quickActions() == null) continue;
            List<QuickAction> declared = provider.quickActions();
            for (int i = 0; i < declared.size(); i++) scratch.add(declared.get(i));
        }
        if (!scratch.equals(discovered)) discover(modules);
        if (orderChanged) arrange();
        else if (enabledChanged) applyEnabled();
    }

    private void discover(Collection<HoloModule> modules)
    {
        discovered.clear();
        discoveredKeys.clear();
        for (HoloModule module : modules) {
            if (!(module instanceof QuickActionProvider provider) || provider.quickActions() == null) continue;
            String prefix = module.type().id() + "/";
            for (QuickAction action : provider.quickActions()) {
                discovered.add(action);
                discoveredKeys.add(prefix + action.id());
            }
        }
        orderChanged = true;
    }

    /** Range les actions selon l'ordre du joueur (inconnues a la fin), puis reconstruit les lignes. */
    private void arrange()
    {
        actions.clear();
        keys.clear();
        for (String key : order) {
            int index = discoveredKeys.indexOf(key);
            if (index >= 0 && !keys.contains(key)) {
                actions.add(discovered.get(index));
                keys.add(key);
            }
        }
        for (int i = 0; i < discovered.size(); i++) {
            String key = discoveredKeys.get(i);
            if (keys.contains(key)) continue;
            actions.add(discovered.get(i));
            keys.add(key);
        }
        list.clearItems();
        rows.clear();
        for (int i = 0; i < actions.size(); i++) {
            String key = keys.get(i);
            QuickActionRow row = list.addItem(new QuickActionRow(actions.get(i), on -> requestEnabled(key, on)));
            row.setOnMove((moved, from, to) -> requestMove(from, to));
            rows.add(row);
        }
        orderChanged = false;
        applyEnabled();
    }

    /** Interrupteurs des lignes et secteurs de la roue d'apres les actions desactivees. */
    private void applyEnabled()
    {
        enabled.clear();
        for (int i = 0; i < actions.size(); i++) {
            boolean on = !disabled.contains(keys.get(i));
            rows.get(i).setOn(on);
            if (on) enabled.add(actions.get(i));
        }
        wheel.setActions(enabled);
        enabledChanged = false;
    }

    /** Cote client : clic sur un secteur de la roue ouverte. */
    private void perform(QuickAction action)
    {
        if (!wheel.isOpen()) return;
        wheel.setOpen(false);
        action.run();
        if (!keepFocus && backend != null) backend.closeFocus();
    }

    private void requestFlag(byte kind, boolean on)
    {
        switch (kind) {
            case ACTION_WHEEL -> wheelEnabled = on;
            case ACTION_DISMISS -> dismissIfIgnored = on;
            default -> keepFocus = on;
        }
        refresh();
        sendAction(buf -> buf.writeByte(kind).writeBoolean(on));
    }

    private void requestValue(byte kind, float value)
    {
        if (kind == ACTION_SIZE) wheelSize = value;
        else deadZone = value;
        refresh();
        sendAction(buf -> buf.writeByte(kind).writeFloat(value));
    }

    private void requestEnabled(String key, boolean on)
    {
        if (key.length() > MAX_KEY_LENGTH || disabled.contains(key) != on) return;
        if (on) disabled.remove(key);
        else if (disabled.size() < MAX_KEYS) disabled.add(key);
        else return;
        enabledChanged = true;
        sendAction(buf -> {
            buf.writeByte(ACTION_TOGGLE);
            buf.writeUtf(key, MAX_KEY_LENGTH);
            buf.writeBoolean(on);
        });
    }

    /** Ligne deplacee par le joueur (la liste l'a deja remise en place) : nouvel ordre, envoye en entier. */
    private void requestMove(int from, int to)
    {
        actions.add(to, actions.remove(from));
        keys.add(to, keys.remove(from));
        rows.add(to, rows.remove(from));
        // Actions connues dans l'ordre affiche, puis celles des modules absents, a leur place relative.
        List<String> next = new ArrayList<>();
        for (String key : keys) {
            if (key.length() <= MAX_KEY_LENGTH && next.size() < MAX_KEYS) next.add(key);
        }
        for (String key : order) {
            if (!next.contains(key) && next.size() < MAX_KEYS) next.add(key);
        }
        order.clear();
        order.addAll(next);
        enabledChanged = true;
        sendAction(buf -> {
            buf.writeByte(ACTION_ORDER);
            buf.writeVarInt(order.size());
            for (String key : order) buf.writeUtf(key, MAX_KEY_LENGTH);
        });
    }

    /** Widgets et geometrie de la roue d'apres l'etat (deux cotes). */
    private void refresh()
    {
        wheelSwitch.setOn(wheelEnabled);
        dismissSwitch.setOn(dismissIfIgnored);
        keepFocusSwitch.setOn(keepFocus);
        sizeBar.setValue(wheelSize);
        deadZoneBar.setValue(deadZone);
        wheel.setGeometry(radius(), deadRadius());
    }

    @Override
    public void onSync() { refresh(); }

    // ---------------------------------------------------------------- serveur

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        switch (buf.readByte()) {
            case ACTION_SIZE -> {
                float value = buf.readFloat();
                if (!Float.isFinite(value)) return;
                wheelSize = Mth.clamp(value, 0f, 1f);
            }
            case ACTION_DEAD_ZONE -> {
                float value = buf.readFloat();
                if (!Float.isFinite(value)) return;
                deadZone = Mth.clamp(value, 0f, 1f);
            }
            case ACTION_WHEEL -> wheelEnabled = buf.readBoolean();
            case ACTION_DISMISS -> dismissIfIgnored = buf.readBoolean();
            case ACTION_KEEP_FOCUS -> keepFocus = buf.readBoolean();
            case ACTION_ORDER -> {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_KEYS) return;
                List<String> next = new ArrayList<>(count);
                for (int i = 0; i < count; i++) next.add(buf.readUtf(MAX_KEY_LENGTH));
                order.clear();
                order.addAll(next);
            }
            case ACTION_TOGGLE -> {
                String key = buf.readUtf(MAX_KEY_LENGTH);
                boolean on = buf.readBoolean();
                if (on ? !disabled.remove(key) : disabled.size() >= MAX_KEYS || !disabled.add(key)) return;
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
        tag.putFloat("wheelSize", wheelSize);
        tag.putFloat("deadZone", deadZone);
        tag.putBoolean("wheelEnabled", wheelEnabled);
        tag.putBoolean("dismissIfIgnored", dismissIfIgnored);
        tag.putBoolean("keepFocus", keepFocus);
        tag.put("order", saveKeys(order));
        tag.put("disabled", saveKeys(disabled));
    }

    @Override
    public void load(CompoundTag tag)
    {
        wheelSize = tag.contains("wheelSize") ? Mth.clamp(tag.getFloat("wheelSize"), 0f, 1f) : 0.5f;
        deadZone = tag.contains("deadZone") ? Mth.clamp(tag.getFloat("deadZone"), 0f, 1f) : 0.4f;
        wheelEnabled = !tag.contains("wheelEnabled") || tag.getBoolean("wheelEnabled");
        dismissIfIgnored = !tag.contains("dismissIfIgnored") || tag.getBoolean("dismissIfIgnored");
        keepFocus = !tag.contains("keepFocus") || tag.getBoolean("keepFocus");
        loadKeys(tag.getList("order", Tag.TAG_STRING), order);
        loadKeys(tag.getList("disabled", Tag.TAG_STRING), disabled);
    }

    private static ListTag saveKeys(Collection<String> keys)
    {
        ListTag tag = new ListTag();
        for (String key : keys) tag.add(StringTag.valueOf(key));
        return tag;
    }

    private static void loadKeys(ListTag tag, Collection<String> keys)
    {
        keys.clear();
        for (int i = 0; i < tag.size() && keys.size() < MAX_KEYS; i++) {
            String key = tag.getString(i);
            if (key.length() <= MAX_KEY_LENGTH) keys.add(key);
        }
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeFloat(wheelSize);
        buf.writeFloat(deadZone);
        buf.writeBoolean(wheelEnabled);
        buf.writeBoolean(dismissIfIgnored);
        buf.writeBoolean(keepFocus);
        // Envoyes aussi aux observateurs : leur roue (publique) est rangee et filtree comme celle du proprietaire.
        writeKeys(buf, order);
        writeKeys(buf, disabled);
    }

    private static void writeKeys(FriendlyByteBuf buf, Collection<String> keys)
    {
        buf.writeVarInt(keys.size());
        for (String key : keys) buf.writeUtf(key, MAX_KEY_LENGTH);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        wheelSize = buf.readFloat();
        deadZone = buf.readFloat();
        wheelEnabled = buf.readBoolean();
        dismissIfIgnored = buf.readBoolean();
        keepFocus = buf.readBoolean();
        List<String> nextOrder = new ArrayList<>();
        readKeys(buf, nextOrder);
        Set<String> nextDisabled = new HashSet<>();
        readKeys(buf, nextDisabled);
        // Seul un vrai changement reconstruit les lignes (pas l'echo d'un deplacement deja applique).
        if (!nextOrder.equals(order)) {
            order.clear();
            order.addAll(nextOrder);
            orderChanged = true;
        }
        if (!nextDisabled.equals(disabled)) {
            disabled.clear();
            disabled.addAll(nextDisabled);
            enabledChanged = true;
        }
    }

    private static void readKeys(FriendlyByteBuf buf, Collection<String> keys)
    {
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) keys.add(buf.readUtf(MAX_KEY_LENGTH));
    }
}
