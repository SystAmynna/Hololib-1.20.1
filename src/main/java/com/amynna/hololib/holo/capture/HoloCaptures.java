package com.amynna.hololib.holo.capture;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Point d'entree commun des captures explicites : utilisable depuis un widget ou un module (code commun) sans classe
 * client. Delegue au controleur client, installe au setup client ; inerte sur un serveur dedie (toujours faux).
 * Fil client uniquement.
 */
public final class HoloCaptures
{
    /** Implementation cote client. */
    @ApiStatus.Internal
    public interface Backend
    {
        boolean begin(HoloCaptor captor);

        void end(HoloCaptor captor);

        boolean isCapturing(HoloCaptor captor);
    }

    private static Backend backend;

    private HoloCaptures() {}

    /**
     * Commence une capture explicite (au-dessus des captures en cours) ; faux si le captor capture deja ou hors du
     * client. Elle dure jusqu'a {@link #end} ou {@link HoloCapture#release()}.
     */
    public static boolean begin(HoloCaptor captor)
    {
        Objects.requireNonNull(captor);
        return backend != null && backend.begin(captor);
    }

    /** Termine la capture explicite de ce captor, s'il en a une. */
    public static void end(HoloCaptor captor)
    {
        if (backend != null) backend.end(captor);
    }

    public static boolean isCapturing(HoloCaptor captor)
    {
        return backend != null && backend.isCapturing(captor);
    }

    @ApiStatus.Internal
    public static void install(Backend backend) { HoloCaptures.backend = Objects.requireNonNull(backend); }
}
