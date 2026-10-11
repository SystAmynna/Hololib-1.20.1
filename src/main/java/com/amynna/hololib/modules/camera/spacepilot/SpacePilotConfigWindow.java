package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de configuration de {@link SpacePilotModule} (objet du module, cree dans {@code onLoad} des deux cotes),
 * reduite en T33 (C75) : trois interrupteurs, « HUD » (donnees de navigation, affichees pendant le pilotage),
 * « Lien referentiel » (lien et anneau du referentiel, pendant le pilotage) et « Vitesse de croisiere » (calculateur de
 * T34). Les reglages d'affichage du ciel sont dans la fenetre de Space Knowledge. {@code PRIVATE} (C45) :
 * contrairement aux autres fenetres de Config, un joueur qui inspecte le proprietaire ne la recoit pas.
 */
final class SpacePilotConfigWindow extends ConfigWindow
{
    private static final int WIDTH = 170;
    private static final int MARGIN = 6;
    private static final int ROW = 16;
    private static final int GAP = 2;

    private final SpacePilotModule module;
    private final HoloWidgetSwitch hud;
    private final HoloWidgetSwitch refLink;
    private final HoloWidgetSwitch cruise;

    SpacePilotConfigWindow(String key, Component title, SpacePilotModule module)
    {
        super(key, title, HoloTransform.translation(28f, 0f, 0.7f), WIDTH, 3 * (ROW + GAP) + 4 + MARGIN, 1f / 400f);
        this.module = module;
        setVisibility(HoloVisibility.PRIVATE);
        float y = 4f;
        hud = flagSwitch(y, "HUD", SpacePilotModule.HUD);
        y += ROW + GAP;
        refLink = flagSwitch(y, "Lien referentiel", SpacePilotModule.REF_LINK);
        y += ROW + GAP;
        cruise = flagSwitch(y, "Vitesse de croisiere", SpacePilotModule.CRUISE);
    }

    private HoloWidgetSwitch flagSwitch(float y, String label, int index)
    {
        return content().add(new HoloWidgetSwitch(MARGIN, y, WIDTH - 2 * MARGIN, ROW, Component.literal(label),
                (widget, on) -> module.requestFlag(index, on)));
    }

    /** Widgets d'apres l'etat du module (deux cotes). */
    void refresh()
    {
        hud.setOn(module.hud());
        refLink.setOn(module.refLink());
        cruise.setOn(module.cruise());
    }
}
