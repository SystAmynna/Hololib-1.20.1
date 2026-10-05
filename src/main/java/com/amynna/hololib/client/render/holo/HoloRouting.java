package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
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
 * local n'en est pas proprietaire.
 */
public final class HoloRouting
{
    private static final List<ResolvedHolo> SKY = new ArrayList<>();
    private static final List<ResolvedHolo> WORLD = new ArrayList<>();
    private static final List<ResolvedHolo> VIEW = new ArrayList<>();
    private static final List<ResolvedHolo> HUD = new ArrayList<>();
    private static final List<ResolvedHolo> SKY_READ_ONLY = Collections.unmodifiableList(SKY);
    private static final List<ResolvedHolo> WORLD_READ_ONLY = Collections.unmodifiableList(WORLD);
    private static final List<ResolvedHolo> VIEW_READ_ONLY = Collections.unmodifiableList(VIEW);
    private static final List<ResolvedHolo> HUD_READ_ONLY = Collections.unmodifiableList(HUD);
    private static final Map<HoloObject, ResolvedHolo> CACHE = new IdentityHashMap<>();
    /** Ordre de dessin du HUD : {@code z} du transform croissant (le dernier dessine est devant). */
    private static final Comparator<ResolvedHolo> HUD_ORDER =
            Comparator.comparingDouble(resolved -> resolved.object.transform().z());
    private static int generation;

    private HoloRouting() {}

    static List<ResolvedHolo> sky() { return SKY_READ_ONLY; }

    static List<ResolvedHolo> world() { return WORLD_READ_ONLY; }

    static List<ResolvedHolo> view() { return VIEW_READ_ONLY; }

    static List<ResolvedHolo> hud() { return HUD_READ_ONLY; }

    /** Appele par {@code ClientHoloWorld} en fin de tick client. */
    public static void refresh(Minecraft mc)
    {
        SKY.clear();
        WORLD.clear();
        VIEW.clear();
        HUD.clear();
        generation++;
        if (mc.player == null) {
            CACHE.clear();
            return;
        }
        UUID local = mc.player.getUUID();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (ClientHoloContainer container : ClientHoloWorld.containers()) {
            UUID owner = container.scope().owner();
            boolean foreign = owner != null && !owner.equals(local);
            boolean ownerView = owner != null && !foreign;
            for (HoloModule module : container.modules()) {
                for (HoloObject object : module.objects()) {
                    if (!object.replicated() || (!ownerView && object.visibility() != HoloVisibility.PUBLIC)) {
                        continue;
                    }
                    HoloAnchor.Family family = resolve(object.anchor().family(), foreign, firstPerson);
                    if (family == null) continue;
                    ResolvedHolo resolved = CACHE.computeIfAbsent(object, ResolvedHolo::new);
                    resolved.container = container;
                    resolved.reference = foreign ? owner : local;
                    resolved.ownerView = ownerView;
                    resolved.generation = generation;
                    resolved.family = family;
                    switch (family) {
                        case SKY -> SKY.add(resolved);
                        case WORLD -> WORLD.add(resolved);
                        case VIEW -> VIEW.add(resolved);
                        case HUD -> HUD.add(resolved);
                    }
                }
            }
        }
        CACHE.values().removeIf(HoloRouting::stale);
        HUD.sort(HUD_ORDER);
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
