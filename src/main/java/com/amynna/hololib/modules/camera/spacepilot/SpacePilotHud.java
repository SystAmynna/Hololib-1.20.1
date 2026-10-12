package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloThemeColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloTextMetrics;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import com.amynna.hololib.modules.dimension.space.math.SpaceCruise;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Objects;

/**
 * HUD de navigation de Space Pilot (T20, D14) : objet de {@link SpacePilotModule}, ancrage {@code Hud} au coin haut
 * gauche de l'ecran (dessine sous le HUD vanilla, famille {@code HUD}), {@code PRIVATE}. Lignes :
 * <ul>
 *     <li>« Vitesse : v » : vitesse signee relative au referentiel, km/s ou UA/s (meme format que la fenetre du Space
 *     Controller, {@link SpaceFormat#speed}) ; avec la <b>vitesse de croisiere</b> (T34, D25, C76) et une cible :
 *     « Vitesse : v - fleche v* », meme unite des deux cotes (choisie sur la plus grande), fleche haut s'il faut
 *     accelerer, bas s'il faut ralentir (glyphes U+2191 / U+2193 de la police vanilla, {@code nonlatin_european.png}),
 *     fleche et {@code v*} en couleur : vert si {@code |v - v*| <= 10 % v*}, rouge au-dela de 30 %, degrade entre
 *     ({@link SpaceCruise#redness}, {@value #COLOR_STEPS} paliers) ;</li>
 *     <li>« Reference : nom » : corps de reference (referentiel), nom raccourci s'il depasse la fenetre ;</li>
 *     <li>« Distance : d » : altitude au-dessus du corps de reference (comme l'« Altitude » du Controller), km ou UA
 *     ({@link SpaceFormat#distance}) ; pendant la charge d'un saut, « Saut -> cible », puis pendant le voyage
 *     (T22, livrable 4 de T20) « Distorsion -> cible : n s » (nom de l'etoile cible raccourci s'il le faut, temps
 *     restant en secondes entieres) a sa place ;</li>
 *     <li>« Cible : nom » (T34, seulement avec la vitesse de croisiere) : corps vise par le calculateur, « aucune »
 *     sans cible.</li>
 * </ul>
 * Fenetre plutot qu'objet dessine a la main : des {@link HoloLabel} dessines par le renderer generique des fenetres
 * (aucun renderer propre a enregistrer), et le cadre HUD de l'editor connait deja la taille d'une fenetre. La fenetre
 * est declaree a sa taille avec la vitesse de croisiere ({@value #WIDTH} x {@value #HEIGHT}) ; sans elle, le panneau
 * racine (fond et bordure) est ramene a {@value #COMPACT_WIDTH} x {@value #COMPACT_HEIGHT} depuis le coin haut gauche
 * ({@link #setCruise}) et la ligne « Cible » masquee.
 * <p>
 * Deplacable par l'Own HUD Editor ({@link HoloEditTranslation} seulement : glisser dans le cadre, molette = ordre de
 * dessin ; capacite {@code OWN_HUD} deduite de l'ancrage), placement sauvegarde par le framework.
 * {@link #shown()} : client du proprietaire, Space Environment dans sa dimension, reglage {@code hud} actif et
 * joueur local aux commandes ({@link SpacePilotModule#hudShown()} : seulement pendant le pilotage depuis T33, C75 ;
 * une session d'edition le revele pour le deplacer). Le routage du {@code Hud} le masque en F5 ; non partage,
 * un joueur qui inspecte ne le voit jamais (C45).
 * <p>
 * Textes reconstruits seulement quand leur valeur arrondie (ou la couleur, qui en derive) change : la ligne de
 * vitesse par {@link #showSpeed} (chaque tick), les autres par {@link #show} (au plus 4 fois par seconde) ; aucune
 * chaine allouee par frame.
 */
public final class SpacePilotHud extends HoloWindow implements HoloEditTranslation
{
    /** Largeur avec la vitesse de croisiere : « Vitesse : -1 490 000 km/s - v 1 490 000 km/s » tient (230 px). */
    public static final int WIDTH = 240;
    /** Hauteur avec la vitesse de croisiere (quatre lignes). */
    public static final int HEIGHT = 50;
    /** Largeur sans la vitesse de croisiere (T20). */
    public static final int COMPACT_WIDTH = 150;
    /** Hauteur sans la vitesse de croisiere (trois lignes, T20). */
    public static final int COMPACT_HEIGHT = 39;
    /** Taille d'un pixel de fenetre ; l'echelle du transform la compense : 1 pixel de fenetre = 1 pixel GUI. */
    private static final float PIXEL_SCALE = 1f / 160f;
    /** Marge au bord de l'ecran, pixels GUI (placement par defaut). */
    private static final float MARGIN = 4f;
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final String SPEED = "Vitesse : ";
    private static final String SEPARATOR = " - ";
    /** Fleche haut (U+2191) : accelerer. Glyphe de {@code nonlatin_european.png}, 5 px + 1. */
    private static final String UP = "↑ ";
    /** Fleche bas (U+2193) : ralentir. */
    private static final String DOWN = "↓ ";
    private static final String REFERENCE = "Reference : ";
    private static final String DISTANCE = "Distance : ";
    private static final String TARGET = "Cible : ";
    private static final String WARP = "Distorsion -> ";
    private static final String CHARGE = "Saut -> ";
    /** Valeur de {@code warpSeconds} pendant la charge d'un saut (pas de temps restant affiche). */
    static final int CHARGING = -1;
    private static final String ELLIPSIS = "...";
    private static final Component NO_REFERENCE = Component.literal(REFERENCE + "aucune");
    private static final Component NO_DISTANCE = Component.literal(DISTANCE + "-");
    private static final Component NO_TARGET = Component.literal(TARGET + "aucune");
    /** Paliers du degrade selection -> critique (le texte n'est reconstruit qu'au changement de palier). */
    private static final int COLOR_STEPS = 8;
    /**
     * Styles du degrade, palier 0 (couleur de selection : proche de la consigne, {@code |v - v*| <= 10 %}) a
     * {@link #COLOR_STEPS} (couleur critique : loin, {@code >= 30 %}) ; recalcules quand le theme change.
     */
    private final Style[] styles = new Style[COLOR_STEPS + 1];
    private int stylesRevision = Integer.MIN_VALUE;

    private final HoloLabel speedLine;
    private final HoloLabel referenceLine;
    private final HoloLabel distanceLine;
    private final HoloLabel targetLine;

    /** Vitesse de croisiere affichee (taille du panneau, ligne « Cible »). */
    private boolean cruise = true;
    // Valeurs affichees (arrondies) : une ligne n'est reconstruite que si la sienne change.
    private double shownSpeed = Double.NaN;
    /** Vitesse cible affichee, {@code NaN} sans cible ; {@link Double#NEGATIVE_INFINITY} = a reconstruire. */
    private double shownTarget = Double.NEGATIVE_INFINITY;
    private String shownTargetName;
    private boolean targetNameShown;
    private String shownReference;
    private double shownDistance = Double.NaN;
    /** Cible du saut affichee ({@code null} hors saut) et seconde restante affichee ({@link #CHARGING} en charge). */
    private String shownWarp;
    private int shownWarpSeconds;

    SpacePilotHud(String key)
    {
        super(key, new HoloAnchor.Hud(0f, 0f),
                HoloTransform.translation(MARGIN + 0.5f * WIDTH, MARGIN + 0.5f * HEIGHT, 0f).setScale(1f / PIXEL_SCALE),
                WIDTH, HEIGHT, PIXEL_SCALE);
        setVisibility(HoloVisibility.PRIVATE);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        int width = WIDTH - 2 * PADDING;
        speedLine = root().add(new HoloLabel(PADDING, 3, width, LINE_HEIGHT, Component.literal(SPEED + "-"))
                .setColor(TEXT));
        referenceLine = root().add(new HoloLabel(PADDING, 3 + LINE_HEIGHT, width, LINE_HEIGHT, NO_REFERENCE)
                .setColor(TEXT));
        distanceLine = root().add(new HoloLabel(PADDING, 3 + 2 * LINE_HEIGHT, width, LINE_HEIGHT, NO_DISTANCE)
                .setColor(TEXT));
        targetLine = root().add(new HoloLabel(PADDING, 3 + 3 * LINE_HEIGHT, width, LINE_HEIGHT, NO_TARGET)
                .setColor(TEXT));
    }

    /** Client du proprietaire : Environment dans sa dimension, {@code hud} actif, joueur local aux commandes. */
    @Override
    public boolean shown() { return module() instanceof SpacePilotModule pilot && pilot.hudShown(); }

    /**
     * Reglage « Vitesse de croisiere » (deux cotes, sans effet s'il ne change pas) : taille du panneau racine
     * (fond et bordure) et ligne « Cible » ; les textes raccourcis a la largeur du panneau seront reconstruits.
     */
    void setCruise(boolean on)
    {
        if (on == cruise) return;
        cruise = on;
        root().setBounds(0f, 0f, on ? WIDTH : COMPACT_WIDTH, on ? HEIGHT : COMPACT_HEIGHT);
        targetLine.setVisible(on);
        shownTarget = Double.NEGATIVE_INFINITY;
        shownReference = null;
        shownWarp = null;
        shownDistance = Double.NaN;
        targetNameShown = false;
    }

    /**
     * Ligne de vitesse et ligne « Cible » (client, appele par le module a chaque tick) ; reconstruites seulement si
     * une valeur arrondie change (la fleche et la couleur en derivent).
     *
     * @param speed      vitesse signee arrondie ({@link SpaceFormat#round3}), km par tick
     * @param target     vitesse cible arrondie {@code v* >= 0}, km par tick, ou {@code NaN} sans cible (ou sans
     *                   vitesse de croisiere) : partie droite masquee
     * @param targetName nom de la cible, ou {@code null} sans cible
     */
    void showSpeed(double speed, double target, String targetName)
    {
        refreshStyles();
        boolean hasTarget = cruise && !Double.isNaN(target);
        double value = hasTarget ? target : Double.NaN;
        if (Double.compare(speed, shownSpeed) != 0 || Double.compare(value, shownTarget) != 0) {
            shownSpeed = speed;
            shownTarget = value;
            speedLine.setText(hasTarget ? cruiseText(speed, target)
                    : Component.literal(SPEED + SpaceFormat.speed(speed)));
        }
        if (!cruise) return;
        String name = hasTarget ? targetName : null;
        if (targetNameShown && Objects.equals(name, shownTargetName)) return;
        targetNameShown = true;
        shownTargetName = name;
        targetLine.setText(name == null ? NO_TARGET : Component.literal(fit(TARGET, name, "")));
    }

    /**
     * « Vitesse : v - fleche v* » : meme unite des deux cotes (la plus grande vitesse la choisit), fleche haut si
     * {@code v < v*} (ou {@code v = v* > 0}), bas sinon ; fleche et {@code v*} dans la couleur du palier de
     * {@link SpaceCruise#redness}.
     */
    private Component cruiseText(double speed, double target)
    {
        boolean au = SpaceFormat.speedInAu(Math.max(Math.abs(speed), target));
        boolean up = speed < target || speed == target && target > 0.0;
        int step = Math.round((float) SpaceCruise.redness(speed, target) * COLOR_STEPS);
        return Component.literal(SPEED + SpaceFormat.speed(speed, au) + SEPARATOR)
                .append(Component.literal((up ? UP : DOWN) + SpaceFormat.speed(target, au)).withStyle(styles[step]));
    }

    /**
     * Met a jour les lignes de referentiel et de distance dont la valeur a change (client, appele par le module au
     * plus 4 fois par seconde).
     *
     * @param reference   nom du corps de reference, ou {@code null} sans referentiel
     * @param distance    altitude arrondie au-dessus de ce corps, km (ignoree sans referentiel)
     * @param warpTarget  nom de l'etoile cible pendant la charge ou le voyage d'un saut, ou {@code null} hors saut :
     *                    la troisieme ligne affiche alors le saut a la place de la distance
     * @param warpSeconds temps de voyage restant, secondes entieres ({@link SpaceFormat#wholeSeconds}), ou
     *                    {@link #CHARGING} pendant la charge ; ignore hors saut
     */
    void show(String reference, double distance, String warpTarget, int warpSeconds)
    {
        if (reference == null) {
            if (shownReference != null || referenceLine.text() != NO_REFERENCE) {
                shownReference = null;
                referenceLine.setText(NO_REFERENCE);
            }
        } else if (!reference.equals(shownReference)) {
            shownReference = reference;
            referenceLine.setText(Component.literal(fit(REFERENCE, reference, "")));
        }
        if (warpTarget != null) {
            if (!warpTarget.equals(shownWarp) || warpSeconds != shownWarpSeconds) {
                shownWarp = warpTarget;
                shownWarpSeconds = warpSeconds;
                distanceLine.setText(Component.literal(warpSeconds == CHARGING ? fit(CHARGE, warpTarget, "")
                        : fit(WARP, warpTarget, " : " + SpaceFormat.seconds(warpSeconds))));
            }
            // La distance sera reecrite a la fin du saut.
            shownDistance = Double.NaN;
            return;
        }
        shownWarp = null;
        double value = reference == null ? Double.POSITIVE_INFINITY : distance;
        if (Double.compare(value, shownDistance) != 0) {
            shownDistance = value;
            distanceLine.setText(reference == null ? NO_DISTANCE
                    : Component.literal(DISTANCE + SpaceFormat.distance(value)));
        }
    }

    /**
     * {@code prefix + text + suffix}, {@code text} raccourci avec {@value #ELLIPSIS} pour tenir dans la largeur d'une
     * ligne du panneau courant.
     */
    private String fit(String prefix, String text, String suffix)
    {
        int max = (cruise ? WIDTH : COMPACT_WIDTH) - 2 * PADDING;
        String line = prefix + text + suffix;
        if (HoloTextMetrics.width(line) <= max) return line;
        for (int end = text.length() - 1; end > 0; end--) {
            line = prefix + text.substring(0, end).stripTrailing() + ELLIPSIS + suffix;
            if (HoloTextMetrics.width(line) <= max) return line;
        }
        return prefix + ELLIPSIS + suffix;
    }

    /** Styles du degrade d'apres le theme de la fenetre ; texte reconstruit si le theme a change. */
    private void refreshStyles()
    {
        int revision = HoloThemes.revision();
        if (revision == stylesRevision) return;
        stylesRevision = revision;
        HoloTheme theme = HoloThemes.resolve(this);
        int green = theme.rgb(HoloThemeColor.SELECTION);
        int red = theme.rgb(HoloThemeColor.CRITICAL);
        for (int i = 0; i <= COLOR_STEPS; i++) {
            styles[i] = Style.EMPTY.withColor(mix(green, red, i / (float) COLOR_STEPS));
        }
        shownSpeed = Double.NEGATIVE_INFINITY;
    }

    /** Melange lineaire de deux couleurs RGB, {@code t} dans {@code [0, 1]}. */
    private static int mix(int from, int to, float t)
    {
        int r = Math.round((from >> 16 & 0xFF) + t * ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)));
        int g = Math.round((from >> 8 & 0xFF) + t * ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)));
        int b = Math.round((from & 0xFF) + t * ((to & 0xFF) - (from & 0xFF)));
        return r << 16 | g << 8 | b;
    }
}
