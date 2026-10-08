package com.amynna.hololib.client.edit;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.client.render.holo.ResolvedHolo;
import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.edit.HoloEditRules;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.edit.HoloEditorCapability;
import com.amynna.hololib.holo.edit.HoloEditorUi;
import com.amynna.hololib.holo.edit.HoloRotationFrame;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.network.HoloEditObjectPacket;
import com.amynna.hololib.network.HoloNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector2fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Session d'edition du joueur local (fil client), ouverte et fermee par un module (l'editor). Tant qu'elle est
 * ouverte :
 * <ul>
 *     <li>chaque objet vu, editable ({@link HoloEditRules#mayEdit} avec les capacites des modules editeurs du
 *     joueur) et proposant un outil recoit ses boutons ; un objet ancre {@code World}, seulement a moins de sa portee
 *     d'edition ({@code HoloObject.editRange()})
 *     ({@link HoloEditTarget}) ; un objet editable masque ({@code OFF} / {@code FOCUS}) est dessine ;</li>
 *     <li>avec l'edition des objets dynamiques ({@link #setDynamicEditing}, Own Dyn Editor actif ; toujours pour les
 *     objets d'interface de l'editeur {@link HoloEditorUi}), les objets {@code Local} follow du joueur (et des
 *     memoires sans proprietaire) sont <b>tenus</b> : affiches
 *     fixes dans le repere de reference (regard a l'ouverture), puis reconvertis a la fermeture ; de meme, un
 *     {@code HoloRelativeToOpen} de la memoire du joueur est place depuis sa position relative (selon son
 *     {@link HoloRotationFrame}), sauf s'il est deja place (focus ouvert a l'ouverture : il reste ou il est, dans le
 *     repere de son placement, et y reste a la fermeture) ;</li>
 *     <li>avec un hote de cadre HUD, les objets HUD quittent le HUD pour ce cadre ({@link #hudSuspended()}).</li>
 * </ul>
 * Une modification est envoyee au serveur ({@link HoloEditObjectPacket}) a la fin de chaque manipulation ; celle d'un
 * objet tenu, a la fermeture. Le placement recu du serveur pour un objet tenu ou manipule est ignore ({@link #holds}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class HoloEditSession
{
    private static final HoloEditMath MATH = new HoloEditMath();
    private static final Quaternionf IDENTITY = new Quaternionf();
    /** Repere de reference : sphere orientee comme le regard a l'ouverture. */
    private static final Quaternionf BASE = new Quaternionf();
    private static final HoloTransform TMP = new HoloTransform();
    private static final HoloAnchor.Local FIXED = new HoloAnchor.Local(false);
    private static final HoloAnchor.Local FOLLOW = new HoloAnchor.Local(true);

    private static boolean active;
    private static boolean dynamicEditing;
    private static HoloObject hudHost;
    private static HoloObject selectedHud;
    private static final Map<HoloObject, HoloEditTarget> TARGETS = new IdentityHashMap<>();
    private static final Map<HoloObject, Held> HELD = new IdentityHashMap<>();
    private static final Map<HoloObject, Boolean> SEEN = new IdentityHashMap<>();
    private static final List<HoloEditTarget> TICKING = new ArrayList<>();

    private HoloEditSession() {}

    // ---------------------------------------------------------------- cycle de vie

    public static boolean active() { return active; }

    /**
     * Ouvre la session.
     *
     * @param yaw     regard de reference (degres)
     * @param pitch   regard de reference
     * @param hudHost objet qui affiche le cadre HUD (les objets HUD quittent alors le HUD), ou {@code null}
     * @param placements regard (yaw, pitch) avec lequel un {@code HoloRelativeToOpen} deja affiche a ete place
     *                   (focus), ou {@code null} ; fonction elle-meme {@code null} si rien n'est place
     * @param dynamicEditing objets dynamiques tenus et editables ({@link #setDynamicEditing})
     */
    public static void open(float yaw, float pitch, HoloObject hudHost,
                            Function<HoloObject, ? extends Vector2fc> placements, boolean dynamicEditing)
    {
        if (active) return;
        active = true;
        HoloEditSession.dynamicEditing = dynamicEditing;
        HoloEditMath.yawPitch(BASE, yaw, pitch);
        HoloEditSession.hudHost = hudHost;
        selectedHud = null;
        holdNew(placements);
    }

    /** Ferme la session : envoie les objets tenus modifies, restaure les autres, retire les boutons. */
    public static void close()
    {
        if (!active) return;
        HoloEditTools.reset();
        for (Map.Entry<HoloObject, Held> entry : HELD.entrySet()) release(entry.getKey(), entry.getValue());
        HELD.clear();
        for (HoloEditTarget target : TARGETS.values()) target.detach();
        TARGETS.clear();
        active = false;
        hudHost = null;
        selectedHud = null;
    }

    /**
     * Edition des objets dynamiques (Own Dyn Editor actif) : ils sont tenus dans le repere du regard a l'ouverture.
     * Desactivee en cours de session, les objets dynamiques tenus sont relaches (reconvertis, envoyes s'ils ont
     * change) ; reactivee, ils sont repris au tick suivant. Les objets {@link HoloEditorUi} restent tenus.
     */
    public static void setDynamicEditing(boolean dynamicEditing)
    {
        if (!active || dynamicEditing == HoloEditSession.dynamicEditing) return;
        HoloEditSession.dynamicEditing = dynamicEditing;
        if (dynamicEditing) return;
        Iterator<Map.Entry<HoloObject, Held>> iterator = HELD.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HoloObject, Held> entry = iterator.next();
            HoloObject object = entry.getKey();
            Held held = entry.getValue();
            if (object instanceof HoloEditorUi || !held.dynamic()) continue;
            HoloEditTarget target = TARGETS.get(object);
            if (target != null && HoloEditTools.target() == target) HoloEditTools.select(null, null);
            release(object, held);
            iterator.remove();
        }
    }

    /** Vrai si le joueur local a les deux editeurs de sa memoire actifs : la bascule fixe / dynamique est proposee. */
    public static boolean bothOwnEditors()
    {
        ClientHoloContainer local = ClientHoloWorld.local();
        return local != null && HoloEditRules.bothOwn(HoloEditRules.capabilities(local.modules()));
    }

    /** Vrai si les objets HUD sont affiches dans le cadre de l'editor plutot qu'au HUD. */
    public static boolean hudSuspended() { return active && hudHost != null; }

    /** Hote du cadre HUD, ou {@code null}. */
    public static HoloObject hudHost() { return active ? hudHost : null; }

    /** Objet HUD selectionne dans le cadre (ses boutons sont a cote de l'hote du cadre), ou {@code null}. */
    public static HoloObject selectedHud() { return active ? selectedHud : null; }

    public static void selectHud(HoloObject object)
    {
        if (!active || object == selectedHud) return;
        HoloEditTarget previous = selectedHud != null ? TARGETS.get(selectedHud) : null;
        if (previous != null && HoloEditTools.target() == previous) HoloEditTools.select(null, null);
        selectedHud = object;
    }

    /**
     * En session, vrai si le joueur local peut editer l'objet : il est alors dessine quel que soit son affichage. Un
     * objet d'interface de l'editeur ({@link HoloEditorUi}) seulement s'il doit apparaitre.
     */
    public static boolean reveals(HoloObject object, HoloScope scope)
    {
        if (!active || object instanceof HoloEditorUi ui && !ui.shownInSession()) return false;
        return mayEdit(object, scope);
    }

    /** Remplace l'hote du cadre HUD ({@code null} : les objets HUD reviennent au HUD). */
    public static void setHudHost(HoloObject host)
    {
        if (!active || host == hudHost) return;
        selectHud(null);
        hudHost = host;
    }

    /** Vrai si le placement de l'objet est tenu localement (le serveur ne doit pas l'ecraser). */
    public static boolean holds(HoloObject object)
    {
        return active && HELD.containsKey(object) || HoloEditTools.manipulating(object);
    }

    /**
     * Vrai si le joueur local peut editer cet objet de la portee {@code scope} : memes regles que le serveur, avec les
     * capacites des modules editeurs de sa memoire. Capacite de l'objet (fixe, dynamique, HUD) d'apres son etat reel
     * (celui d'un objet tenu avant la session).
     */
    public static boolean mayEdit(HoloObject object, HoloScope scope)
    {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        ClientHoloContainer local = ClientHoloWorld.local();
        Set<HoloEditorCapability> capabilities = local != null ? HoloEditRules.capabilities(local.modules())
                : Set.of();
        UUID editOwner = object.module() != null ? object.module().editOwner() : null;
        Held held = active ? HELD.get(object) : null;
        HoloEditorCapability own = HoloEditRules.ownCapability(object, held != null ? held.anchor : object.anchor());
        return HoloEditRules.mayEdit(player.getUUID(), player.hasPermissions(2), scope, editOwner, object, own,
                capabilities);
    }

    // ---------------------------------------------------------------- modifications

    /**
     * Un groupe de composantes a change : pour un objet tenu, note pour la fermeture ; sinon envoi au serveur. Fin de
     * la modification tout de suite ({@code immediate}) ou apres quelques ticks sans changement (molette,
     * {@link #settle}) : les boutons de l'objet sont alors re-places, sauf pour la portee (silhouette inchangee).
     */
    static void changed(HoloObject object, HoloEditTool group, boolean immediate)
    {
        Held held = active ? HELD.get(object) : null;
        if (held != null) {
            switch (group) {
                case TRANSLATE -> held.translated = true;
                case ROTATE -> held.rotated = true;
                case SCALE -> held.scaled = true;
                case RANGE -> {}
            }
        }
        if (immediate) {
            settle(object, group != HoloEditTool.RANGE);
        } else {
            HoloEditTools.schedule(object, group != HoloEditTool.RANGE);
        }
    }

    /**
     * Fin d'une modification : envoi au serveur (sauf objet tenu, envoye a la fermeture) ; boutons re-places si
     * {@code replace}.
     */
    static void settle(HoloObject object, boolean replace)
    {
        if (!active || !HELD.containsKey(object)) send(object);
        if (!replace || !active) return;
        HoloEditTarget target = TARGETS.get(object);
        if (target != null) target.replace();
    }

    /** Affichage suivant ({@code OFF -> FOCUS -> ON}). */
    static void cycleDisplay(HoloObject object) { setDisplay(object, object.display().next()); }

    static void setDisplay(HoloObject object, HoloDisplay display)
    {
        if (!active || !(object instanceof HoloEditVisibility) || display == object.display()) return;
        object.setDisplay(display);
        Held held = HELD.get(object);
        if (held != null) {
            held.display = true;
        } else {
            send(object);
        }
    }

    /**
     * RESET d'un groupe : valeurs du transform par defaut de l'objet, dans son repere reel ; portee d'edition par
     * defaut.
     */
    static void reset(HoloObject object, HoloEditTool group)
    {
        if (!active) return;
        if (group == HoloEditTool.RANGE) {
            object.setEditRange(HoloObject.DEFAULT_EDIT_RANGE);
            changed(object, group, true);
            return;
        }
        HoloTransform transform = object.transform();
        Held held = converted(object);
        if (held != null) {
            toReal(object, held, transform, TMP);
            copy(TMP, object.defaultTransform(), group);
            toDisplayed(object, held, TMP, transform);
        } else {
            copy(transform, object.defaultTransform(), group);
        }
        changed(object, group, true);
    }

    /** Vrai si le groupe est a sa valeur par defaut (dans le repere reel de l'objet). */
    static boolean isDefault(HoloObject object, HoloEditTool group)
    {
        HoloTransform real = object.transform();
        Held held = converted(object);
        if (held != null) real = toReal(object, held, real, TMP);
        HoloTransform def = object.defaultTransform();
        return switch (group) {
            case TRANSLATE -> HoloEditRules.sameTranslation(real, def);
            case ROTATE -> MATH.sameRotation(real, def, 1e-5f);
            case SCALE -> HoloEditRules.sameScale(real, def);
            case RANGE -> Math.abs(object.editRange() - HoloObject.DEFAULT_EDIT_RANGE) < 1e-3f;
        };
    }

    /** Valeur saisie dans une case du panneau ; ignoree si elle n'est pas un nombre valide. */
    static void submit(HoloObject object, HoloEditTool group, int index, String text)
    {
        // Saisie terminee apres la fermeture (capture du clavier liberee) : l'objet est revenu a son repere reel.
        if (!active) return;
        float value;
        try {
            value = Float.parseFloat(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        if (!Float.isFinite(value)) return;
        HoloTransform t = object.transform();
        switch (group) {
            case TRANSLATE -> {
                float x = index == 0 ? value : t.x();
                float y = index == 1 ? value : t.y();
                float z = index == 2 ? value : t.z();
                TMP.set(t).setTranslation(x, y, z);
                if (!object.anchor().acceptable(TMP)) return;
                t.setTranslation(x, y, z);
            }
            case ROTATE -> t.setRotation(index == 0 ? value : t.yaw(), index == 1 ? value : t.pitch(),
                    index == 2 ? value : t.roll());
            case SCALE -> {
                if (!(value > 0f) || value > HoloTransform.MAX_SCALE) return;
                t.setScale(index == 0 ? value : t.scaleX(), index == 1 ? value : t.scaleY(),
                        index == 2 ? value : t.scaleZ());
            }
            case RANGE -> object.setEditRange(value);
        }
        changed(object, group, true);
    }

    /** Vrai si l'objet est (ou sera a la fermeture) dynamique : {@code Local} follow. */
    static boolean dynamic(HoloObject object)
    {
        Held held = HELD.get(object);
        return held != null ? held.follow : object.anchor() instanceof HoloAnchor.Local local && local.follow();
    }

    /**
     * Bascule fixe / dynamique : l'objet reste affiche fixe pendant la session ; devenu dynamique, il est tenu et
     * converti dans le repere de reference a la fermeture.
     */
    static void setDynamic(HoloObject object, boolean dynamic)
    {
        if (!active) return;
        Held held = HELD.get(object);
        if (held == null) {
            if (!dynamic) return;
            held = new Held(object.anchor(), object.transform().copy(), null);
            HELD.put(object, held);
        }
        held.follow = dynamic;
    }

    /**
     * Repere de la rotation d'un {@code HoloRelativeToOpen} : l'objet affiche ne bouge pas, sa position relative sera
     * recalculee dans le nouveau repere a la fermeture.
     */
    static void setRotationFrame(HoloObject object, HoloRotationFrame frame)
    {
        if (!active || object.openOffset() == null || frame == object.rotationFrame()) return;
        object.setRotationFrame(frame);
        Held held = HELD.get(object);
        if (held != null) {
            held.rotated = true;
            held.frame = true;
        } else {
            send(object);
        }
    }

    // ---------------------------------------------------------------- tick

    /** Apres le routage du tick ({@code ClientHoloWorld}, priorite normale). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        if (mc.player == null || mc.level == null) {
            close();
            return;
        }
        dropDead();
        holdNew(null);
        refreshTargets();
        HoloEditTools.tick();
    }

    /** Oublie les objets tenus qui ont disparu (module retire, memoire oubliee). */
    private static void dropDead()
    {
        HELD.keySet().removeIf(object -> !alive(object));
    }

    /**
     * Tient les objets follow et {@code HoloRelativeToOpen} pas encore tenus (nouveaux, ou a l'ouverture) ;
     * {@code placements} : voir {@link #open}.
     */
    private static void holdNew(Function<HoloObject, ? extends Vector2fc> placements)
    {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        UUID local = player.getUUID();
        for (ClientHoloContainer container : ClientHoloWorld.containers()) {
            UUID owner = container.scope().owner();
            if (owner != null && !owner.equals(local)) continue;
            for (HoloModule module : container.modules()) {
                for (HoloObject object : module.objects()) {
                    if (!object.replicated() || HELD.containsKey(object)) continue;
                    if (!dynamicEditing && !(object instanceof HoloEditorUi)) continue;
                    if (object.openOffset() != null && owner != null) {
                        hold(object, true, placements != null ? placements.apply(object) : null);
                    } else if (object.anchor() instanceof HoloAnchor.Local anchor && anchor.follow()) {
                        hold(object, false, null);
                    }
                }
            }
        }
    }

    /** @param placement regard de placement d'un {@code HoloRelativeToOpen} deja affiche, ou {@code null} */
    private static void hold(HoloObject object, boolean offset, Vector2fc placement)
    {
        HoloTransform transform = object.transform();
        Held held = new Held(object.anchor(), transform.copy(), offset ? object.openOffset().copy() : null);
        held.follow = !offset;
        HELD.put(object, held);
        if (offset && placement != null && FIXED.equals(object.anchor())) {
            // Deja place : il reste ou il est, sa position relative est dans le repere de son placement.
            held.placed = true;
            HoloEditMath.yawPitch(held.base, placement.x(), placement.y());
            return;
        }
        if (offset) {
            MATH.fromOffset(object.openOffset(), BASE, object.rotationFrame(), transform);
        } else {
            MATH.rebaseLocal(transform, BASE, IDENTITY, transform);
        }
        object.setAnchor(FIXED);
    }

    /**
     * Fin de session pour un objet tenu : etat reel recalcule, envoye s'il a change, sinon restaure. Un
     * {@code HoloRelativeToOpen} deja place garde sa position affichee.
     */
    private static void release(HoloObject object, Held held)
    {
        if (!alive(object)) return;
        HoloTransform current = object.transform();
        if (held.offset != null) {
            HoloTransform offset = held.offset.copy();
            merge(offset, toReal(object, held, current, TMP), held, true);
            if (held.placed) {
                object.openOffset().set(offset);
                if (held.edited()) send(object, FIXED, current, offset);
                return;
            }
            object.setAnchor(held.anchor);
            current.set(held.original);
            object.openOffset().set(offset);
            if (held.edited()) send(object, held.anchor, current, offset);
            return;
        }
        boolean wasFollow = held.anchor instanceof HoloAnchor.Local local && local.follow();
        if (held.follow) {
            HoloTransform real = held.original.copy();
            merge(real, MATH.rebaseLocal(current, IDENTITY, held.base, TMP), held, wasFollow);
            object.setAnchor(FOLLOW);
            current.set(real);
            if (held.edited() || !wasFollow) send(object, FOLLOW, current, null);
        } else {
            object.setAnchor(FIXED);
            if (held.edited() || wasFollow) send(object, FIXED, current, null);
        }
    }

    /**
     * Recopie dans {@code dest} les groupes modifies depuis {@code converted} ; tous si {@code sameFrame} est faux
     * ({@code dest} n'est alors pas dans le repere de {@code converted}).
     */
    private static void merge(HoloTransform dest, HoloTransform converted, Held held, boolean sameFrame)
    {
        if (held.translated || !sameFrame) copy(dest, converted, HoloEditTool.TRANSLATE);
        if (held.rotated || !sameFrame) copy(dest, converted, HoloEditTool.ROTATE);
        if (held.scaled || !sameFrame) copy(dest, converted, HoloEditTool.SCALE);
    }

    private static void refreshTargets()
    {
        SEEN.clear();
        collect(HoloRouting.view());
        collect(HoloRouting.world());
        collect(HoloRouting.sky());
        if (selectedHud != null) {
            ResolvedHolo resolved = HoloRouting.resolved(selectedHud);
            if (hudHost != null && resolved != null && HoloRouting.hudPreview().contains(resolved)
                    && HoloEditRules.hasTools(selectedHud) && mayEdit(selectedHud, selectedHud.module().scope())) {
                SEEN.put(selectedHud, Boolean.TRUE);
                TARGETS.computeIfAbsent(selectedHud, object -> HoloEditTarget.create(object, hudHost, true))
                        .attach();
            } else {
                selectedHud = null;
            }
        }
        Iterator<Map.Entry<HoloObject, HoloEditTarget>> iterator = TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            HoloEditTarget target = iterator.next().getValue();
            if (SEEN.containsKey(target.object())) continue;
            if (HoloEditTools.target() == target) HoloEditTools.select(null, null);
            target.detach();
            iterator.remove();
        }
        TICKING.clear();
        TICKING.addAll(TARGETS.values());
        for (int i = 0; i < TICKING.size(); i++) TICKING.get(i).tick();
    }

    private static void collect(List<ResolvedHolo> objects)
    {
        for (int i = 0; i < objects.size(); i++) {
            ResolvedHolo resolved = objects.get(i);
            HoloObject object = resolved.object();
            // Un Local dessine dans le monde (F5) n'est pas editable : ses outils supposent la vue.
            if (resolved.attachment() != null || resolved.family() != object.anchor().family()) continue;
            if (!HoloEditRules.hasTools(object) || !mayEdit(object, resolved.container().scope())) continue;
            if (object.anchor() instanceof HoloAnchor.World && !inRange(resolved)) continue;
            SEEN.put(object, Boolean.TRUE);
            TARGETS.computeIfAbsent(object, o -> HoloEditTarget.create(o, o, false)).attach();
        }
    }

    // ---------------------------------------------------------------- interne

    /** Objet du monde a moins de sa portee d'edition de l'oeil du joueur local. */
    private static boolean inRange(ResolvedHolo resolved)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        float partialTick = mc.getFrameTime();
        Vec3 position = resolved.position(partialTick);
        if (!resolved.available()) return false;
        double range = resolved.object().editRange();
        return position.distanceToSqr(mc.player.getEyePosition(partialTick)) <= range * range;
    }

    /**
     * Objet tenu si l'objet affiche est un {@code Local} fixe converti depuis un repere de regard (follow, offset),
     * sinon {@code null}.
     */
    private static Held converted(HoloObject object)
    {
        Held held = HELD.get(object);
        return held != null && (held.offset != null || held.anchor instanceof HoloAnchor.Local local && local.follow())
                ? held : null;
    }

    /** Transform reel (position relative, ou {@code Local} follow) d'un objet tenu converti, depuis l'affiche. */
    private static HoloTransform toReal(HoloObject object, Held held, HoloTransform displayed, HoloTransform out)
    {
        return held.offset != null ? MATH.toOffset(displayed, held.base, object.rotationFrame(), out)
                : MATH.rebaseLocal(displayed, IDENTITY, held.base, out);
    }

    /** Inverse de {@link #toReal}. */
    private static HoloTransform toDisplayed(HoloObject object, Held held, HoloTransform real, HoloTransform out)
    {
        return held.offset != null ? MATH.fromOffset(real, held.base, object.rotationFrame(), out)
                : MATH.rebaseLocal(real, held.base, IDENTITY, out);
    }

    /** Vrai si l'objet appartient a une memoire de joueur. */
    static boolean playerScope(HoloObject object)
    {
        return object.module() != null && object.module().scope() instanceof HoloScope.Player;
    }

    private static boolean alive(HoloObject object)
    {
        HoloModule module = object.module();
        return module != null && module.object(object.key()) == object;
    }

    static void send(HoloObject object) { send(object, object.anchor(), object.transform(), null); }

    private static void send(HoloObject object, HoloAnchor anchor, HoloTransform transform, HoloTransform offset)
    {
        HoloModule module = object.module();
        if (module == null || !alive(object)) return;
        HoloNetwork.CHANNEL.sendToServer(new HoloEditObjectPacket(module.scope(), module.instanceId(), object.key(),
                anchor, transform.copy(), object.display(), offset != null ? offset.copy() : null,
                object.rotationFrame(), object.editRange()));
    }

    private static void copy(HoloTransform dest, HoloTransform source, HoloEditTool group)
    {
        switch (group) {
            case TRANSLATE -> dest.setTranslation(source.x(), source.y(), source.z());
            case ROTATE -> dest.setRotation(source.yaw(), source.pitch(), source.roll());
            case SCALE -> dest.setScale(source.scaleX(), source.scaleY(), source.scaleZ());
            case RANGE -> {}
        }
    }

    /** Objet tenu : etat reel a l'ouverture et groupes modifies depuis. */
    private static final class Held
    {
        private final HoloAnchor anchor;
        private final HoloTransform original;
        /** Non nul : place depuis sa position relative ({@code HoloRelativeToOpen}), qui est ce qu'on edite. */
        private final HoloTransform offset;
        /** Repere de regard de l'objet converti : celui de la session, ou celui de son placement. */
        private final Quaternionf base = new Quaternionf(BASE);
        /** {@code HoloRelativeToOpen} deja place a l'ouverture : il garde sa position a la fermeture. */
        private boolean placed;
        /** Mode reel voulu a la fermeture : follow (sinon fixe). */
        private boolean follow;
        private boolean translated;
        private boolean rotated;
        private boolean scaled;
        private boolean display;
        private boolean frame;

        private Held(HoloAnchor anchor, HoloTransform original, HoloTransform offset)
        {
            this.anchor = anchor;
            this.original = original;
            this.offset = offset;
        }

        private boolean edited() { return translated || rotated || scaled || display || frame; }

        /** Objet dynamique avant la session ({@code HoloEditRules.dynamic}) : position relative ou follow. */
        private boolean dynamic()
        {
            return offset != null || anchor instanceof HoloAnchor.Local local && local.follow();
        }
    }
}
