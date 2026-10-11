package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloTextMetrics;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetScrollZone;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import net.minecraft.network.chat.Component;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fenetre de navigation de la Space Map (T37, C77) : objet separe du cube, place a sa creation devant la base du cube
 * par defaut (aussi son transform de RESET), puis independant (transform sauvegarde et synchronise par le framework).
 * Ancrage {@code World}, {@code PUBLIC}, visible et utilisable par tous (acces {@code EVERYONE}) ; editable par les
 * operateurs au {@code world_editor} (translation, rotation, bouton oeil : a {@code OFF}, seul le cube reste).
 * <p>
 * <b>Arborescence de la gravite</b> ({@link SpaceMapTreeRow}, liste {@link HoloWidgetList} qui defile a la molette) :
 * « Galaxie » &gt; systemes &gt; corps par parente (etoile &gt; planete &gt; lune). Systeme courant du monde en vert
 * (C67), origine des coordonnees precedee de « @ » (C68). Clic sur un nom = selection + recentrage (envoye au
 * serveur : la vue est partagee) : « Galaxie » = carte d'ensemble ; un systeme = carte galactique centree sur lui ; un
 * corps = vue du systeme centree sur lui. Clic sur le repere {@code +} / {@code -} (ou clic droit) = deplier /
 * replier : un seul systeme est deplie a la fois, celui dont le module copie les corps (deplier un autre systeme le
 * demande au serveur) ; replier un systeme, la galaxie ou un corps est local au joueur, comme le depliage des corps
 * (par defaut : etoiles depliees, autres corps replies, ancetres de la selection deplies).
 * <p>
 * <b>Zoom</b> : « - » / « + », molette sur la zone centrale ou sur le fond de la fenetre (la liste garde la molette pour
 * defiler) ; vers le centre de la vue. « Systeme » / « Galaxie » change de vue, « Vue d'ensemble » recadre tout,
 * « Origine @ » choisit l'origine des coordonnees (systeme selectionne, ou affiche en vue systeme).
 * <p>
 * <b>Cible du vaisseau</b> (T43, D33) : la ligne selectionnee (systeme sur la carte galactique, corps en vue systeme ;
 * jamais « Galaxie ») porte au bout un bouton « CIBLER », ou « DECIBLER » en vert si sa selection est deja la cible du
 * vaisseau (lue dans le miroir client de l'Environment) ; clic = action {@code TARGET} de la Map. Les lignes sont
 * reconstruites quand la cible du vaisseau change (comparee a chaque tick client, jamais par frame).
 * <p>
 * Ligne d'information : coordonnees de la selection (al relatives a l'origine ; corps : UA relatives a l'etoile
 * principale, au temps vivant de la vue, rafraichies toutes les {@value #INFO_TICKS} ticks), sinon le cadre et les
 * legendes. Textes et lignes reconstruits a la synchronisation ou au depliage, jamais par frame.
 */
public final class SpaceMapWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation, HoloEditVisibility
{
    public static final int WIDTH = 220;
    public static final int HEIGHT = 176;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;
    /** Demi-hauteur de la fenetre, en blocs (placement contre la base du cube). */
    public static final float HALF_HEIGHT = HEIGHT * PIXEL_SCALE * 0.5f;
    /** Rafraichissement des coordonnees d'un corps (temps vivant), en ticks. */
    public static final int INFO_TICKS = 10;

    private static final int TEXT = 0xFFE0F0FF;
    private static final int MISSING = 0xFFFF8060;
    private static final int MUTED = 0xFF90A0B0;
    private static final int BUTTON_BACKGROUND = 0x6020A0FF;
    private static final int BUTTON_TEXT = 0xFFFFFFFF;
    private static final int DISABLED_BACKGROUND = 0x40404850;
    private static final int DISABLED_BORDER = 0xFF586070;
    private static final int DISABLED_TEXT = 0xFF808890;
    private static final int ROW = 0x4020A0FF;
    private static final int ROW_SELECTED = 0xA040C0FF;
    /** Systeme montre par la vue systeme, dans la liste. */
    private static final int ROW_ACTIVE = 0x7030B0FF;
    private static final int SELECTED_TEXT = 0xFFFFE040;
    private static final int CURRENT_TEXT = SpaceMapCube.WORLD_COLOR;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 14;
    private static final float ROW_HEIGHT = 11f;
    private static final int LIST_TOP = 26;
    private static final int LIST_HEIGHT = HEIGHT - LIST_TOP - MARGIN - 2 * (BUTTON_HEIGHT + GAP);
    /** Largeur utile d'une ligne de la liste (marges et barre de defilement deduites, comme {@code HoloWidgetList}). */
    private static final float ROW_WIDTH = WIDTH - 2 * MARGIN - 3 - 3 - 3 - 2;
    private static final int ZOOM_BUTTON_WIDTH = 40;
    private static final int MODE_WIDTH = 48;
    private static final int OVERVIEW_WIDTH = 84;
    /** Vert de la cible du vaisseau (T43, C125 : meme valeur que {@code SpaceOverlay.TARGET_COLOR} des renderers). */
    public static final int TARGET_COLOR = 0xFF40FF40;
    /** Fond du bouton de cible d'une ligne. */
    private static final int TARGET_BACKGROUND = 0x90183048;
    private static final Component TARGET = Component.literal("CIBLER");
    private static final Component UNTARGET = Component.literal("DECIBLER");
    private static final String ORIGIN_PREFIX = "@ ";
    private static final String ELLIPSIS = "...";

    private static final Component NO_ENVIRONMENT = Component.literal("Aucun Space Environment ici");
    private static final Component TO_SYSTEM = Component.literal("Systeme");
    private static final Component TO_GALAXY = Component.literal("Galaxie");
    private static final Component ZOOM_HOVER = Component.literal("Molette : zoom");

    private final SpaceMapModule module;
    private final HoloLabel infoLine;
    private final HoloWidgetList tree;
    private final HoloWidgetButton zoomOutButton;
    private final HoloWidgetScrollZone zoomZone;
    private final HoloWidgetButton zoomInButton;
    private final HoloWidgetButton modeButton;
    private final HoloWidgetButton overviewButton;
    private final HoloWidgetButton originButton;

    // ---------------------------------------------------------------- etat local de l'arborescence (client)

    private boolean galaxyExpanded = true;
    /** Systeme deplie dont les corps sont connus (copie du module) ; replie localement si {@link #systemCollapsed}. */
    private int treeSystem;
    private boolean systemCollapsed;
    /** Corps deplies du systeme {@link #treeSystem}. */
    private final Set<Integer> expandedBodies = new HashSet<>();
    /** Indice de la ligne selectionnee dans la liste (defilement pour la montrer), {@code -1} si aucune. */
    private int selectedRow;
    /** Ticks avant le prochain rafraichissement des coordonnees d'un corps. */
    private int infoCountdown;
    /** Positions des corps (km) pour la ligne d'information, tableau reutilise. */
    private final Vector3d[] bodyPositions = SpaceOrbits.newPositionArray();
    /** Cible du vaisseau lue a la derniere reconstruction des lignes (client, T43). */
    private int shownTargetSystem;
    private int shownTargetBody;

    /**
     * @param module    module qui porte la fenetre (demandes envoyees au serveur, etat lu a la synchronisation)
     * @param transform placement initial (devant la base du cube par defaut), aussi placement de RESET
     */
    SpaceMapWindow(String key, SpaceMapModule module, HoloTransform transform)
    {
        super(key, new HoloAnchor.World(), transform, WIDTH, HEIGHT, PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setAccess(HoloAccess.EVERYONE);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.SCROLL);
        root().setBackground(ConfigWindow.BACKGROUND).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Space Map"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        infoLine = root().add(new HoloLabel(MARGIN, 14, WIDTH - 2 * MARGIN, 10, Component.empty()).setColor(MUTED));
        tree = root().add(new HoloWidgetList(MARGIN, LIST_TOP, WIDTH - 2 * MARGIN, LIST_HEIGHT, ROW_HEIGHT));

        float row1 = LIST_TOP + LIST_HEIGHT + GAP;
        float inner = WIDTH - 2 * MARGIN;
        zoomOutButton = root().add(button(MARGIN, row1, ZOOM_BUTTON_WIDTH, "Zoom -", b -> module.requestZoom(-1)));
        zoomZone = root().add(new HoloWidgetScrollZone(MARGIN + ZOOM_BUTTON_WIDTH + GAP, row1,
                inner - 2 * (ZOOM_BUTTON_WIDTH + GAP), BUTTON_HEIGHT, Component.empty(), ZOOM_HOVER,
                (zone, steps) -> module.requestZoom(steps)));
        zoomZone.setColors(0x2080C0, ConfigWindow.ACCENT, TEXT);
        zoomInButton = root().add(button(WIDTH - MARGIN - ZOOM_BUTTON_WIDTH, row1, ZOOM_BUTTON_WIDTH, "Zoom +",
                b -> module.requestZoom(1)));
        float row2 = row1 + BUTTON_HEIGHT + GAP;
        modeButton = root().add(button(MARGIN, row2, MODE_WIDTH, "Systeme", b -> pressMode()));
        overviewButton = root().add(button(MARGIN + MODE_WIDTH + GAP, row2, OVERVIEW_WIDTH, "Vue d'ensemble",
                b -> module.requestReframe()));
        float originX = MARGIN + MODE_WIDTH + OVERVIEW_WIDTH + 2 * GAP;
        originButton = root().add(button(originX, row2, WIDTH - MARGIN - originX, "Origine @", b -> pressOrigin()));
    }

    private static HoloWidgetButton button(float x, float y, float width, String text,
                                           Consumer<? super HoloWidgetButton> onPress)
    {
        return new HoloWidgetButton(x, y, width, BUTTON_HEIGHT, Component.literal(text), onPress);
    }

    /** Placement et affichage reserves aux operateurs (le contenu, lui, sert a tous). */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la deplacer ou la masquer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Molette sur le fond de la fenetre (hors de la liste) : zoom. */
    @Override
    public void onScroll(HoloInteraction ctx, double delta)
    {
        int steps = (int) Math.round(delta);
        if (steps == 0) steps = delta > 0 ? 1 : -1;
        module.requestZoom(steps);
    }

    /**
     * Client : lignes reconstruites si la cible du vaisseau a change (libelle CIBLER / DECIBLER, T43) ; coordonnees du
     * corps selectionne rafraichies au temps vivant.
     */
    @Override
    public void tick()
    {
        super.tick();
        SpaceEnvironmentModule env = environment();
        int targetSystem = env != null ? env.targetSystemId() : 0;
        int targetBody = env != null ? env.targetBodyId() : 0;
        if (targetSystem != shownTargetSystem || targetBody != shownTargetBody) rebuildTree();
        if (!module.systemView() || module.selectedBodyData() == null) return;
        if (--infoCountdown > 0) return;
        infoCountdown = INFO_TICKS;
        showInfo();
    }

    /** Miroir client de l'Environment (cote client seulement : {@code null} cote serveur). */
    private SpaceEnvironmentModule environment()
    {
        return module.host() != null && module.host().clientSide() ? SpaceEnvironmentModule.client() : null;
    }

    // ---------------------------------------------------------------- acces (lecture, tests)

    public HoloWidgetList tree() { return tree; }

    public HoloWidgetButton zoomInButton() { return zoomInButton; }

    public HoloWidgetButton zoomOutButton() { return zoomOutButton; }

    public HoloWidgetScrollZone zoomZone() { return zoomZone; }

    public HoloWidgetButton modeButton() { return modeButton; }

    public HoloWidgetButton overviewButton() { return overviewButton; }

    public HoloWidgetButton originButton() { return originButton; }

    // ---------------------------------------------------------------- reactions (client)

    private void pressMode()
    {
        module.requestMode(module.systemView() ? SpaceMapModule.Mode.GALAXY : SpaceMapModule.Mode.SYSTEM);
    }

    /** « Origine @ » : systeme affiche (vue systeme) ou selectionne (carte galactique). */
    private void pressOrigin()
    {
        int target = originTarget();
        SystemHeader origin = module.originHeader();
        if (target != 0 && (origin == null || origin.id() != target)) module.requestOrigin(target);
    }

    private int originTarget() { return module.systemView() ? module.displayedSystem() : module.selectedSystem(); }

    private void toggleGalaxy()
    {
        galaxyExpanded = !galaxyExpanded;
        rebuildTree();
    }

    /** Systeme deja copie : depliage local ; sinon demande au serveur (le systeme deplie change pour tous). */
    private void toggleSystem(int id)
    {
        if (id == treeSystem && treeSystem != 0) {
            systemCollapsed = !systemCollapsed;
            rebuildTree();
            return;
        }
        module.requestDisplay(id);
    }

    private void toggleBody(int id)
    {
        if (!expandedBodies.remove(id)) expandedBodies.add(id);
        rebuildTree();
    }

    // ---------------------------------------------------------------- etat

    /** Met a jour textes, lignes et boutons depuis l'etat du module (deux cotes, creation et synchronisation). */
    void showState()
    {
        boolean environment = module.environmentPresent();
        boolean systemView = module.systemView();
        SpaceSystem displayed = module.displayedSystemData();
        int displayedId = displayed != null ? displayed.id() : 0;
        if (displayedId != treeSystem) {
            // Nouveau systeme deplie : etoiles depliees, autres corps replies.
            treeSystem = displayedId;
            systemCollapsed = false;
            expandedBodies.clear();
            if (displayed != null) {
                for (SpaceBody body : displayed.bodies()) {
                    if (body.kind() == SpaceBodyKind.STAR) expandedBodies.add(body.id());
                }
            }
        }
        // La selection reste visible : ses ancetres sont deplies.
        if (displayed != null && module.selectedBody() != 0) {
            for (int index = displayed.parentIndex(displayed.indexOf(module.selectedBody())); index >= 0;
                 index = displayed.parentIndex(index)) {
                expandedBodies.add(displayed.bodies().get(index).id());
            }
            systemCollapsed = false;
            galaxyExpanded = true;
        }
        infoCountdown = INFO_TICKS;
        showInfo();
        rebuildTree();

        double half = systemView ? module.viewHalf() : module.frameHalf();
        String unit = systemView ? " UA" : " al";
        zoomZone.setTexts(Component.literal(half > 0.0 ? "+/- " + SpaceFormat.number(half) + unit : "-"), ZOOM_HOVER);
        enable(zoomOutButton, environment);
        enable(zoomInButton, environment);
        modeButton.setText(systemView ? TO_GALAXY : TO_SYSTEM);
        enable(modeButton, environment && (systemView || module.selectedSystem() != 0 || displayedId != 0));
        enable(overviewButton, environment);
        SystemHeader origin = module.originHeader();
        int target = originTarget();
        enable(originButton, environment && target != 0 && (origin == null || origin.id() != target));
    }

    /** Ligne d'information : coordonnees de la selection, sinon cadre et legendes. */
    private void showInfo()
    {
        if (!module.environmentPresent()) {
            infoLine.setText(NO_ENVIRONMENT).setColor(MISSING);
            return;
        }
        if (module.systemView()) {
            SpaceSystem system = module.shownSystem();
            SpaceBody body = module.selectedBodyData();
            if (body != null) {
                infoLine.setText(Component.literal(bodyCoordinates(system, body))).setColor(TEXT);
                return;
            }
            String text = system.name() + " : " + system.bodies().size() + " corps, cadre +/- "
                    + SpaceFormat.number(module.viewHalf()) + " UA";
            if (system.id() == module.currentSystemId()) text += " - vaisseau en vert";
            infoLine.setText(Component.literal(fit(text, WIDTH - 2 * MARGIN))).setColor(MUTED);
            return;
        }
        SystemHeader selected = module.selectedHeader();
        if (selected != null) {
            infoLine.setText(Component.literal(fit(coordinates(selected, module.originHeader()), WIDTH - 2 * MARGIN)))
                    .setColor(TEXT);
            return;
        }
        int count = module.headers().size();
        infoLine.setText(Component.literal(fit(count + (count > 1 ? " systemes" : " systeme") + " - cadre +/- "
                + SpaceFormat.number(module.frameHalf()) + " al - vert : monde ici - @ : origine",
                WIDTH - 2 * MARGIN))).setColor(MUTED);
    }

    /** Coordonnees galactiques d'un systeme relatives a l'origine (memes valeurs que la croix du cube). */
    private static String coordinates(SystemHeader selected, SystemHeader origin)
    {
        double ox = origin != null ? origin.x() : 0.0;
        double oy = origin != null ? origin.y() : 0.0;
        double oz = origin != null ? origin.z() : 0.0;
        return selected.name() + " : X " + SpaceCoordinates.format(selected.x() - ox)
                + "  Y " + SpaceCoordinates.format(selected.y() - oy)
                + "  Z " + SpaceCoordinates.format(selected.z() - oz) + " al";
    }

    /** Coordonnees d'un corps en UA relatives a l'etoile principale (indice 0) au temps vivant de la vue. */
    private String bodyCoordinates(SpaceSystem system, SpaceBody body)
    {
        int index = system.indexOf(body.id());
        SpaceOrbits.positions(system, module.viewDays(0f), bodyPositions);
        Vector3d p = bodyPositions[index];
        Vector3d star = bodyPositions[0];
        double toAu = 1.0 / SpaceUnits.AU_KM;
        return fit(body.name() + " : X " + SpaceCoordinates.format((p.x - star.x) * toAu)
                + "  Y " + SpaceCoordinates.format((p.y - star.y) * toAu)
                + "  Z " + SpaceCoordinates.format((p.z - star.z) * toAu) + " UA", WIDTH - 2 * MARGIN);
    }

    /**
     * Recree les lignes de l'arborescence (synchronisation, depliage) en gardant le defilement ; la ligne
     * selectionnee est ramenee dans la partie visible.
     */
    private void rebuildTree()
    {
        int first = tree.first();
        tree.clearItems();
        selectedRow = -1;
        boolean systemView = module.systemView();
        List<SystemHeader> headers = module.headers();
        SystemHeader origin = module.originHeader();
        int originId = origin != null ? origin.id() : 0;
        int current = module.currentSystemId();
        SpaceSystem displayed = module.displayedSystemData();
        SpaceEnvironmentModule env = environment();
        shownTargetSystem = env != null ? env.targetSystemId() : 0;
        shownTargetBody = env != null ? env.targetBodyId() : 0;

        boolean galaxySelected = !systemView && module.selectedSystem() == 0;
        String galaxyText = "Galaxie (" + headers.size() + ")";
        addRow(galaxyText, 0, !headers.isEmpty(), galaxyExpanded, galaxySelected ? ROW_SELECTED : ROW,
                galaxySelected ? SELECTED_TEXT : BUTTON_TEXT, module::requestGalaxyView, this::toggleGalaxy,
                galaxySelected, false, false);
        if (galaxyExpanded) {
            for (SystemHeader header : headers) {
                int id = header.id();
                boolean open = displayed != null && id == treeSystem && !systemCollapsed;
                boolean selected = !systemView && module.selectedSystem() == id;
                boolean active = systemView && displayed != null && displayed.id() == id;
                int text = selected ? SELECTED_TEXT : id == current ? CURRENT_TEXT : BUTTON_TEXT;
                addRow((id == originId ? ORIGIN_PREFIX : "") + header.name(), 1, true, open,
                        selected ? ROW_SELECTED : active ? ROW_ACTIVE : ROW, text,
                        () -> module.requestSelectSystem(id), () -> toggleSystem(id), selected, selected,
                        selected && targeted(id, 0));
                if (open) {
                    for (int i = 0; i < displayed.bodies().size(); i++) {
                        if (displayed.parentIndex(i) < 0) addBody(displayed, i, 2, systemView);
                    }
                }
            }
        }
        tree.scrollTo(first);
        int visible = tree.visibleCount();
        if (selectedRow >= 0 && (selectedRow < tree.first() || selectedRow >= tree.first() + visible)) {
            tree.scrollTo(selectedRow - visible / 2);
        }
    }

    /** Ligne d'un corps puis, s'il est deplie, celles de ses enfants (parents avant enfants dans le systeme). */
    private void addBody(SpaceSystem system, int index, int depth, boolean systemView)
    {
        SpaceBody body = system.bodies().get(index);
        int id = body.id();
        int systemId = system.id();
        boolean parent = false;
        for (int i = index + 1; i < system.bodies().size() && !parent; i++) parent = system.parentIndex(i) == index;
        boolean open = parent && expandedBodies.contains(id);
        boolean selected = systemView && module.selectedBody() == id;
        addRow(body.name(), depth, parent, open, selected ? ROW_SELECTED : ROW, selected ? SELECTED_TEXT : BUTTON_TEXT,
                () -> module.requestSelectBody(systemId, id), () -> toggleBody(id), selected, selected,
                selected && targeted(systemId, id));
        if (!open) return;
        for (int i = index + 1; i < system.bodies().size(); i++) {
            if (system.parentIndex(i) == index) addBody(system, i, depth + 1, systemView);
        }
    }

    /** Vrai si {@code (systemId, bodyId)} est la cible du vaisseau lue a cette reconstruction. */
    private boolean targeted(int systemId, int bodyId)
    {
        return shownTargetSystem != 0 && shownTargetSystem == systemId && shownTargetBody == bodyId;
    }

    /**
     * @param targetButton vrai pour la ligne selectionnee (bouton de cible au bout, T43)
     * @param targeted     sa selection est la cible du vaisseau : « DECIBLER » (sinon « CIBLER »)
     */
    private void addRow(String text, int depth, boolean expandable, boolean expanded, int background, int textColor,
                        Runnable onSelect, Runnable onToggle, boolean selected, boolean targetButton,
                        boolean targeted)
    {
        if (selected) selectedRow = tree.items().size();
        Component label = Component.literal(fit(text, (int) SpaceMapTreeRow.textWidth(ROW_WIDTH, depth,
                targetButton)));
        SpaceMapTreeRow row = tree.addItem(new SpaceMapTreeRow(label, ROW_HEIGHT, depth, expandable, expanded,
                onSelect, onToggle)).setColors(background, ConfigWindow.ACCENT, textColor);
        if (targetButton) {
            row.setTargetButton(targeted ? UNTARGET : TARGET, targeted ? TARGET_COLOR : BUTTON_TEXT,
                    TARGET_BACKGROUND, () -> module.requestTarget(!targeted));
        }
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
}
