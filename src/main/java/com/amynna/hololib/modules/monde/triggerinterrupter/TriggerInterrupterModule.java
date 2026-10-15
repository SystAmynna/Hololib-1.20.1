package com.amynna.hololib.modules.monde.triggerinterrupter;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module {@code hololib:trigger_interrupter} (memoire de position, marker) : une fenetre ({@link
 * TriggerInterrupterWindow}) avec un titre, un bouton et un texte facultatif en dessous, qui execute des commandes
 * serveur selon ce que fait le joueur sur le bouton. Visible et utilisable par tous (sauf si « OP requis »).
 * <p>
 * <b>Commandes</b> : onze emplacements, chacun une <b>liste</b> de commandes executees dans l'ordre (vide = ignore). Pour chacun des trois clics ({@link
 * #BUTTON_LEFT} gauche, {@link #BUTTON_RIGHT} droit, {@link #BUTTON_MIDDLE} molette) : {@link #TRIGGER_CLICK} a
 * l'appui, {@link #TRIGGER_RELEASE} au relachement, {@link #TRIGGER_HOLD} <b>chaque tick tant que le clic est
 * maintenu</b> (le tick de l'appui compris) ; plus {@link #SLOT_SCROLL_UP} et {@link #SLOT_SCROLL_DOWN} (un cran de molette =
 * une execution). {@link #slot} donne l'emplacement d'un clic et d'un declencheur.
 * <p>
 * <b>Etat</b> (sauvegarde et synchronise) : titre, texte du bouton et texte du dessous ({@link #MAX_TEXT} caracteres),
 * « OP requis » ({@link #requireOp()}, defaut oui : seul un operateur niveau {@value TriggerOps#OP_LEVEL} peut agir),
 * et les onze listes de commandes. Modifie seulement par l'API serveur publique ({@link #setText}, {@link #setRequireOp},
 * {@link #addCommand}, {@link #removeCommand}, {@link #editCommand}, {@link #moveCommand}), appelee par le Trigger Interrupter Configurator du meme marker (operateurs). Les commandes
 * voyagent dans l'etat synchronise : les fenetres operateurs les lisent ; un client modifie peut les lire aussi.
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}, premier octet = type), verifiees cote serveur : source = la fenetre,
 * joueur a moins de {@value #RANGE} blocs de la fenetre dans son niveau, puis droit d'agir ({@link #requireOp}) :
 * <pre>
 * 0 DOWN   + byte clic         appui : commande OnClick, debut du maintien
 * 1 UP     + byte clic         relachement : commande OnRelease, fin du maintien
 * 2 BEAT   + byte masque       battement tant qu'un clic est maintenu (le serveur ne le croit que s'il est recu)
 * 3 SCROLL + boolean haut      un cran de molette
 * </pre>
 * Les commandes executees sont <b>toujours lues dans l'etat serveur</b>, jamais dans une action. <b>Maintien</b> :
 * un joueur qui ne bat plus depuis {@value #HOLD_TIMEOUT} ticks, qui se deconnecte, change de dimension, s'eloigne ou
 * perd le droit d'agir voit ses clics maintenus relaches d'office (commande OnRelease, s'il est encore joignable) : une
 * commande de maintien ne se repete jamais sans fin si son client disparait.
 * <p>
 * <b>Source</b> des commandes : le joueur ({@code @s}, sa position), permission {@value TriggerCommands#PERMISSION}.
 */
public final class TriggerInterrupterModule extends HoloModule
{
    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int TRIGGER_CLICK = 0;
    public static final int TRIGGER_RELEASE = 1;
    public static final int TRIGGER_HOLD = 2;
    /** Emplacement des commandes de molette. */
    public static final int SLOT_SCROLL_UP = 9;
    public static final int SLOT_SCROLL_DOWN = 10;
    public static final int SLOT_COUNT = 11;

    public static final int FIELD_TITLE = 0;
    public static final int FIELD_BUTTON = 1;
    public static final int FIELD_FOOTER = 2;
    /** Longueur maximale des textes de la fenetre. */
    public static final int MAX_TEXT = 64;
    public static final String DEFAULT_TITLE = "Interrupteur";
    public static final String DEFAULT_BUTTON = "Bouton";

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_BEAT = 2;
    public static final int ACTION_SCROLL = 3;

    /** Distance maximale entre le joueur et la fenetre, en blocs. */
    public static final double RANGE = 32.0;
    /** Ticks sans nouvelle d'un maintien avant relachement d'office. */
    public static final int HOLD_TIMEOUT = 40;
    private static final Component OPS_ONLY = Component.literal("Reserve aux operateurs");

    private static final Map<HoloScope, TriggerInterrupterModule> SERVER_MODULES = new ConcurrentHashMap<>();
    private static final Map<HoloScope, TriggerInterrupterModule> CLIENT_MODULES = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    private String title = DEFAULT_TITLE;
    private String buttonText = DEFAULT_BUTTON;
    private String footer = "";
    private boolean requireOp = true;
    /** Commandes de chaque emplacement, dans l'ordre d'execution ; listes immuables remplacees. */
    @SuppressWarnings("unchecked")
    private final List<String>[] commands = new List[SLOT_COUNT];

    // ---------------------------------------------------------------- deux cotes

    private TriggerInterrupterWindow window;
    /** Compteur de changements d'etat (serveur : a chaque modification ; client : a chaque reception). */
    private int revision;

    // ---------------------------------------------------------------- serveur

    /** Clics maintenus d'un joueur : un bit par bouton, et dernier tick ou le client a donne signe de vie. */
    private static final class Held
    {
        int mask;
        long lastSeen;
    }

    private final TriggerCommands runner = new TriggerCommands();
    private final Map<UUID, Held> held = new HashMap<>();
    private long ticks;

    public TriggerInterrupterModule()
    {
        Arrays.fill(commands, TriggerCommands.NONE);
    }

    /** Emplacement de la commande du clic {@code button} pour le declencheur {@code trigger}. */
    public static int slot(int button, int trigger) { return button * 3 + trigger; }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new TriggerInterrupterWindow("window", this));
        modules(host()).put(host().scope(), this);
        window.showState();
    }

    @Override
    public void onUnload()
    {
        modules(host()).remove(host().scope(), this);
        held.clear();
    }

    private static Map<HoloScope, TriggerInterrupterModule> modules(HoloModuleHost host)
    {
        return host.clientSide() ? CLIENT_MODULES : SERVER_MODULES;
    }

    /**
     * Trigger Interrupter de la meme memoire de position que {@code module} (meme marker, meme cote), {@code null}
     * s'il n'y en a pas (utilise par le Trigger Interrupter Configurator, deux cotes). Serveur : confirme par le
     * conteneur du marker. Sans allocation.
     */
    public static TriggerInterrupterModule of(HoloModule module)
    {
        HoloModuleHost host = module.host();
        if (host == null || !(host.scope() instanceof HoloScope.Position position)) return null;
        TriggerInterrupterModule found = modules(host).get(position);
        if (found == null || found.host().clientSide()) return found;
        PositionHoloContainer container = HoloContainers.position(position.markerId());
        return container != null && container.module(found.instanceId()) == found ? found : null;
    }

    /** Identifiant du marker qui porte le module. */
    public String markerId() { return ((HoloScope.Position) host().scope()).markerId(); }

    // ---------------------------------------------------------------- sauvegarde

    @Override
    public void save(CompoundTag tag)
    {
        tag.putString("title", title);
        tag.putString("button", buttonText);
        tag.putString("footer", footer);
        tag.putBoolean("requireOp", requireOp);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ListTag list = new ListTag();
            for (String command : commands[i]) list.add(StringTag.valueOf(command));
            tag.put("c" + i, list);
        }
    }

    /** Lecture tolerante : valeur absente ou invalide = defaut. */
    @Override
    public void load(CompoundTag tag)
    {
        title = tag.contains("title") ? text(tag.getString("title")) : DEFAULT_TITLE;
        buttonText = tag.contains("button") ? text(tag.getString("button")) : DEFAULT_BUTTON;
        footer = text(tag.getString("footer"));
        requireOp = !tag.contains("requireOp") || tag.getBoolean("requireOp");
        for (int i = 0; i < SLOT_COUNT; i++) {
            List<String> raw = new ArrayList<>();
            if (tag.contains("c" + i, Tag.TAG_LIST)) {
                ListTag list = tag.getList("c" + i, Tag.TAG_STRING);
                for (int k = 0; k < list.size(); k++) raw.add(list.getString(k));
            } else {
                // Sauvegarde d'avant les listes : une seule commande.
                raw.add(tag.getString("c" + i));
            }
            commands[i] = TriggerCommands.sanitizeAll(raw);
        }
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(buttonText, MAX_TEXT);
        buf.writeUtf(footer, MAX_TEXT);
        buf.writeBoolean(requireOp);
        for (List<String> list : commands) {
            buf.writeVarInt(list.size());
            for (String command : list) buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        }
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        title = text(buf.readUtf(MAX_TEXT));
        buttonText = text(buf.readUtf(MAX_TEXT));
        footer = text(buf.readUtf(MAX_TEXT));
        requireOp = buf.readBoolean();
        for (int i = 0; i < SLOT_COUNT; i++) {
            int count = Math.min(buf.readVarInt(), TriggerCommands.MAX_COMMANDS);
            List<String> raw = new ArrayList<>(count);
            for (int k = 0; k < count; k++) raw.add(buf.readUtf(TriggerCommands.MAX_LENGTH));
            commands[i] = TriggerCommands.sanitizeAll(raw);
        }
        revision++;
    }

    /** Textes de la fenetre relus (client). */
    @Override
    public void onSync()
    {
        if (window != null) window.showState();
    }

    /** Texte de fenetre : une seule ligne, borne (le texte vide reste vide : ni titre ni defaut). */
    private static String text(String value)
    {
        String clean = com.amynna.hololib.holo.widget.control.HoloTextBuffer.sanitize(value, MAX_TEXT, 1);
        return clean.strip();
    }

    // ---------------------------------------------------------------- serveur : actions

    /** Joueur dans la portee de la fenetre, dans le niveau du marker. */
    private boolean inRange(ServerPlayer player)
    {
        PositionHoloContainer container = HoloContainers.position(markerId());
        if (container == null || container.entity() == null || player.level() != container.level()) return false;
        HoloTransform t = window.transform();
        Vec3 center = container.entity().position().add(t.x(), t.y(), t.z());
        return player.distanceToSqr(center) <= RANGE * RANGE;
    }

    private boolean allowed(ServerPlayer player) { return !requireOp || player.hasPermissions(TriggerOps.OP_LEVEL); }

    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (source == null || source != window) return;
        int action = buf.readByte();
        switch (action) {
            case ACTION_DOWN -> {
                int button = buf.readByte();
                if (button < 0 || button > BUTTON_MIDDLE || !inRange(player)) return;
                if (!allowed(player)) {
                    player.displayClientMessage(OPS_ONLY, true);
                    return;
                }
                Held entry = held.computeIfAbsent(player.getUUID(), id -> new Held());
                int bit = 1 << button;
                if ((entry.mask & bit) != 0) return;
                entry.mask |= bit;
                entry.lastSeen = ticks;
                run(player, slot(button, TRIGGER_CLICK));
            }
            case ACTION_UP -> {
                int button = buf.readByte();
                Held entry = held.get(player.getUUID());
                if (button < 0 || button > BUTTON_MIDDLE || entry == null || (entry.mask & 1 << button) == 0) return;
                entry.mask &= ~(1 << button);
                if (entry.mask == 0) held.remove(player.getUUID());
                run(player, slot(button, TRIGGER_RELEASE));
            }
            case ACTION_BEAT -> {
                buf.readByte();
                Held entry = held.get(player.getUUID());
                if (entry != null) entry.lastSeen = ticks;
            }
            case ACTION_SCROLL -> {
                boolean up = buf.readBoolean();
                if (!inRange(player)) return;
                if (!allowed(player)) {
                    player.displayClientMessage(OPS_ONLY, true);
                    return;
                }
                run(player, up ? SLOT_SCROLL_UP : SLOT_SCROLL_DOWN);
            }
            default -> {
            }
        }
    }

    private void run(ServerPlayer player, int slot)
    {
        runner.runAll(player.server, player.createCommandSourceStack(), commands[slot]);
    }

    /** Commande « chaque tick » des clics maintenus ; relachement d'office des maintiens qui ne sont plus valides. */
    @Override
    public void serverTick(ServerLevel level)
    {
        runner.beginTick();
        ticks++;
        if (held.isEmpty()) return;
        MinecraftServer server = level.getServer();
        // Copie : une commande peut retirer le module ou modifier l'etat pendant le parcours.
        List<Map.Entry<UUID, Held>> entries = new ArrayList<>(held.entrySet());
        for (Map.Entry<UUID, Held> entry : entries) {
            Held state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean valid = player != null && !player.hasDisconnected() && ticks - state.lastSeen <= HOLD_TIMEOUT
                    && inRange(player) && allowed(player);
            if (!valid) {
                held.remove(entry.getKey());
                if (player != null && !player.hasDisconnected()) {
                    for (int button = 0; button <= BUTTON_MIDDLE; button++) {
                        if ((state.mask & 1 << button) != 0) run(player, slot(button, TRIGGER_RELEASE));
                    }
                }
                continue;
            }
            for (int button = 0; button <= BUTTON_MIDDLE; button++) {
                if ((state.mask & 1 << button) != 0) run(player, slot(button, TRIGGER_HOLD));
            }
        }
    }

    // ---------------------------------------------------------------- API serveur (Configurator)

    private void changed()
    {
        revision++;
        markDirty();
    }

    /** Serveur : texte de la fenetre ({@link #FIELD_TITLE}, {@link #FIELD_BUTTON} ou {@link #FIELD_FOOTER}). */
    public void setText(int field, String value)
    {
        String clean = text(value);
        switch (field) {
            case FIELD_TITLE -> {
                if (clean.equals(title)) return;
                title = clean;
            }
            case FIELD_BUTTON -> {
                if (clean.equals(buttonText)) return;
                buttonText = clean;
            }
            case FIELD_FOOTER -> {
                if (clean.equals(footer)) return;
                footer = clean;
            }
            default -> {
                return;
            }
        }
        changed();
    }

    /** Serveur : seul un operateur peut agir sur le bouton. */
    public void setRequireOp(boolean requireOp)
    {
        if (requireOp == this.requireOp) return;
        this.requireOp = requireOp;
        changed();
    }

    /** Serveur : ajoute {@code command} a la fin de la liste de {@code slot} (vide ou liste pleine : sans effet). */
    public void addCommand(int slot, String command)
    {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        replace(slot, TriggerCommands.with(commands[slot], command));
    }

    /** Serveur : retire l'element {@code index} de la liste de {@code slot} s'il vaut {@code expected}. */
    public void removeCommand(int slot, int index, String expected)
    {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        replace(slot, TriggerCommands.without(commands[slot], index, expected));
    }

    /** Serveur : remplace l'element {@code index} de la liste de {@code slot} par {@code text} s'il vaut {@code expected}. */
    public void editCommand(int slot, int index, String expected, String text)
    {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        replace(slot, TriggerCommands.replaced(commands[slot], index, expected, text));
    }

    /** Serveur : deplace l'element {@code from} de la liste de {@code slot} a la place {@code to}. */
    public void moveCommand(int slot, int from, int to)
    {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        replace(slot, TriggerCommands.moved(commands[slot], from, to));
    }

    private void replace(int slot, List<String> next)
    {
        if (next == commands[slot]) return;
        commands[slot] = next;
        changed();
    }

    // ---------------------------------------------------------------- demandes du client (bouton)

    void requestDown(int button) { sendTyped(ACTION_DOWN, buf -> buf.writeByte(button)); }

    void requestUp(int button) { sendTyped(ACTION_UP, buf -> buf.writeByte(button)); }

    void requestBeat(int heldMask) { sendTyped(ACTION_BEAT, buf -> buf.writeByte(heldMask)); }

    void requestScroll(boolean up) { sendTyped(ACTION_SCROLL, buf -> buf.writeBoolean(up)); }

    private void sendTyped(int action, java.util.function.Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            content.accept(buf);
        });
    }

    // ---------------------------------------------------------------- lecture

    public String title() { return title; }

    public String buttonText() { return buttonText; }

    public String footer() { return footer; }

    public boolean requireOp() { return requireOp; }

    /** Commandes de l'emplacement {@code slot}, dans l'ordre d'execution (liste immuable). */
    public List<String> commands(int slot) { return commands[slot]; }

    /** Compteur de changements d'etat : la fenetre du Configurator se met a jour quand il bouge. */
    public int revision() { return revision; }

    /** Fenetre de ce module. */
    public TriggerInterrupterWindow window() { return window; }
}
