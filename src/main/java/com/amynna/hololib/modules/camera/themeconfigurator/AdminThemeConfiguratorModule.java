package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Module {@code hololib:admin_theme_configurator} (memoire de joueur, <b>ops</b>) : meme interface que
 * {@link ThemeConfiguratorModule}, mais l'onglet choisit la selection <b>du serveur</b> (theme commun a tous) et tous
 * les themes de la bibliotheque sont modifiables. Le serveur verifie l'op avant toute action ; fenetres masquees aux
 * non-ops, au theme staff du serveur. Aucun etat propre : la selection est dans {@link HoloThemeLibrary}.
 */
public final class AdminThemeConfiguratorModule extends ThemeConfiguratorBase
{
    private static final Component NAME = Component.literal("Themes (serveur)");

    @Override
    boolean admin() { return true; }

    @Override
    int selection(boolean staff) { return HoloThemes.serverSelection(staff); }

    @Override
    public Component configName() { return NAME; }

    /** Absent de la liste de Config pour un non-op (cote client). */
    @Override
    public ConfigWindow configWindow() { return HoloThemes.localOp() ? super.configWindow() : null; }

    @Override
    boolean applySelection(ServerPlayer player, HoloThemeLibrary library, boolean staff, int id)
    {
        library.select(staff, id);
        return false;
    }
}
