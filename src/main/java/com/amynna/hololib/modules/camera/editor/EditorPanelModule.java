package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Editor Panel : activation des editeurs, dans la couche de configuration ({@link Configurable}, ligne "Editeurs" de
 * la liste du module Config). Sa fenetre ({@link ConfigWindow}) liste ({@link HoloWidgetList}) les modules editeurs
 * equipes, chacun avec un interrupteur actif / desactive ({@link EditorCapabilityModule#requestEnabled}).
 * <p>
 * Le contenu de la liste vient des modules voisins : {@link #sync} est appele cote client chaque tick.
 */
public final class EditorPanelModule extends HoloModule implements Configurable
{
    private static final Component NAME = Component.literal("Editeurs");
    private static final int WIDTH = 160;
    private static final int MARGIN = 6;
    private static final float ROW_HEIGHT = 18f;
    private static final int LIST_HEIGHT = 108;

    private ConfigWindow window;
    private HoloWidgetList list;
    private final List<EditorCapabilityModule> editors = new ArrayList<>();
    private final List<HoloWidgetSwitch> switches = new ArrayList<>();
    private final List<EditorCapabilityModule> scratch = new ArrayList<>();

    @Override
    public void onLoad()
    {
        window = addObject(new ConfigWindow("config", NAME, HoloTransform.translation(-28f, 0f, 0.7f), WIDTH,
                LIST_HEIGHT + 2 * MARGIN, 1f / 400f));
        list = window.content().add(new HoloWidgetList(MARGIN, MARGIN, WIDTH - 2 * MARGIN, LIST_HEIGHT, ROW_HEIGHT));
        editors.clear();
        switches.clear();
    }

    @Override
    public Component configName() { return NAME; }

    @Override
    public ConfigWindow configWindow() { return window; }

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
}
