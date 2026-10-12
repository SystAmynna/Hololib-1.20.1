package com.amynna.hololib.holo.theme;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Resolution des themes, facade commune : le backend client ({@code client.theme.ClientThemes}) est installe au setup
 * client ; sans lui (serveur dedie, avant le setup), les themes par defaut. Appels du fil client, sans allocation.
 * <p>
 * {@code SERVER} = selection du serveur ; {@code CLIENT} = selection du joueur local (celle du serveur s'il suit le
 * serveur, si son theme a disparu, ou pour le jeu staff d'un non-op).
 */
public final class HoloThemes
{
    private static Backend backend;

    /** Partie client : themes selectionnes, mis en cache. */
    public interface Backend
    {
        HoloTheme theme(HoloThemeSource source, boolean staff);

        /** Incremente a chaque changement d'un theme resolu (renderers qui precalculent des couleurs). */
        int revision();

        /** Bibliotheque du serveur recue, dans l'ordre. */
        Collection<HoloThemeEntry> library();

        /** Theme de la bibliotheque, ou {@code null}. */
        HoloThemeEntry entry(int id);

        /** Identifiant du theme selectionne par le serveur. */
        int serverSelection(boolean staff);

        /** Incremente a chaque instantane recu (configurateurs). */
        int libraryRevision();

        /** Vrai si le joueur local est op ({@link HoloThemeLibrary#OP_LEVEL}). */
        boolean localOp();
    }

    private HoloThemes() {}

    /** Usage interne : branche la partie client, au setup client. */
    public static void install(Backend backend) { HoloThemes.backend = Objects.requireNonNull(backend); }

    /** Theme selectionne pour la source et le jeu (standard ou staff). */
    public static HoloTheme theme(HoloThemeSource source, boolean staff)
    {
        if (backend == null) return staff ? HoloTheme.DEFAULT_STAFF : HoloTheme.DEFAULT;
        return backend.theme(source, staff);
    }

    /**
     * Theme d'un objet : {@link HoloThemed#themeOverride()}, sinon sa source et son jeu ; theme client standard pour un
     * objet qui n'est pas {@link HoloThemed}.
     */
    public static HoloTheme resolve(Object object)
    {
        if (!(object instanceof HoloThemed themed)) return clientStandard();
        HoloTheme override = themed.themeOverride();
        return override != null ? override : theme(themed.themeSource(), themed.staffTheme());
    }

    /** Theme standard du joueur local : rendus qui ne sont pas des objets (radar, ciblage, surcouches). */
    public static HoloTheme clientStandard() { return theme(HoloThemeSource.CLIENT, false); }

    /** Compteur de changements des themes resolus. */
    public static int revision() { return backend == null ? 0 : backend.revision(); }

    /** Cote client : bibliotheque du serveur recue (vide sans backend). */
    public static Collection<HoloThemeEntry> library() { return backend == null ? List.of() : backend.library(); }

    /** Cote client : theme de la bibliotheque, ou {@code null}. */
    public static HoloThemeEntry entry(int id) { return backend == null ? null : backend.entry(id); }

    /** Cote client : identifiant du theme selectionne par le serveur. */
    public static int serverSelection(boolean staff)
    {
        if (backend == null) return staff ? HoloThemeLibrary.BUILTIN_STAFF : HoloThemeLibrary.BUILTIN_STANDARD;
        return backend.serverSelection(staff);
    }

    /** Cote client : compteur des instantanes de la bibliotheque recus. */
    public static int libraryRevision() { return backend == null ? 0 : backend.libraryRevision(); }

    /** Cote client : vrai si le joueur local est op. */
    public static boolean localOp() { return backend != null && backend.localOp(); }
}
