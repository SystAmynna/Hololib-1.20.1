package com.amynna.hololib.modules.monde.triggerinterrupterconfigurator;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.trigger.CommandListEditor;
import com.amynna.hololib.modules.monde.trigger.CommandListGroup;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import com.amynna.hololib.modules.monde.triggerinterrupter.TriggerInterrupterModule;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Fenetre du Trigger Interrupter Configurator : ancrage {@code World}, {@code PUBLIC}, affichee seulement chez un
 * operateur ({@link #shown()}) ; acces = operateurs connectes (tenu a jour par le module) ; clics gauche et droit
 * interceptes. Editable par les operateurs (translation, rotation, bouton oeil ; {@code world_editor}), placee par
 * defaut a droite de la fenetre de l'Interrupter.
 * <p>
 * Contenu (lignes en constantes, {@link #HEIGHT} calculee depuis la derniere : aucun widget ne deborde) : titre ; trois
 * textes de la fenetre (Titre, Bouton, Dessous : vide = non affiche, sans place reservee) ; interrupteur « OP requis » ;
 * quatre onglets, <b>Clic Gauche</b>, <b>Clic Droit</b>, <b>Clic Molette</b> (OnClick, OnRelease, Clicked : chaque tick
 * tant que le clic est maintenu) et <b>Molette</b> (un cran haut, un cran bas : une liste chacun). Chaque emplacement est
 * une <b>liste de commandes</b> executees dans l'ordre, retractee par defaut, une seule ouverte a la fois ({@link
 * CommandListGroup}) : champ + « + », « x » par ligne, lignes glissables ; commande vide = ignoree. L'onglet choisi
 * est local a chaque client et replie les listes. La fenetre montre l'etat recu du Trigger Interrupter, relu par {@link
 * #showState} a chaque changement (jamais par frame).
 */
public final class TriggerInterrupterConfiguratorWindow extends HoloWindow implements HoloEditTranslation,
        HoloEditRotation, HoloEditVisibility
{
    public static final int WIDTH = 240;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;

    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int FIELD_HEIGHT = 16;
    private static final int SWITCH_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 14;
    private static final int LABEL_HEIGHT = 10;
    private static final int TEXT_LABEL_WIDTH = 52;

    // Lignes (ordonnee du haut, en pixels).
    private static final int MISSING_ROW = 15;
    private static final int TEXT_ROW = 27;
    private static final int TEXT_STEP = FIELD_HEIGHT + 3;
    private static final int OP_ROW = TEXT_ROW + 3 * TEXT_STEP + 1;
    private static final int TAB_ROW = OP_ROW + SWITCH_HEIGHT + 5;
    private static final int TAB_CONTENT = TAB_ROW + BUTTON_HEIGHT + GAP;
    /** Un onglet : trois listes de commandes dont une ouverte (accordeon). */
    private static final int TAB_HEIGHT = (int) Math.ceil(CommandListGroup.totalHeight(3));
    public static final int HEIGHT = TAB_CONTENT + TAB_HEIGHT + MARGIN;

    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final HoloColor MUTED = HoloColor.special(0xFF).darker(0.37f);
    private static final HoloColor MISSING = HoloColor.critical(0xFF).lighter(0.18f);
    private static final HoloColor ON = HoloColor.selection(0xFF).darker(0.19f);
    private static final HoloColor OFF = HoloColor.special(0xFF).darker(0.62f);
    private static final HoloColor BUTTON_BACKGROUND = HoloColor.secondary(0x60);
    private static final HoloColor BUTTON_TEXT = HoloColor.special(0xFF);
    private static final HoloColor TAB_ACTIVE_BACKGROUND = HoloColor.secondary(0xC0).darker(0.23f);
    private static final HoloColor TAB_ACTIVE_BORDER = HoloColor.special(0xFF);

    private static final String[] TAB_NAMES = {"Clic Gauche", "Clic Droit", "Clic Molette", "Molette"};
    /** Largeur de chaque onglet (la somme et les ecarts remplissent la colonne). */
    private static final float[] TAB_WIDTHS = {58f, 52f, 62f, 44f};
    private static final String[] TRIGGER_NAMES = {"OnClick", "OnRelease", "Clicked (chaque tick maintenu)"};
    private static final String[] TEXT_NAMES = {"Titre", "Bouton", "Dessous"};
    private static final int[] TEXT_FIELDS = {TriggerInterrupterModule.FIELD_TITLE,
            TriggerInterrupterModule.FIELD_BUTTON, TriggerInterrupterModule.FIELD_FOOTER};
    private static final int SCROLL_TAB = 3;

    private final TriggerInterrupterConfiguratorModule module;
    private final HoloLabel missingLine;
    private final HoloWidgetTextBox[] textBoxes = new HoloWidgetTextBox[3];
    private final HoloWidgetSwitch opSwitch;
    private final HoloWidgetButton[] tabButtons = new HoloWidgetButton[TAB_NAMES.length];
    private final HoloPanel[] tabPanels = new HoloPanel[TAB_NAMES.length];
    /** Un groupe de listes par onglet. */
    private final CommandListGroup[] groups = new CommandListGroup[TAB_NAMES.length];
    private final CommandListEditor[] commandLists = new CommandListEditor[TriggerInterrupterModule.SLOT_COUNT];
    /** Onglet choisi (local). */
    private int tab;
    /** Trigger Interrupter affiche ({@code null} : absent). */
    private TriggerInterrupterModule interrupter;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur)
     * @param transform placement initial, aussi placement de RESET
     */
    TriggerInterrupterConfiguratorWindow(String key, TriggerInterrupterConfiguratorModule module,
                                         HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER).setStaffTheme(true);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Trigger Interrupter Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        int column = WIDTH - 2 * MARGIN;
        missingLine = root().add(new HoloLabel(MARGIN, MISSING_ROW, column, LABEL_HEIGHT, Component.empty())
                .setColor(MISSING));

        // Textes de la fenetre.
        for (int i = 0; i < 3; i++) {
            int field = TEXT_FIELDS[i];
            float y = TEXT_ROW + i * TEXT_STEP;
            root().add(new HoloLabel(MARGIN, y + 4, TEXT_LABEL_WIDTH, LABEL_HEIGHT, Component.literal(TEXT_NAMES[i]))
                    .setColor(MUTED));
            HoloWidgetTextBox box = root().add(new HoloWidgetTextBox(MARGIN + TEXT_LABEL_WIDTH, y,
                    column - TEXT_LABEL_WIDTH, FIELD_HEIGHT, TriggerInterrupterModule.MAX_TEXT, false));
            box.setPlaceholder(Component.literal(i == 2 ? "vide = non affiche" : "texte"))
                    .setOnSubmit((b, text) -> submitText(field, text));
            textBoxes[i] = box;
        }
        opSwitch = root().add(new HoloWidgetSwitch(MARGIN, OP_ROW, column, SWITCH_HEIGHT,
                Component.literal("OP requis"), (widget, requested) -> module.requestRequireOp(requested)));
        opSwitch.setPredictive(false).setColors(ON, OFF, TEXT);

        // Onglets, puis un panneau de commandes par onglet.
        float x = MARGIN;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int index = i;
            tabButtons[i] = root().add(new HoloWidgetButton(x, TAB_ROW, TAB_WIDTHS[i], BUTTON_HEIGHT,
                    Component.literal(TAB_NAMES[i]), b -> selectTab(index)));
            x += TAB_WIDTHS[i] + GAP;
            tabPanels[i] = root().add(new HoloPanel(0, TAB_CONTENT, WIDTH, TAB_HEIGHT));
        }
        for (int i = 0; i < groups.length; i++) groups[i] = new CommandListGroup(tabPanels[i], MARGIN, 0, column);
        for (int button = 0; button < 3; button++) {
            for (int trigger = 0; trigger < 3; trigger++) {
                addCommand(groups[button], TRIGGER_NAMES[trigger], TriggerInterrupterModule.slot(button, trigger));
            }
        }
        addCommand(groups[SCROLL_TAB], "Cran vers le haut (OnClick)", TriggerInterrupterModule.SLOT_SCROLL_UP);
        addCommand(groups[SCROLL_TAB], "Cran vers le bas (OnClick)", TriggerInterrupterModule.SLOT_SCROLL_DOWN);
        showTabs();
    }

    /** Section de commandes de {@code slot} dans {@code group} (retractee). */
    private void addCommand(CommandListGroup group, String name, int slot)
    {
        commandLists[slot] = group.add(name, new CommandListEditor.Listener()
        {
            @Override
            public void add(String command) { submitAdd(slot, command); }

            @Override
            public void remove(int index, String command) { submitRemove(slot, index, command); }

            @Override
            public void edit(int index, String old, String command) { submitEdit(slot, index, old, command); }

            @Override
            public void move(int from, int to) { submitMove(slot, from, to); }
        });
    }

    /** Dessinee seulement chez un operateur (backend client des modules Trigger). */
    @Override
    public boolean shown() { return TriggerOps.localOp(); }

    /** Reservee aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la placer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    // ---------------------------------------------------------------- saisies

    /** Texte de la fenetre valide ; sans Interrupter, les champs se vident. */
    private void submitText(int field, String text)
    {
        if (interrupter == null) {
            showState(null);
            return;
        }
        module.requestText(field, text.strip());
    }

    /** Sans Trigger Interrupter, les demandes sont ignorees et les listes se vident ; sinon elles partent au serveur. */
    private void submitAdd(int slot, String command)
    {
        if (interrupter == null) showState(null);
        else module.requestAdd(slot, command);
    }

    private void submitRemove(int slot, int index, String command)
    {
        if (interrupter == null) showState(null);
        else module.requestRemove(slot, index, command);
    }

    private void submitEdit(int slot, int index, String old, String command)
    {
        if (interrupter == null) showState(null);
        else module.requestEdit(slot, index, old, command);
    }

    private void submitMove(int slot, int from, int to)
    {
        if (interrupter == null) showState(null);
        else module.requestMove(slot, from, to);
    }

    /** Onglet choisi (local) : panneaux et couleurs des onglets. */
    private void selectTab(int index)
    {
        if (index == tab) return;
        groups[tab].collapseAll();
        tab = index;
        showTabs();
    }

    private void showTabs()
    {
        for (int i = 0; i < tabButtons.length; i++) {
            tabPanels[i].setVisible(i == tab);
            if (i == tab) tabButtons[i].setColors(TAB_ACTIVE_BACKGROUND, TAB_ACTIVE_BORDER, BUTTON_TEXT);
            else tabButtons[i].setColors(BUTTON_BACKGROUND, ConfigWindow.ACCENT, BUTTON_TEXT);
        }
    }

    // ---------------------------------------------------------------- etat

    /**
     * Relit l'etat du Trigger Interrupter du marker ({@code null} : absent). Deux cotes, a la creation et a chaque
     * changement.
     */
    void showState(TriggerInterrupterModule interrupter)
    {
        this.interrupter = interrupter;
        missingLine.setText(Component.literal(interrupter == null ? "Trigger Interrupter requis sur ce marker" : ""));
        textBoxes[0].setText(interrupter != null ? interrupter.title() : "");
        textBoxes[1].setText(interrupter != null ? interrupter.buttonText() : "");
        textBoxes[2].setText(interrupter != null ? interrupter.footer() : "");
        opSwitch.setOn(interrupter != null && interrupter.requireOp());
        for (int slot = 0; slot < commandLists.length; slot++) {
            commandLists[slot].setCommands(interrupter != null ? interrupter.commands(slot) : TriggerCommands.NONE);
        }
    }
}
