package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloTextMetrics;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import net.minecraft.network.chat.Component;

/**
 * HUD de navigation de Space Pilot (T20, D14) : objet de {@link SpacePilotModule}, ancrage {@code Hud} au coin haut
 * gauche de l'ecran (dessine sous le HUD vanilla, famille {@code HUD}), {@code PRIVATE}. Trois lignes :
 * <ul>
 *     <li>« Vitesse : v » : vitesse signee relative au referentiel, km/s ou UA/s (meme format que la fenetre du Space
 *     Controller, {@link SpaceFormat#speed}) ;</li>
 *     <li>« Reference : nom » : corps de reference (referentiel), nom raccourci s'il depasse la fenetre ;</li>
 *     <li>« Distance : d » : altitude au-dessus du corps de reference (comme l'« Altitude » du Controller), km ou UA
 *     ({@link SpaceFormat#distance}) ; pendant la charge d'un saut, « Saut -> cible », puis pendant le voyage
 *     (T22, livrable 4 de T20) « Distorsion -> cible : n s » (nom de l'etoile cible raccourci s'il le faut, temps
 *     restant en secondes entieres) a sa place.</li>
 * </ul>
 * Fenetre plutot qu'objet dessine a la main : trois {@link HoloLabel} dessines par le renderer generique des fenetres
 * (aucun renderer propre a enregistrer), et le cadre HUD de l'editor connait deja la taille d'une fenetre.
 * <p>
 * Deplacable par l'Own HUD Editor ({@link HoloEditTranslation} seulement : glisser dans le cadre, molette = ordre de
 * dessin ; capacite {@code OWN_HUD} deduite de l'ancrage), placement sauvegarde par le framework.
 * {@link #shown()} : client du proprietaire, Space Environment dans sa dimension, interrupteur general et reglage
 * {@code hud} actifs ({@link SpacePilotModule#hudShown()}). Le routage du {@code Hud} le masque en F5 ; non partage,
 * un joueur qui inspecte ne le voit jamais (C45).
 * <p>
 * Textes reconstruits par {@link #show} (appele par le module au plus 4 fois par seconde), seulement pour les lignes
 * dont la valeur arrondie a change : aucune chaine allouee par frame.
 */
public final class SpacePilotHud extends HoloWindow implements HoloEditTranslation
{
    public static final int WIDTH = 150;
    public static final int HEIGHT = 39;
    /** Taille d'un pixel de fenetre ; l'echelle du transform la compense : 1 pixel de fenetre = 1 pixel GUI. */
    private static final float PIXEL_SCALE = 1f / 160f;
    /** Marge au bord de l'ecran, pixels GUI (placement par defaut). */
    private static final float MARGIN = 4f;
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int TEXT = 0xFFE0F0FF;
    private static final String SPEED = "Vitesse : ";
    private static final String REFERENCE = "Reference : ";
    private static final String DISTANCE = "Distance : ";
    private static final String WARP = "Distorsion -> ";
    private static final String CHARGE = "Saut -> ";
    /** Valeur de {@code warpSeconds} pendant la charge d'un saut (pas de temps restant affiche). */
    static final int CHARGING = -1;
    private static final String ELLIPSIS = "...";
    private static final Component NO_REFERENCE = Component.literal(REFERENCE + "aucune");
    private static final Component NO_DISTANCE = Component.literal(DISTANCE + "-");

    private final HoloLabel speedLine;
    private final HoloLabel referenceLine;
    private final HoloLabel distanceLine;

    // Valeurs affichees (arrondies) : une ligne n'est reconstruite que si la sienne change.
    private double shownSpeed = Double.NaN;
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
        root().setBackground(ConfigWindow.BACKGROUND).setBorder(ConfigWindow.ACCENT, 1f);
        int width = WIDTH - 2 * PADDING;
        speedLine = root().add(new HoloLabel(PADDING, 3, width, LINE_HEIGHT, Component.literal(SPEED + "-"))
                .setColor(TEXT));
        referenceLine = root().add(new HoloLabel(PADDING, 3 + LINE_HEIGHT, width, LINE_HEIGHT, NO_REFERENCE)
                .setColor(TEXT));
        distanceLine = root().add(new HoloLabel(PADDING, 3 + 2 * LINE_HEIGHT, width, LINE_HEIGHT, NO_DISTANCE)
                .setColor(TEXT));
    }

    /** Client du proprietaire : Environment dans sa dimension, {@code enabled} et {@code hud} actifs. */
    @Override
    public boolean shown() { return module() instanceof SpacePilotModule pilot && pilot.hudShown(); }

    /**
     * Met a jour les lignes dont la valeur a change (client, appele par le module au plus 4 fois par seconde).
     *
     * @param speed       vitesse signee arrondie ({@link SpaceFormat#round3}), km par tick
     * @param reference   nom du corps de reference, ou {@code null} sans referentiel
     * @param distance    altitude arrondie au-dessus de ce corps, km (ignoree sans referentiel)
     * @param warpTarget  nom de l'etoile cible pendant la charge ou le voyage d'un saut, ou {@code null} hors saut :
     *                    la troisieme ligne affiche alors le saut a la place de la distance
     * @param warpSeconds temps de voyage restant, secondes entieres ({@link SpaceFormat#wholeSeconds}), ou
     *                    {@link #CHARGING} pendant la charge ; ignore hors saut
     */
    void show(double speed, String reference, double distance, String warpTarget, int warpSeconds)
    {
        if (Double.compare(speed, shownSpeed) != 0) {
            shownSpeed = speed;
            speedLine.setText(Component.literal(SPEED + SpaceFormat.speed(speed)));
        }
        if (reference == null) {
            if (shownReference != null) {
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
     * ligne.
     */
    private static String fit(String prefix, String text, String suffix)
    {
        int max = WIDTH - 2 * PADDING;
        String line = prefix + text + suffix;
        if (HoloTextMetrics.width(line) <= max) return line;
        for (int end = text.length() - 1; end > 0; end--) {
            line = prefix + text.substring(0, end).stripTrailing() + ELLIPSIS + suffix;
            if (HoloTextMetrics.width(line) <= max) return line;
        }
        return prefix + ELLIPSIS + suffix;
    }
}
