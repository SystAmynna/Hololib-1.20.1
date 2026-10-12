package com.amynna.hololib.modules.camera.radar;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de declenchement du radar ({@link RadarModule}) : camera normale ({@code Local} relatif), {@code PUBLIC}
 * (un joueur qui inspecte la voit, sans pouvoir s'en servir : acces {@code OWNER}), editable par l'Own Fix Editor.
 * Boutons Activer (relance l'onde si elle est deja lancee) et Desactiver, ligne d'etat du radar local.
 * <p>
 * Retiree ({@link #shown()} faux) quand la memoire contient Quick Action : les deux actions de la roue la remplacent.
 */
final class RadarWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation, HoloEditScale,
        HoloEditVisibility
{
    static final float DISTANCE = 0.8f;

    private static final int WIDTH = 140;
    private static final int HEIGHT = 62;
    private static final HoloColor ACCENT = HoloColor.primary(0xFF);
    private static final String IDLE = "Radar : inactif";

    private final RadarModule module;
    private final HoloLabel status;
    private String shown = IDLE;

    RadarWindow(String key, RadarModule module)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(0f, 0f, DISTANCE), WIDTH, HEIGHT,
                1f / 160f);
        this.module = module;
        setVisibility(HoloVisibility.PUBLIC);
        // Le fond prend les clics : viser la fenetre ne casse jamais le bloc derriere.
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.MIDDLE_CLICK);
        HoloPanel root = root();
        root.setPattern(0x80).setBorder(ACCENT, 1f);
        root.add(new HoloLabel(4, 3, WIDTH - 8, 14, Component.literal("Radar"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ACCENT));
        root.add(new HoloWidgetButton(6, 20, 62, 20, Component.literal("Activer"),
                button -> module.requestActivate()));
        root.add(new HoloWidgetButton(WIDTH - 68, 20, 62, 20, Component.literal("Desactiver"),
                button -> module.requestDeactivate()));
        status = root.add(new HoloLabel(4, 44, WIDTH - 8, 14, Component.literal(IDLE))
                .setAlign(HoloLabel.Align.CENTER).setColor(HoloColor.special(0xFF).darker(0.18f)));
    }

    /** Ligne d'etat (client du proprietaire) ; le composant n'est recree que si le texte change. */
    void setStatus(String text)
    {
        if (text == null || text.equals(shown)) return;
        shown = text;
        status.setText(Component.literal(text));
    }

    @Override
    public boolean shown() { return !module.quickActionPresent(); }
}
