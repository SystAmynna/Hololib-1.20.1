package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloColor;
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
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fenetre de controle du Space Controller : ancrage {@code World} a {@value #HEIGHT_ABOVE} bloc au-dessus du marker,
 * {@code PUBLIC}, acces {@code EVERYONE} par defaut (reglable, sauvegarde par le framework), clics gauche et droit
 * interceptes sur toute la fenetre. Editable par les operateurs (translation, rotation, echelle ; pas besoin d'etre le
 * createur du module) pour la placer.
 * <p>
 * Contenu : titre, etat (« Libre », « Pilote par X », « Aucun Space Environment dans cette dimension » ; pendant un
 * saut, T22 : « Saut vers X » puis « Distorsion : n s », {@link #showJump}), vitesse
 * (relative au referentiel), corps de reference (« Referentiel : X ») et altitude au-dessus de lui, interrupteur « Pilotage : tous / ops » (non predictif : il affiche l'etat recu ;
 * seul un operateur peut le changer, verifie par le serveur), zone {@link SpacePilotPad}. Les textes sont reconstruits
 * a chaque synchronisation ({@link #showState}), jamais par frame ; formats communs {@link SpaceFormat} (aussi utilises
 * par le HUD de navigation de Space Pilot).
 * <p>
 * <b>Masquee pendant le pilotage</b> (T41, D31) : {@link #shown()} est faux pour tous les joueurs tant que le pilote
 * passe par ce poste (etat synchronise du module) ; les fenetres des autres postes restent affichees (« Pilote par
 * X », clic refuse). La capture du pilote n'est pas liee a la fenetre ({@link SpacePilotPad}) : le masquage ne la
 * coupe pas. En session d'edition, la fenetre reste revelee par le framework.
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

    private static final HoloColor TEXT = HoloColor.primary(0xFF).lighter(0.84f);
    private static final HoloColor FREE = HoloColor.selection(0xFF).lighter(0.2f);
    private static final HoloColor BUSY = HoloColor.action(0xFF);
    private static final HoloColor MISSING = HoloColor.critical(0xFF).lighter(0.18f);
    /** Etat du saut : charge (couleur de selection des modules Space), voyage (blanc bleute). */
    private static final HoloColor JUMP_CHARGE = HoloColor.action(0xFF).lighter(0.03f);
    private static final HoloColor JUMP_TRAVEL = HoloColor.special(0xFF).darker(0.05f);
    private static final Component ACCESS_ALL = Component.literal("Pilotage : tous");
    private static final Component ACCESS_OPS = Component.literal("Pilotage : ops");

    private final HoloLabel state;
    private final HoloLabel speedLine;
    private final HoloLabel bodyLine;
    private final HoloLabel altitudeLine;
    private final HoloWidgetSwitch accessSwitch;
    private final SpacePilotPad pad;
    /** Etat normal de la ligne d'etat (dernier {@link #showState}), remis a la fin d'un saut. */
    private Component stateText = Component.empty();
    private HoloColor stateColor = TEXT;
    /** Texte du saut en cours qui remplace l'etat normal, ou {@code null}. */
    private Component jumpText;
    private HoloColor jumpColor = JUMP_CHARGE;

    /**
     * @param link             envois du pilotage (module)
     * @param opsOnlyRequested demande d'un nouvel acces au pilotage (vrai : reserve aux ops), envoyee au serveur
     */
    SpaceControllerWindow(String key, SpacePilotPad.Link link, Consumer<Boolean> opsOnlyRequested)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, HEIGHT_ABOVE, 0f), WIDTH, HEIGHT,
                PIXEL_SCALE);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER);
        setAccess(HoloAccess.EVERYONE);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        root().add(new HoloLabel(4, 3, WIDTH - 8, 11, Component.literal("Space Controller"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        state = root().add(new HoloLabel(6, 16, WIDTH - 12, 10, Component.empty()));
        speedLine = root().add(new HoloLabel(6, 28, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        bodyLine = root().add(new HoloLabel(6, 39, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        altitudeLine = root().add(new HoloLabel(6, 50, WIDTH - 12, 10, Component.empty()).setColor(TEXT));
        Objects.requireNonNull(opsOnlyRequested);
        accessSwitch = root().add(new HoloWidgetSwitch(6, 62, WIDTH - 12, 12, ACCESS_ALL,
                (widget, requested) -> opsOnlyRequested.accept(requested)));
        accessSwitch.setPredictive(false).setColors(BUSY, HoloColor.selection(0xFF).darker(0.28f), TEXT);
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
     * Client : faux quand le pilote courant passe par ce poste (D31, {@code pilot != null && pilotHere}, etat du module
     * synchronise chez tous), pour le pilote comme pour les autres joueurs ; vrai sinon (poste libre, ou pilote depuis
     * un autre poste).
     */
    @Override
    public boolean shown()
    {
        return !(module() instanceof SpaceControllerModule controller && controller.pilot() != null
                && controller.pilotHere());
    }

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
            setState(Component.literal("Aucun Space Environment"), MISSING);
            speedLine.setText(Component.literal("dans cette dimension")).setColor(MISSING);
            bodyLine.setText(Component.empty());
            altitudeLine.setText(Component.empty());
            return;
        }
        if (pilot == null) {
            setState(Component.literal("Libre"), FREE);
        } else {
            setState(Component.literal("Pilote par " + pilotName), BUSY);
        }
        speedLine.setText(Component.literal("Vitesse : " + SpaceFormat.speed(speed))).setColor(TEXT);
        if (body.isEmpty()) {
            bodyLine.setText(Component.literal("Aucun referentiel"));
            altitudeLine.setText(Component.empty());
        } else {
            bodyLine.setText(Component.literal("Referentiel : " + body));
            altitudeLine.setText(Component.literal("Altitude : " + SpaceFormat.distance(altitude)));
        }
    }

    /**
     * Client (T22) : etat du saut qui remplace la ligne d'etat ({@code text} : « Saut vers X » pendant la charge,
     * « Distorsion : n s » pendant le voyage), ou {@code null} pour revenir a l'etat normal. Appele par le module
     * seulement quand le texte change.
     *
     * @param travel vrai pendant le voyage (couleur du voyage), faux pendant la charge
     */
    void showJump(Component text, boolean travel)
    {
        jumpText = text;
        jumpColor = travel ? JUMP_TRAVEL : JUMP_CHARGE;
        refreshState();
    }

    /** Etat normal (synchronise), affiche sauf pendant un saut. */
    private void setState(Component text, HoloColor color)
    {
        stateText = text;
        stateColor = color;
        refreshState();
    }

    private void refreshState()
    {
        if (jumpText != null) state.setText(jumpText).setColor(jumpColor);
        else state.setText(stateText).setColor(stateColor);
    }
}
