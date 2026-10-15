package com.amynna.hololib.modules.monde.triggerarea;

import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module {@code hololib:trigger_area} (memoire de position, marker) : une zone ({@link TriggerAreaZone}), editable avec
 * le World Editor, qui execute des commandes quand une entite y <b>entre</b>, d'autres quand elle en <b>sort</b>, et
 * d'autres <b>a chaque tick</b> pour chaque entite dedans. Une liste vide est ignoree (la boucle du tick n'existe
 * meme pas sans commande de tick).
 * <p>
 * <b>Etat</b> (sauvegarde et synchronise) : trois <b>listes</b> de commandes (entree, sortie, tick : {@value
 * TriggerCommands#MAX_COMMANDS} au plus chacune, {@link TriggerCommands#MAX_LENGTH} caracteres, executees dans
 * l'ordre), les
 * options {@link #visible()} (zone dessinee), {@link #players()} (joueurs, defaut oui), {@link #living()} (autres
 * entites vivantes hors porte-armures, defaut non) et {@link #opOnly()} (« Est OP » : seuls les joueurs operateurs,
 * niveau {@value #OP_LEVEL}, declenchent la zone ; defaut non). Modifie seulement par l'API serveur publique ({@link #addCommand},
 * {@link #removeCommand}, {@link #editCommand}, {@link #moveCommand}, {@link #setVisible}, {@link #setPlayers}, {@link #setLiving}, {@link #setOpOnly}), appelee par le Trigger Area Configurator du meme
 * marker (operateurs) ; le module n'a aucune action. Les commandes voyagent dans l'etat synchronise : les fenetres
 * operateurs les lisent ; elles ne sont montrees qu'aux operateurs, mais un client modifie peut les lire.
 * <p>
 * <b>Detection</b> (serveur, chaque tick, relative au transform courant de la zone, relu a chaque tick : aucune
 * poignee) : une entite est dedans si le centre de sa boite de collision est dans le pave oriente (repere local de
 * la zone, demi-aretes = echelle de chaque axe, bornee a {@link TriggerAreaZone#MAX_HALF}). Au plus
 * {@value #MAX_TRACKED} entites suivies. Ordre dans un tick : sorties, entrees, puis commande de tick de toutes les
 * entites dedans (celles qui viennent d'entrer comprises).
 * <p>
 * <b>Commandes</b> : source = l'entite qui entre / sort ({@code @s}, sa position), permission
 * {@value TriggerCommands#PERMISSION}. Une entite morte, dechargee, ou partie dans une autre dimension (deconnexion
 * comprise) est oubliee <b>sans</b> commande de sortie (pas de source valide) ; au rechargement du marker, les entites
 * deja dedans declenchent de nouveau l'entree.
 */
public final class TriggerAreaModule extends HoloModule
{
    public static final int KIND_ENTER = 0;
    public static final int KIND_EXIT = 1;
    public static final int KIND_TICK = 2;
    /** Entites suivies au plus. */
    public static final int MAX_TRACKED = 64;
    private static final String[] KEYS = {"enter", "exit", "tick"};
    /** Cles (chaines) d'avant les listes. */
    private static final String[] OLD_KEYS = {"onEnter", "onExit", "onTick"};
    /** Niveau de permission d'un operateur (option « Est OP »). */
    public static final int OP_LEVEL = 2;

    private static final Map<HoloScope, TriggerAreaModule> SERVER_AREAS = new ConcurrentHashMap<>();
    private static final Map<HoloScope, TriggerAreaModule> CLIENT_AREAS = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    /** Commandes de chaque declencheur ({@code KIND_*}), dans l'ordre d'execution ; listes immuables remplacees. */
    @SuppressWarnings("unchecked")
    private final List<String>[] lists = new List[3];
    private boolean visible;
    private boolean players = true;
    private boolean living;
    private boolean opOnly;

    // ---------------------------------------------------------------- deux cotes

    private TriggerAreaZone zone;
    /** Compteur de changements d'etat (serveur : a chaque modification ; client : a chaque reception). */
    private int revision;

    // ---------------------------------------------------------------- serveur

    private final TriggerCommands commands = new TriggerCommands();
    private final Set<UUID> inside = new HashSet<>();
    private final Map<UUID, Entity> now = new HashMap<>();
    private final Quaternionf inverse = new Quaternionf();
    private final Vector3f local = new Vector3f();

    public TriggerAreaModule()
    {
        Arrays.fill(lists, TriggerCommands.NONE);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        zone = addObject(new TriggerAreaZone("zone"));
        areas(host()).put(host().scope(), this);
    }

    @Override
    public void onUnload()
    {
        areas(host()).remove(host().scope(), this);
        inside.clear();
        now.clear();
    }

    private static Map<HoloScope, TriggerAreaModule> areas(HoloModuleHost host)
    {
        return host.clientSide() ? CLIENT_AREAS : SERVER_AREAS;
    }

    /**
     * Trigger Area de la meme memoire de position que {@code module} (meme marker, meme cote), {@code null} s'il n'y
     * en a pas (utilise par le Trigger Area Configurator, deux cotes). Serveur : confirme par le conteneur du marker.
     * Sans allocation.
     */
    public static TriggerAreaModule of(HoloModule module)
    {
        HoloModuleHost host = module.host();
        if (host == null || !(host.scope() instanceof HoloScope.Position position)) return null;
        TriggerAreaModule area = areas(host).get(position);
        if (area == null || area.host().clientSide()) return area;
        PositionHoloContainer container = HoloContainers.position(position.markerId());
        return container != null && container.module(area.instanceId()) == area ? area : null;
    }

    /** Identifiant du marker qui porte le module. */
    public String markerId() { return ((HoloScope.Position) host().scope()).markerId(); }

    // ---------------------------------------------------------------- sauvegarde

    @Override
    public void save(CompoundTag tag)
    {
        for (int kind = 0; kind < lists.length; kind++) {
            ListTag list = new ListTag();
            for (String command : lists[kind]) list.add(StringTag.valueOf(command));
            tag.put(KEYS[kind], list);
        }
        tag.putBoolean("visible", visible);
        tag.putBoolean("players", players);
        tag.putBoolean("living", living);
        tag.putBoolean("opOnly", opOnly);
    }

    /** Lecture tolerante : valeur absente ou invalide = defaut. */
    @Override
    public void load(CompoundTag tag)
    {
        for (int kind = 0; kind < lists.length; kind++) {
            List<String> raw = new ArrayList<>();
            if (tag.contains(KEYS[kind], Tag.TAG_LIST)) {
                ListTag list = tag.getList(KEYS[kind], Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) raw.add(list.getString(i));
            } else {
                // Sauvegarde d'avant les listes : une seule commande.
                raw.add(tag.getString(OLD_KEYS[kind]));
            }
            lists[kind] = TriggerCommands.sanitizeAll(raw);
        }
        visible = tag.getBoolean("visible");
        players = !tag.contains("players") || tag.getBoolean("players");
        living = tag.getBoolean("living");
        opOnly = tag.getBoolean("opOnly");
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        for (List<String> list : lists) {
            buf.writeVarInt(list.size());
            for (String command : list) buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        }
        buf.writeBoolean(visible);
        buf.writeBoolean(players);
        buf.writeBoolean(living);
        buf.writeBoolean(opOnly);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        for (int kind = 0; kind < lists.length; kind++) {
            int count = Math.min(buf.readVarInt(), TriggerCommands.MAX_COMMANDS);
            List<String> raw = new ArrayList<>(count);
            for (int i = 0; i < count; i++) raw.add(buf.readUtf(TriggerCommands.MAX_LENGTH));
            lists[kind] = TriggerCommands.sanitizeAll(raw);
        }
        visible = buf.readBoolean();
        players = buf.readBoolean();
        living = buf.readBoolean();
        opOnly = buf.readBoolean();
        revision++;
    }

    // ---------------------------------------------------------------- serveur : detection

    @Override
    public void serverTick(ServerLevel level)
    {
        commands.beginTick();
        if (lists[KIND_ENTER].isEmpty() && lists[KIND_EXIT].isEmpty() && lists[KIND_TICK].isEmpty()) {
            // Aucune commande : rien a declencher, aucune detection.
            inside.clear();
            return;
        }
        PositionHoloContainer container = HoloContainers.position(markerId());
        if (container == null || container.entity() == null) {
            inside.clear();
            return;
        }
        detect(level, container.entity().position());
        if (inside.isEmpty() && now.isEmpty()) return;
        MinecraftServer server = level.getServer();

        // Sorties : suivie mais plus dedans (entite introuvable, morte ou partie ailleurs : oubliee sans commande).
        for (Iterator<UUID> it = inside.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (now.containsKey(id)) continue;
            it.remove();
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                commands.runAll(server, entity.createCommandSourceStack(), lists[KIND_EXIT]);
            }
        }
        // Entrees.
        for (Map.Entry<UUID, Entity> entry : now.entrySet()) {
            if (inside.add(entry.getKey())) {
                commands.runAll(server, entry.getValue().createCommandSourceStack(), lists[KIND_ENTER]);
            }
        }
        // Tick : pas de boucle sans commande.
        if (!lists[KIND_TICK].isEmpty()) {
            for (Entity entity : now.values()) {
                commands.runAll(server, entity.createCommandSourceStack(), lists[KIND_TICK]);
            }
        }
        now.clear();
    }

    /** Remplit {@link #now} : entites eligibles dont le centre est dans la zone (au plus {@value #MAX_TRACKED}). */
    private void detect(ServerLevel level, Vec3 origin)
    {
        now.clear();
        HoloTransform t = zone.transform();
        float hx = TriggerAreaZone.half(t.scaleX());
        float hy = TriggerAreaZone.half(t.scaleY());
        float hz = TriggerAreaZone.half(t.scaleZ());
        double cx = origin.x + t.x();
        double cy = origin.y + t.y();
        double cz = origin.z + t.z();
        HoloEditMath.rotation(inverse, t).conjugate();
        if (players) {
            for (ServerPlayer player : level.players()) {
                if (now.size() >= MAX_TRACKED) return;
                if (!player.isSpectator() && player.isAlive() && (!opOnly || player.hasPermissions(OP_LEVEL))
                        && contains(player, cx, cy, cz, hx, hy, hz)) {
                    now.put(player.getUUID(), player);
                }
            }
        }
        // « Est OP » : une entite qui n'est pas un joueur n'est jamais operateur.
        if (living && !opOnly) {
            // Boite englobante de la zone orientee (sphere de rayon la demi-diagonale).
            double r = Math.sqrt((double) hx * hx + (double) hy * hy + (double) hz * hz);
            AABB bounds = new AABB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds,
                    e -> e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand))) {
                if (now.size() >= MAX_TRACKED) return;
                if (contains(entity, cx, cy, cz, hx, hy, hz)) now.put(entity.getUUID(), entity);
            }
        }
    }

    /** Centre de la boite de {@code entity} dans le pave centre en {@code (cx, cy, cz)} (rotation inverse prete). */
    private boolean contains(Entity entity, double cx, double cy, double cz, float hx, float hy, float hz)
    {
        AABB box = entity.getBoundingBox();
        local.set((float) (box.minX + (box.maxX - box.minX) * 0.5 - cx),
                (float) (box.minY + (box.maxY - box.minY) * 0.5 - cy),
                (float) (box.minZ + (box.maxZ - box.minZ) * 0.5 - cz));
        inverse.transform(local);
        return Math.abs(local.x) <= hx && Math.abs(local.y) <= hy && Math.abs(local.z) <= hz;
    }

    private void changed()
    {
        revision++;
        markDirty();
    }

    // ---------------------------------------------------------------- API serveur (Configurator)

    /** Serveur : ajoute {@code text} a la fin de la liste {@code kind} (vide ou liste pleine : sans effet). */
    public void addCommand(int kind, String text)
    {
        if (kind < 0 || kind >= lists.length) return;
        replace(kind, TriggerCommands.with(lists[kind], text));
    }

    /** Serveur : retire l'element {@code index} de la liste {@code kind} s'il vaut {@code expected}. */
    public void removeCommand(int kind, int index, String expected)
    {
        if (kind < 0 || kind >= lists.length) return;
        replace(kind, TriggerCommands.without(lists[kind], index, expected));
    }

    /** Serveur : remplace l'element {@code index} de la liste {@code kind} par {@code text} s'il vaut {@code expected}. */
    public void editCommand(int kind, int index, String expected, String text)
    {
        if (kind < 0 || kind >= lists.length) return;
        replace(kind, TriggerCommands.replaced(lists[kind], index, expected, text));
    }

    /** Serveur : deplace l'element {@code from} de la liste {@code kind} a la place {@code to}. */
    public void moveCommand(int kind, int from, int to)
    {
        if (kind < 0 || kind >= lists.length) return;
        replace(kind, TriggerCommands.moved(lists[kind], from, to));
    }

    private void replace(int kind, List<String> next)
    {
        if (next == lists[kind]) return;
        lists[kind] = next;
        changed();
    }

    /** Serveur : zone dessinee ou non. */
    public void setVisible(boolean visible)
    {
        if (visible == this.visible) return;
        this.visible = visible;
        changed();
    }

    /** Serveur : les joueurs declenchent la zone. */
    public void setPlayers(boolean players)
    {
        if (players == this.players) return;
        this.players = players;
        changed();
    }

    /** Serveur : les autres entites vivantes declenchent la zone. */
    public void setLiving(boolean living)
    {
        if (living == this.living) return;
        this.living = living;
        changed();
    }

    /** Serveur : seuls les joueurs operateurs declenchent la zone. */
    public void setOpOnly(boolean opOnly)
    {
        if (opOnly == this.opOnly) return;
        this.opOnly = opOnly;
        changed();
    }

    // ---------------------------------------------------------------- lecture

    /** Commandes du declencheur {@code kind} ({@code KIND_*}), dans l'ordre d'execution (liste immuable). */
    public List<String> commands(int kind) { return lists[kind]; }

    public boolean visible() { return visible; }

    public boolean players() { return players; }

    public boolean living() { return living; }

    public boolean opOnly() { return opOnly; }

    /** Compteur de changements d'etat : la fenetre du Configurator se met a jour quand il bouge. */
    public int revision() { return revision; }

    /** Zone de ce module. */
    public TriggerAreaZone zone() { return zone; }
}
