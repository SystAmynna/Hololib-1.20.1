package com.amynna.hololib.holo.render;

import com.amynna.hololib.holo.module.HoloModule;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Point d'entree commun pour annuler le rendu d'elements vanilla ({@link HoloRenderElement}) depuis un module, sans
 * classe client. Delegue au client, installe au setup client ; inerte sur un serveur dedie. Fil client uniquement.
 * <p>
 * Un element est annule tant qu'au moins un module le demande (demandeurs comptes par identite, appels idempotents).
 * Seul un module qui peut agir chez le client local est pris en compte ({@code HoloModuleHost.canAct()} : miroir
 * client d'une memoire du joueur local, de dimension ou de position) : la copie serveur d'une partie solo et le module
 * d'un joueur inspecte sont ignores. Appeler {@link #cancel} dans {@code onLoad} (ou au fil de l'etat) et
 * {@link #restoreAll} dans {@code onUnload}.
 */
public final class HoloRenderCanceler
{
    /** Implementation cote client. */
    @ApiStatus.Internal
    public interface Backend
    {
        void set(Object requester, HoloRenderElement element, boolean cancel);

        void releaseAll(Object requester);

        boolean cancelled(HoloRenderElement element);
    }

    private static Backend backend;

    private HoloRenderCanceler() {}

    /** Annule le rendu de l'element pour ce module (sans effet si le module ne peut pas agir chez ce client). */
    public static void cancel(HoloModule module, HoloRenderElement element)
    {
        set(module, element, true);
    }

    /** Retire la demande d'annulation de ce module ; l'element revient si plus personne ne l'annule. */
    public static void restore(HoloModule module, HoloRenderElement element)
    {
        set(module, element, false);
    }

    /** Retire toutes les demandes de ce module (a appeler dans {@code onUnload}). */
    public static void restoreAll(HoloModule module)
    {
        Objects.requireNonNull(module);
        if (backend != null) backend.releaseAll(module);
    }

    /** Vrai si l'element est annule chez ce client. */
    public static boolean cancelled(HoloRenderElement element)
    {
        return backend != null && backend.cancelled(element);
    }

    private static void set(HoloModule module, HoloRenderElement element, boolean cancel)
    {
        Objects.requireNonNull(module);
        Objects.requireNonNull(element);
        if (backend != null && module.host().canAct()) backend.set(module, element, cancel);
    }

    @ApiStatus.Internal
    public static void install(Backend backend) { HoloRenderCanceler.backend = Objects.requireNonNull(backend); }
}
