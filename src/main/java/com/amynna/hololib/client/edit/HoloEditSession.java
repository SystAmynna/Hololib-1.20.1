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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session d'edition du joueur local (fil client), ouverte et fermee par un module (l'editor). Tant qu'elle est
 * ouverte :
 * <ul>
 *     <li>chaque objet vu, editable ({@link HoloEditRules#mayEdit} avec les capacites des modules editeurs du
 *     joueur) et proposant un outil recoit ses boutons ; un objet ancre {@code World}, seulement a moins de sa portee
 *     d'edition ({@code HoloObject.editRange()})
 *     ({@link HoloEditTarget}) ; un objet editable masque ({@code OFF} / {@code FOCUS}) est dessine ;</li>
 *     <li>les objets {@code Local} follow du joueur (et des memoires sans proprietaire) sont <b>tenus</b> : affiches
 *     fixes dans le repere de reference (regard a l'ouverture), puis reconvertis a la fermeture ; de meme, un
 *     {@code HoloRelativeToOpen} de la memoire du joueur est place depuis sa position relative ;</li>
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
     */
    public static void open(float yaw, float pitch, HoloObject hudHost)
    {
        if (active) return;
        active = true;
        HoloEditMath.yawPitch(BASE, yaw, pitch);
        HoloEditSession.hudHost = hudHost;
        selectedHud = null;
        holdNew();
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
     * capacites des modules editeurs de sa memoire.
     */
    public static boolean mayEdit(HoloObject object, HoloScope scope)
    {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        ClientHoloContainer local = ClientHoloWorld.local();
        Set<HoloEditorCapability> capabilities = local != null ? HoloEditRules.capabilities(local.modules())
                : Set.of();
        UUID editOwner = object.module() != null ? object.module().editOwner() : null;
        return HoloEditRules.mayEdit(player.getUUID(), player.hasPermissions(2), scope, editOwner, object,
                capabilities);
    }

    // ---------------------------------------------------------------- modifications

    /**
     * Un groupe de composantes a change : pour un objet tenu, note pour la fermeture ; sinon envoi au serveur, tout de
     * suite ({@code immediate}) ou apres quelques ticks sans changement (molette).
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
            return;
        }
        if (immediate) {
            send(object);
        } else {
            HoloEditTools.schedule(object);
        }
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
        if (converted(object)) {
            MATH.rebaseLocal(transform, IDENTITY, BASE, TMP);
            copy(TMP, object.defaultTransform(), group);
            MATH.rebaseLocal(TMP, BASE, IDENTITY, transform);
        } else {
            copy(transform, object.defaultTransform(), group);
        }
        changed(object, group, true);
    }

    /** Vrai si le groupe est a sa valeur par defaut (dans le repere reel de l'objet). */
    static boolean isDefault(HoloObject object, HoloEditTool group)
    {
        HoloTransform real = object.transform();
        if (converted(object)) real = MATH.rebaseLocal(real, IDENTITY, BASE, TMP);
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
        holdNew();
        refreshTargets();
        HoloEditTools.tick();
    }

    /** Oublie les objets tenus qui ont disparu (module retire, memoire oubliee). */
    private static void dropDead()
    {
        HELD.keySet().removeIf(object -> !alive(object));
    }

    /** Tient les objets follow et {@code HoloRelativeToOpen} pas encore tenus (nouveaux, ou a l'ouverture). */
    private static void holdNew()
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
                    if (object.openOffset() != null && owner != null) {
                        hold(object, true);
                    } else if (object.anchor() instanceof HoloAnchor.Local anchor && anchor.follow()) {
                        hold(object, false);
                    }
                }
            }
        }
    }

    private static void hold(HoloObject object, boolean offset)
    {
        HoloTransform transform = object.transform();
        Held held = new Held(object.anchor(), transform.copy(), offset ? object.openOffset().copy() : null);
        held.follow = !offset;
        HELD.put(object, held);
        if (offset) transform.set(object.openOffset());
        MATH.rebaseLocal(transform, BASE, IDENTITY, transform);
        object.setAnchor(FIXED);
    }

    /** Fin de session pour un objet tenu : etat reel recalcule, envoye s'il a change, sinon restaure. */
    private static void release(HoloObject object, Held held)
    {
        if (!alive(object)) return;
        HoloTransform current = object.transform();
        if (held.offset != null) {
            HoloTransform offset = held.offset.copy();
            merge(offset, MATH.rebaseLocal(current, IDENTITY, BASE, TMP), held, true);
            object.setAnchor(held.anchor);
            current.set(held.original);
            object.openOffset().set(offset);
            if (held.edited()) send(object, held.anchor, current, offset);
            return;
        }
        boolean wasFollow = held.anchor instanceof HoloAnchor.Local local && local.follow();
        if (held.follow) {
            HoloTransform real = held.original.copy();
            merge(real, MATH.rebaseLocal(current, IDENTITY, BASE, TMP), held, wasFollow);
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

    /** Vrai si l'objet affiche est un {@code Local} fixe converti depuis le repere de reference (follow, offset). */
    private static boolean converted(HoloObject object)
    {
        Held held = HELD.get(object);
        return held != null && (held.offset != null || held.anchor instanceof HoloAnchor.Local local && local.follow());
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
                object.editRange()));
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
        /** Mode reel voulu a la fermeture : follow (sinon fixe). */
        private boolean follow;
        private boolean translated;
        private boolean rotated;
        private boolean scaled;
        private boolean display;

        private Held(HoloAnchor anchor, HoloTransform original, HoloTransform offset)
        {
            this.anchor = anchor;
            this.original = original;
            this.offset = offset;
        }

        private boolean edited() { return translated || rotated || scaled || display; }
    }
}
