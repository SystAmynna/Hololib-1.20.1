package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.edit.HoloEditSession;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloVisibility;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repartit les objets holographiques connus du client entre les ponts, selon leur ancrage resolu pour le joueur local.
 * Recalcule a chaque tick client (pas pendant le rendu) ; sans allocation tant que l'ensemble des objets ne change pas.
 * <p>
 * Resolution, pour un objet de la memoire du joueur local ou d'une memoire sans proprietaire (dimension, position) :
 * {@code Sky} -> ciel, {@code World} -> monde, {@code Local} -> vue en premiere personne (monde autour de l'oeil du
 * joueur local sinon), {@code Hud} -> HUD en premiere personne seulement (masque en F5). Pour un objet de la memoire
 * d'un autre joueur (recu parce que le joueur local l'inspecte) : {@code Local} est dessine dans le monde autour de
 * l'oeil de ce joueur, {@code Hud} jamais. La liste HUD est triee par {@code z} du transform.
 * <p>
 * Un objet n'est dessine qu'une fois son etat recu du serveur, et seulement s'il est {@code PUBLIC} quand le joueur
 * local n'en est pas proprietaire. Son affichage ({@code HoloDisplay}) filtre ensuite : {@code FOCUS} seulement si le
 * proprietaire de la memoire (le joueur local pour une memoire sans proprietaire) est en mode focus, {@code OFF}
 * jamais ; {@code ON} et {@code FOCUS} demandent aussi {@code HoloObject.shown()} (condition locale). En session
 * d'edition, un objet que le joueur local peut editer est toujours dessine.
 * <p>
 * Session d'edition avec cadre HUD : les objets HUD ne vont plus au HUD mais a {@link #hudPreview()} (dessines dans
 * la fenetre de l'editor). Les overlays ({@link HoloAttachment}) sont routes apres leur hote, dans sa famille.
 */
public final class HoloRouting
{
    private static final List<ResolvedHolo> SKY = new ArrayList<>();
    private static final List<ResolvedHolo> WORLD = new ArrayList<>();
    private static final List<ResolvedHolo> VIEW = new ArrayList<>();
    private static final List<ResolvedHolo> HUD = new ArrayList<>();
    private static final List<ResolvedHolo> HUD_PREVIEW = new ArrayList<>();
    private static final List<ResolvedHolo> SKY_READ_ONLY = Collections.unmodifiableList(SKY);
    private static final List<ResolvedHolo> WORLD_READ_ONLY = Collections.unmodifiableList(WORLD);
    private static final List<ResolvedHolo> VIEW_READ_ONLY = Collections.unmodifiableList(VIEW);
    private static final List<ResolvedHolo> HUD_READ_ONLY = Collections.unmodifiableList(HUD);
    private static final List<ResolvedHolo> HUD_PREVIEW_READ_ONLY = Collections.unmodifiableList(HUD_PREVIEW);
    private static final List<HoloAttachment> ATTACHMENTS = new ArrayList<>();
    private static final Map<HoloObject, ResolvedHolo> CACHE = new IdentityHashMap<>();
    /** Ordre de dessin du HUD : {@code z} du transform croissant (le dernier dessine est devant). */
    private static final Comparator<ResolvedHolo> HUD_ORDER =
            Comparator.comparingDouble(resolved -> resolved.object.transform().z());
    private static int generation;

    private HoloRouting() {}

    /** Objets routes au ciel au dernier tick (vue non modifiable). */
    public static List<ResolvedHolo> sky() { return SKY_READ_ONLY; }

    public static List<ResolvedHolo> world() { return WORLD_READ_ONLY; }

    public static List<ResolvedHolo> view() { return VIEW_READ_ONLY; }

    public static List<ResolvedHolo> hud() { return HUD_READ_ONLY; }

    /** Objets HUD retires du HUD par une session d'edition (cadre de l'editor), tries comme le HUD. */
    public static List<ResolvedHolo> hudPreview() { return HUD_PREVIEW_READ_ONLY; }

    /** Objet route au dernier tick, tel que le voit le joueur local ; {@code null} s'il n'est pas dessine. */
    public static ResolvedHolo resolved(HoloObject object) { return CACHE.get(object); }

    /** Vrai si l'objet a ete route au dernier tick (donc dessine pour le joueur local). */
    public static boolean isRouted(HoloObject object) { return CACHE.containsKey(object); }

    /** Ajoute un overlay, route a partir du prochain tick. Fil client. */
    public static void attach(HoloAttachment attachment)
    {
        if (!ATTACHMENTS.contains(attachment)) ATTACHMENTS.add(attachment);
    }

    public static void detach(HoloAttachment attachment) { ATTACHMENTS.remove(attachment); }

    /** Appele par {@code ClientHoloWorld} en fin de tick client. */
    public static void refresh(Minecraft mc)
    {
        SKY.clear();
        WORLD.clear();
        VIEW.clear();
        HUD.clear();
        HUD_PREVIEW.clear();
        generation++;
        if (mc.player == null) {
            CACHE.clear();
            return;
        }
        UUID local = mc.player.getUUID();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean localFocus = HoloModes.active(ClientHoloWorld.local(), HoloMode.FOCUS);
        boolean hudSuspended = HoloEditSession.hudSuspended();
        for (ClientHoloContainer container : ClientHoloWorld.containers()) {
            UUID owner = container.scope().owner();
            boolean foreign = owner != null && !owner.equals(local);
            boolean ownerView = owner != null && !foreign;
            boolean focus = foreign ? HoloModes.active(container, HoloMode.FOCUS) : localFocus;
            for (HoloModule module : container.modules()) {
                for (HoloObject object : module.objects()) {
                    if (!object.replicated() || (!ownerView && object.visibility() != HoloVisibility.PUBLIC)) {
                        continue;
                    }
                    if (!displayed(object, focus, container.scope())) continue;
                    HoloAnchor.Family family = resolve(object.anchor().family(), foreign, firstPerson);
                    if (family == null) continue;
                    ResolvedHolo resolved = CACHE.computeIfAbsent(object, ResolvedHolo::new);
                    resolved.container = container;
                    resolved.reference = foreign ? owner : local;
                    resolved.ownerView = ownerView;
                    resolved.attachment = null;
                    resolved.generation = generation;
                    resolved.family = family;
                    switch (family) {
                        case SKY -> SKY.add(resolved);
                        case WORLD -> WORLD.add(resolved);
                        case VIEW -> VIEW.add(resolved);
                        case HUD -> (hudSuspended ? HUD_PREVIEW : HUD).add(resolved);
                    }
                }
            }
        }
        routeAttachments();
        CACHE.values().removeIf(HoloRouting::stale);
        HUD.sort(HUD_ORDER);
        HUD_PREVIEW.sort(HUD_ORDER);
    }

    /**
     * Filtre d'affichage ; {@code focus} : mode focus du proprietaire de la memoire. La condition locale
     * ({@code shown}) ne masque pas un objet que la session d'edition revele.
     */
    private static boolean displayed(HoloObject object, boolean focus, HoloScope scope)
    {
        return switch (object.display()) {
            case ON -> object.shown() || HoloEditSession.reveals(object, scope);
            case FOCUS -> focus && object.shown() || HoloEditSession.reveals(object, scope);
            case OFF -> HoloEditSession.reveals(object, scope);
        };
    }

    /** Overlays dont l'hote vient d'etre route (hors HUD), dans la famille de l'hote. */
    private static void routeAttachments()
    {
        for (int i = 0; i < ATTACHMENTS.size(); i++) {
            HoloAttachment attachment = ATTACHMENTS.get(i);
            ResolvedHolo host = CACHE.get(attachment.host());
            if (host == null || stale(host) || host.attachment != null || host.family == HoloAnchor.Family.HUD) {
                continue;
            }
            ResolvedHolo resolved = CACHE.computeIfAbsent(attachment.overlay(), ResolvedHolo::new);
            resolved.container = host.container;
            resolved.reference = host.reference;
            resolved.ownerView = host.ownerView;
            resolved.attachment = attachment;
            resolved.generation = generation;
            resolved.family = host.family;
            switch (host.family) {
                case SKY -> SKY.add(resolved);
                case WORLD -> WORLD.add(resolved);
                case VIEW -> VIEW.add(resolved);
                case HUD -> {}
            }
        }
    }

    /** Famille effective ; {@code null} = non dessine. */
    private static HoloAnchor.Family resolve(HoloAnchor.Family family, boolean foreign, boolean firstPerson)
    {
        return switch (family) {
            case SKY, WORLD -> family;
            case VIEW -> foreign || !firstPerson ? HoloAnchor.Family.WORLD : HoloAnchor.Family.VIEW;
            case HUD -> foreign || !firstPerson ? null : HoloAnchor.Family.HUD;
        };
    }

    private static boolean stale(ResolvedHolo resolved) { return resolved.generation != generation; }
}
