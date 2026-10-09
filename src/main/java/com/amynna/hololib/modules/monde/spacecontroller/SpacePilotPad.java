package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloDirections;
import com.amynna.hololib.holo.widget.control.HoloPointerInput;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpacePilotInput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.UUID;

/**
 * Zone « Cliquer pour piloter » de la fenetre du Space Controller ({@link HoloPointerInput} : survol, capture au clic
 * gauche ou droit, liee a la fenetre ; Echap ou Entree la terminent). Au debut de la capture, demande le pilotage
 * ({@link Link#begin()}) ; a la fin, envoie une entree nulle puis rend le pilotage ({@link Link#end()}).
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
 *     <li>joystick au regard : la camera est bornee a un cone de {@value #CONE_ANGLE} degres autour de l'axe avant du
 *     monde (reglage de l'Environment, fixe dans le monde) ; l'ecart au centre, au-dela d'une zone morte de
 *     {@value #DEAD_ZONE} degres, donne les taux de lacet et de tangage. A la prise, le regard est ramene sur l'axe
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
 * <p>
 * Lecture pour la mini-interface du joystick (T08, client) : {@link #piloting()}, {@link #recentering()},
 * {@link #deviationYaw()}, {@link #deviationPitch()}, {@link #deflection()}, {@link #beyondDeadZone()}. Ne pas
 * redefinir {@code intercepts} (regle des traits).
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
    /** Zone morte du joystick {@code D}, en degres : en dessous, aucun taux de rotation. */
    public static final float DEAD_ZONE = 2f;
    /** Duree du retour du regard sur l'axe avant a la prise du pilotage, en secondes. */
    public static final float RECENTER_SECONDS = 0.4f;
    /** Ecart (degres) sous lequel le retour sur l'axe avant est inutile. */
    private static final float RECENTER_EPSILON = 0.05f;
    /** Controle du frein : le mapping Saut (Espace par defaut, la touche choisie par le joueur). */
    public static final HoloControl BRAKE = HoloControl.mapping("key.jump");

    private static final int ACCENT = 0x40C0FF;
    private static final Component IDLE_TEXT = Component.literal("Cliquer pour piloter");
    private static final Component PILOTING_TEXT = Component.literal("Pilotage en cours");
    private static final Component EXIT_TEXT = Component.literal("Echap : rendre la main");
    private static final Component BUSY_TEXT = Component.literal("Poste occupe");
    private static final Component ELSEWHERE_TEXT = Component.literal("Pilote depuis un autre poste");
    private static final Component UNAVAILABLE_TEXT = Component.literal("Indisponible");
    private static final Component RESERVED_TEXT = Component.literal("Reserve aux operateurs");

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

    /** Pas de capture si un autre joueur pilote deja, ni pour un non-op en mode « ops ». */
    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        if (!available || pilot != null && !pilot.equals(ctx.player().getUUID())) return;
        if (opsOnly && !ctx.player().hasPermissions(SpaceControllerModule.OP_LEVEL)) return;
        HoloPointerInput.super.onClick(ctx, button);
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

    @Override
    public void tick()
    {
        if (!piloting) return;
        if (!confirmed()) {
            if (++unconfirmed > CLAIM_TIMEOUT) {
                HoloCaptures.end(this);
                return;
            }
        } else {
            unconfirmed = 0;
        }
        pilotTick();
        if (++sinceSend >= HEARTBEAT_INTERVAL) sendInput(currentInput());
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
     * Controles du pilote : les quatre mappings de deplacement (gaz, roulis), le mapping Saut (frein) et les clics
     * bruts (pris sans effet : pas d'attaque ni d'utilisation d'objet pendant le pilotage, la camera visant le monde).
     */
    protected boolean capturesPilotControl(HoloControl control)
    {
        return HoloDirections.isDirection(control) || control.equals(BRAKE) || control.equals(HoloControl.LEFT_CLICK)
                || control.equals(HoloControl.RIGHT_CLICK) || control.equals(HoloControl.MIDDLE_CLICK);
    }

    /** Appui d'une touche de deplacement ou du frein (les clics sont ignores). */
    protected void onPilotPress(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(BRAKE)) braking = true;
        else directions.set(control, true);
    }

    /** Relachement d'une touche de deplacement ou du frein. */
    protected void onPilotRelease(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(BRAKE)) braking = false;
        else directions.set(control, false);
    }

    /**
     * Debut du pilotage : touches et ecart remis a zero, retour du regard sur l'axe avant au premier mouvement de
     * souris ({@code setLook} n'agit qu'une fois la capture de la souris prise par le framework).
     */
    protected void onPilotStart(HoloCapture ctx)
    {
        directions.clear();
        braking = false;
        sent = SpacePilotInput.ZERO;
        recenterPending = true;
        recentering = false;
        deviationYaw = deviationPitch = 0f;
        predict(SpacePilotInput.ZERO);
    }

    /** Fin du pilotage : entree nulle (frein leve) envoyee (avant {@code END}) et predite, etat remis a zero. */
    protected void onPilotEnd(HoloCapture ctx)
    {
        directions.clear();
        braking = false;
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
     * Entree quantifiee courante : gaz (Avancer +1, Reculer -1), roulis (Droite +1, Gauche -1) et frein (Saut)
     * depuis les touches, taux depuis l'ecart du regard ({@code docs/space/04-mathematiques.md} par. 6) :
     * {@code k = clamp((m - D) / (A - D), 0, 1) / m}, {@code yawRate = dYaw k}, {@code pitchRate = -dPitch k} (regarder
     * plus haut = pitch Minecraft negatif = nez qui monte) ; visee = ecart lui-meme ({@code aimYaw = dYaw},
     * {@code aimPitch = -dPitch}, positif au-dessus), avec la tolerance d'un pas ({@link #aim}).
     */
    private SpacePilotInput computeInput()
    {
        int throttle = -(int) Math.signum(directions.dy());
        int roll = (int) Math.signum(directions.dx());
        double magnitude = Math.sqrt(deviationYaw * deviationYaw + deviationPitch * deviationPitch);
        double k = magnitude > 0.0 ? deflection(magnitude) / magnitude : 0.0;
        byte aimYaw = aim(deviationYaw, sent.aimYaw(), sent.aimYawDegrees());
        byte aimPitch = aim(-deviationPitch, sent.aimPitch(), sent.aimPitchDegrees());
        return SpacePilotInput.of(throttle, roll, deviationYaw * k, -deviationPitch * k, braking, aimYaw, aimPitch);
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

    /** Deflexion du joystick, {@code [0, 1]} : 0 dans la zone morte, 1 au bord du cone. */
    public final float deflection()
    {
        return deflection(Math.sqrt(deviationYaw * deviationYaw + deviationPitch * deviationPitch));
    }

    /** Vrai si l'ecart depasse la zone morte (une rotation est commandee). */
    public final boolean beyondDeadZone() { return deflection() > 0f; }

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
