package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloTextMetrics;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.SpaceBodyTextures;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceRotation;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import com.amynna.hololib.modules.dimension.space.model.SpaceTextureRef;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapCube;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapModule;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fenetre d'edition du Space Configurator (T42 : branchee sur la Space Map du meme marker, D20). Ancrage {@code World},
 * {@code PUBLIC}, affichee seulement chez un operateur ({@link #shown()}) ; acces = operateurs connectes (tenu a jour
 * par le module). Clics gauche et droit interceptes sur toute la fenetre (viser le fond ne casse pas de bloc). Pas de
 * liste ni de navigation : elle edite la <b>selection de la Map</b> (systeme selectionne sur sa carte galactique, corps
 * selectionne dans sa vue systeme), choisie dans l'arborescence ou le cube de la Map.
 * <p>
 * <b>Edition</b> ({@link #opRequired()} vrai, {@link #ownRequired()} faux, {@code world_editor}) : translation,
 * rotation et affichage ({@link HoloEditVisibility}, bouton oeil). Placee a sa creation a droite de la fenetre de la Map
 * par defaut ({@link SpaceConfiguratorModule#defaultWindowTransform}, aussi son RESET), puis independante.
 * <p>
 * <b>Sans Space Map</b> sur le marker (C78) : ligne d'information « Space Map requise sur ce marker », boutons
 * desactives (aucun clic pris, couleurs eteintes), champs masques ; de meme sans Environment.
 * <p>
 * <b>Carte galactique de la Map</b> : nom du systeme selectionne ({@link HoloWidgetTextBox}, valide a la fin de la
 * saisie : Entree, Echap ou regard qui s'eloigne), « Ajouter » (au centre de la carte, puis selectionne par la Map),
 * « Retirer » (second clic de confirmation dans les {@value #CONFIRM_TICKS} ticks ; desactive pour le dernier
 * systeme), « Monde ici » (desactive sur le systeme deja courant, que l'Environment remettrait a sa pose par defaut),
 * ligne « Avant du monde » ({@link HoloWidgetMultiButton} : clic gauche = choix suivant, droit = precedent, parmi nord,
 * est, sud, ouest, haut, bas), ligne « Temps (s/tick) » (echelle de temps des astres, C119 : {@code 360} par defaut,
 * {@code 0} = astres figes, toute la galaxie de la dimension, sans saut) et ligne « Distorsion (al/s) ». Les reglages
 * restent editables sans selection.
 * <p>
 * <b>Vue systeme de la Map</b> : nom du corps selectionne, « +Etoile », « +Planete », « +Lune », « +Mineur » (parent =
 * premier corps compatible en remontant depuis la selection, sinon l'etoile principale pour une etoile, une planete ou
 * un objet mineur ; « +Lune » desactive sans planete ni objet mineur dans la selection ; le corps ajoute est
 * selectionne par la Map), puis trois <b>onglets</b> (T44, choix local a chaque client, rien n'est envoye ; masques
 * sans corps selectionne). Deux boutons du corps (D34, C133) : « Se teleporter » au bout de la ligne du nom (le
 * vaisseau est pose pres du corps, autre systeme compris : {@code SpaceEnvironmentModule.teleportTo}) et « RESET » au
 * bout de la rangee d'onglets (tout le corps remis a ses valeurs par defaut, second clic de confirmation) :
 * <ul>
 *     <li>« Corps » : « Retirer » (descendants compris, second clic de confirmation), « Orbite » (la Map centre sa vue
 *     sur le parent du corps, son orbite entiere dans le cube : la poignee d'orbite y apparait), rayon (km) et
 *     luminosite (etoiles), masse (kg, T39 : notation {@code 5,972e24} acceptee) puis periode (jours) dans des champs
 *     texte, couleur par une palette, phase par une {@link HoloWidgetValueBar} (0..1 converti en 0-360 degres, pas de
 *     1 % ; un {@link HoloLabel} voisin affiche les degres), et ligne de lecture de l'orbite ({@code a} en UA,
 *     {@code e}). L'orbite elle-meme s'edite avec la poignee d'orbite ({@link SpaceOrbitHandle}, outils Rotation et
 *     Echelle) ;</li>
 *     <li>« Rotation » (rotation propre, C71) : « Rotation (h) » (periode siderale signee, {@code 0} = synchrone,
 *     negative = retrograde), « Obliquite », « Noeud » (longitude du noeud du pole), « Phase rot. » (meridien origine
 *     a {@code t = 0}), en degres, champs texte (toute valeur finie, bornee par le serveur), ligne d'etat (sens et
 *     duree d'un tour) ;</li>
 *     <li>« Texture » : apercu de la texture affichee (image entiere, dessinee par le renderer de la fenetre a
 *     {@link #PREVIEW_X}, {@link #PREVIEW_Y} ; couleur du corps tant qu'elle n'est pas chargee), texture actuelle,
 *     bouton « Importer » (T45 : image PNG / JPEG de l'ordinateur de l'operateur, envoyee au serveur puis appliquee
 *     au corps ; le bouton affiche l'avancement, « ... » pendant le choix du fichier, et reste desactive pendant un
 *     import) et liste « Auto (texture retenue) » + textures integrees ({@link SpaceBodyTextures#all()}, nom
 *     lisible), la texture choisie en vert ; clic = texture envoyee.</li>
 * </ul>
 * Les coordonnees de la selection sont dans la fenetre de la Map ; la ligne d'information dit ce qui est edite et si la
 * poignee d'orbite est sur la carte.
 * <p>
 * <b>Champs numeriques</b> : texte libre valide a la fin de la saisie, virgule ou point decimal, notation {@code 1e5}
 * acceptee ; une valeur illisible, non finie ou (champs strictement positifs : rayon, luminosite, periode, masse)
 * negative remet la valeur courante, comme le texte affiche laisse tel quel (arrondi a l'affichage : il n'ecrase pas
 * la valeur exacte) ; les bornes du modele s'appliquent cote serveur et la valeur bornee revient a la synchronisation.
 * Chaque champ est une action a lui seul : le serveur ne change que ce champ, sur sa propre valeur courante du corps
 * (une copie client en retard n'ecrase pas les autres).
 * <p>
 * Chaque clic envoie une action au module ({@code sendAction(window, ...)}), avec les ids vises ; le serveur la
 * verifie ; rien n'est modifie localement. Textes et boutons reconstruits par {@link #showState} quand l'etat du module
 * ou ce que montre la Map change, jamais par frame.
 */
public final class SpaceConfiguratorWindow extends HoloWindow
        implements HoloEditTranslation, HoloEditRotation, HoloEditVisibility
{
    /** Largeur, en pixels (208 depuis T42 : plus de liste ; 320 avant). */
    public static final int WIDTH = 208;
    /** Hauteur, en pixels (192 depuis T44 : rangee d'onglets de la vue systeme ; 174 depuis T39). */
    public static final int HEIGHT = 192;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;
    /** Demi-hauteur de la fenetre, en blocs (placement par defaut). */
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
    /** Systeme courant du monde (couleur de son anneau dans la Map). */
    private static final int CURRENT_TEXT = SpaceMapCube.WORLD_COLOR;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 14;
    /** Haut des panneaux, sous le titre et la ligne d'information. */
    private static final int PANEL_TOP = 26;
    private static final String ELLIPSIS = "...";
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
    /** Largeur des libelles « Temps (s/tick) » et « Distorsion (al/s) », en pixels. */
    private static final int SETTING_LABEL_WIDTH = 92;
    /** Largeur du bouton « Orbite », en pixels (a cote de « Retirer »). */
    private static final int ORBIT_WIDTH = 40;
    /** Largeur du bouton « RESET », en pixels (bout de la rangee d'onglets). */
    private static final int RESET_WIDTH = 40;
    /** Largeur du bouton « Se teleporter », en pixels (bout de la ligne du nom du corps). */
    private static final int TELEPORT_WIDTH = 74;
    /** Onglets de la vue systeme (T44). */
    public static final int TAB_BODY = 0;
    public static final int TAB_ROTATION = 1;
    public static final int TAB_TEXTURE = 2;
    private static final Component[] TAB_NAMES = {
            Component.literal("Corps"), Component.literal("Rotation"), Component.literal("Texture")};
    /** Ordonnee de la rangee d'onglets dans le panneau de la vue systeme (sous les boutons d'ajout), en pixels. */
    private static final int TAB_ROW = FIELD_HEIGHT + GAP + BUTTON_HEIGHT + GAP;
    /** Ordonnee du contenu des onglets dans le panneau de la vue systeme, en pixels. */
    private static final int TAB_CONTENT = TAB_ROW + BUTTON_HEIGHT + GAP;
    private static final int TAB_ACTIVE_BACKGROUND = 0xC02080C0;
    private static final int TAB_ACTIVE_BORDER = 0xFFFFFFFF;
    /** Largeur des libelles de l'onglet « Rotation », en pixels. */
    private static final int ROTATION_LABEL_WIDTH = 64;
    /** Hauteur d'une ligne de la liste des textures, en pixels. */
    private static final int TEXTURE_ROW_HEIGHT = 10;
    /** Largeur du bouton « Importer » (T45), en pixels. */
    private static final int IMPORT_WIDTH = 56;
    /** Apercu de la texture (onglet « Texture ») : abscisse dans le panneau, en pixels (aussi dans la fenetre). */
    public static final int PREVIEW_X = MARGIN;
    /** Ordonnee de l'apercu dans la fenetre, en pixels (renderer de la fenetre). */
    public static final int PREVIEW_Y = PANEL_TOP + TAB_CONTENT;
    /** Taille de l'apercu, en pixels (image equirectangulaire entiere, 2:1). */
    public static final int PREVIEW_WIDTH = 48;
    public static final int PREVIEW_HEIGHT = 24;
    private static final int ROW_SELECTED_BACKGROUND = 0xA0208040;
    private static final int ROW_SELECTED_BORDER = 0xFF60FF80;

    private static final Component NO_MAP = Component.literal("Space Map requise sur ce marker");
    private static final Component NO_ENVIRONMENT = Component.literal("Aucun Space Environment ici");
    private static final Component NO_SYSTEM = Component.literal("Choisir un systeme dans la Map");
    private static final Component REMOVE = Component.literal("Retirer");
    private static final Component REMOVE_CONFIRM = Component.literal("Retirer ?");
    /** Libelles des choix de l'axe avant, dans l'ordre de {@link SpaceConfiguratorModule#FORWARD_YAW}. */
    private static final Component[] FORWARD_NAMES = {
            Component.literal("Nord"), Component.literal("Est"), Component.literal("Sud"),
            Component.literal("Ouest"), Component.literal("Haut"), Component.literal("Bas")};
    private static final Component FORWARD_OTHER = Component.literal("Autre");
    private static final Component NO_BODY = Component.literal("Choisir un corps dans la Map");
    private static final Component ROTATION_HINT = Component.literal("0 h = synchrone, < 0 = retrograde");
    private static final Component AUTO_LABEL = Component.literal("Auto");
    private static final Component RESET = Component.literal("RESET");
    private static final Component RESET_CONFIRM = Component.literal("RESET ?");
    private static final Component IMPORT = Component.literal("Importer");
    private static final Component IMPORT_PREPARING = Component.literal("...");

    private final SpaceConfiguratorModule module;
    private final HoloLabel infoLine;

    // ---------------------------------------------------------------- carte galactique

    private final HoloPanel galaxyPanel;
    private final HoloWidgetTextBox nameBox;
    private final HoloWidgetButton addButton;
    private final HoloWidgetButton removeButton;
    private final HoloWidgetButton worldHereButton;
    private final HoloWidgetMultiButton forwardButton;
    private final HoloLabel timeScaleLabel;
    private final HoloWidgetTextBox timeScaleBox;
    private final HoloLabel warpLabel;
    private final HoloWidgetTextBox warpBox;

    /** Systeme dont le retrait attend sa confirmation ({@code 0} = aucun) et ticks restants (client). */
    private int armedId;
    private int armedTicks;

    // ---------------------------------------------------------------- vue systeme

    private final HoloPanel systemPanel;
    private final HoloWidgetTextBox bodyNameBox;
    private final HoloWidgetButton addStarButton;
    private final HoloWidgetButton addPlanetButton;
    private final HoloWidgetButton addMoonButton;
    private final HoloWidgetButton addMinorButton;
    private final HoloWidgetButton removeBodyButton;
    private final HoloWidgetButton orbitButton;
    private final HoloLabel radiusLabel;
    private final HoloWidgetTextBox radiusBox;
    private final HoloLabel luminosityLabel;
    private final HoloWidgetTextBox luminosityBox;
    private final HoloLabel massLabel;
    private final HoloWidgetTextBox massBox;
    private final HoloLabel periodLabel;
    private final HoloWidgetTextBox periodBox;
    private final HoloWidgetButton[] swatches = new HoloWidgetButton[PALETTE.length];
    private final HoloWidgetValueBar phaseBar;
    private final HoloLabel phaseLabel;
    private final HoloLabel orbitLabel;

    /** Corps dont le retrait attend sa confirmation ({@code 0} = aucun) et ticks restants (client). */
    private int armedBody;
    private int armedBodyTicks;

    // ---------------------------------------------------------------- onglets de la vue systeme (T44)

    private final HoloWidgetButton[] tabButtons = new HoloWidgetButton[TAB_NAMES.length];
    /** « RESET » (D34) : corps remis a ses valeurs par defaut, second clic de confirmation. */
    private final HoloWidgetButton resetButton;
    /** « Se teleporter » (D34) : vaisseau pose pres du corps selectionne. */
    private final HoloWidgetButton teleportButton;
    /** Corps dont le RESET attend sa confirmation ({@code 0} = aucun) et ticks restants (client). */
    private int armedReset;
    private int armedResetTicks;
    /** « Choisir un corps dans la Map » a la place des onglets quand aucun corps n'est selectionne. */
    private final HoloLabel noBodyLabel;
    private final HoloPanel bodyTab;
    private final HoloPanel rotationTab;
    private final HoloPanel textureTab;
    /** Onglet affiche ({@link #TAB_BODY}, {@link #TAB_ROTATION}, {@link #TAB_TEXTURE}), local a chaque client. */
    private int tab = TAB_BODY;

    private final HoloWidgetTextBox rotationPeriodBox;
    private final HoloWidgetTextBox obliquityBox;
    private final HoloWidgetTextBox nodeBox;
    private final HoloWidgetTextBox rotationPhaseBox;
    private final HoloLabel rotationInfo;

    private final HoloLabel textureLabel;
    private final HoloWidgetButton importButton;
    /** Etat de l'import affiche par le bouton ({@link SpaceConfiguratorModule#importProgress}, client). */
    private int shownImport = SpaceConfiguratorModule.IMPORT_IDLE;
    private final HoloWidgetList textureList;
    /** Lignes de la liste : « Auto » puis textures integrees ({@link SpaceBodyTextures#all()}) ; valeurs envoyees. */
    private final HoloWidgetButton[] textureRows;
    private final String[] textureValues;
    /** Corps (systeme, id) pour lequel la liste a ete defilee jusqu'a la texture choisie. */
    private int scrolledSystem = -1;
    private int scrolledBody = -1;
    /** Apercu lu par le renderer de la fenetre : systeme et indice du corps, {@code -1} = pas d'apercu (client). */
    private SpaceSystem previewSystem;
    private int previewIndex = -1;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur, etat lu a la synchronisation)
     * @param transform placement initial, aussi placement de RESET
     */
    SpaceConfiguratorWindow(String key, SpaceConfiguratorModule module, HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setBackground(ConfigWindow.BACKGROUND).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Space Configurator"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        infoLine = root().add(new HoloLabel(MARGIN, 14, WIDTH - 2 * MARGIN, 10, Component.empty()).setColor(MUTED));

        int panelHeight = HEIGHT - PANEL_TOP - MARGIN;
        float column = MARGIN;
        float columnWidth = WIDTH - 2 * MARGIN;
        float half = (columnWidth - GAP) / 2f;
        galaxyPanel = root().add(new HoloPanel(0, PANEL_TOP, WIDTH, panelHeight));
        nameBox = galaxyPanel.add(new HoloWidgetTextBox(column, 0, columnWidth, 18, SpaceLimits.MAX_NAME_LENGTH,
                false));
        nameBox.setPlaceholder(Component.literal("Nom du systeme"))
                .setOnSubmit((box, text) -> submitName(text));
        float row1 = 22;
        float row2 = row1 + BUTTON_HEIGHT + GAP;
        float row3 = row2 + BUTTON_HEIGHT + GAP;
        addButton = galaxyPanel.add(button(column, row1, half, "Ajouter", b -> module.requestAdd()));
        removeButton = galaxyPanel.add(button(column + half + GAP, row1, half, "Retirer", b -> pressRemove()));
        worldHereButton = galaxyPanel.add(button(column, row2, columnWidth, "Monde ici", b -> pressWorldHere()));
        galaxyPanel.add(new HoloLabel(column, row3, columnWidth, 10, Component.literal("Avant du monde"))
                .setColor(MUTED));
        forwardButton = galaxyPanel.add(new HoloWidgetMultiButton(column, row3 + 12, columnWidth, BUTTON_HEIGHT,
                FORWARD_OTHER));
        // Deux dernieres lignes du panneau (bord bas du dernier champ = bas du panneau).
        float warpRow = panelHeight - FIELD_HEIGHT;
        float timeRow = warpRow - FIELD_HEIGHT - GAP;
        timeScaleLabel = galaxyPanel.add(new HoloLabel(column, timeRow + 4, SETTING_LABEL_WIDTH, 10,
                Component.literal("Temps (s/tick)")).setColor(MUTED));
        timeScaleBox = galaxyPanel.add(new HoloWidgetTextBox(column + SETTING_LABEL_WIDTH, timeRow,
                columnWidth - SETTING_LABEL_WIDTH, FIELD_HEIGHT, NUMBER_LENGTH, false));
        timeScaleBox.setPlaceholder(Component.literal("-")).setOnSubmit((box, text) -> submitTimeScale(text));
        warpLabel = galaxyPanel.add(new HoloLabel(column, warpRow + 4, SETTING_LABEL_WIDTH, 10,
                Component.literal("Distorsion (al/s)")).setColor(MUTED));
        warpBox = galaxyPanel.add(new HoloWidgetTextBox(column + SETTING_LABEL_WIDTH, warpRow,
                columnWidth - SETTING_LABEL_WIDTH, FIELD_HEIGHT, NUMBER_LENGTH, false));
        warpBox.setPlaceholder(Component.literal("-")).setOnSubmit((box, text) -> submitWarp(text));

        systemPanel = root().add(new HoloPanel(0, PANEL_TOP, WIDTH, panelHeight));
        // Nom du corps, puis « Se teleporter » (D34) au bout de la ligne.
        bodyNameBox = systemPanel.add(new HoloWidgetTextBox(column, 0, columnWidth - TELEPORT_WIDTH - GAP,
                FIELD_HEIGHT, SpaceLimits.MAX_NAME_LENGTH, false));
        bodyNameBox.setPlaceholder(Component.literal("Nom du corps")).setOnSubmit((box, text) -> submitBodyName(text));
        teleportButton = systemPanel.add(button(column + columnWidth - TELEPORT_WIDTH, 1, TELEPORT_WIDTH,
                "Se teleporter", b -> pressTeleport()));
        // Un bouton par type de corps (libelles sans espace).
        float quarter = (columnWidth - 3 * GAP) / 4f;
        float addRow = FIELD_HEIGHT + GAP;
        addStarButton = systemPanel.add(button(column, addRow, quarter, "+Etoile",
                b -> pressAdd(SpaceBodyKind.STAR)));
        addPlanetButton = systemPanel.add(button(column + quarter + GAP, addRow, quarter, "+Planete",
                b -> pressAdd(SpaceBodyKind.PLANET)));
        addMoonButton = systemPanel.add(button(column + 2 * (quarter + GAP), addRow, quarter, "+Lune",
                b -> pressAdd(SpaceBodyKind.MOON)));
        addMinorButton = systemPanel.add(button(column + 3 * (quarter + GAP), addRow, quarter, "+Mineur",
                b -> pressAdd(SpaceBodyKind.MINOR)));
        // Onglets (T44) : « Corps », « Rotation », « Texture » ; choix local a chaque client (rien n'est envoye).
        // Onglets, puis « RESET » (D34) au bout de la rangee.
        float tabWidth = (columnWidth - RESET_WIDTH - TAB_NAMES.length * GAP) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int index = i;
            tabButtons[i] = systemPanel.add(new HoloWidgetButton(column + i * (tabWidth + GAP), TAB_ROW, tabWidth,
                    BUTTON_HEIGHT, TAB_NAMES[i], b -> selectTab(index)));
        }
        resetButton = systemPanel.add(button(column + columnWidth - RESET_WIDTH, TAB_ROW, RESET_WIDTH, "RESET",
                b -> pressReset()));
        noBodyLabel = systemPanel.add(new HoloLabel(column, TAB_ROW + 3, columnWidth, 10, Component.empty())
                .setColor(MUTED));
        float tabHeight = panelHeight - TAB_CONTENT;

        // ---- onglet « Corps » : champs de T12 et T39, inchanges
        bodyTab = systemPanel.add(new HoloPanel(0, TAB_CONTENT, WIDTH, tabHeight));
        float right = column + half + GAP;
        float fieldRow1 = 0;
        radiusLabel = bodyTab.add(fieldLabel(column, fieldRow1, "Rayon km"));
        radiusBox = bodyTab.add(numberBox(column + FIELD_LABEL_WIDTH, fieldRow1, half - FIELD_LABEL_WIDTH,
                SpaceConfiguratorModule.FIELD_RADIUS));
        luminosityLabel = bodyTab.add(fieldLabel(right, fieldRow1, "Lum."));
        luminosityBox = bodyTab.add(numberBox(right + 24, fieldRow1, half - 24,
                SpaceConfiguratorModule.FIELD_LUMINOSITY));
        // Masse (T39) : ligne entiere (valeurs en notation scientifique).
        float massRow = fieldRow1 + FIELD_HEIGHT + GAP;
        massLabel = bodyTab.add(fieldLabel(column, massRow, "Masse kg"));
        massBox = bodyTab.add(numberBox(column + FIELD_LABEL_WIDTH, massRow, columnWidth - FIELD_LABEL_WIDTH,
                SpaceConfiguratorModule.FIELD_MASS));
        float fieldRow2 = massRow + FIELD_HEIGHT + GAP;
        periodLabel = bodyTab.add(fieldLabel(column, fieldRow2, "Periode j"));
        periodBox = bodyTab.add(numberBox(column + FIELD_LABEL_WIDTH, fieldRow2, half - FIELD_LABEL_WIDTH,
                SpaceConfiguratorModule.FIELD_PERIOD));
        removeBodyButton = bodyTab.add(button(right, fieldRow2 + 1, half - ORBIT_WIDTH - GAP, "Retirer",
                b -> pressRemoveBody()));
        orbitButton = bodyTab.add(button(right + half - ORBIT_WIDTH, fieldRow2 + 1, ORBIT_WIDTH, "Orbite",
                b -> pressOrbit()));
        float paletteRow = fieldRow2 + FIELD_HEIGHT + GAP;
        float swatchWidth = (columnWidth - (PALETTE.length - 1) * SWATCH_GAP) / (float) PALETTE.length;
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            swatches[i] = bodyTab.add(new HoloWidgetButton(column + i * (swatchWidth + SWATCH_GAP), paletteRow,
                    swatchWidth, SWATCH_HEIGHT, Component.empty(), b -> pressColor(color)));
        }
        float phaseRow = paletteRow + SWATCH_HEIGHT + GAP;
        float barWidth = columnWidth - 56 - GAP;
        phaseBar = bodyTab.add(new HoloWidgetValueBar(column, phaseRow, barWidth, BUTTON_HEIGHT,
                Component.literal("Phase"), (bar, value) -> submitPhase(value)));
        phaseBar.setStep(0.01f);
        phaseLabel = bodyTab.add(new HoloLabel(column + barWidth + GAP, phaseRow + 3, 56, 10, Component.empty())
                .setColor(TEXT));
        orbitLabel = bodyTab.add(new HoloLabel(column, phaseRow + BUTTON_HEIGHT + GAP, columnWidth, 10,
                Component.empty()).setColor(MUTED));

        // ---- onglet « Rotation » (T44, C71) : un champ texte par valeur, une action par champ
        rotationTab = systemPanel.add(new HoloPanel(0, TAB_CONTENT, WIDTH, tabHeight));
        float rotationBoxX = column + ROTATION_LABEL_WIDTH;
        float rotationBoxWidth = columnWidth - ROTATION_LABEL_WIDTH;
        float rotationRow = 0;
        rotationTab.add(rotationLabel(column, rotationRow, "Rotation (h)"));
        rotationPeriodBox = rotationTab.add(numberBox(rotationBoxX, rotationRow, rotationBoxWidth,
                SpaceConfiguratorModule.FIELD_ROTATION_PERIOD));
        rotationRow += FIELD_HEIGHT + GAP;
        rotationTab.add(rotationLabel(column, rotationRow, "Obliquite"));
        obliquityBox = rotationTab.add(numberBox(rotationBoxX, rotationRow, rotationBoxWidth,
                SpaceConfiguratorModule.FIELD_OBLIQUITY));
        rotationRow += FIELD_HEIGHT + GAP;
        rotationTab.add(rotationLabel(column, rotationRow, "Noeud"));
        nodeBox = rotationTab.add(numberBox(rotationBoxX, rotationRow, rotationBoxWidth,
                SpaceConfiguratorModule.FIELD_NODE));
        rotationRow += FIELD_HEIGHT + GAP;
        rotationTab.add(rotationLabel(column, rotationRow, "Phase rot."));
        rotationPhaseBox = rotationTab.add(numberBox(rotationBoxX, rotationRow, rotationBoxWidth,
                SpaceConfiguratorModule.FIELD_ROTATION_PHASE));
        rotationRow += FIELD_HEIGHT + GAP + 2;
        rotationInfo = rotationTab.add(new HoloLabel(column, rotationRow, columnWidth, 10, Component.empty())
                .setColor(TEXT));
        rotationTab.add(new HoloLabel(column, rotationRow + 12, columnWidth, 10, ROTATION_HINT).setColor(MUTED));

        // ---- onglet « Texture » (T44, D21, C69) : apercu, texture actuelle, place de « Importer » (T45), liste
        textureTab = systemPanel.add(new HoloPanel(0, TAB_CONTENT, WIDTH, tabHeight));
        // Cadre de l'apercu ; l'image est dessinee par le renderer de la fenetre (PREVIEW_X, PREVIEW_Y).
        textureTab.add(new HoloPanel(PREVIEW_X - 1, -1, PREVIEW_WIDTH + 2, PREVIEW_HEIGHT + 2)
                .setBorder(SWATCH_BORDER, 1f));
        float textureTextX = column + PREVIEW_WIDTH + GAP;
        float textureTextWidth = columnWidth - PREVIEW_WIDTH - GAP;
        textureLabel = textureTab.add(new HoloLabel(textureTextX, 0, textureTextWidth, 10, Component.empty())
                .setColor(TEXT));
        importButton = textureTab.add(button(textureTextX, PREVIEW_HEIGHT - BUTTON_HEIGHT, IMPORT_WIDTH, "Importer",
                b -> pressImport()));
        enable(importButton, false);
        float listTop = PREVIEW_HEIGHT + GAP;
        textureList = textureTab.add(new HoloWidgetList(column, listTop, columnWidth, tabHeight - listTop,
                TEXTURE_ROW_HEIGHT));
        List<SpaceBodyTextures.Builtin> builtins = SpaceBodyTextures.all();
        textureRows = new HoloWidgetButton[builtins.size() + 1];
        textureValues = new String[builtins.size() + 1];
        textureValues[0] = SpaceTextureRef.AUTO.serialized();
        for (int i = 0; i < builtins.size(); i++) {
            textureValues[i + 1] = SpaceTextureRef.builtin(builtins.get(i).id()).serialized();
        }
        for (int i = 0; i < textureRows.length; i++) {
            String value = textureValues[i];
            Component label = i == 0 ? AUTO_LABEL : Component.literal(builtins.get(i - 1).displayName());
            textureRows[i] = textureList.addItem(new HoloWidgetButton(0, 0, 1, TEXTURE_ROW_HEIGHT, label,
                    b -> pressTexture(value)));
        }
        showTabs();
        systemPanel.setVisible(false);
    }

    private static HoloLabel rotationLabel(float x, float y, String text)
    {
        return new HoloLabel(x, y + 4, ROTATION_LABEL_WIDTH, 10, Component.literal(text)).setColor(MUTED);
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

    /** Reservee aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la deplacer ou la masquer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Fin des delais de confirmation de « Retirer » et de « RESET » ; etat de l'import sur son bouton (client). */
    @Override
    public void tick()
    {
        super.tick();
        if (armedTicks > 0 && --armedTicks == 0) disarm();
        if (armedBodyTicks > 0 && --armedBodyTicks == 0) disarmBody();
        if (armedResetTicks > 0 && --armedResetTicks == 0) disarmReset();
        if (module.host().clientSide()) {
            int state = SpaceConfiguratorModule.importProgress();
            if (state != shownImport) {
                shownImport = state;
                showImport();
            }
        }
    }

    // ---------------------------------------------------------------- acces (lecture, tests)

    public HoloWidgetTextBox nameBox() { return nameBox; }

    public HoloWidgetButton addButton() { return addButton; }

    public HoloWidgetButton removeButton() { return removeButton; }

    public HoloWidgetButton worldHereButton() { return worldHereButton; }

    public HoloWidgetMultiButton forwardButton() { return forwardButton; }

    public HoloWidgetTextBox timeScaleBox() { return timeScaleBox; }

    public HoloWidgetTextBox warpBox() { return warpBox; }

    public HoloWidgetTextBox bodyNameBox() { return bodyNameBox; }

    public HoloWidgetButton removeBodyButton() { return removeBodyButton; }

    public HoloWidgetButton orbitButton() { return orbitButton; }

    public HoloWidgetValueBar phaseBar() { return phaseBar; }

    // ---------------------------------------------------------------- lecture de la Map (client)

    /** Systeme selectionne sur la carte galactique de la Map, {@code null} hors de la carte galactique ou sans Map. */
    private SystemHeader selectedSystem()
    {
        SpaceMapModule map = module.map();
        return map != null && !map.systemView() ? map.selectedHeader() : null;
    }

    /** Systeme de la vue systeme de la Map, {@code null} hors de la vue systeme ou sans Map. */
    private SpaceSystem editedSystem()
    {
        SpaceMapModule map = module.map();
        return map != null ? map.shownSystem() : null;
    }

    /** Corps selectionne dans la vue systeme de la Map, {@code null} si aucun. */
    private SpaceBody selectedBody()
    {
        SpaceSystem system = editedSystem();
        int id = system != null ? module.map().selectedBody() : 0;
        return id != 0 ? system.body(id) : null;
    }

    // ---------------------------------------------------------------- reactions (client)

    /** Premier clic : arme la confirmation ; second clic sur le meme systeme dans le delai : retrait demande. */
    private void pressRemove()
    {
        SystemHeader selected = selectedSystem();
        if (selected == null) return;
        if (armedId == selected.id() && armedTicks > 0) {
            disarm();
            module.requestRemove(selected.id());
            return;
        }
        armedId = selected.id();
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
        SpaceMapModule map = module.map();
        return editable() && selectedSystem() != null && map.headers().size() > 1;
    }

    /** Vrai si l'edition est possible : Space Map sur le marker et Environment dans la dimension. */
    private boolean editable()
    {
        SpaceMapModule map = module.map();
        return map != null && map.environmentPresent() && module.environmentPresent();
    }

    /** « Monde ici » : le systeme selectionne devient celui du monde. */
    private void pressWorldHere()
    {
        SystemHeader selected = selectedSystem();
        if (selected != null) module.requestSetCurrent(selected.id());
    }

    /** Nom valide : renommage demande s'il change ; sinon (vide, inchange, pas de selection) le champ est remis. */
    private void submitName(String text)
    {
        SystemHeader selected = selectedSystem();
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

    /**
     * « Distorsion (al/s) » validee : valeur lue (virgule ou point, notation {@code 1e5}) ; illisible, non finie,
     * negative ou nulle, inchangee ou sans Environment : la valeur courante est remise ; sinon bornee comme le fera
     * l'Environment ({@link SpaceLimits#sanitizeWarpSpeed}), affichee et envoyee.
     */
    private void submitWarp(String text)
    {
        double current = module.warpSpeed();
        double value = parse(text);
        if (!editable() || !(value > 0.0) || !Double.isFinite(value)) {
            warpBox.setText(module.environmentPresent() ? format(current) : "");
            return;
        }
        value = SpaceLimits.sanitizeWarpSpeed(value);
        warpBox.setText(format(value));
        if (Math.abs(value - current) <= 1e-9 * Math.abs(current)) return;
        module.requestWarpSpeed(value);
    }

    /**
     * « Temps (s/tick) » valide : valeur lue comme {@link #submitWarp} ; illisible, non finie, negative, inchangee ou
     * sans Environment : la valeur courante est remise ; sinon bornee comme le fera l'Environment
     * ({@code [0, SpaceMotionSettings.MAX_TIME_SCALE]}, 0 = astres figes), affichee et envoyee.
     */
    private void submitTimeScale(String text)
    {
        double current = module.timeScale();
        double value = parse(text);
        if (!editable() || !(value >= 0.0) || !Double.isFinite(value)) {
            timeScaleBox.setText(module.environmentPresent() ? format(current) : "");
            return;
        }
        value = Math.min(value, SpaceMotionSettings.MAX_TIME_SCALE);
        timeScaleBox.setText(format(value));
        if (Math.abs(value - current) <= 1e-9 * Math.abs(current)) return;
        module.requestTimeScale(value);
    }

    /** Ajout d'un corps de type {@code kind} autour du parent compatible ({@link #parentFor}). */
    private void pressAdd(SpaceBodyKind kind)
    {
        SpaceSystem system = editedSystem();
        if (system == null) return;
        int parent = parentFor(system, selectedBody(), kind);
        if (parent != 0) module.requestAddBody(system.id(), kind, parent);
    }

    /**
     * Parent d'un corps ajoute : premier corps compatible en remontant depuis la selection (elle comprise) ; sans
     * selection compatible, l'etoile principale pour une etoile, une planete ou un objet mineur, aucun ({@code 0})
     * pour une lune (parent planete ou objet mineur requis).
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
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system == null || body == null) return;
        if (armedBody == body.id() && armedBodyTicks > 0) {
            disarmBody();
            module.requestRemoveBody(system.id(), body.id());
            return;
        }
        armedBody = body.id();
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
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        return editable() && system != null && body != null && !(body.isRoot() && system.rootCount() <= 1);
    }

    /** « Orbite » : la Map cadre sa vue sur l'orbite du corps selectionne (poignee d'orbite ensuite). */
    private void pressOrbit()
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system != null && body != null && body.orbit() != null) module.requestFrameOrbit(system.id(), body.id());
    }

    private void pressColor(int color)
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system != null && body != null && body.color() != color) {
            module.requestBodyColor(system.id(), body.id(), color);
        }
    }

    /** Nom valide : renommage demande s'il change ; sinon le champ est remis. */
    private void submitBodyName(String text)
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system == null || body == null) {
            bodyNameBox.setText("");
            return;
        }
        String clean = SpaceLimits.sanitizeName(text);
        if (clean.isEmpty() || clean.equals(body.name())) {
            bodyNameBox.setText(body.name());
            return;
        }
        module.requestBodyName(system.id(), body.id(), clean);
    }

    /**
     * Champ numerique valide : valeur lue (virgule ou point, notation {@code 1e5}) ; illisible, non finie, inchangee,
     * sans objet pour le corps, ou negative ou nulle pour un champ strictement positif (rayon, luminosite, periode,
     * masse ; les champs de la rotation propre acceptent toute valeur finie, T44) : la valeur courante est remise ;
     * sinon envoyee (bornee par le serveur). Un champ par action : seul ce champ change cote serveur.
     */
    private void submitNumber(HoloWidgetTextBox box, String text, int field)
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        double current = body != null ? fieldValue(body, field) : Double.NaN;
        double value = parse(text);
        boolean accepted = Double.isFinite(value) && (!positiveField(field) || value > 0.0);
        // Texte affiche laisse tel quel (arrondi par format) : rien n'est envoye, la valeur exacte est gardee.
        if (Double.isNaN(current) || !accepted
                || Math.abs(value - current) <= 1e-9 * Math.abs(current) || value == parse(format(current))) {
            box.setText(Double.isNaN(current) ? "" : format(current));
            return;
        }
        module.requestBodyNumber(system.id(), body.id(), field, value);
    }

    /** Vrai si le champ n'accepte qu'une valeur strictement positive (champs de T12 et T39). */
    private static boolean positiveField(int field)
    {
        return field == SpaceConfiguratorModule.FIELD_RADIUS || field == SpaceConfiguratorModule.FIELD_LUMINOSITY
                || field == SpaceConfiguratorModule.FIELD_PERIOD || field == SpaceConfiguratorModule.FIELD_MASS;
    }

    /** Valeur courante d'un champ numerique, {@code NaN} s'il ne s'applique pas au corps. */
    private static double fieldValue(SpaceBody body, int field)
    {
        SpaceOrbit orbit = body.orbit();
        SpaceRotation rotation = body.rotation();
        return switch (field) {
            case SpaceConfiguratorModule.FIELD_RADIUS -> body.radius();
            case SpaceConfiguratorModule.FIELD_LUMINOSITY -> body.kind() == SpaceBodyKind.STAR ? body.luminosity()
                    : Double.NaN;
            case SpaceConfiguratorModule.FIELD_PERIOD -> orbit != null ? orbit.period() : Double.NaN;
            case SpaceConfiguratorModule.FIELD_MASS -> body.mass();
            case SpaceConfiguratorModule.FIELD_ROTATION_PERIOD -> rotation.period();
            case SpaceConfiguratorModule.FIELD_OBLIQUITY -> rotation.obliquity();
            case SpaceConfiguratorModule.FIELD_NODE -> rotation.node();
            case SpaceConfiguratorModule.FIELD_ROTATION_PHASE -> rotation.phase();
            default -> Double.NaN;
        };
    }

    /**
     * « RESET » (D34) : premier clic = confirmation armee ; second clic sur le meme corps dans le delai : le serveur
     * remet le corps a ses valeurs par defaut (galaxie par defaut, ou astre neuf de son type).
     */
    private void pressReset()
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system == null || body == null) return;
        if (armedReset == body.id() && armedResetTicks > 0) {
            disarmReset();
            module.requestResetBody(system.id(), body.id());
            return;
        }
        armedReset = body.id();
        armedResetTicks = CONFIRM_TICKS;
        resetButton.setText(RESET_CONFIRM).setColors(CONFIRM_BACKGROUND, CONFIRM_BORDER, BUTTON_TEXT);
    }

    private void disarmReset()
    {
        boolean wasArmed = armedReset != 0;
        armedReset = 0;
        armedResetTicks = 0;
        if (wasArmed) {
            resetButton.setText(RESET);
            enable(resetButton, editable() && selectedBody() != null);
        }
    }

    /** « Se teleporter » (D34) : le vaisseau est pose pres du corps selectionne (autre systeme compris). */
    private void pressTeleport()
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (editable() && system != null && body != null) module.requestTeleport(system.id(), body.id());
    }

    /** « Importer » (T45) : choix d'une image pour le corps selectionne, envoi puis application (backend client). */
    private void pressImport()
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (!editable() || system == null || body == null) return;
        if (SpaceConfiguratorModule.importProgress() != SpaceConfiguratorModule.IMPORT_IDLE) return;
        module.requestImport(system.id(), body.id());
    }

    /**
     * Bouton « Importer » : libelle selon l'etat de l'import (« Importer », « ... », avancement en %), actif seulement
     * sans import en cours, avec un corps selectionne.
     */
    private void showImport()
    {
        int state = shownImport;
        if (state == SpaceConfiguratorModule.IMPORT_IDLE) importButton.setText(IMPORT);
        else if (state == SpaceConfiguratorModule.IMPORT_PREPARING) importButton.setText(IMPORT_PREPARING);
        else importButton.setText(Component.literal(state + " %"));
        enable(importButton, state == SpaceConfiguratorModule.IMPORT_IDLE && editable() && selectedBody() != null);
    }

    /** Clic sur une ligne de la liste des textures : {@code auto} ou {@code builtin:<id>}, envoye s'il change. */
    private void pressTexture(String value)
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (!editable() || system == null || body == null || body.texture().serialized().equals(value)) return;
        module.requestBodyTexture(system.id(), body.id(), value);
    }

    /** Onglet de la vue systeme choisi (local) : panneaux et couleurs des onglets, apercu. */
    private void selectTab(int index)
    {
        if (index == tab) return;
        tab = index;
        showTabs();
        updatePreview();
    }

    /** Couleurs des onglets et visibilite des panneaux selon l'onglet choisi et la selection d'un corps. */
    private void showTabs()
    {
        boolean body = selectedBody() != null && editable();
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setVisible(body);
            if (i == tab) tabButtons[i].setColors(TAB_ACTIVE_BACKGROUND, TAB_ACTIVE_BORDER, BUTTON_TEXT);
            else tabButtons[i].setColors(BUTTON_BACKGROUND, ConfigWindow.ACCENT, BUTTON_TEXT);
        }
        resetButton.setVisible(body);
        teleportButton.setVisible(body);
        enable(teleportButton, body);
        if (armedReset == 0) enable(resetButton, body);
        bodyTab.setVisible(body && tab == TAB_BODY);
        rotationTab.setVisible(body && tab == TAB_ROTATION);
        textureTab.setVisible(body && tab == TAB_TEXTURE);
    }

    /** Apercu de la texture : seulement onglet « Texture » affiche, vue systeme, corps selectionne. */
    private void updatePreview()
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        boolean on = tab == TAB_TEXTURE && systemPanel.visible() && editable() && system != null && body != null;
        previewSystem = on ? system : null;
        previewIndex = on ? system.indexOf(body.id()) : -1;
    }

    /** Systeme du corps dont l'apercu de texture est affiche (renderer de la fenetre), {@code null} sinon. */
    public SpaceSystem previewSystem() { return previewSystem; }

    /** Indice du corps dont l'apercu de texture est affiche dans {@link #previewSystem()}, {@code -1} sinon. */
    public int previewIndex() { return previewIndex; }

    /** Onglet affiche de la vue systeme ({@link #TAB_BODY}, {@link #TAB_ROTATION}, {@link #TAB_TEXTURE}). */
    public int tab() { return tab; }

    /** Phase : barre {@code 0..1} convertie en degres {@code 0..360} (pas de 1 % = 3,6 degres). */
    private void submitPhase(float value)
    {
        SpaceSystem system = editedSystem();
        SpaceBody body = selectedBody();
        if (system == null || body == null || body.orbit() == null) return;
        double degrees = value * 360.0;
        double delta = Math.abs(degrees - body.orbit().phase());
        if (Math.min(delta, 360.0 - delta) <= 1e-6) return;
        module.requestBodyNumber(system.id(), body.id(), SpaceConfiguratorModule.FIELD_PHASE, degrees);
    }

    // ---------------------------------------------------------------- etat

    /**
     * Met a jour textes et boutons depuis l'etat du module et ce que montre la Map (deux cotes : a la creation, a
     * chaque synchronisation du module et, cote client, quand la Map change).
     */
    void showState()
    {
        SpaceMapModule map = module.map();
        boolean editable = editable();
        boolean system = map != null && map.systemView();
        galaxyPanel.setVisible(!system);
        systemPanel.setVisible(system);
        SystemHeader selected = selectedSystem();

        if (map == null) {
            infoLine.setText(NO_MAP).setColor(MISSING);
        } else if (!editable) {
            infoLine.setText(NO_ENVIRONMENT).setColor(MISSING);
        } else if (system) {
            infoLine.setText(Component.literal(systemInfo(map))).setColor(TEXT);
        } else if (selected != null) {
            boolean current = selected.id() == map.currentSystemId();
            infoLine.setText(Component.literal(fit("Systeme : " + selected.name() + (current ? " (monde ici)" : ""),
                    WIDTH - 2 * MARGIN))).setColor(current ? CURRENT_TEXT : TEXT);
        } else {
            infoLine.setText(NO_SYSTEM).setColor(MUTED);
        }

        int selectedId = selected != null ? selected.id() : 0;
        if (armedId != 0 && armedId != selectedId) disarm();
        nameBox.setVisible(editable && selected != null);
        nameBox.setText(selected != null ? selected.name() : "");
        enable(addButton, editable);
        if (armedId == 0) enable(removeButton, removeAllowed());
        enable(worldHereButton, editable && selected != null && selected.id() != map.currentSystemId());
        showForward(editable, module.forwardChoice());
        timeScaleLabel.setVisible(editable);
        timeScaleBox.setVisible(editable);
        timeScaleBox.setText(editable ? format(module.timeScale()) : "");
        warpLabel.setVisible(editable);
        warpBox.setVisible(editable);
        warpBox.setText(editable ? format(module.warpSpeed()) : "");
        showSystem(editable && system ? map.shownSystem() : null);
    }

    /**
     * Ligne d'information de la vue systeme : systeme et nombre de corps sans selection ; sinon corps edite et etat de
     * la poignee d'orbite (sur la carte, ou orbite hors de la vue : bouton « Orbite »).
     */
    private String systemInfo(SpaceMapModule map)
    {
        SpaceSystem edited = map.shownSystem();
        SpaceBody body = selectedBody();
        int width = WIDTH - 2 * MARGIN;
        if (body == null) return fit(edited.name() + " : " + edited.bodies().size() + " corps - choisir un corps",
                width);
        String text = edited.name() + " > " + body.name();
        if (body.orbit() == null) return fit(text, width);
        if (module.orbitHandle() != null) return fit(text + " - poignee sur la carte", width);
        return fit(text + " - orbite hors vue : Orbite", width);
    }

    /** Panneau de la vue systeme : boutons et champs du corps selectionne ({@code system} nul : tout eteint). */
    private void showSystem(SpaceSystem system)
    {
        SpaceBody body = system != null ? selectedBody() : null;
        int selectedId = body != null ? body.id() : 0;
        if (armedBody != 0 && armedBody != selectedId) disarmBody();
        if (armedReset != 0 && armedReset != selectedId) disarmReset();

        boolean full = system == null || system.bodies().size() >= SpaceLimits.MAX_BODIES;
        enable(addStarButton, !full && parentFor(system, body, SpaceBodyKind.STAR) != 0);
        enable(addPlanetButton, !full && parentFor(system, body, SpaceBodyKind.PLANET) != 0);
        enable(addMoonButton, !full && parentFor(system, body, SpaceBodyKind.MOON) != 0);
        enable(addMinorButton, !full && parentFor(system, body, SpaceBodyKind.MINOR) != 0);
        if (armedBody == 0) enable(removeBodyButton, removeBodyAllowed());
        SpaceOrbit orbit = body != null ? body.orbit() : null;
        enable(orbitButton, orbit != null && !body.isRoot());

        boolean star = body != null && body.kind() == SpaceBodyKind.STAR;
        bodyNameBox.setVisible(body != null);
        radiusLabel.setVisible(body != null);
        radiusBox.setVisible(body != null);
        luminosityLabel.setVisible(star);
        luminosityBox.setVisible(star);
        massLabel.setVisible(body != null);
        massBox.setVisible(body != null);
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
        showTabs();
        noBodyLabel.setVisible(system != null && body == null);
        noBodyLabel.setText(NO_BODY);
        if (body != null) showRotation(body);
        showTexture(system, body);
        updatePreview();
        if (body == null) {
            bodyNameBox.setText("");
            orbitLabel.setText(Component.empty());
            return;
        }
        bodyNameBox.setText(body.name());
        radiusBox.setText(format(body.radius()));
        if (star) luminosityBox.setText(format(body.luminosity()));
        massBox.setText(format(body.mass()));
        if (orbit == null) {
            orbitLabel.setText(Component.literal("Centre du systeme (immobile)"));
            return;
        }
        periodBox.setText(format(orbit.period()));
        phaseBar.setValue((float) (orbit.phase() / 360.0));
        phaseLabel.setText(Component.literal(String.format(Locale.ROOT, "%.1f", orbit.phase()).replace('.', ',')
                + "°"));
        orbitLabel.setText(Component.literal("Orbite : a = " + format(orbit.semiMajorAxis()) + " UA, e = "
                + String.format(Locale.ROOT, "%.3f", orbit.eccentricity()).replace('.', ',')));
    }

    /** Onglet « Rotation » : quatre champs et ligne d'etat (sens, periode ; synchrone). */
    private void showRotation(SpaceBody body)
    {
        SpaceRotation rotation = body.rotation();
        rotationPeriodBox.setText(format(rotation.period()));
        obliquityBox.setText(format(rotation.obliquity()));
        nodeBox.setText(format(rotation.node()));
        rotationPhaseBox.setText(format(rotation.phase()));
        String state;
        if (rotation.isSynchronous()) {
            state = body.isRoot() ? "Synchrone sans parent : immobile" : "Synchrone : meme face vers le parent";
        } else {
            state = (rotation.isRetrograde() ? "Retrograde" : "Directe") + " : 1 tour en "
                    + format(Math.abs(rotation.period())) + " h";
        }
        rotationInfo.setText(Component.literal(fit(state, WIDTH - 2 * MARGIN)));
    }

    /**
     * Onglet « Texture » : texture actuelle (forme et nom lisible ; {@code auto} : texture retenue par la regle auto),
     * libelle de la ligne « Auto », ligne de la texture choisie en vert, liste defilee jusqu'a elle quand le corps
     * change. Sans corps : rien de choisi.
     */
    private void showTexture(SpaceSystem system, SpaceBody body)
    {
        int index = system != null && body != null ? system.indexOf(body.id()) : -1;
        SpaceTextureRef texture = index >= 0 ? body.texture() : SpaceTextureRef.AUTO;
        String autoName = index >= 0 ? SpaceBodyTextures.auto(system, index).displayName() : null;
        textureRows[0].setText(Component.literal(autoName != null ? fit("Auto (" + autoName + ")",
                (int) textureList.width() - 12) : "Auto"));
        int selected = -1;
        String current = "";
        if (index >= 0) {
            switch (texture.source()) {
                case AUTO -> {
                    selected = 0;
                    current = "Auto : " + autoName;
                }
                case BUILTIN -> {
                    SpaceBodyTextures.Builtin builtin = SpaceBodyTextures.builtin(texture.id());
                    if (builtin != null) {
                        current = builtin.displayName();
                        String value = texture.serialized();
                        for (int i = 1; i < textureValues.length; i++) {
                            if (textureValues[i].equals(value)) selected = i;
                        }
                    } else {
                        current = "Inconnue, auto : " + autoName;
                    }
                }
                case CUSTOM -> current = "Importee " + texture.id().substring(0, 8);
            }
        }
        textureLabel.setText(Component.literal(fit(current, (int) textureLabel.width())));
        showImport();
        for (int i = 0; i < textureRows.length; i++) {
            if (i == selected) textureRows[i].setColors(ROW_SELECTED_BACKGROUND, ROW_SELECTED_BORDER, BUTTON_TEXT);
            else textureRows[i].setColors(BUTTON_BACKGROUND, ConfigWindow.ACCENT, BUTTON_TEXT);
        }
        int systemId = system != null ? system.id() : -1;
        int bodyId = body != null ? body.id() : -1;
        if (systemId != scrolledSystem || bodyId != scrolledBody) {
            scrolledSystem = systemId;
            scrolledBody = bodyId;
            // Ligne choisie vers le haut de la liste, la precedente visible.
            textureList.scrollTo(Math.max(0, selected - 1));
        }
    }

    /** Ligne « Avant du monde » : choix courant ; clic gauche = suivant, droit = precedent. */
    private void showForward(boolean enabled, int choice)
    {
        forwardButton.setText(choice >= 0 ? FORWARD_NAMES[choice] : FORWARD_OTHER);
        forwardButton.off(HoloControl.LEFT_CLICK).off(HoloControl.RIGHT_CLICK);
        if (!enabled) {
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
