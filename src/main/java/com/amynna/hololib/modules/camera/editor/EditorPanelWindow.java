package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditFixOrDynamic;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditorUi;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fenetre du panneau des editeurs : la liste ({@link HoloWidgetList}) des modules editeurs equipes, chacun avec un
 * interrupteur actif / desactive. Ancree {@code Local} follow a gauche du regard ; editable (deplacement, rotation,
 * echelle, fixe / dynamique) mais pas son affichage : elle n'apparait qu'en session d'edition, et seulement avec au
 * moins {@value #MIN_EDITORS} editeurs equipes.
 * <p>
 * Le contenu de la liste vient des modules voisins : {@link #sync} est appele cote client chaque tick.
 */
public final class EditorPanelWindow extends HoloWindow implements HoloEditorUi, HoloEditTranslation, HoloEditRotation,
        HoloEditScale, HoloEditFixOrDynamic
{
    public static final int MIN_EDITORS = 2;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 124;
    private static final int MARGIN = 6;
    private static final int TOP = 20;
    private static final float ROW_HEIGHT = 18f;

    private final HoloWidgetList list;
    private final List<EditorCapabilityModule> editors = new ArrayList<>();
    private final List<HoloWidgetSwitch> switches = new ArrayList<>();
    private final List<EditorCapabilityModule> scratch = new ArrayList<>();

    public EditorPanelWindow(String key)
    {
        super(key, new HoloAnchor.Local(true), HoloTransform.translation(-38f, 4f, 0.7f), WIDTH, HEIGHT, 1f / 400f);
        root().setBackground(0xC0101828).setBorder(0xFF40C0FF, 1f);
        root().add(new HoloLabel(MARGIN, 4, WIDTH - 2 * MARGIN, 12, Component.literal("Editeurs"))
                .setAlign(HoloLabel.Align.CENTER).setColor(0xFF40C0FF));
        list = root().add(new HoloWidgetList(MARGIN, TOP, WIDTH - 2 * MARGIN, HEIGHT - TOP - MARGIN, ROW_HEIGHT));
    }

    /**
     * Cote client : editeurs equipes parmi {@code modules} (memoire du joueur). Reconstruit les lignes si l'ensemble
     * a change, sinon recopie seulement leur etat.
     */
    public void sync(Collection<HoloModule> modules)
    {
        scratch.clear();
        for (HoloModule module : modules) {
            if (module instanceof EditorCapabilityModule editor) scratch.add(editor);
        }
        if (!scratch.equals(editors)) {
            editors.clear();
            editors.addAll(scratch);
            switches.clear();
            list.clearItems();
            for (EditorCapabilityModule editor : editors) {
                switches.add(list.addItem(new HoloWidgetSwitch(0, 0, 1, ROW_HEIGHT, editor.editorName(),
                        (widget, on) -> editor.requestEnabled(on))));
            }
        }
        for (int i = 0; i < editors.size(); i++) switches.get(i).setOn(editors.get(i).editorEnabled());
    }

    /** Visible en session seulement avec au moins {@value #MIN_EDITORS} editeurs equipes. */
    @Override
    public boolean shownInSession() { return editors.size() >= MIN_EDITORS; }
}
