package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fenetre de controle du Space Controller : ancrage {@code World} a {@value #HEIGHT_ABOVE} bloc au-dessus du marker,
 * {@code PUBLIC}, acces {@code EVERYONE} par defaut (reglable, sauvegarde par le framework), clics gauche et droit
 * interceptes sur toute la fenetre. Editable par les operateurs (translation, rotation, echelle ; pas besoin d'etre le
 * createur du module) pour la placer.
 * <p>
 * Contenu : titre, etat (« Libre », « Pilote par X », « Aucun Space Environment dans cette dimension »), vitesse
 * (relative au referentiel), corps de reference (« Referentiel : X ») et altitude au-dessus de lui, interrupteur « Pilotage : tous / ops » (non predictif : il affiche l'etat recu ;
 * seul un operateur peut le changer, verifie par le serveur), zone {@link SpacePilotPad}. Les textes sont reconstruits
 * a chaque synchronisation ({@link #showState}), jamais par frame.
 */
public final class SpaceControllerWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
        HoloEditScale
{
    public static final int WIDTH = 176;
    public static final int HEIGHT = 108;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;
    /** Hauteur du centre de la fenetre au-dessus du marker, en blocs. */
    public static final float HEIGHT_ABOVE = 1.6f;

    private static final int TEXT = 0xFFE0F0FF;
    private static final int FREE = 0xFF80FF80;
    private static final int BUSY = 0xFFFFC040;
    private static final int MISSING = 0xFFFF8060;
    /** Au-dela (km/s), la vitesse est affichee en UA/s. */
    private static final double SPEED_AU_THRESHOLD = 0.01 * SpaceUnits.AU_KM;
    /** Au-dela (km), l'altitude est affichee en UA. */
    private static final double DISTANCE_AU_THRESHOLD = 0.01 * SpaceUnits.AU_KM;
    private static final Component ACCESS_ALL = Component.literal("Pilotage : tous");
    private static final Component ACCESS_OPS = Component.literal("Pilotage : ops");

    private final HoloLabel state;
    private final HoloLabel speedLine;
    private final HoloLabel bodyLine;
    private final HoloLabel altitudeLine;
    private final HoloWidgetSwitch accessSwitch;
    private final SpacePilotPad pad;

    /**
     * @param link             envois du pilotage (module)
     * @param opsOnlyRequested demande d'un nouvel acces au pilotage (vrai : reserve aux ops), envoyee au serveur
     */
    SpaceControllerWindow(String key, SpacePilotPad.Link link, Consumer<Boolean> opsOnlyRequested)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, HEIGHT_ABOVE, 0f), WIDTH, HEIGHT,
                PIXEL_SCALE);
        setVisibility(HoloVisibility.PUBLIC);
        setAccess(HoloAccess.EVERYONE);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setBackground(ConfigWindow.BACKGROUND).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Space Controller"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        state = root().add(new HoloLabel(6, 16, WIDTH - 12, 10, Component.empty()));
        speedLine = root().add(new HoloLabel(6, 28, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        bodyLine = root().add(new HoloLabel(6, 39, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        altitudeLine = root().add(new HoloLabel(6, 50, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        Objects.requireNonNull(opsOnlyRequested);
        accessSwitch = root().add(new HoloWidgetSwitch(6, 62, WIDTH - 12, 12, ACCESS_ALL,
                (widget, requested) -> opsOnlyRequested.accept(requested)));
        accessSwitch.setPredictive(false).setColors(BUSY, 0xFF40A060, TEXT);
        pad = root().add(new SpacePilotPad(6, 76, WIDTH - 12, 26, link));
    }

    /** Pour la fenetre de controle d'un poste public, l'edition est reservee aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la placer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    public SpacePilotPad pad() { return pad; }

    /**
     * Met a jour les textes et la zone de pilotage (deux cotes, a la creation et a chaque synchronisation).
     *
     * @param environment Space Environment present dans la dimension
     * @param pilot       pilote courant, ou {@code null}
     * @param pilotName   nom du pilote
     * @param here        vrai si le pilote passe par ce poste
     * @param speed       vitesse nominale signee, km par tick, relative au referentiel (negative en marche arriere,
     *                    affichee avec son signe)
     * @param body        nom du corps de reference ({@code ""} sans corps)
     * @param altitude    altitude au-dessus de ce corps, km
     * @param opsOnly     pilotage reserve aux operateurs
     */
    void showState(boolean environment, UUID pilot, String pilotName, boolean here, double speed, String body,
                   double altitude, boolean opsOnly)
    {
        accessSwitch.setOn(opsOnly);
        accessSwitch.setLabel(opsOnly ? ACCESS_OPS : ACCESS_ALL);
        pad.syncState(environment, pilot, here, opsOnly);
        if (!environment) {
            state.setText(Component.literal("Aucun Space Environment")).setColor(MISSING);
            speedLine.setText(Component.literal("dans cette dimension")).setColor(MISSING);
            bodyLine.setText(Component.empty());
            altitudeLine.setText(Component.empty());
            return;
        }
        if (pilot == null) {
            state.setText(Component.literal("Libre")).setColor(FREE);
        } else {
            state.setText(Component.literal("Pilote par " + pilotName)).setColor(BUSY);
        }
        speedLine.setText(Component.literal("Vitesse : " + speedText(speed))).setColor(TEXT);
        if (body.isEmpty()) {
            bodyLine.setText(Component.literal("Aucun referentiel"));
            altitudeLine.setText(Component.empty());
        } else {
            bodyLine.setText(Component.literal("Referentiel : " + body));
            altitudeLine.setText(Component.literal("Altitude : " + distanceText(altitude)));
        }
    }

    // ---------------------------------------------------------------- formats

    /**
     * Vitesse signee (km par tick) en km/s, ou en UA/s au-dela de 0,01 UA/s en valeur absolue ; une vitesse negative
     * (marche arriere) garde son signe ({@code -12,3 km/s}).
     */
    static String speedText(double kmPerTick)
    {
        double kms = kmPerTick * SpaceUnits.TICKS_PER_SECOND;
        if (Math.abs(kms) >= SPEED_AU_THRESHOLD) return number(SpaceUnits.kmToAu(kms)) + " UA/s";
        return number(kms) + " km/s";
    }

    /** Distance (km) en km, ou en UA au-dela de 0,01 UA. */
    static String distanceText(double km)
    {
        if (!Double.isFinite(km)) return "-";
        if (Math.abs(km) >= DISTANCE_AU_THRESHOLD) return number(SpaceUnits.kmToAu(km)) + " UA";
        return number(km) + " km";
    }

    /** Nombre a trois chiffres significatifs (a la francaise : virgule decimale, milliers separes par une espace). */
    static String number(double value)
    {
        if (!Double.isFinite(value)) return "-";
        double magnitude = Math.abs(value);
        if (magnitude < 1e-6) return "0";
        if (magnitude >= 1000.0) return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
        int decimals = Math.min(6, Math.max(0, 2 - (int) Math.floor(Math.log10(magnitude))));
        return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', ',');
    }
}
