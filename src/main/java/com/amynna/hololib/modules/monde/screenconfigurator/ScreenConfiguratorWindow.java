package com.amynna.hololib.modules.monde.screenconfigurator;

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
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.screen.ScreenModule;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Fenetre du Screen Configurator : ancrage {@code World}, {@code PUBLIC}, affichee seulement chez un operateur
 * ({@link #shown()}) ; acces = operateurs connectes (tenu a jour par le module) ; clics gauche et droit interceptes.
 * Editable par les operateurs (translation, rotation, bouton oeil ; {@code world_editor}), placee par defaut a droite
 * de l'ecran par defaut.
 * <p>
 * Contenu (lignes en constantes, {@link #HEIGHT} calculee depuis la derniere) : titre ; etat (« Screen requis sur ce
 * marker ») ; Mode (clic = camera fixe / joueur) ; selon le mode, dans les memes lignes : camera fixe = position X Y Z
 * et « Ici » (oeil et regard de l'operateur), orientation Lacet / Tangage / Roulis ; joueur = nom (Entree, cherche
 * parmi les joueurs connectes) et « Moi », joueur suivi ; FOV (clic gauche +5, droit -5) ; resolution Largeur x
 * Hauteur et prereglages 16:9 (clic gauche = suivant, droit = precedent) ; images/s et distance de vue a paliers ;
 * rappel du cout.
 * <p>
 * Chaque champ valide (Entree ou Echap) envoie aussitot sa ligne, completee par les valeurs actuelles du Screen ; une
 * saisie invalide remet le champ. Etat relu par {@link #showState} a chaque changement du Screen (jamais par frame) ;
 * un champ en cours de saisie n'est pas ecrase.
 */
public final class ScreenConfiguratorWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
        HoloEditVisibility
{
    public static final int WIDTH = 200;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;

    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int FIELD_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 14;
    private static final int LABEL_HEIGHT = 10;

    // Lignes (ordonnee du haut, en pixels).
    private static final int STATE_ROW = 15;
    private static final int MODE_ROW = STATE_ROW + LABEL_HEIGHT + 3;
    private static final int FIRST_ROW = MODE_ROW + BUTTON_HEIGHT + 5;
    private static final int SECOND_ROW = FIRST_ROW + FIELD_HEIGHT + GAP;
    private static final int FOV_ROW = SECOND_ROW + FIELD_HEIGHT + 5;
    private static final int RESOLUTION_LABEL_ROW = FOV_ROW + BUTTON_HEIGHT + 5;
    private static final int RESOLUTION_ROW = RESOLUTION_LABEL_ROW + LABEL_HEIGHT + 1;
    private static final int PRESET_ROW = RESOLUTION_ROW + FIELD_HEIGHT + 3;
    private static final int FPS_ROW = PRESET_ROW + BUTTON_HEIGHT + 3;
    private static final int DISTANCE_ROW = FPS_ROW + BUTTON_HEIGHT + 3;
    private static final int COST_ROW = DISTANCE_ROW + BUTTON_HEIGHT + 3;
    public static final int HEIGHT = COST_ROW + LABEL_HEIGHT + MARGIN;

    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final HoloColor MUTED = HoloColor.special(0xFF).darker(0.37f);
    private static final HoloColor MISSING = HoloColor.critical(0xFF).lighter(0.18f);
    private static final int NUMBER_LENGTH = 12;
    private static final int ROW_LABEL_WIDTH = 30;
    private static final int SIDE_BUTTON_WIDTH = 30;
    private static final int TIMES_WIDTH = 10;
    private static final float FOV_STEP = 5f;
    /** Prereglages 16:9 du bouton (largeur, hauteur). */
    private static final int[][] PRESETS = {{64, 36}, {128, 72}, {192, 108}, {256, 144}, {320, 180}, {480, 270},
            {640, 360}, {854, 480}, {1024, 576}};
    /** Paliers des images par seconde. */
    private static final int[] FPS_LEVELS = {1, 2, 5, 10, 15, 20, 25, 30};
    /** Paliers de la distance de vue, en chunks ; 0 = celle du spectateur (la plus grande). */
    private static final int[] DISTANCE_LEVELS = {2, 3, 4, 6, 8, 12, 16, 0};

    private final ScreenConfiguratorModule module;
    private final HoloLabel stateLine;
    private final HoloWidgetMultiButton modeButton;
    private final HoloWidget[] positionWidgets;
    private final HoloWidget[] playerWidgets;
    private final HoloWidgetTextBox[] position = new HoloWidgetTextBox[3];
    private final HoloWidgetTextBox[] angles = new HoloWidgetTextBox[3];
    private final HoloWidgetTextBox nameBox;
    private final HoloLabel targetLine;
    private final HoloWidgetMultiButton fovButton;
    private final HoloWidgetTextBox widthBox;
    private final HoloWidgetTextBox heightBox;
    private final HoloWidgetMultiButton presetButton;
    private final HoloWidgetMultiButton fpsButton;
    private final HoloWidgetMultiButton distanceButton;
    /** Screen affiche ({@code null} : absent). */
    private ScreenModule screen;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur)
     * @param transform placement initial, aussi placement de RESET
     */
    ScreenConfiguratorWindow(String key, ScreenConfiguratorModule module, HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER).setStaffTheme(true);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Screen Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        int column = WIDTH - 2 * MARGIN;
        stateLine = root().add(new HoloLabel(MARGIN, STATE_ROW, column, LABEL_HEIGHT, Component.empty())
                .setColor(MUTED));

        modeButton = root().add(new HoloWidgetMultiButton(MARGIN, MODE_ROW, column, BUTTON_HEIGHT,
                Component.literal("Mode")));
        modeButton.on(HoloControl.LEFT_CLICK, b -> toggleMode()).on(HoloControl.RIGHT_CLICK, b -> toggleMode());

        // Camera fixe : position (X Y Z, « Ici ») et orientation (lacet, tangage, roulis).
        float fieldWidth = (column - ROW_LABEL_WIDTH - SIDE_BUTTON_WIDTH - 3 * GAP) / 3f;
        float angleWidth = (column - ROW_LABEL_WIDTH - 2 * GAP) / 3f;
        HoloLabel positionLabel = rowLabel(FIRST_ROW, "Pos.");
        HoloLabel anglesLabel = rowLabel(SECOND_ROW, "Rot.");
        String[] axes = {"X", "Y", "Z"};
        String[] rotations = {"Lacet", "Tangage", "Roulis"};
        for (int a = 0; a < 3; a++) {
            int axis = a;
            position[a] = root().add(new HoloWidgetTextBox(MARGIN + ROW_LABEL_WIDTH + a * (fieldWidth + GAP),
                    FIRST_ROW, fieldWidth, FIELD_HEIGHT, NUMBER_LENGTH, false));
            position[a].setPlaceholder(Component.literal(axes[a])).setOnSubmit((b, text) -> submitPosition(axis, text));
            angles[a] = root().add(new HoloWidgetTextBox(MARGIN + ROW_LABEL_WIDTH + a * (angleWidth + GAP),
                    SECOND_ROW, angleWidth, FIELD_HEIGHT, NUMBER_LENGTH, false));
            angles[a].setPlaceholder(Component.literal(rotations[a])).setOnSubmit((b, text) -> submitAngle(axis, text));
        }
        HoloWidgetButton hereButton = root().add(new HoloWidgetButton(MARGIN + column - SIDE_BUTTON_WIDTH,
                FIRST_ROW + 1, SIDE_BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal("Ici"), b -> module.requestHere()));
        positionWidgets = new HoloWidget[] {positionLabel, anglesLabel, position[0], position[1], position[2],
                angles[0], angles[1], angles[2], hereButton};

        // Joueur : nom et « Moi », joueur suivi.
        HoloLabel playerLabel = rowLabel(FIRST_ROW, "Nom");
        nameBox = root().add(new HoloWidgetTextBox(MARGIN + ROW_LABEL_WIDTH, FIRST_ROW,
                column - ROW_LABEL_WIDTH - SIDE_BUTTON_WIDTH - GAP, FIELD_HEIGHT, ScreenModule.MAX_NAME, false));
        nameBox.setPlaceholder(Component.literal("Joueur")).setOnSubmit((b, text) -> module.requestTarget(text));
        HoloWidgetButton selfButton = root().add(new HoloWidgetButton(MARGIN + column - SIDE_BUTTON_WIDTH,
                FIRST_ROW + 1, SIDE_BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal("Moi"),
                b -> module.requestTargetSelf()));
        targetLine = root().add(new HoloLabel(MARGIN, SECOND_ROW + 4, column, LABEL_HEIGHT, Component.empty())
                .setColor(MUTED));
        playerWidgets = new HoloWidget[] {playerLabel, nameBox, selfButton, targetLine};

        fovButton = root().add(new HoloWidgetMultiButton(MARGIN, FOV_ROW, column, BUTTON_HEIGHT,
                Component.literal("FOV")));
        fovButton.on(HoloControl.LEFT_CLICK, b -> stepFov(FOV_STEP)).on(HoloControl.RIGHT_CLICK, b -> stepFov(-FOV_STEP));

        // Resolution : largeur x hauteur, prereglages.
        root().add(new HoloLabel(MARGIN, RESOLUTION_LABEL_ROW, column, LABEL_HEIGHT,
                Component.literal("Resolution (pixels)")).setColor(MUTED));
        float sizeWidth = (column - TIMES_WIDTH) / 2f;
        widthBox = root().add(new HoloWidgetTextBox(MARGIN, RESOLUTION_ROW, sizeWidth, FIELD_HEIGHT, 4, false));
        widthBox.setPlaceholder(Component.literal("Largeur")).setOnSubmit((b, text) -> submitSize(true, text));
        root().add(new HoloLabel(MARGIN + sizeWidth, RESOLUTION_ROW + 4, TIMES_WIDTH, LABEL_HEIGHT,
                Component.literal("x")).setAlign(HoloLabel.Align.CENTER).setColor(MUTED));
        heightBox = root().add(new HoloWidgetTextBox(MARGIN + sizeWidth + TIMES_WIDTH, RESOLUTION_ROW, sizeWidth,
                FIELD_HEIGHT, 4, false));
        heightBox.setPlaceholder(Component.literal("Hauteur")).setOnSubmit((b, text) -> submitSize(false, text));
        presetButton = root().add(new HoloWidgetMultiButton(MARGIN, PRESET_ROW, column, BUTTON_HEIGHT,
                Component.literal("Prereglage 16:9")));
        presetButton.on(HoloControl.LEFT_CLICK, b -> stepPreset(1)).on(HoloControl.RIGHT_CLICK, b -> stepPreset(-1));
        fpsButton = root().add(new HoloWidgetMultiButton(MARGIN, FPS_ROW, column, BUTTON_HEIGHT,
                Component.literal("Images/s")));
        fpsButton.on(HoloControl.LEFT_CLICK, b -> stepFps(1)).on(HoloControl.RIGHT_CLICK, b -> stepFps(-1));
        distanceButton = root().add(new HoloWidgetMultiButton(MARGIN, DISTANCE_ROW, column, BUTTON_HEIGHT,
                Component.literal("Distance")));
        distanceButton.on(HoloControl.LEFT_CLICK, b -> stepDistance(1))
                .on(HoloControl.RIGHT_CLICK, b -> stepDistance(-1));
        root().add(new HoloLabel(MARGIN, COST_ROW, column, LABEL_HEIGHT,
                Component.literal("Cout (chaque spectateur) : distance, images/s")).setColor(MUTED));
    }

    private HoloLabel rowLabel(int row, String text)
    {
        return root().add(new HoloLabel(MARGIN, row + 4, ROW_LABEL_WIDTH, LABEL_HEIGHT, Component.literal(text))
                .setColor(ConfigWindow.ACCENT));
    }

    /** Dessinee seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return ScreenConfiguratorModule.localOp(); }

    /** Reservee aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la placer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    // ---------------------------------------------------------------- saisies

    private void toggleMode()
    {
        if (screen == null) return;
        module.requestMode(screen.mode() == ScreenModule.Mode.PLAYER ? ScreenModule.Mode.POSITION
                : ScreenModule.Mode.PLAYER);
    }

    /** Coordonnee {@code axis} saisie : position envoyee avec les deux autres axes du Screen. */
    private void submitPosition(int axis, String text)
    {
        if (screen == null) return;
        double value;
        try {
            value = Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            position[axis].setText(positionText(axis));
            return;
        }
        if (!Double.isFinite(value)) {
            position[axis].setText(positionText(axis));
            return;
        }
        module.requestPosition(axis == 0 ? value : screen.x(), axis == 1 ? value : screen.y(),
                axis == 2 ? value : screen.z());
    }

    /** Angle {@code axis} saisi (lacet, tangage, roulis) : orientation envoyee avec les deux autres du Screen. */
    private void submitAngle(int axis, String text)
    {
        if (screen == null) return;
        float value;
        try {
            value = Float.parseFloat(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            angles[axis].setText(angleText(axis));
            return;
        }
        if (!Float.isFinite(value)) {
            angles[axis].setText(angleText(axis));
            return;
        }
        module.requestAngles(axis == 0 ? value : screen.yaw(), axis == 1 ? value : screen.pitch(),
                axis == 2 ? value : screen.roll());
    }

    /** Largeur ({@code width} vrai) ou hauteur saisie : resolution envoyee avec l'autre dimension du Screen. */
    private void submitSize(boolean width, String text)
    {
        if (screen == null) return;
        HoloWidgetTextBox box = width ? widthBox : heightBox;
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            box.setText(Integer.toString(width ? screen.width() : screen.height()));
            return;
        }
        module.requestResolution(width ? value : screen.width(), width ? screen.height() : value);
    }

    private void stepFov(float delta)
    {
        if (screen == null) return;
        float next = Math.round((screen.fov() + delta) / FOV_STEP) * FOV_STEP;
        module.requestFov(Math.max(ScreenModule.MIN_FOV, Math.min(ScreenModule.MAX_FOV, next)));
    }

    /** Prereglage suivant ({@code +1}) ou precedent ({@code -1}) depuis la resolution actuelle, en boucle. */
    private void stepPreset(int direction)
    {
        if (screen == null) return;
        int index = -1;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][0] == screen.width() && PRESETS[i][1] == screen.height()) index = i;
        }
        int next;
        if (index >= 0) {
            next = Math.floorMod(index + direction, PRESETS.length);
        } else {
            // Resolution libre : prereglage le plus proche en hauteur dans le sens demande.
            next = direction > 0 ? 0 : PRESETS.length - 1;
            for (int i = 0; i < PRESETS.length; i++) {
                int candidate = direction > 0 ? i : PRESETS.length - 1 - i;
                boolean beyond = direction > 0 ? PRESETS[candidate][1] > screen.height()
                        : PRESETS[candidate][1] < screen.height();
                if (beyond) {
                    next = candidate;
                    break;
                }
            }
        }
        module.requestResolution(PRESETS[next][0], PRESETS[next][1]);
    }

    /** Palier suivant ({@code +1}) ou precedent ({@code -1}) des images par seconde, borne. */
    private void stepFps(int direction)
    {
        if (screen == null) return;
        int index = 0;
        for (int i = 0; i < FPS_LEVELS.length; i++) if (FPS_LEVELS[i] <= screen.maxFps()) index = i;
        if (FPS_LEVELS[index] != screen.maxFps() && direction < 0) index++;
        int next = Math.max(0, Math.min(FPS_LEVELS.length - 1, index + direction));
        module.requestFps(FPS_LEVELS[next]);
    }

    /** Palier suivant ({@code +1}) ou precedent ({@code -1}) de la distance de vue, borne. */
    private void stepDistance(int direction)
    {
        if (screen == null) return;
        int index = DISTANCE_LEVELS.length - 1;
        for (int i = 0; i < DISTANCE_LEVELS.length; i++) if (DISTANCE_LEVELS[i] == screen.viewDistance()) index = i;
        int next = Math.max(0, Math.min(DISTANCE_LEVELS.length - 1, index + direction));
        module.requestViewDistance(DISTANCE_LEVELS[next]);
    }

    private String positionText(int axis)
    {
        if (screen == null) return "";
        double value = axis == 0 ? screen.x() : axis == 1 ? screen.y() : screen.z();
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String angleText(int axis)
    {
        if (screen == null) return "";
        float value = axis == 0 ? screen.yaw() : axis == 1 ? screen.pitch() : screen.roll();
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ---------------------------------------------------------------- etat

    /** Relit l'etat du Screen du marker ({@code null} : absent). Deux cotes, a la creation et a chaque changement. */
    void showState(ScreenModule screen)
    {
        this.screen = screen;
        boolean player = screen != null && screen.mode() == ScreenModule.Mode.PLAYER;
        for (HoloWidget widget : positionWidgets) widget.setVisible(screen != null && !player);
        for (HoloWidget widget : playerWidgets) widget.setVisible(player);
        for (int a = 0; a < 3; a++) {
            position[a].setText(positionText(a));
            angles[a].setText(angleText(a));
        }
        if (screen == null) {
            stateLine.setText(Component.literal("Screen requis sur ce marker")).setColor(MISSING);
            modeButton.setText(Component.literal("Mode"));
            fovButton.setText(Component.literal("FOV"));
            widthBox.setText("");
            heightBox.setText("");
            fpsButton.setText(Component.literal("Images/s"));
            distanceButton.setText(Component.literal("Distance"));
            return;
        }
        stateLine.setText(Component.literal("Camera de l'ecran du marker")).setColor(MUTED);
        modeButton.setText(Component.literal(player ? "Mode : regard d'un joueur" : "Mode : camera fixe"));
        nameBox.setText(screen.targetName());
        targetLine.setText(Component.literal(screen.target() != null ? "Suivi : " + screen.targetName()
                : "Aucun joueur suivi")).setColor(screen.target() != null ? TEXT : MUTED);
        fovButton.setText(Component.literal(String.format(Locale.ROOT, "FOV : %.0f", screen.fov())));
        widthBox.setText(Integer.toString(screen.width()));
        heightBox.setText(Integer.toString(screen.height()));
        fpsButton.setText(Component.literal("Images/s : " + screen.maxFps()));
        distanceButton.setText(Component.literal(screen.viewDistance() == 0 ? "Distance : celle du spectateur"
                : "Distance : " + screen.viewDistance() + " chunks"));
    }
}
