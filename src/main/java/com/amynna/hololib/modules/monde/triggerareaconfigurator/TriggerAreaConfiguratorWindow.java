package com.amynna.hololib.modules.monde.triggerareaconfigurator;

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
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.trigger.CommandListEditor;
import com.amynna.hololib.modules.monde.trigger.CommandListGroup;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import com.amynna.hololib.modules.monde.triggerarea.TriggerAreaModule;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Fenetre du Trigger Area Configurator : ancrage {@code World}, {@code PUBLIC}, affichee seulement chez un operateur
 * ({@link #shown()}) ; acces = operateurs connectes (tenu a jour par le module) ; clics gauche et droit interceptes.
 * Editable par les operateurs (translation, rotation, bouton oeil ; {@code world_editor}), placee par defaut a droite
 * de la zone par defaut.
 * <p>
 * Contenu (lignes en constantes, {@link #HEIGHT} calculee depuis la derniere : aucun widget ne deborde) : titre ;
 * interrupteurs « Zone visible », « Joueurs », « Entites vivantes », « Est OP » (seuls les joueurs operateurs
 * declenchent la zone ; les autres entites ne la declenchent alors plus) (non predictifs : ils montrent l'etat recu de la
 * Trigger Area, relu par {@link #showState} a chaque changement, jamais par frame) ; puis trois listes de commandes
 * (A l'entree, A la sortie, A chaque tick), retractees, une seule ouverte a la fois ({@link CommandListGroup}) :
 * champ + « + », « x » par ligne, lignes glissables ; chaque liste s'execute dans l'ordre.
 */
public final class TriggerAreaConfiguratorWindow extends HoloWindow implements HoloEditTranslation,
        HoloEditRotation, HoloEditVisibility
{
    public static final int WIDTH = 200;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;

    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int FIELD_HEIGHT = 16;
    private static final int SWITCH_HEIGHT = 12;
    private static final int LABEL_HEIGHT = 10;

    // Lignes (ordonnee du haut, en pixels).
    private static final int MISSING_ROW = 15;
    private static final int VISIBLE_ROW = 27;
    private static final int KINDS_ROW = VISIBLE_ROW + SWITCH_HEIGHT + 3;
    private static final int OP_ROW = KINDS_ROW + SWITCH_HEIGHT + 3;
    /** Listes de commandes, sous les interrupteurs : le vide du bas (listes repliees) est en fin de fenetre. */
    private static final int LISTS_ROW = OP_ROW + SWITCH_HEIGHT + 6;
    public static final int HEIGHT = LISTS_ROW + (int) Math.ceil(CommandListGroup.totalHeight(3)) + MARGIN;

    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final HoloColor MUTED = HoloColor.special(0xFF).darker(0.37f);
    private static final HoloColor MISSING = HoloColor.critical(0xFF).lighter(0.18f);
    private static final HoloColor ON = HoloColor.selection(0xFF).darker(0.19f);
    private static final HoloColor OFF = HoloColor.special(0xFF).darker(0.62f);
    /** Noms des listes, dans l'ordre de {@code TriggerAreaModule.KIND_*}. */
    private static final String[] COMMAND_NAMES = {"A l'entree", "A la sortie", "A chaque tick"};

    private final TriggerAreaConfiguratorModule module;
    private final HoloLabel missingLine;
    private final CommandListEditor[] commandLists = new CommandListEditor[3];
    private final HoloWidgetSwitch visibleSwitch;
    private final HoloWidgetSwitch playersSwitch;
    private final HoloWidgetSwitch livingSwitch;
    private final HoloWidgetSwitch opSwitch;
    /** Trigger Area affichee ({@code null} : absente). */
    private TriggerAreaModule area;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur)
     * @param transform placement initial, aussi placement de RESET
     */
    TriggerAreaConfiguratorWindow(String key, TriggerAreaConfiguratorModule module, HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER).setStaffTheme(true);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Trigger Area Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        int column = WIDTH - 2 * MARGIN;
        missingLine = root().add(new HoloLabel(MARGIN, MISSING_ROW, column, LABEL_HEIGHT, Component.empty())
                .setColor(MISSING));

        float half = (column - GAP) / 2f;
        visibleSwitch = toggle(MARGIN, VISIBLE_ROW, column, "Zone visible", TriggerAreaConfiguratorModule.ACTION_VISIBLE);
        playersSwitch = toggle(MARGIN, KINDS_ROW, half, "Joueurs", TriggerAreaConfiguratorModule.ACTION_PLAYERS);
        livingSwitch = toggle(MARGIN + half + GAP, KINDS_ROW, half, "Entites vivantes",
                TriggerAreaConfiguratorModule.ACTION_LIVING);
        opSwitch = toggle(MARGIN, OP_ROW, column, "Est OP (seuls les joueurs ops)",
                TriggerAreaConfiguratorModule.ACTION_OP);

        // Listes de commandes : retractees, une seule ouverte a la fois.
        CommandListGroup group = new CommandListGroup(root(), MARGIN, LISTS_ROW, column);
        for (int kind = 0; kind < 3; kind++) {
            int k = kind;
            commandLists[kind] = group.add(COMMAND_NAMES[kind], new CommandListEditor.Listener()
            {
                @Override
                public void add(String command) { submitAdd(k, command); }

                @Override
                public void remove(int index, String command) { submitRemove(k, index, command); }

                @Override
                public void edit(int index, String old, String command) { submitEdit(k, index, old, command); }

                @Override
                public void move(int from, int to) { submitMove(k, from, to); }
            });
        }
    }

    private HoloWidgetSwitch toggle(float x, float y, float width, String label, int action)
    {
        HoloWidgetSwitch toggle = root().add(new HoloWidgetSwitch(x, y, width, SWITCH_HEIGHT, Component.literal(label),
                (widget, requested) -> module.requestFlag(action, requested)));
        toggle.setPredictive(false).setColors(ON, OFF, TEXT);
        return toggle;
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

    /** Sans Trigger Area, les demandes sont ignorees et les listes se vident ; sinon elles partent au serveur. */
    private void submitAdd(int kind, String command)
    {
        if (area == null) showState(null);
        else module.requestAdd(kind, command);
    }

    private void submitRemove(int kind, int index, String command)
    {
        if (area == null) showState(null);
        else module.requestRemove(kind, index, command);
    }

    private void submitEdit(int kind, int index, String old, String command)
    {
        if (area == null) showState(null);
        else module.requestEdit(kind, index, old, command);
    }

    private void submitMove(int kind, int from, int to)
    {
        if (area == null) showState(null);
        else module.requestMove(kind, from, to);
    }

    // ---------------------------------------------------------------- etat

    /** Relit l'etat de la Trigger Area du marker ({@code null} : absente). Deux cotes, a la creation et a chaque changement. */
    void showState(TriggerAreaModule area)
    {
        this.area = area;
        missingLine.setText(Component.literal(area == null ? "Trigger Area requise sur ce marker" : ""));
        for (int kind = 0; kind < 3; kind++) {
            commandLists[kind].setCommands(area != null ? area.commands(kind) : TriggerCommands.NONE);
        }
        visibleSwitch.setOn(area != null && area.visible());
        playersSwitch.setOn(area != null && area.players());
        livingSwitch.setOn(area != null && area.living());
        opSwitch.setOn(area != null && area.opOnly());
    }
}
