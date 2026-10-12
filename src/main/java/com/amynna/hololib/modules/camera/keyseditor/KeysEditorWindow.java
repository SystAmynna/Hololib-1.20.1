package com.amynna.hololib.modules.camera.keyseditor;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.keys.KeyListEditor;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de configuration « Cles » de {@link KeysEditorModule} (objet du module, cree dans {@code onLoad} des deux
 * cotes) : onglets « Soi-meme » / « Cible », ligne d'etat (sujet edite ou raison de l'indisponibilite), puis
 * {@link KeyListEditor} (champ, Ajouter, cles avec croix), modifiable seulement quand le sujet est disponible.
 */
final class KeysEditorWindow extends ConfigWindow
{
    private static final int WIDTH = 180;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int TAB_HEIGHT = 16;
    private static final int STATUS_HEIGHT = 12;
    private static final int EDITOR_HEIGHT = 120;
    private static final HoloColor TAB_ON = HoloColor.primary(0xA0);
    private static final HoloColor TAB_OFF = HoloColor.secondary(0x40);
    private static final HoloColor TEXT = HoloColor.special(0xFF);
    private static final HoloColor STATUS_OK = HoloColor.selection(0xFF).lighter(0.39f);
    private static final HoloColor STATUS_ERROR = HoloColor.critical(0xFF).lighter(0.37f);

    private final KeysEditorModule module;
    private final HoloWidgetButton self;
    private final HoloWidgetButton target;
    private final HoloLabel status;
    private final KeyListEditor editor;
    private KeysEditorModule.Status shownStatus;
    private String shownName;

    KeysEditorWindow(String key, Component title, KeysEditorModule module)
    {
        super(key, title, HoloTransform.translation(28f, 0f, 0.7f), WIDTH,
                GAP + TAB_HEIGHT + GAP + STATUS_HEIGHT + GAP + EDITOR_HEIGHT + MARGIN, 1f / 400f);
        this.module = module;
        float width = WIDTH - 2 * MARGIN;
        float tab = (width - GAP) / 2f;
        float y = GAP;
        self = content().add(new HoloWidgetButton(MARGIN, y, tab, TAB_HEIGHT, Component.literal("Soi-meme"),
                button -> module.requestMode(KeysEditorModule.SELF)));
        target = content().add(new HoloWidgetButton(MARGIN + tab + GAP, y, tab, TAB_HEIGHT,
                Component.literal("Cible"), button -> module.requestMode(KeysEditorModule.TARGET)));
        y += TAB_HEIGHT + GAP;
        status = content().add(new HoloLabel(MARGIN, y, width, STATUS_HEIGHT, Component.empty()));
        y += STATUS_HEIGHT + GAP;
        editor = content().add(new KeyListEditor(MARGIN, y, width, EDITOR_HEIGHT, module::requestAdd,
                module::requestRemove));
    }

    /** Widgets d'apres l'etat du module (deux cotes). */
    void refresh()
    {
        boolean selfMode = module.mode() == KeysEditorModule.SELF;
        self.setColors(selfMode ? TAB_ON : TAB_OFF, ACCENT, TEXT);
        target.setColors(selfMode ? TAB_OFF : TAB_ON, ACCENT, TEXT);
        KeysEditorModule.Status current = module.status();
        if (current != shownStatus || !module.subjectName().equals(shownName)) {
            shownStatus = current;
            shownName = module.subjectName();
            status.setText(Component.literal(text(current, shownName)));
            status.setColor(current == KeysEditorModule.Status.OK ? STATUS_OK : STATUS_ERROR);
        }
        editor.setKeys(module.shown().keys());
        editor.setEditable(current == KeysEditorModule.Status.OK);
    }

    private static String text(KeysEditorModule.Status status, String name)
    {
        return switch (status) {
            case OK -> "Cles de " + name;
            case NO_KEYS -> "Module Keys absent de votre memoire";
            case NOT_OP -> "Reserve aux operateurs";
            case NO_TARGETING -> "Module Ciblage absent";
            case NO_TARGET -> "Aucune cible (focus sur un joueur)";
            case NOT_PLAYER -> "La cible n'est pas un joueur";
            case TARGET_NO_KEYS -> name + " n'a pas le module Keys";
        };
    }
}
