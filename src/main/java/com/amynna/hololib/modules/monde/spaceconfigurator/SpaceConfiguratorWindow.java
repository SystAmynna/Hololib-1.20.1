package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloTextMetrics;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fenetre du Space Configurator, posee devant la base du cube et tournee comme lui (lacet seulement) : le module la
 * replace quand le cube bouge ({@link SpaceConfiguratorModule}). Ancrage {@code World}, {@code PUBLIC}, <b>non
 * editable</b>, affichee seulement chez un operateur ({@link #shown()}) ; acces = operateurs connectes (tenu a jour
 * par le module). Clics gauche et droit interceptes sur toute la fenetre (viser le fond ne casse pas de bloc).
 * <p>
 * <b>Mode galaxie</b> (T11) : liste des systemes (un {@link HoloWidgetButton} par systeme ; clic = selection, second
 * clic = deselection ; ligne selectionnee surlignee, systeme courant du monde precede de « * »), champ du nom du
 * systeme selectionne ({@link HoloWidgetTextBox}, valide a la fin de la saisie : Entree, Echap ou regard qui s'eloigne,
 * comme tout champ du mod), boutons « Ajouter » (au centre du cadrage, puis selectionne), « Retirer » (second clic de
 * confirmation dans les {@value #CONFIRM_TICKS} ticks), « Editer » (mode systeme), « Monde ici » (le systeme
 * selectionne devient celui du monde ; desactive sur le systeme deja courant, que l'Environment remettrait a sa pose
 * par defaut), « Recadrer », et ligne « Avant du monde » ({@link HoloWidgetMultiButton} : clic gauche = choix
 * suivant, droit = precedent, parmi nord, est, sud, ouest, haut, bas). Un bouton sans objet (pas d'Environment, pas
 * de selection, dernier systeme pour « Retirer ») est desactive : aucun clic pris, couleurs eteintes.
 * <p>
 * <b>Mode systeme</b> (T12) : liste des corps du systeme edite (indentee par parente ; clic = selection, second clic
 * = deselection), « Retour » (mode galaxie) et interrupteur « Animer » (temps d'affichage local, rien n'est envoye) ;
 * a droite, nom du corps selectionne, « + Etoile », « + Planete », « + Lune » (parent = premier corps compatible en
 * remontant depuis la selection, sinon l'etoile principale pour une etoile ou une planete ; « + Lune » desactive
 * sans planete dans la selection), « Retirer » (descendants compris, second clic de confirmation), rayon (km) et
 * luminosite (etoiles) puis periode (jours) dans des champs texte, couleur par une palette, phase par une
 * {@link HoloWidgetValueBar} (0..1 converti en 0-360 degres, pas de 1 % = 3,6 degres ; un {@link HoloLabel} voisin
 * affiche les degres), et ligne de lecture de l'orbite ({@code a} en UA, {@code e}). L'orbite elle-meme s'edite avec
 * la poignee d'orbite ({@link SpaceOrbitHandle}, outils Rotation et Echelle).
 * <p>
 * <b>Champs numeriques</b> : texte libre valide a la fin de la saisie (Entree, Echap ou regard qui s'eloigne), virgule
 * ou point decimal, notation {@code 1e5} acceptee ; une valeur illisible, non finie ou negative remet la valeur
 * courante ; les bornes du modele s'appliquent cote serveur et la valeur bornee revient a la synchronisation.
 * <p>
 * Chaque clic envoie une action au module ({@code sendAction(window, ...)}) que le serveur verifie ; rien n'est
 * modifie localement. Textes et lignes reconstruits a chaque synchronisation ({@link #showState}), jamais par frame ;
 * les lignes de la liste ne sont recreees que si les systemes (ids, noms, systeme courant) changent.
 */
public final class SpaceConfiguratorWindow extends HoloWindow
{
    public static final int WIDTH = 320;
    public static final int HEIGHT = 154;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;
    /** Demi-hauteur de la fenetre, en blocs (placement contre la base du cube). */
    public static final float HALF_HEIGHT = HEIGHT * PIXEL_SCALE * 0.5f;
    /** Delai de confirmation de « Retirer », en ticks. */
    public static final int CONFIRM_TICKS = 60;

    private static final int TEXT = 0xFFE0F0FF;
    private static final int MISSING = 0xFFFF8060;
    private static final int MUTED = 0xFF90A0B0;
    private static final int BUTTON_BACKGROUND = 0x6020A0FF;
    private static final int BUTTON_TEXT = 0xFFFFFFFF;
    private static final int DISABLED_BACKGROUND = 0x40404850;
    private static final int DISABLED_BORDER = 0xFF586070;
    private static final int DISABLED_TEXT = 0xFF808890;
    private static final int CONFIRM_BACKGROUND = 0x80C03020;
    private static final int CONFIRM_BORDER = 0xFFFF6040;
    private static final int ROW = 0x6020A0FF;
    private static final int ROW_SELECTED = 0xA040C0FF;
    /** Nom du systeme selectionne (comme dans le cube). */
    private static final int SELECTED_TEXT = 0xFFFFE040;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 14;
    private static final float ROW_HEIGHT = 12f;
    /** Haut des panneaux de mode, sous le titre et la ligne d'information. */
    private static final int PANEL_TOP = 26;
    private static final int LIST_WIDTH = 112;
    /** Largeur utile d'une ligne de la liste (bordure, marge et barre de defilement deduites). */
    private static final int ROW_TEXT_WIDTH = LIST_WIDTH - 6 - 5 - 6;
    private static final String CURRENT_PREFIX = "* ";
    private static final String ELLIPSIS = "...";
    /** Retrait d'un niveau de parente dans la liste des corps. */
    private static final String INDENT = "  ";
    private static final int FIELD_HEIGHT = 16;
    private static final int FIELD_LABEL_WIDTH = 44;
    private static final int SWATCH_HEIGHT = 12;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCH_BORDER = 0xFF404850;
    private static final int SWATCH_SELECTED_BORDER = 0xFFFFFFFF;
    /** Palette des corps : etoiles (bleutee, blanche, jaune, orangee, rouge), planetes et lunes. */
    private static final int[] PALETTE = {
            0xFFCAD8FF, 0xFFFFF4E8, 0xFFFFE07A, 0xFFFFB46A, 0xFFFF7448,
            0xFF3A6FD8, 0xFF4CA050, 0xFFC1440E, 0xFFD8A06A, 0xFFB4B4B4};
    /** Longueur maximale d'un champ numerique, en caracteres. */
    private static final int NUMBER_LENGTH = 16;

    private static final Component NO_ENVIRONMENT = Component.literal("Aucun Space Environment ici");
    private static final Component REMOVE = Component.literal("Retirer");
    private static final Component REMOVE_CONFIRM = Component.literal("Retirer ?");
    /** Libelles des choix de l'axe avant, dans l'ordre de {@link SpaceConfiguratorModule#FORWARD_YAW}. */
    private static final Component[] FORWARD_NAMES = {
            Component.literal("Nord"), Component.literal("Est"), Component.literal("Sud"),
            Component.literal("Ouest"), Component.literal("Haut"), Component.literal("Bas")};
    private static final Component FORWARD_OTHER = Component.literal("Autre");

    private final SpaceConfiguratorModule module;
    private final HoloLabel infoLine;

    // ---------------------------------------------------------------- mode galaxie

    private final HoloPanel galaxyPanel;
    private final HoloWidgetList list;
    private final HoloWidgetTextBox nameBox;
    private final HoloWidgetButton addButton;
    private final HoloWidgetButton removeButton;
    private final HoloWidgetButton editButton;
    private final HoloWidgetButton worldHereButton;
    private final HoloWidgetButton reframeButton;
    private final HoloWidgetMultiButton forwardButton;

    /** Lignes de la liste, dans l'ordre des en-tetes. */
    private final List<HoloWidgetButton> rows = new ArrayList<>();
    private int[] rowIds = new int[0];
    private String[] rowNames = new String[0];
    private int rowCurrent = -1;

    /** Systeme dont le retrait attend sa confirmation ({@code 0} = aucun) et ticks restants (client). */
    private int armedId;
    private int armedTicks;

    // ---------------------------------------------------------------- mode systeme (T12)

    private final HoloPanel systemPanel;
    private final HoloWidgetButton backButton;
    private final HoloWidgetSwitch animateSwitch;
    private final HoloWidgetList bodyList;
    private final HoloWidgetTextBox bodyNameBox;
    private final HoloWidgetButton addStarButton;
    private final HoloWidgetButton addPlanetButton;
    private final HoloWidgetButton addMoonButton;
    private final HoloWidgetButton removeBodyButton;
    private final HoloLabel radiusLabel;
    private final HoloWidgetTextBox radiusBox;
    private final HoloLabel luminosityLabel;
    private final HoloWidgetTextBox luminosityBox;
    private final HoloLabel periodLabel;
    private final HoloWidgetTextBox periodBox;
    private final HoloWidgetButton[] swatches = new HoloWidgetButton[PALETTE.length];
    private final HoloWidgetValueBar phaseBar;
    private final HoloLabel phaseLabel;
    private final HoloLabel orbitLabel;

    /** Lignes de la liste des corps, dans l'ordre du systeme. */
    private final List<HoloWidgetButton> bodyRows = new ArrayList<>();
    private int[] bodyRowIds = new int[0];
    private String[] bodyRowNames = new String[0];

    /** Corps dont le retrait attend sa confirmation ({@code 0} = aucun) et ticks restants (client). */
    private int armedBody;
    private int armedBodyTicks;

    /** @param module module qui porte la fenetre (demandes envoyees au serveur, etat lu a la synchronisation) */
    SpaceConfiguratorWindow(String key, SpaceConfiguratorModule module)
    {
        super(key, new HoloAnchor.World(), new HoloTransform(), WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setBackground(ConfigWindow.BACKGROUND).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Space Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        infoLine = root().add(new HoloLabel(MARGIN, 14, WIDTH - 2 * MARGIN, 10, Component.empty()).setColor(MUTED));

        int panelHeight = HEIGHT - PANEL_TOP - MARGIN;
        galaxyPanel = root().add(new HoloPanel(0, PANEL_TOP, WIDTH, panelHeight));
        list = galaxyPanel.add(new HoloWidgetList(MARGIN, 0, LIST_WIDTH, panelHeight, ROW_HEIGHT));
        float column = MARGIN + LIST_WIDTH + MARGIN;
        float columnWidth = WIDTH - column - MARGIN;
        float half = (columnWidth - GAP) / 2f;
        nameBox = galaxyPanel.add(new HoloWidgetTextBox(column, 0, columnWidth, 18, SpaceLimits.MAX_NAME_LENGTH,
                false));
        nameBox.setPlaceholder(Component.literal("Nom du systeme"))
                .setOnSubmit((box, text) -> submitName(text));
        float row1 = 22;
        float row2 = row1 + BUTTON_HEIGHT + GAP;
        float row3 = row2 + BUTTON_HEIGHT + GAP;
        addButton = galaxyPanel.add(button(column, row1, half, "Ajouter", b -> module.requestAdd()));
        removeButton = galaxyPanel.add(button(column + half + GAP, row1, half, "Retirer", b -> pressRemove()));
        editButton = galaxyPanel.add(button(column, row2, half, "Editer",
                b -> module.requestMode(SpaceConfiguratorModule.Mode.SYSTEM)));
        worldHereButton = galaxyPanel.add(button(column + half + GAP, row2, half, "Monde ici",
                b -> module.requestSetCurrent(module.selectedSystem())));
        reframeButton = galaxyPanel.add(button(column, row3, columnWidth, "Recadrer", b -> module.requestReframe()));
        float row4 = row3 + BUTTON_HEIGHT + GAP;
        galaxyPanel.add(new HoloLabel(column, row4, columnWidth, 10, Component.literal("Avant du monde"))
                .setColor(MUTED));
        forwardButton = galaxyPanel.add(new HoloWidgetMultiButton(column, row4 + 12, columnWidth, BUTTON_HEIGHT,
                FORWARD_OTHER));

        systemPanel = root().add(new HoloPanel(0, PANEL_TOP, WIDTH, panelHeight));
        float bottom = panelHeight - BUTTON_HEIGHT;
        bodyList = systemPanel.add(new HoloWidgetList(MARGIN, 0, LIST_WIDTH, bottom - GAP, ROW_HEIGHT));
        backButton = systemPanel.add(button(MARGIN, bottom, 40, "Retour",
                b -> module.requestMode(SpaceConfiguratorModule.Mode.GALAXY)));
        animateSwitch = systemPanel.add(new HoloWidgetSwitch(MARGIN + 40 + GAP, bottom, LIST_WIDTH - 40 - GAP,
                BUTTON_HEIGHT, Component.literal("Animer"), (s, on) -> module.setAnimating(on)));
        bodyNameBox = systemPanel.add(new HoloWidgetTextBox(column, 0, columnWidth, FIELD_HEIGHT,
                SpaceLimits.MAX_NAME_LENGTH, false));
        bodyNameBox.setPlaceholder(Component.literal("Nom du corps")).setOnSubmit((box, text) -> submitBodyName(text));
        float third = (columnWidth - 2 * GAP) / 3f;
        float addRow = FIELD_HEIGHT + GAP;
        addStarButton = systemPanel.add(button(column, addRow, third, "+ Etoile",
                b -> pressAdd(SpaceBodyKind.STAR)));
        addPlanetButton = systemPanel.add(button(column + third + GAP, addRow, third, "+ Planete",
                b -> pressAdd(SpaceBodyKind.PLANET)));
        addMoonButton = systemPanel.add(button(column + 2 * (third + GAP), addRow, third, "+ Lune",
                b -> pressAdd(SpaceBodyKind.MOON)));
        float right = column + half + GAP;
        float fieldRow1 = addRow + BUTTON_HEIGHT + GAP;
        radiusLabel = systemPanel.add(fieldLabel(column, fieldRow1, "Rayon km"));
        radiusBox = systemPanel.add(numberBox(column + FIELD_LABEL_WIDTH, fieldRow1, half - FIELD_LABEL_WIDTH,
                SpaceConfiguratorModule.FIELD_RADIUS));
        luminosityLabel = systemPanel.add(fieldLabel(right, fieldRow1, "Lum."));
        luminosityBox = systemPanel.add(numberBox(right + 24, fieldRow1, half - 24,
                SpaceConfiguratorModule.FIELD_LUMINOSITY));
        float fieldRow2 = fieldRow1 + FIELD_HEIGHT + GAP;
        periodLabel = systemPanel.add(fieldLabel(column, fieldRow2, "Periode j"));
        periodBox = systemPanel.add(numberBox(column + FIELD_LABEL_WIDTH, fieldRow2, half - FIELD_LABEL_WIDTH,
                SpaceConfiguratorModule.FIELD_PERIOD));
        removeBodyButton = systemPanel.add(button(right, fieldRow2 + 1, half, "Retirer", b -> pressRemoveBody()));
        float paletteRow = fieldRow2 + FIELD_HEIGHT + GAP;
        float swatchWidth = (columnWidth - (PALETTE.length - 1) * SWATCH_GAP) / (float) PALETTE.length;
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            swatches[i] = systemPanel.add(new HoloWidgetButton(column + i * (swatchWidth + SWATCH_GAP), paletteRow,
                    swatchWidth, SWATCH_HEIGHT, Component.empty(), b -> pressColor(color)));
        }
        float phaseRow = paletteRow + SWATCH_HEIGHT + GAP;
        float barWidth = columnWidth - 56 - GAP;
        phaseBar = systemPanel.add(new HoloWidgetValueBar(column, phaseRow, barWidth, BUTTON_HEIGHT,
                Component.literal("Phase"), (bar, value) -> submitPhase(value)));
        phaseBar.setStep(0.01f);
        phaseLabel = systemPanel.add(new HoloLabel(column + barWidth + GAP, phaseRow + 3, 56, 10, Component.empty())
                .setColor(TEXT));
        orbitLabel = systemPanel.add(new HoloLabel(column, phaseRow + BUTTON_HEIGHT + GAP, columnWidth, 10,
                Component.empty()).setColor(MUTED));
        systemPanel.setVisible(false);
    }

    private static HoloLabel fieldLabel(float x, float y, String text)
    {
        return new HoloLabel(x, y + 4, FIELD_LABEL_WIDTH, 10, Component.literal(text)).setColor(MUTED);
    }

    private HoloWidgetTextBox numberBox(float x, float y, float width, int field)
    {
        HoloWidgetTextBox box = new HoloWidgetTextBox(x, y, width, FIELD_HEIGHT, NUMBER_LENGTH, false);
        box.setPlaceholder(Component.literal("-")).setOnSubmit((b, text) -> submitNumber(b, text, field));
        return box;
    }

    private static HoloWidgetButton button(float x, float y, float width, String text,
                                           Consumer<? super HoloWidgetButton> onPress)
    {
        return new HoloWidgetButton(x, y, width, BUTTON_HEIGHT, Component.literal(text), onPress);
    }

    /** Dessinee seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return SpaceConfiguratorModule.localOp(); }

    /** Fin des delais de confirmation de « Retirer » (client). */
    @Override
    public void tick()
    {
        super.tick();
        if (armedTicks > 0 && --armedTicks == 0) disarm();
        if (armedBodyTicks > 0 && --armedBodyTicks == 0) disarmBody();
    }

    // ---------------------------------------------------------------- acces (lecture, tests)

    public HoloWidgetList systemList() { return list; }

    public HoloWidgetTextBox nameBox() { return nameBox; }

    public HoloWidgetButton addButton() { return addButton; }

    public HoloWidgetButton removeButton() { return removeButton; }

    public HoloWidgetButton editButton() { return editButton; }

    public HoloWidgetButton worldHereButton() { return worldHereButton; }

    public HoloWidgetButton reframeButton() { return reframeButton; }

    public HoloWidgetMultiButton forwardButton() { return forwardButton; }

    public HoloWidgetButton backButton() { return backButton; }

    public HoloWidgetSwitch animateSwitch() { return animateSwitch; }

    public HoloWidgetList bodyList() { return bodyList; }

    public HoloWidgetTextBox bodyNameBox() { return bodyNameBox; }

    public HoloWidgetButton removeBodyButton() { return removeBodyButton; }

    public HoloWidgetValueBar phaseBar() { return phaseBar; }

    // ---------------------------------------------------------------- reactions (client)

    /** Premier clic : arme la confirmation ; second clic sur le meme systeme dans le delai : retrait demande. */
    private void pressRemove()
    {
        int selected = module.selectedSystem();
        if (selected == 0) return;
        if (armedId == selected && armedTicks > 0) {
            disarm();
            module.requestRemove(selected);
            return;
        }
        armedId = selected;
        armedTicks = CONFIRM_TICKS;
        removeButton.setText(REMOVE_CONFIRM).setColors(CONFIRM_BACKGROUND, CONFIRM_BORDER, BUTTON_TEXT);
    }

    private void disarm()
    {
        boolean wasArmed = armedId != 0;
        armedId = 0;
        armedTicks = 0;
        if (wasArmed) {
            removeButton.setText(REMOVE);
            enable(removeButton, removeAllowed());
        }
    }

    private boolean removeAllowed()
    {
        return module.environmentPresent() && module.selectedHeader() != null && module.headers().size() > 1;
    }

    /** Nom valide : renommage demande s'il change ; sinon (vide, inchange, pas de selection) le champ est remis. */
    private void submitName(String text)
    {
        SystemHeader selected = module.selectedHeader();
        if (selected == null) {
            nameBox.setText("");
            return;
        }
        String clean = SpaceLimits.sanitizeName(text);
        if (clean.isEmpty() || clean.equals(selected.name())) {
            nameBox.setText(selected.name());
            return;
        }
        module.requestRename(selected.id(), clean);
    }

    // ---------------------------------------------------------------- reactions du mode systeme (client)

    /** Ajout d'un corps de type {@code kind} autour du parent compatible ({@link #parentFor}). */
    private void pressAdd(SpaceBodyKind kind)
    {
        SpaceSystem system = module.editedSystem();
        if (system == null) return;
        int parent = parentFor(system, module.selectedBodyData(), kind);
        if (parent != 0) module.requestAddBody(kind, parent);
    }

    /**
     * Parent d'un corps ajoute : premier corps compatible en remontant depuis la selection (elle comprise) ; sans
     * selection compatible, l'etoile principale pour une etoile ou une planete, aucun ({@code 0}) pour une lune.
     */
    private static int parentFor(SpaceSystem system, SpaceBody selected, SpaceBodyKind kind)
    {
        for (int index = selected != null ? system.indexOf(selected.id()) : -1; index >= 0;
             index = system.parentIndex(index)) {
            SpaceBody body = system.bodies().get(index);
            if (kind.acceptsParent(body.kind())) return body.id();
        }
        return kind == SpaceBodyKind.MOON ? 0 : system.primaryStar().id();
    }

    /** Premier clic : arme la confirmation ; second clic sur le meme corps dans le delai : retrait demande. */
    private void pressRemoveBody()
    {
        int selected = module.selectedBody();
        if (selected == 0) return;
        if (armedBody == selected && armedBodyTicks > 0) {
            disarmBody();
            module.requestRemoveBody(selected);
            return;
        }
        armedBody = selected;
        armedBodyTicks = CONFIRM_TICKS;
        removeBodyButton.setText(REMOVE_CONFIRM).setColors(CONFIRM_BACKGROUND, CONFIRM_BORDER, BUTTON_TEXT);
    }

    private void disarmBody()
    {
        boolean wasArmed = armedBody != 0;
        armedBody = 0;
        armedBodyTicks = 0;
        if (wasArmed) {
            removeBodyButton.setText(REMOVE);
            enable(removeBodyButton, removeBodyAllowed());
        }
    }

    /** Un corps est selectionne et ce n'est pas la derniere etoile centrale. */
    private boolean removeBodyAllowed()
    {
        SpaceSystem system = module.editedSystem();
        SpaceBody body = module.selectedBodyData();
        return system != null && body != null && !(body.isRoot() && system.rootCount() <= 1);
    }

    private void pressColor(int color)
    {
        SpaceBody body = module.selectedBodyData();
        if (body != null && body.color() != color) module.requestBodyColor(body.id(), color);
    }

    /** Nom valide : renommage demande s'il change ; sinon le champ est remis. */
    private void submitBodyName(String text)
    {
        SpaceBody body = module.selectedBodyData();
        if (body == null) {
            bodyNameBox.setText("");
            return;
        }
        String clean = SpaceLimits.sanitizeName(text);
        if (clean.isEmpty() || clean.equals(body.name())) {
            bodyNameBox.setText(body.name());
            return;
        }
        module.requestBodyName(body.id(), clean);
    }

    /**
     * Champ numerique valide : valeur lue (virgule ou point, notation {@code 1e5}) ; illisible, non finie, negative ou
     * nulle, inchangee ou sans objet pour le corps : la valeur courante est remise ; sinon envoyee (bornee par le
     * serveur).
     */
    private void submitNumber(HoloWidgetTextBox box, String text, int field)
    {
        SpaceBody body = module.selectedBodyData();
        double current = body != null ? fieldValue(body, field) : Double.NaN;
        double value = parse(text);
        if (Double.isNaN(current) || !(value > 0.0) || !Double.isFinite(value)
                || Math.abs(value - current) <= 1e-9 * Math.abs(current)) {
            box.setText(Double.isNaN(current) ? "" : format(current));
            return;
        }
        module.requestBodyNumber(body.id(), field, value);
    }

    /** Valeur courante d'un champ numerique, {@code NaN} s'il ne s'applique pas au corps. */
    private static double fieldValue(SpaceBody body, int field)
    {
        SpaceOrbit orbit = body.orbit();
        return switch (field) {
            case SpaceConfiguratorModule.FIELD_RADIUS -> body.radius();
            case SpaceConfiguratorModule.FIELD_LUMINOSITY -> body.kind() == SpaceBodyKind.STAR ? body.luminosity()
                    : Double.NaN;
            case SpaceConfiguratorModule.FIELD_PERIOD -> orbit != null ? orbit.period() : Double.NaN;
            default -> Double.NaN;
        };
    }

    /** Phase : barre {@code 0..1} convertie en degres {@code 0..360} (pas de 1 % = 3,6 degres). */
    private void submitPhase(float value)
    {
        SpaceBody body = module.selectedBodyData();
        if (body == null || body.orbit() == null) return;
        double degrees = value * 360.0;
        double delta = Math.abs(degrees - body.orbit().phase());
        if (Math.min(delta, 360.0 - delta) <= 1e-6) return;
        module.requestBodyNumber(body.id(), SpaceConfiguratorModule.FIELD_PHASE, degrees);
    }

    // ---------------------------------------------------------------- etat

    /**
     * Met a jour textes, lignes et boutons depuis l'etat du module (deux cotes, a la creation et a chaque
     * synchronisation).
     */
    void showState()
    {
        boolean environment = module.environmentPresent();
        boolean system = module.mode() == SpaceConfiguratorModule.Mode.SYSTEM;
        galaxyPanel.setVisible(!system);
        systemPanel.setVisible(system);
        SystemHeader selected = module.selectedHeader();
        List<SystemHeader> headers = module.headers();

        if (!environment) {
            infoLine.setText(NO_ENVIRONMENT).setColor(MISSING);
        } else if (system) {
            infoLine.setText(Component.literal(systemInfo(selected))).setColor(TEXT);
        } else {
            int count = headers.size();
            infoLine.setText(Component.literal(count + (count > 1 ? " systemes" : " systeme") + " - cadre +/- "
                    + number(module.frameHalf() > 0.0 ? module.frameHalf() : SpaceConfiguratorModule.MIN_HALF_EXTENT)
                    + " al")).setColor(MUTED);
        }

        syncRows(headers, module.currentSystemId());
        int selectedId = selected != null ? selected.id() : 0;
        for (int i = 0; i < rows.size(); i++) {
            boolean on = rowIds[i] == selectedId;
            rows.get(i).setColors(on ? ROW_SELECTED : ROW, ConfigWindow.ACCENT, on ? SELECTED_TEXT : BUTTON_TEXT);
        }

        if (armedId != 0 && armedId != selectedId) disarm();
        nameBox.setText(selected != null ? selected.name() : "");
        enable(addButton, environment);
        if (armedId == 0) enable(removeButton, removeAllowed());
        enable(editButton, environment && selected != null);
        enable(worldHereButton, environment && selected != null && selected.id() != module.currentSystemId());
        enable(reframeButton, true);
        enable(backButton, true);
        showForward(environment, module.forwardChoice());
        showSystem(environment && system ? module.editedSystem() : null);
    }

    /** Ligne d'information du mode systeme : nom, nombre de corps, echelle du cube, loupe des lunes. */
    private String systemInfo(SystemHeader selected)
    {
        SpaceSystem edited = module.editedSystem();
        String name = edited != null ? edited.name()
                : selected != null ? selected.name() : "#" + module.selectedSystem();
        if (edited == null) return "Systeme : " + name;
        int count = edited.bodies().size();
        String text = name + " : " + count + " corps, cadre +/- " + format(module.systemHalf()) + " UA";
        double magnifier = module.moonMagnifier();
        return magnifier > 1.0 ? text + ", lunes x " + format(magnifier) : text;
    }

    /** Panneau du mode systeme : lignes, boutons et champs du corps selectionne ({@code system} nul : tout eteint). */
    private void showSystem(SpaceSystem system)
    {
        syncBodyRows(system);
        SpaceBody body = system != null ? module.selectedBodyData() : null;
        int selectedId = body != null ? body.id() : 0;
        for (int i = 0; i < bodyRows.size(); i++) {
            boolean on = bodyRowIds[i] == selectedId;
            bodyRows.get(i).setColors(on ? ROW_SELECTED : ROW, ConfigWindow.ACCENT, on ? SELECTED_TEXT : BUTTON_TEXT);
        }
        if (armedBody != 0 && armedBody != selectedId) disarmBody();
        animateSwitch.setOn(module.animating());

        boolean full = system == null || system.bodies().size() >= SpaceLimits.MAX_BODIES;
        enable(addStarButton, !full && parentFor(system, body, SpaceBodyKind.STAR) != 0);
        enable(addPlanetButton, !full && parentFor(system, body, SpaceBodyKind.PLANET) != 0);
        enable(addMoonButton, !full && parentFor(system, body, SpaceBodyKind.MOON) != 0);
        if (armedBody == 0) enable(removeBodyButton, removeBodyAllowed());

        boolean star = body != null && body.kind() == SpaceBodyKind.STAR;
        SpaceOrbit orbit = body != null ? body.orbit() : null;
        bodyNameBox.setVisible(body != null);
        radiusLabel.setVisible(body != null);
        radiusBox.setVisible(body != null);
        luminosityLabel.setVisible(star);
        luminosityBox.setVisible(star);
        periodLabel.setVisible(orbit != null);
        periodBox.setVisible(orbit != null);
        phaseBar.setVisible(orbit != null);
        phaseLabel.setVisible(orbit != null);
        for (int i = 0; i < swatches.length; i++) {
            swatches[i].setVisible(body != null);
            boolean on = body != null && body.color() == PALETTE[i];
            swatches[i].setColors(PALETTE[i], on ? SWATCH_SELECTED_BORDER : SWATCH_BORDER, 0xFF000000)
                    .setText(on ? Component.literal("o") : Component.empty());
        }
        if (body == null) {
            bodyNameBox.setText("");
            orbitLabel.setText(Component.literal(system == null ? "" : "Aucun corps selectionne"));
            return;
        }
        bodyNameBox.setText(body.name());
        radiusBox.setText(format(body.radius()));
        if (star) luminosityBox.setText(format(body.luminosity()));
        if (orbit == null) {
            orbitLabel.setText(Component.literal("Centre du systeme (immobile)"));
            return;
        }
        periodBox.setText(format(orbit.period()));
        phaseBar.setValue((float) (orbit.phase() / 360.0));
        phaseLabel.setText(Component.literal(String.format(Locale.ROOT, "%.1f", orbit.phase()).replace('.', ',')
                + "\u00B0"));
        orbitLabel.setText(Component.literal("Orbite : a = " + format(orbit.semiMajorAxis()) + " UA, e = "
                + String.format(Locale.ROOT, "%.3f", orbit.eccentricity()).replace('.', ',')));
    }

    /** Recree les lignes des corps si leurs ids ou noms ont change ; garde le defilement. */
    private void syncBodyRows(SpaceSystem system)
    {
        List<SpaceBody> bodies = system != null ? system.bodies() : List.of();
        boolean same = bodies.size() == bodyRowIds.length;
        for (int i = 0; same && i < bodies.size(); i++) {
            same = bodies.get(i).id() == bodyRowIds[i] && bodies.get(i).name().equals(bodyRowNames[i]);
        }
        if (same) return;
        int first = bodyList.first();
        bodyList.clearItems();
        bodyRows.clear();
        bodyRowIds = new int[bodies.size()];
        bodyRowNames = new String[bodies.size()];
        for (int i = 0; i < bodies.size(); i++) {
            SpaceBody body = bodies.get(i);
            int id = body.id();
            bodyRowIds[i] = id;
            bodyRowNames[i] = body.name();
            StringBuilder text = new StringBuilder();
            for (int index = system.parentIndex(i); index >= 0; index = system.parentIndex(index)) {
                text.append(INDENT);
            }
            text.append(body.name());
            bodyRows.add(bodyList.addItem(new HoloWidgetButton(0, 0, 1, ROW_HEIGHT,
                    Component.literal(fit(text.toString(), ROW_TEXT_WIDTH)),
                    b -> module.requestSelectBody(module.selectedBody() == id ? 0 : id))));
        }
        bodyList.scrollTo(first);
    }

    /** Recree les lignes si les systemes (ids, noms) ou le systeme courant ont change ; garde le defilement. */
    private void syncRows(List<SystemHeader> headers, int current)
    {
        boolean same = headers.size() == rowIds.length && current == rowCurrent;
        for (int i = 0; same && i < headers.size(); i++) {
            same = headers.get(i).id() == rowIds[i] && headers.get(i).name().equals(rowNames[i]);
        }
        if (same) return;
        int first = list.first();
        list.clearItems();
        rows.clear();
        rowIds = new int[headers.size()];
        rowNames = new String[headers.size()];
        rowCurrent = current;
        for (int i = 0; i < headers.size(); i++) {
            SystemHeader header = headers.get(i);
            int id = header.id();
            rowIds[i] = id;
            rowNames[i] = header.name();
            String text = fit((id == current ? CURRENT_PREFIX : "") + header.name(), ROW_TEXT_WIDTH);
            rows.add(list.addItem(new HoloWidgetButton(0, 0, 1, ROW_HEIGHT, Component.literal(text),
                    b -> module.requestSelectSystem(module.selectedSystem() == id ? 0 : id))));
        }
        list.scrollTo(first);
    }

    /** Ligne « Avant du monde » : choix courant ; clic gauche = suivant, droit = precedent. */
    private void showForward(boolean environment, int choice)
    {
        forwardButton.setText(choice >= 0 ? FORWARD_NAMES[choice] : FORWARD_OTHER);
        forwardButton.off(HoloControl.LEFT_CLICK).off(HoloControl.RIGHT_CLICK);
        if (!environment) {
            forwardButton.setColors(DISABLED_BACKGROUND, DISABLED_BORDER, DISABLED_TEXT);
            return;
        }
        int count = FORWARD_NAMES.length;
        int next = choice < 0 ? 0 : (choice + 1) % count;
        int previous = choice < 0 ? count - 1 : (choice + count - 1) % count;
        forwardButton.on(HoloControl.LEFT_CLICK, b -> module.requestForward(next))
                .on(HoloControl.RIGHT_CLICK, b -> module.requestForward(previous))
                .setColors(BUTTON_BACKGROUND, ConfigWindow.ACCENT, BUTTON_TEXT);
    }

    /** Active (clic gauche, couleurs normales) ou desactive (aucun clic, couleurs eteintes) un bouton. */
    private static void enable(HoloWidgetButton button, boolean enabled)
    {
        if (enabled) {
            button.setTriggers(HoloControl.LEFT_CLICK).setColors(BUTTON_BACKGROUND, ConfigWindow.ACCENT, BUTTON_TEXT);
        } else {
            button.setTriggers().setColors(DISABLED_BACKGROUND, DISABLED_BORDER, DISABLED_TEXT);
        }
    }

    /** Texte tronque (points de suspension) pour tenir dans {@code width} pixels. */
    private static String fit(String text, int width)
    {
        if (HoloTextMetrics.width(text) <= width) return text;
        int end = text.length();
        while (end > 0 && HoloTextMetrics.width(text.substring(0, end) + ELLIPSIS) > width) end--;
        // Ne pas couper une paire de surrogates.
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + ELLIPSIS;
    }

    /** Demi-etendue du cadrage (valeur 1-2-5) : entier si possible, sinon une decimale significative. */
    private static String number(double value)
    {
        if (value >= 1.0 && value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value).replace('.', ',');
    }

    /**
     * Nombre d'un champ ou d'une lecture : 6 chiffres significatifs au plus, sans zeros inutiles, virgule decimale ;
     * notation {@code 1,5e7} hors de {@code [0,001 ; 10^7[}.
     */
    static String format(double value)
    {
        if (!Double.isFinite(value)) return "?";
        if (value == 0.0) return "0";
        double abs = Math.abs(value);
        if (abs >= 1e7 || abs < 1e-3) {
            int exponent = (int) Math.floor(Math.log10(abs));
            BigDecimal mantissa = new BigDecimal(value / Math.pow(10.0, exponent)).round(new MathContext(4))
                    .stripTrailingZeros();
            if (mantissa.abs().compareTo(BigDecimal.TEN) >= 0) {
                mantissa = mantissa.movePointLeft(1).stripTrailingZeros();
                exponent++;
            }
            return mantissa.toPlainString().replace('.', ',') + "e" + exponent;
        }
        return new BigDecimal(value).round(new MathContext(6)).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** Lecture d'un nombre saisi (virgule ou point, espaces ignores, notation {@code 1e5}) ; {@code NaN} sinon. */
    static double parse(String text)
    {
        String clean = text == null ? "" : text.replace(" ", "").replace(',', '.');
        if (clean.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
