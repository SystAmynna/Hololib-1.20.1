package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloDirections;
import com.amynna.hololib.holo.widget.control.HoloPointerInput;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpacePilotInput;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * Zone « Cliquer pour piloter » de la fenetre du Space Controller ({@link HoloPointerInput} : survol, capture au clic
 * gauche ou droit ; Echap ou Entree la terminent). Au debut de la capture, demande le pilotage ({@link Link#begin()}) ;
 * a la fin, envoie une entree nulle puis rend le pilotage ({@link Link#end()}).
 * <p>
 * <b>Capture independante de la fenetre</b> (T41, D31) : la fenetre du poste utilise est masquee pendant le pilotage
 * ({@link SpaceControllerWindow#shown()}). Une capture liee a l'objet ({@code ctx.capture}, celle du trait) serait
 * terminee par le framework des que la fenetre n'est plus routee : le pad la demarre donc lui-meme par
 * {@link HoloCaptures#begin} (sans lien avec la fenetre) et la termine lui-meme dans tous les cas ou la capture liee
 * le faisait : Echap / Entree (trait), autre pilote, Environment absent, pilotage reserve aux ops
 * ({@link #syncState}), delai {@value #CLAIM_TIMEOUT} ticks, module Space Pilot retire, acces de la fenetre perdu
 * ({@link #tick()}), module decharge ou marker retire, changement de dimension, deconnexion
 * ({@code SpaceControllerModule.onUnload}, cote client). Son {@link #tick()} continue quand la fenetre est masquee
 * (objets tickes par leur module, pas par le rendu).
 * <p>
 * <b>Pilotage</b> (conception : {@code docs/space/04-mathematiques.md} par. 5 et 6, {@code 05-synchronisation.md}
 * par. 2 et 3) :
 * <ul>
 *     <li>controles pris en plus du trait pointeur : les quatre mappings de deplacement ({@link HoloDirections}, la
 *     touche choisie par le joueur), le mapping Saut ({@link #BRAKE}) et les clics bruts (rien n'est attaque ni
 *     utilise pendant le pilotage, le joueur ne saute pas : la capture neutralise les mappings pris) ;
 *     Avancer / Reculer = gaz (vitesse signee : Reculer fait partir le monde en arriere), Gauche / Droite = roulis,
 *     Saut maintenu = frein vers l'arret, prioritaire sur les gaz (le pad envoie le drapeau et les gaz tels quels,
 *     le calcul est dans {@code SpaceMotion.step}) ;</li>
 *     <li>boost (T32, D24, C74) : le mapping Sprint ({@link #BOOST}, la touche choisie par le joueur) est pris aussi ;
 *     maintenu, il leve le drapeau {@code boost} de l'entree (envoye au changement) : avec Avancer / Reculer, la
 *     vitesse change bien plus vite, avec Saut le frein est bien plus fort, seul il ne fait rien ; le personnage ne
 *     sprinte pas ;</li>
 *     <li>saut interstellaire (T22, {@code docs/space/04-mathematiques.md} par. 8, C39 a C41) : le mapping Accroupi
 *     ({@link #JUMP}, la touche choisie par le joueur) est pris aussi ; a l'appui, la cible est l'etoile de fond la
 *     plus proche du centre de l'ecran a moins de {@value SpaceJump#TARGET_CONE} degres
 *     ({@link SpaceJump#pickTarget}, ciel affiche a la derniere frame), figee ensuite ; tant que la touche est tenue,
 *     l'entree porte le drapeau du saut et cette cible ({@link SpacePilotInput#jumpTarget()}) ; relachee, le drapeau
 *     tombe (le serveur annule une charge). Aucune cible : rien (pas de message). Pas de nouvelle cible pendant un
 *     voyage. Pendant un saut (touche tenue avec une cible, ou charge / voyage affiches), les taux du joystick sont
 *     envoyes nuls (la camera reste bornee au cone, la visee continue). Echap pendant le voyage rend la main sans
 *     l'arreter (C40) ;</li>
 *     <li>Sneak et Sprint n'agissent jamais sur le personnage (T32) : en mode maintenu, la capture neutralise le
 *     mapping aussitot apres chaque appui et au debut de chaque tick (avant les entrees du joueur) ; en mode
 *     <b>bascule</b>, cette neutralisation est sans effet ({@code ToggleKeyMapping} ignore {@code setDown(false)}),
 *     donc le pad remet les mappings a bascule au repos au debut du pilotage, a chaque appui pris et a chaque tick
 *     ({@link SpaceControllerModule#releaseToggleKeys()}, backend client) ;</li>
 *     <li>joystick au regard : la camera est bornee a un cone de {@value #CONE_ANGLE} degres autour de l'axe avant du
 *     monde (reglage de l'Environment, fixe dans le monde) ; l'ecart au centre, au-dela de la zone morte
 *     {@link #DEAD_ZONE} (C48 : rayon angulaire du cercle avant moins celui du cercle de visee, 2,0 degres), donne
 *     les taux de lacet et de tangage, continus au bord de la zone morte. A la prise, le regard est ramene sur l'axe
 *     avant en {@value #RECENTER_SECONDS} s (taux nuls pendant ce retour) ;</li>
 *     <li>visee (choix du referentiel, {@code docs/space/04-mathematiques.md} par. 5 point 0, D8) : l'ecart <b>reel</b>
 *     du regard (y compris dans la zone morte, nul pendant le retour) est quantifie dans l'entree
 *     ({@code SpacePilotInput.aimYaw / aimPitch}) ; une variation de moins d'un pas de quantification garde la visee
 *     envoyee (pas de renvoi pour un tremblement autour d'un arrondi) ;</li>
 *     <li>envoi ({@code INPUT}) quand l'entree quantifiee change, au plus tous les {@value #SEND_INTERVAL} ticks, et
 *     battement toutes les {@value #HEARTBEAT_INTERVAL} ticks (sinon l'Environment libere le pilote,
 *     {@code SpaceEnvironmentModule.PILOT_TIMEOUT}) ; chaque entree envoyee est aussitot predite localement
 *     ({@code SpaceEnvironmentModule.predictPilotInput}).</li>
 * </ul>
 * Etat affiche (recu du module, {@link #syncState}) : Environment present, pilote courant, pilotage reserve aux ops.
 * La main est rendue ({@link HoloCaptures#end}) si l'etat designe un autre pilote, si l'Environment disparait, si le
 * pilotage devient reserve aux ops et que le joueur local ne l'est pas, ou si l'etat ne designe toujours pas le
 * joueur local {@value #CLAIM_TIMEOUT} ticks apres la demande (refus du serveur, pilote libere). En mode « ops », un
 * non-op voit « Reserve aux operateurs » et la zone ne prend pas son clic (le serveur refuse aussi {@code BEGIN}).
 * Sans module Space Pilot dans la memoire active du joueur local (D11), le clic est pris sans capture et le message
 * « Module Space Pilot requis » s'affiche (le serveur refuse aussi {@code BEGIN}, la main est alors rendue apres
 * {@value #CLAIM_TIMEOUT} ticks comme pour un autre refus).
 * <p>
 * Lecture pour le joystick de Space Pilot (T19, client) : {@link #localPilot()} (pad du pilote local),
 * {@link #piloting()}, {@link #recentering()}, {@link #deviationYaw()}, {@link #deviationPitch()},
 * {@link #deflection()}, {@link #beyondDeadZone()} ; pour le reperage de la cible du saut (T22) : {@link #jumpHeld()},
 * {@link #jumpTarget()}, {@link #jumpLocked()}. Ne pas redefinir {@code intercepts} (regle des traits).
 */
public class SpacePilotPad extends HoloWidget implements HoloPointerInput
{
    /** Envois vers le module serveur, fournis par le module (qui seul peut appeler {@code sendAction}). */
    public interface Link
    {
        /** Action {@code BEGIN} : demande le pilotage. */
        void begin();

        /** Action {@code INPUT} : entree quantifiee du pilote. */
        void input(SpacePilotInput input);

        /** Action {@code END} : rend le pilotage. */
        void end();
    }

    /** Intervalle maximal entre deux envois d'entree pendant le pilotage (battement), en ticks. */
    public static final int HEARTBEAT_INTERVAL = 20;
    /** Delai apres lequel la capture est rendue si l'etat ne designe pas le joueur local comme pilote, en ticks. */
    public static final int CLAIM_TIMEOUT = 40;
    /** Intervalle minimal entre deux envois d'une entree modifiee, en ticks. */
    public static final int SEND_INTERVAL = 2;
    /** Demi-angle du cone du joystick {@code A}, en degres (ecart maximal du regard a l'axe avant). */
    public static final float CONE_ANGLE = 30f;
    /**
     * Zone morte du joystick {@code D}, en degres : en dessous, aucun taux de rotation. Rayon angulaire du cercle avant
     * moins celui du cercle de visee (C48, {@code docs/space/04-mathematiques.md} par. 6), tous deux a
     * {@link SpaceJoystickForward#DISTANCE} blocs : aucune rotation tant que le cercle de visee est entierement dans le
     * cercle avant. Vaut 2,0 degres ({@link SpaceJoystickForward#RADIUS} 0,105, {@link SpaceJoystickAim#RADIUS}
     * 0,035).
     */
    public static final double DEAD_ZONE = StrictMath.toDegrees(
            StrictMath.atan((double) SpaceJoystickForward.RADIUS / SpaceJoystickForward.DISTANCE)
                    - StrictMath.atan((double) SpaceJoystickAim.RADIUS / SpaceJoystickForward.DISTANCE));
    /** Duree du retour du regard sur l'axe avant a la prise du pilotage, en secondes. */
    public static final float RECENTER_SECONDS = 0.4f;
    /** Ecart (degres) sous lequel le retour sur l'axe avant est inutile. */
    private static final float RECENTER_EPSILON = 0.05f;
    /** Controle du frein : le mapping Saut (Espace par defaut, la touche choisie par le joueur). */
    public static final HoloControl BRAKE = HoloControl.mapping("key.jump");
    /**
     * Controle du saut interstellaire : le mapping Accroupi (Maj gauche par defaut, la touche choisie par le joueur).
     * Pris par la capture, il ne va ni au jeu (le joueur ne s'accroupit pas) ni au double appui qui ferme le focus.
     */
    public static final HoloControl JUMP = HoloControl.mapping("key.sneak");
    /**
     * Controle du boost (T32) : le mapping Sprint (Ctrl gauche par defaut, la touche choisie par le joueur). Pris par
     * la capture, il ne va ni au jeu (le joueur ne sprinte pas) ni a la sortie du focus.
     */
    public static final HoloControl BOOST = HoloControl.mapping("key.sprint");

    private static final int ACCENT = 0x40C0FF;
    private static final Component IDLE_TEXT = Component.literal("Cliquer pour piloter");
    private static final Component PILOTING_TEXT = Component.literal("Pilotage en cours");
    private static final Component EXIT_TEXT = Component.literal("Echap : rendre la main");
    private static final Component BUSY_TEXT = Component.literal("Poste occupe");
    private static final Component ELSEWHERE_TEXT = Component.literal("Pilote depuis un autre poste");
    private static final Component UNAVAILABLE_TEXT = Component.literal("Indisponible");
    private static final Component RESERVED_TEXT = Component.literal("Reserve aux operateurs");

    /**
     * Client : pad dont la capture de pilotage est en cours (une capture de pilotage a la fois chez un client), ou
     * {@code null}. Pose au debut de la capture, retire a la fin ; jamais pose cote serveur (aucune capture).
     */
    private static SpacePilotPad capturing;

    private final Link link;
    private boolean hovered;
    /** Environment present dans la dimension (etat recu). */
    private boolean available;
    /** Pilote courant (etat recu), ou {@code null}. */
    private UUID pilot;
    /** Vrai si le pilote courant passe par ce poste (etat recu). */
    private boolean pilotHere;
    /** Pilotage reserve aux operateurs (etat recu). */
    private boolean opsOnly;
    /** Vrai entre le debut et la fin de la capture. */
    private boolean piloting;
    /** Joueur local, connu pendant la capture. */
    private UUID self;
    private int sinceSend;
    private int unconfirmed;

    // ---------------------------------------------------------------- pilotage (client, pendant la capture)

    /** Touches de deplacement enfoncees (appuis pris par la capture). */
    private final HoloDirections directions = new HoloDirections();
    /** Touche Saut enfoncee (appui pris par la capture) : frein. */
    private boolean braking;
    /** Touche du saut (Accroupi) enfoncee (appui pris par la capture). */
    private boolean jumpHeld;
    /** Touche du boost (Sprint) enfoncee (appui pris par la capture, T32). */
    private boolean boosting;
    /** Cible du saut choisie a l'appui et tenue avec la touche ({@code 0} : aucune). */
    private int jumpTarget;
    /** Vrai pendant un saut (touche tenue avec une cible, ou charge / voyage affiches) : taux du joystick nuls. */
    private boolean jumpLocked;
    /** Recherche de la cible (client, crees au premier appui). */
    private SpacePose aimPose;
    private SpaceSkyFrame aimFrame;
    private Vector3f aimWork;
    /** Derniere entree envoyee (et predite) ; renvoyee par le battement. */
    private SpacePilotInput sent = SpacePilotInput.ZERO;
    /** Vrai jusqu'au premier mouvement de souris de la capture (debut du retour sur l'axe avant). */
    private boolean recenterPending;
    /** Vrai pendant le retour du regard sur l'axe avant. */
    private boolean recentering;
    /** Avancement du retour, {@code [0, 1]}. */
    private float recenterProgress;
    /** Ecart initial du retour a l'axe avant, degres. */
    private float recenterYaw;
    private float recenterPitch;
    /** Ecart courant du regard a l'axe avant (dans le cone), degres ; nul pendant le retour ou sans Environment. */
    private float deviationYaw;
    private float deviationPitch;

    public SpacePilotPad(float x, float y, float width, float height, Link link)
    {
        super(x, y, width, height);
        this.link = Objects.requireNonNull(link);
    }

    // ---------------------------------------------------------------- etat

    /**
     * Etat recu du serveur (client, a chaque synchronisation). Rend la main si un autre joueur pilote, s'il n'y a
     * plus d'Environment, ou si le pilotage est reserve aux ops et que le joueur local ne l'est pas.
     */
    public void syncState(boolean available, UUID pilot, boolean pilotHere, boolean opsOnly)
    {
        this.available = available;
        this.pilot = pilot;
        this.pilotHere = pilotHere;
        this.opsOnly = opsOnly;
        if (piloting && (!available || pilot != null && !pilot.equals(self) || !allowed())) HoloCaptures.end(this);
    }

    /** Client : vrai si le joueur local peut piloter depuis ce poste (ouvert a tous, ou joueur local operateur). */
    public final boolean allowed() { return !opsOnly || SpaceControllerModule.localOp(); }

    /** Vrai pendant la capture du pilote (client). */
    public final boolean piloting() { return piloting; }

    /** Vrai si l'etat recu designe le joueur local comme pilote (pendant la capture). */
    public final boolean confirmed() { return piloting && self != null && self.equals(pilot); }

    /**
     * Client : pad depuis lequel le joueur local pilote, ou {@code null}. Meme regle que le lot 1 (C29) : capture en
     * cours ({@code HoloCaptures.isCapturing}) et etat recu du poste qui designe le joueur local comme pilote passant
     * par ce poste. Lu par le joystick de Space Pilot (affichage, zone morte franchie).
     */
    public static SpacePilotPad localPilot()
    {
        SpacePilotPad pad = capturing;
        if (pad == null || !pad.confirmed() || !pad.pilotHere) return null;
        return HoloCaptures.isCapturing(pad) ? pad : null;
    }

    // ---------------------------------------------------------------- traits

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    /**
     * Sans Environment, ou pilotage reserve aux ops pour un non-op, le clic n'est pas pris par la zone (la fenetre
     * l'intercepte : rien n'est casse derriere).
     */
    @Override
    public boolean clickableBy(HoloControl button)
    {
        return available && allowed() && HoloPointerInput.super.clickableBy(button);
    }

    /**
     * Pas de capture si un autre joueur pilote deja, ni pour un non-op en mode « ops », ni sans module Space Pilot dans
     * la memoire active du joueur local (D11 : message dans la barre d'action). Sinon capture explicite
     * ({@link HoloCaptures#begin}), pas la capture liee du trait (T41).
     */
    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        if (!available || pilot != null && !pilot.equals(ctx.player().getUUID())) return;
        if (opsOnly && !ctx.player().hasPermissions(SpaceControllerModule.OP_LEVEL)) return;
        if (!SpaceControllerModule.localSpacePilot()) {
            ctx.player().displayClientMessage(SpaceControllerModule.SPACE_PILOT_REQUIRED, true);
            return;
        }
        // Capture non liee a la fenetre (T41) : elle survit au masquage de la fenetre du poste utilise.
        HoloCaptures.begin(this);
    }

    @Override
    public boolean captures(HoloControl control)
    {
        return HoloPointerInput.super.captures(control) || capturesPilotControl(control);
    }

    @Override
    public void onPress(HoloCapture ctx, HoloControl control)
    {
        if (capturesPilotControl(control)) onPilotPress(ctx, control);
        else HoloPointerInput.super.onPress(ctx, control);
    }

    @Override
    public void onRelease(HoloCapture ctx, HoloControl control)
    {
        if (capturesPilotControl(control)) onPilotRelease(ctx, control);
    }

    @Override
    public void onCaptureStart(HoloCapture ctx)
    {
        piloting = true;
        capturing = this;
        self = ctx.player().getUUID();
        sinceSend = 0;
        unconfirmed = 0;
        link.begin();
        onPilotStart(ctx);
    }

    @Override
    public void onCaptureEnd(HoloCapture ctx)
    {
        onPilotEnd(ctx);
        link.end();
        piloting = false;
        if (capturing == this) capturing = null;
        self = null;
    }

    /**
     * Joystick au regard (chaque frame pendant la capture) : nouveau regard = regard + deplacement, borne au cone
     * autour de l'axe avant de l'Environment, puis {@code setLook}. Au premier appel, retour progressif sur l'axe
     * avant ({@value #RECENTER_SECONDS} s, deplacements ignores). Sans Environment synchronise, la camera reste figee.
     * Le yaw reste continu (pas de saut de 360 degres) : seul l'ecart est ramene a {@code [-180, 180[}.
     */
    @Override
    public void onMouseMove(HoloCapture ctx, float dYaw, float dPitch)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced()) {
            deviationYaw = deviationPitch = 0f;
            return;
        }
        SpaceMotionSettings settings = env.settings();
        float forwardYaw = (float) settings.forwardYaw();
        float forwardPitch = (float) settings.forwardPitch();
        float yaw = ctx.yaw();
        float pitch = ctx.pitch();
        if (recenterPending) {
            recenterPending = false;
            recenterYaw = Mth.wrapDegrees(yaw - forwardYaw);
            recenterPitch = pitch - forwardPitch;
            recenterProgress = 0f;
            recentering = Math.abs(recenterYaw) > RECENTER_EPSILON || Math.abs(recenterPitch) > RECENTER_EPSILON;
        }
        float offsetYaw;
        float offsetPitch;
        if (recentering) {
            recenterProgress = Math.min(1f, recenterProgress + ctx.frameSeconds() / RECENTER_SECONDS);
            float t = recenterProgress;
            float remaining = 1f - t * t * (3f - 2f * t);
            offsetYaw = recenterYaw * remaining;
            offsetPitch = recenterPitch * remaining;
            if (recenterProgress >= 1f) recentering = false;
        } else {
            offsetYaw = Mth.wrapDegrees(yaw + dYaw - forwardYaw);
            offsetPitch = pitch + dPitch - forwardPitch;
            float magnitude = Mth.sqrt(offsetYaw * offsetYaw + offsetPitch * offsetPitch);
            if (magnitude > CONE_ANGLE) {
                float scale = CONE_ANGLE / magnitude;
                offsetYaw *= scale;
                offsetPitch *= scale;
            }
        }
        float targetPitch = Mth.clamp(forwardPitch + offsetPitch, -90f, 90f);
        offsetPitch = targetPitch - forwardPitch;
        float targetYaw = yaw + Mth.wrapDegrees(forwardYaw + offsetYaw - yaw);
        ctx.setLook(targetYaw, targetPitch);
        if (recentering) {
            deviationYaw = deviationPitch = 0f;
        } else {
            deviationYaw = offsetYaw;
            deviationPitch = offsetPitch;
        }
    }

    // ---------------------------------------------------------------- tick (client)

    /**
     * Pendant la capture : rend la main si le module Space Pilot a quitte la memoire active du joueur local (D11) ou si
     * l'acces de la fenetre ne l'autorise plus (T41 : la capture n'est plus liee a la fenetre), ou si l'etat ne le
     * designe toujours pas comme pilote apres {@value #CLAIM_TIMEOUT} ticks ; sinon envoi de l'entree et battement.
     */
    @Override
    public void tick()
    {
        if (!piloting) return;
        if (!SpaceControllerModule.localSpacePilot() || !accessAllowed()) {
            HoloCaptures.end(this);
            return;
        }
        if (!confirmed()) {
            if (++unconfirmed > CLAIM_TIMEOUT) {
                HoloCaptures.end(this);
                return;
            }
        } else {
            unconfirmed = 0;
        }
        SpaceControllerModule.releaseToggleKeys();
        pilotTick();
        if (++sinceSend >= HEARTBEAT_INTERVAL) sendInput(currentInput());
    }

    /** Vrai si l'acces de la fenetre ({@code HoloAccess}) autorise le joueur local (pendant la capture). */
    private boolean accessAllowed()
    {
        HoloWindow window = window();
        HoloModule module = window != null ? window.module() : null;
        return module == null || self == null || window.access().allows(self, module.owner());
    }

    /** Envoie une entree au serveur ({@code INPUT}) et remet a zero le compteur du battement. */
    protected final void sendInput(SpacePilotInput input)
    {
        link.input(input);
        sinceSend = 0;
    }

    /** Ticks ecoules depuis le dernier envoi d'entree (ou le debut de la capture). */
    protected final int ticksSinceSend() { return sinceSend; }

    // ---------------------------------------------------------------- pilotage (T07)

    /**
     * Controles du pilote : les quatre mappings de deplacement (gaz, roulis), le mapping Saut (frein), le mapping
     * Accroupi (saut interstellaire, T22), le mapping Sprint (boost, T32) et les clics bruts (pris sans effet : pas
     * d'attaque ni d'utilisation d'objet pendant le pilotage, la camera visant le monde).
     */
    protected boolean capturesPilotControl(HoloControl control)
    {
        return HoloDirections.isDirection(control) || control.equals(BRAKE) || control.equals(JUMP)
                || control.equals(BOOST) || control.equals(HoloControl.LEFT_CLICK)
                || control.equals(HoloControl.RIGHT_CLICK) || control.equals(HoloControl.MIDDLE_CLICK);
    }

    /**
     * Appui d'une touche de deplacement, du frein, du saut (cible cherchee a l'appui, {@link #findJumpTarget}) ou du
     * boost ; les clics sont ignores. Saut et boost : mappings a bascule remis au repos aussitot (l'appui vient d'etre
     * compte par vanilla, avant le prochain tick du joueur).
     */
    protected void onPilotPress(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(BRAKE)) {
            braking = true;
        } else if (control.equals(JUMP)) {
            SpaceControllerModule.releaseToggleKeys();
            jumpHeld = true;
            jumpTarget = findJumpTarget(ctx);
        } else if (control.equals(BOOST)) {
            SpaceControllerModule.releaseToggleKeys();
            boosting = true;
        } else if (HoloDirections.isDirection(control)) {
            directions.set(control, true);
        }
    }

    /** Relachement d'une touche de deplacement, du frein, du saut (le drapeau du saut tombe) ou du boost. */
    protected void onPilotRelease(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(BRAKE)) {
            braking = false;
        } else if (control.equals(JUMP)) {
            jumpHeld = false;
            jumpTarget = 0;
        } else if (control.equals(BOOST)) {
            boosting = false;
        } else if (HoloDirections.isDirection(control)) {
            directions.set(control, false);
        }
    }

    /**
     * Cible du saut au centre de l'ecran (C41) : etoile de fond la plus proche du regard du joueur a moins de
     * {@value SpaceJump#TARGET_CONE} degres, directions du ciel affiche a la derniere frame
     * ({@code pose(partialTick)}, {@link SpaceJump#pickTarget}) ; {@code 0} sans Environment synchronise, sans systeme
     * courant, pendant un voyage (une cible tenue jusqu'a l'arrivee relancerait un saut) ou si aucune etoile n'est
     * assez proche.
     */
    private int findJumpTarget(HoloCapture ctx)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced() || env.currentSystem() == null) return 0;
        if (env.jump().phase() == SpaceJump.Phase.TRAVEL) return 0;
        if (aimFrame == null) {
            aimPose = new SpacePose();
            aimFrame = new SpaceSkyFrame();
            aimWork = new Vector3f();
        }
        aimFrame.set(env.pose(SpaceControllerModule.partialTick(), aimPose));
        double yaw = Math.toRadians(ctx.yaw());
        double pitch = Math.toRadians(ctx.pitch());
        double cosPitch = Math.cos(pitch);
        float lookX = (float) (-Math.sin(yaw) * cosPitch);
        float lookY = (float) -Math.sin(pitch);
        float lookZ = (float) (Math.cos(yaw) * cosPitch);
        return SpaceJump.pickTarget(aimFrame, env.currentSystem(), env.backgroundStars(), lookX, lookY, lookZ,
                aimWork);
    }

    /**
     * Debut du pilotage : touches et ecart remis a zero, Sneak / Sprint a bascule remis au repos (un personnage
     * accroupi en mode bascule se releve), retour du regard sur l'axe avant au premier mouvement de souris
     * ({@code setLook} n'agit qu'une fois la capture de la souris prise par le framework).
     */
    protected void onPilotStart(HoloCapture ctx)
    {
        SpaceControllerModule.releaseToggleKeys();
        directions.clear();
        braking = false;
        boosting = false;
        jumpHeld = false;
        jumpTarget = 0;
        jumpLocked = false;
        sent = SpacePilotInput.ZERO;
        recenterPending = true;
        recentering = false;
        deviationYaw = deviationPitch = 0f;
        predict(SpacePilotInput.ZERO);
    }

    /**
     * Fin du pilotage : entree nulle (frein et drapeau du saut leves : une charge est annulee, un voyage continue, C40)
     * envoyee (avant {@code END}) et predite, etat remis a zero.
     */
    protected void onPilotEnd(HoloCapture ctx)
    {
        directions.clear();
        braking = false;
        boosting = false;
        jumpHeld = false;
        jumpTarget = 0;
        jumpLocked = false;
        recenterPending = false;
        recentering = false;
        deviationYaw = deviationPitch = 0f;
        send(SpacePilotInput.ZERO);
    }

    /** Envoie l'entree si elle a change depuis le dernier envoi, au plus tous les {@value #SEND_INTERVAL} ticks. */
    protected void pilotTick()
    {
        SpacePilotInput input = computeInput();
        if (!input.equals(sent) && ticksSinceSend() >= SEND_INTERVAL) send(input);
    }

    /** Derniere entree envoyee (le battement la repete). */
    protected SpacePilotInput currentInput() { return sent; }

    /**
     * Entree quantifiee courante : gaz (Avancer +1, Reculer -1), roulis (Droite +1, Gauche -1), frein (Saut) et boost
     * (Sprint, T32) depuis les touches, taux depuis l'ecart du regard ({@code docs/space/04-mathematiques.md} par. 6) :
     * {@code k = clamp((m - D) / (A - D), 0, 1) / m}, {@code yawRate = dYaw k}, {@code pitchRate = -dPitch k} (regarder
     * plus haut = pitch Minecraft negatif = nez qui monte) ; visee = ecart lui-meme ({@code aimYaw = dYaw},
     * {@code aimPitch = -dPitch}, positif au-dessus), avec la tolerance d'un pas ({@link #aim}) ; drapeau et cible du
     * saut tant que la touche est tenue avec une cible. Pendant un saut ({@link #jumpLocked()}), taux nuls ; gaz,
     * roulis, frein et boost restent transmis tels quels (le pas les ignore pendant la charge, C40).
     */
    private SpacePilotInput computeInput()
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        // Arrivee alors que la touche est encore tenue : la cible est devenue le systeme courant, le drapeau tombe.
        SpaceSystem current = env != null ? env.currentSystem() : null;
        if (jumpTarget > 0 && current != null && current.id() == jumpTarget) jumpTarget = 0;
        jumpLocked = jumpTarget > 0 || env != null && env.jump().active();
        int throttle = -(int) Math.signum(directions.dy());
        int roll = (int) Math.signum(directions.dx());
        double magnitude = Math.sqrt(deviationYaw * deviationYaw + deviationPitch * deviationPitch);
        double k = !jumpLocked && magnitude > 0.0 ? deflection(magnitude) / magnitude : 0.0;
        byte aimYaw = aim(deviationYaw, sent.aimYaw(), sent.aimYawDegrees());
        byte aimPitch = aim(-deviationPitch, sent.aimPitch(), sent.aimPitchDegrees());
        return SpacePilotInput.of(throttle, roll, deviationYaw * k, -deviationPitch * k, braking, boosting, aimYaw,
                aimPitch, jumpTarget);
    }

    /**
     * Octet de visee pour l'ecart {@code degrees} : celui deja envoye ({@code sentByte}, valeur {@code sentDegrees})
     * tant que l'ecart en reste a moins d'un pas de quantification, sinon l'ecart quantifie.
     */
    private static byte aim(double degrees, byte sentByte, double sentDegrees)
    {
        if (Math.abs(degrees - sentDegrees) < SpacePilotInput.AIM_STEP) return sentByte;
        return SpacePilotInput.quantizeAim(degrees);
    }

    private void send(SpacePilotInput input)
    {
        sent = input;
        sendInput(input);
        predict(input);
    }

    private static void predict(SpacePilotInput input)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env != null) env.predictPilotInput(input);
    }

    private static float deflection(double magnitude)
    {
        return (float) Mth.clamp((magnitude - DEAD_ZONE) / (CONE_ANGLE - DEAD_ZONE), 0.0, 1.0);
    }

    // ---------------------------------------------------------------- lecture (T08, client)

    /** Vrai pendant le retour du regard sur l'axe avant, a la prise du pilotage. */
    public final boolean recentering() { return piloting && (recentering || recenterPending); }

    /** Ecart en lacet du regard a l'axe avant, degres (positif : a droite) ; nul hors pilotage et pendant le retour. */
    public final float deviationYaw() { return deviationYaw; }

    /** Ecart en tangage du regard a l'axe avant, degres (positif : en dessous, convention Minecraft). */
    public final float deviationPitch() { return deviationPitch; }

    /**
     * Deflexion du joystick, {@code [0, 1]} : 0 dans la zone morte, 1 au bord du cone ; 0 pendant un saut (aucune
     * rotation commandee).
     */
    public final float deflection()
    {
        if (jumpLocked) return 0f;
        return deflection(Math.sqrt(deviationYaw * deviationYaw + deviationPitch * deviationPitch));
    }

    /** Vrai si l'ecart depasse la zone morte (une rotation est commandee). */
    public final boolean beyondDeadZone() { return deflection() > 0f; }

    // ---------------------------------------------------------------- lecture du saut (T22, client)

    /** Vrai tant que la touche du saut (Accroupi) est tenue pendant la capture. */
    public final boolean jumpHeld() { return jumpHeld; }

    /**
     * Cible du saut choisie a l'appui et tenue avec la touche ({@code 0} : touche relachee, ou aucune etoile au
     * reticule) : reperee par Space Pilot jusqu'a ce que la charge soit affichee.
     */
    public final int jumpTarget() { return jumpTarget; }

    /** Vrai pendant un saut (touche tenue avec une cible, ou charge / voyage affiches) : taux du joystick nuls. */
    public final boolean jumpLocked() { return jumpLocked; }

    // ---------------------------------------------------------------- dessin

    @Override
    protected void draw(HoloCanvas canvas)
    {
        boolean allowed = allowed();
        int alpha = !available ? 0x20 : piloting ? 0x80 : hovered && allowed ? 0x58 : 0x38;
        int color = available && (piloting || pilot == null && allowed) ? ACCENT : 0x808080;
        canvas.fill(0, 0, width(), height(), alpha << 24 | color);
        canvas.outline(0, 0, width(), height(), 1f, 0xFF000000 | color);
        if (piloting) {
            centered(canvas, PILOTING_TEXT, height() / 2f - canvas.lineHeight(), 0xFFFFFFFF);
            centered(canvas, EXIT_TEXT, height() / 2f + 1f, 0xFFC0E8FF);
        } else {
            Component text = !available ? UNAVAILABLE_TEXT : !allowed ? RESERVED_TEXT : pilot == null ? IDLE_TEXT
                    : pilotHere ? BUSY_TEXT : ELSEWHERE_TEXT;
            int textColor = available && allowed ? 0xFFFFFFFF : 0xFFA0A0A0;
            centered(canvas, text, (height() - canvas.lineHeight()) / 2f + 1f, textColor);
        }
    }

    private void centered(HoloCanvas canvas, Component text, float y, int argb)
    {
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, y, argb);
    }
}
