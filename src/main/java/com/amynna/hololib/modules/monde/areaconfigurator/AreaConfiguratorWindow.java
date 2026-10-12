package com.amynna.hololib.modules.monde.areaconfigurator;

import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Fenetre de l'Area Displayer Configurator : ancrage {@code World}, {@code PUBLIC}, affichee seulement chez un
 * operateur ({@link #shown()}) ; acces = operateurs connectes (tenu a jour par le module) ; clics gauche et droit
 * interceptes. Editable par les operateurs (translation, rotation, bouton oeil ; {@code world_editor}), placee par
 * defaut a droite du pave par defaut.
 * <p>
 * Contenu (lignes en constantes, {@link #HEIGHT} calculee depuis la derniere : aucun widget ne deborde, sinon il ne
 * serait vise que sur sa partie interieure) : titre ; zone (taille en blocs et resolution effective {@code 1:pas}, ou
 * « Aucune zone », ou « Area Displayer requis sur ce marker ») ; coins A et B (X, Y, Z, « Ici » = bloc du joueur) ;
 * etat du scan ; Cadre ; Resolution (clic gauche = palier suivant, droit = precedent : Auto, 1:1 bloc par bloc ...
 * 1:32 ; « 1:1 -> 1:4 » si la zone est trop grande pour le palier) ; Auto Update et intervalle en secondes ; Joueurs
 * et leurs trainees ; Entites et leurs trainees ; « Actualiser ». Interrupteurs non predictifs : ils montrent l'etat
 * recu du Displayer, relu par {@link #showState} a chaque changement (jamais par frame).
 * <p>
 * <b>Saisie des coins</b> : chaque champ valide (Entree ou Echap) devient un brouillon local de son axe ; des que les
 * trois axes du coin ont une valeur (brouillon, sinon valeur du Displayer), le coin est envoye et ses brouillons
 * effaces. Un coin vide se remplit donc champ par champ ; un brouillon reste affiche malgre les rafraichissements ; une
 * saisie invalide ne remet que son champ.
 */
public final class AreaConfiguratorWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
        HoloEditVisibility
{
    public static final int WIDTH = 200;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;

    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int FIELD_HEIGHT = 16;
    private static final int SWITCH_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 14;
    private static final int LABEL_HEIGHT = 10;

    // Lignes (ordonnee du haut, en pixels).
    private static final int ZONE_ROW = 15;
    private static final int CORNER_ROW = 28;
    private static final int SCAN_ROW = CORNER_ROW + 2 * (FIELD_HEIGHT + GAP);
    private static final int FRAME_ROW = SCAN_ROW + LABEL_HEIGHT + 3;
    private static final int RESOLUTION_ROW = FRAME_ROW + SWITCH_HEIGHT + 3;
    private static final int AUTO_ROW = RESOLUTION_ROW + BUTTON_HEIGHT + 3;
    private static final int PLAYERS_ROW = AUTO_ROW + FIELD_HEIGHT + 3;
    private static final int ENTITIES_ROW = PLAYERS_ROW + SWITCH_HEIGHT + 3;
    private static final int REFRESH_ROW = ENTITIES_ROW + SWITCH_HEIGHT + 5;
    public static final int HEIGHT = REFRESH_ROW + BUTTON_HEIGHT + MARGIN;

    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final HoloColor MUTED = HoloColor.special(0xFF).darker(0.37f);
    private static final HoloColor MISSING = HoloColor.critical(0xFF).lighter(0.18f);
    private static final HoloColor ON = HoloColor.selection(0xFF).darker(0.19f);
    private static final HoloColor OFF = HoloColor.special(0xFF).darker(0.62f);
    private static final int NUMBER_LENGTH = 9;
    private static final int LETTER_WIDTH = 10;
    private static final int HERE_WIDTH = 30;
    private static final int INTERVAL_LABEL_WIDTH = 62;
    /** Ordre des resolutions du bouton : Auto puis les paliers. */
    private static final int[] RESOLUTIONS = new int[AreaGrid.LEVELS.length + 1];

    static {
        RESOLUTIONS[0] = AreaGrid.AUTO;
        System.arraycopy(AreaGrid.LEVELS, 0, RESOLUTIONS, 1, AreaGrid.LEVELS.length);
    }

    private final AreaConfiguratorModule module;
    private final HoloLabel zoneLine;
    private final HoloLabel scanLine;
    private final HoloWidgetTextBox[][] corners = new HoloWidgetTextBox[2][3];
    private final HoloWidgetSwitch[] switches = new HoloWidgetSwitch[AreaDisplayerModule.FLAG_COUNT];
    private final HoloWidgetMultiButton resolutionButton;
    private final HoloWidgetTextBox intervalBox;

    /** Brouillons des coins (client) : valeur et presence, par coin puis par axe. */
    private final int[][] draft = new int[2][3];
    private final boolean[][] drafted = new boolean[2][3];
    /** Displayer affiche ({@code null} : absent). */
    private AreaDisplayerModule displayer;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur)
     * @param transform placement initial, aussi placement de RESET
     */
    AreaConfiguratorWindow(String key, AreaConfiguratorModule module, HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER).setStaffTheme(true);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Area Displayer Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        int column = WIDTH - 2 * MARGIN;
        zoneLine = root().add(new HoloLabel(MARGIN, ZONE_ROW, column, LABEL_HEIGHT, Component.empty())
                .setColor(MUTED));

        // Coins A et B : lettre, X Y Z, « Ici ».
        float fieldWidth = (column - LETTER_WIDTH - HERE_WIDTH - 3 * GAP) / 3f;
        String[] axes = {"X", "Y", "Z"};
        for (int c = 0; c < 2; c++) {
            int corner = c;
            float y = CORNER_ROW + c * (FIELD_HEIGHT + GAP);
            root().add(new HoloLabel(MARGIN, y + 4, LETTER_WIDTH, LABEL_HEIGHT, Component.literal(c == 1 ? "B" : "A"))
                    .setColor(ConfigWindow.ACCENT));
            for (int a = 0; a < 3; a++) {
                int axis = a;
                HoloWidgetTextBox box = root().add(new HoloWidgetTextBox(MARGIN + LETTER_WIDTH + a * (fieldWidth + GAP),
                        y, fieldWidth, FIELD_HEIGHT, NUMBER_LENGTH, false));
                box.setPlaceholder(Component.literal(axes[a])).setOnSubmit((b, text) -> submitAxis(corner, axis, text));
                corners[c][a] = box;
            }
            root().add(new HoloWidgetButton(MARGIN + column - HERE_WIDTH, y + 1, HERE_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Ici"), b -> pressHere(corner)));
        }
        scanLine = root().add(new HoloLabel(MARGIN, SCAN_ROW, column, LABEL_HEIGHT, Component.empty())
                .setColor(MUTED));

        float half = (column - GAP) / 2f;
        switches[AreaDisplayerModule.FLAG_FRAME] = toggle(MARGIN, FRAME_ROW, column, "Cadre",
                AreaDisplayerModule.FLAG_FRAME);
        resolutionButton = root().add(new HoloWidgetMultiButton(MARGIN, RESOLUTION_ROW, column, BUTTON_HEIGHT,
                Component.literal("Resolution")));
        resolutionButton.on(HoloControl.LEFT_CLICK, b -> stepResolution(1))
                .on(HoloControl.RIGHT_CLICK, b -> stepResolution(-1));
        switches[AreaDisplayerModule.FLAG_AUTO_UPDATE] = toggle(MARGIN, AUTO_ROW + 2, half, "Auto Update",
                AreaDisplayerModule.FLAG_AUTO_UPDATE);
        root().add(new HoloLabel(MARGIN + half + GAP, AUTO_ROW + 4, INTERVAL_LABEL_WIDTH, LABEL_HEIGHT,
                Component.literal("Intervalle (s)")).setColor(MUTED));
        intervalBox = root().add(new HoloWidgetTextBox(MARGIN + half + GAP + INTERVAL_LABEL_WIDTH, AUTO_ROW,
                half - INTERVAL_LABEL_WIDTH, FIELD_HEIGHT, 3, false));
        intervalBox.setPlaceholder(Component.literal("-")).setOnSubmit((b, text) -> submitInterval(text));
        switches[AreaDisplayerModule.FLAG_PLAYERS] = toggle(MARGIN, PLAYERS_ROW, half, "Joueurs",
                AreaDisplayerModule.FLAG_PLAYERS);
        switches[AreaDisplayerModule.FLAG_PLAYER_TRAILS] = toggle(MARGIN + half + GAP, PLAYERS_ROW, half, "Trainees",
                AreaDisplayerModule.FLAG_PLAYER_TRAILS);
        switches[AreaDisplayerModule.FLAG_ENTITIES] = toggle(MARGIN, ENTITIES_ROW, half, "Entites",
                AreaDisplayerModule.FLAG_ENTITIES);
        switches[AreaDisplayerModule.FLAG_ENTITY_TRAILS] = toggle(MARGIN + half + GAP, ENTITIES_ROW, half, "Trainees",
                AreaDisplayerModule.FLAG_ENTITY_TRAILS);
        root().add(new HoloWidgetButton(MARGIN, REFRESH_ROW, column, BUTTON_HEIGHT,
                Component.literal("Actualiser"), b -> module.requestRefresh()));
    }

    private HoloWidgetSwitch toggle(float x, float y, float width, String label, int flag)
    {
        HoloWidgetSwitch toggle = root().add(new HoloWidgetSwitch(x, y, width, SWITCH_HEIGHT, Component.literal(label),
                (widget, requested) -> module.requestFlag(flag, requested)));
        toggle.setPredictive(false).setColors(ON, OFF, TEXT);
        return toggle;
    }

    /** Dessinee seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return AreaConfiguratorModule.localOp(); }

    /** Reservee aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la placer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    // ---------------------------------------------------------------- saisies

    /**
     * Champ {@code axis} du coin {@code corner} valide : brouillon de l'axe (saisie invalide : le champ reprend sa
     * valeur) ; coin envoye des que ses trois axes ont une valeur.
     */
    private void submitAxis(int corner, int axis, String text)
    {
        try {
            draft[corner][axis] = Integer.parseInt(text.trim());
            drafted[corner][axis] = true;
        } catch (NumberFormatException e) {
            corners[corner][axis].setText(axisText(corner, axis));
            return;
        }
        BlockPos pos = cornerPos(corner);
        int[] values = new int[3];
        for (int a = 0; a < 3; a++) {
            if (drafted[corner][a]) values[a] = draft[corner][a];
            else if (pos != null) values[a] = coordinate(pos, a);
            else return;
        }
        drafted[corner][0] = drafted[corner][1] = drafted[corner][2] = false;
        module.requestCorner(corner == 1, values[0], values[1], values[2]);
    }

    /** « Ici » : les brouillons du coin sont abandonnes (le coin vient du serveur). */
    private void pressHere(int corner)
    {
        drafted[corner][0] = drafted[corner][1] = drafted[corner][2] = false;
        module.requestHere(corner == 1);
    }

    private void submitInterval(String text)
    {
        try {
            module.requestInterval(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            intervalBox.setText(displayer != null ? Integer.toString(displayer.interval()) : "");
        }
    }

    /** Palier suivant ({@code +1}) ou precedent ({@code -1}) de la resolution demandee, en boucle. */
    private void stepResolution(int direction)
    {
        if (displayer == null) return;
        int index = 0;
        for (int i = 0; i < RESOLUTIONS.length; i++) if (RESOLUTIONS[i] == displayer.resolution()) index = i;
        int next = Math.floorMod(index + direction, RESOLUTIONS.length);
        module.requestResolution(RESOLUTIONS[next]);
    }

    private BlockPos cornerPos(int corner)
    {
        if (displayer == null) return null;
        return corner == 1 ? displayer.cornerB() : displayer.cornerA();
    }

    private static int coordinate(BlockPos pos, int axis)
    {
        return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
    }

    /** Texte d'un champ : brouillon, sinon valeur du Displayer, sinon vide. */
    private String axisText(int corner, int axis)
    {
        if (drafted[corner][axis]) return Integer.toString(draft[corner][axis]);
        BlockPos pos = cornerPos(corner);
        return pos != null ? Integer.toString(coordinate(pos, axis)) : "";
    }

    // ---------------------------------------------------------------- etat

    /** Relit l'etat du Displayer du marker ({@code null} : absent). Deux cotes, a la creation et a chaque changement. */
    void showState(AreaDisplayerModule displayer)
    {
        this.displayer = displayer;
        for (int c = 0; c < 2; c++) for (int a = 0; a < 3; a++) corners[c][a].setText(axisText(c, a));
        if (displayer == null) {
            zoneLine.setText(Component.literal("Area Displayer requis sur ce marker")).setColor(MISSING);
            scanLine.setText(Component.empty());
            for (HoloWidgetSwitch toggle : switches) toggle.setOn(false);
            resolutionButton.setText(Component.literal("Resolution"));
            intervalBox.setText("");
            return;
        }
        AreaGrid grid = displayer.grid();
        if (grid == null) {
            zoneLine.setText(Component.literal("Aucune zone : poser A et B")).setColor(MUTED);
        } else {
            zoneLine.setText(Component.literal("Zone : " + grid.blocksX() + " x " + grid.blocksY() + " x "
                    + grid.blocksZ() + " blocs (1:" + grid.step() + ")")).setColor(TEXT);
        }
        int progress = displayer.progress();
        scanLine.setText(Component.literal(grid == null ? ""
                : progress == AreaDisplayerModule.NO_SCAN ? "Terrain a jour" : "Scan : " + progress + " %"));
        for (int flag = 0; flag < switches.length; flag++) switches[flag].setOn(displayer.flag(flag));
        resolutionButton.setText(Component.literal(resolutionText(displayer.resolution(), grid)));
        intervalBox.setText(Integer.toString(displayer.interval()));
    }

    /** « Resolution : Auto », « Resolution : 1:1 (bloc par bloc) », « Resolution : 1:1 -> 1:4 » (palier releve). */
    private static String resolutionText(int requested, AreaGrid grid)
    {
        if (requested == AreaGrid.AUTO) return "Resolution : Auto";
        if (grid != null && grid.raised()) return "Resolution : 1:" + requested + " -> 1:" + grid.step();
        return requested == 1 ? "Resolution : 1:1 (bloc par bloc)" : "Resolution : 1:" + requested;
    }
}
