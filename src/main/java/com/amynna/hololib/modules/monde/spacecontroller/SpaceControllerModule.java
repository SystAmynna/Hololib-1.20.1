package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpacePilotInput;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/**
 * Module {@code hololib:space_controller} (memoire de position, marker) : fenetre de controle
 * ({@link SpaceControllerWindow}) qui affiche l'etat du Space Environment de la dimension du marker et lui relaie le
 * pilotage. Ne stocke ni la pose ni la galaxie : seulement un etat d'affichage, recopie a chaque tick serveur, et un
 * seul reglage sauvegarde, l'acces au pilotage (placement, acces et visibilite des objets le sont par le framework).
 * <p>
 * <b>Acces au pilotage</b> (interrupteur « Pilotage : tous / ops » de la fenetre, {@code docs/space/decisions.md} D5) :
 * ouvert a tous par defaut ; en mode « ops », seuls les operateurs (niveau {@value #OP_LEVEL}) peuvent prendre le
 * pilotage. {@code HoloAccess} ne sait pas exprimer « operateurs » (seulement une liste fixe) : l'acces de la fenetre
 * reste {@code EVERYONE} et le serveur refuse {@code BEGIN} aux non-ops, et rend le pilotage d'un pilote non-op de ce
 * poste. Seul un operateur peut changer ce reglage (verifie par le serveur).
 * <p>
 * <b>Space Pilot requis</b> (D11, T19) : {@code BEGIN} est refuse (message « Module Space Pilot requis ») si la
 * memoire active du joueur n'a pas de module {@code hololib:space_pilot} ; le client le verifie aussi avant de
 * capturer ({@link Backend#localSpacePilot()}). Les cercles du joystick sont des objets de Space Pilot
 * ({@code modules.camera.spacepilot}), plus de ce module : une ancienne sauvegarde de marker qui les contient encore
 * est relue sans erreur (le framework ignore les objets absents).
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}, envoye au plus tous les {@value #SYNC_INTERVAL} ticks et seulement si
 * une valeur affichee change ; vitesse et altitude arrondies a trois chiffres significatifs) :
 * <pre>
 * boolean opsOnly, boolean environment
 * si environment : boolean pilote [+ UUID, utf nom, boolean ici], double vitesse signee (km/tick, relative au
 *                  referentiel), utf corps de reference ("" sans corps), double altitude au-dessus de lui (km)
 * </pre>
 * <b>Actions</b> ({@code sendAction(window, ...)}, source = la fenetre, acces et portee verifies par le framework),
 * premier octet = type :
 * <pre>
 * 0 BEGIN                 -> claimPilot(joueur, marker) ; refus (reserve aux ops, Space Pilot absent, poste
 *                            occupe) = message dans la barre d'action
 * 1 INPUT + 7 a 12 octets -> pilotInput(joueur, SpacePilotInput.read) si le pilote passe par ce poste (7 octets,
 *                            + varint de la cible si le drapeau du saut est leve, T21)
 * 2 END                   -> releasePilot(joueur) si le pilote passe par ce poste
 * 3 OPS_ONLY + boolean    -> acces au pilotage reserve aux ops (vrai) ou ouvert a tous (faux), operateurs seulement
 * </pre>
 * Sans Environment dans la dimension, les actions de pilotage sont ignorees. Si le module est retire alors que le
 * pilote passe par lui, le pilotage est rendu.
 * <p>
 * <b>Saut</b> (T22) : l'etat du saut n'est pas dans l'etat synchronise de ce module ; chaque client le lit dans le
 * miroir de l'Environment de sa dimension ({@link #clientTick}, au temps d'affichage) et la fenetre remplace la ligne
 * d'etat par « Saut vers nom » pendant la charge et « Distorsion : n s » (temps restant) pendant le voyage, puis
 * revient a l'etat normal. Texte reconstruit seulement quand la phase, la cible ou la seconde affichee change.
 * <p>
 * Le client passe par un {@link Backend} installe au setup client ({@code client.modules.monde.spacecontroller}).
 */
public final class SpaceControllerModule extends HoloModule
{
    /** Type d'action : demande du pilotage. */
    public static final int ACTION_BEGIN = 0;
    /**
     * Type d'action : entree du pilote ({@link SpacePilotInput}, 7 octets : gaz, roulis, lacet, tangage, drapeaux
     * (frein, saut), visee en lacet et en tangage ; + id de la cible du saut en varint si son drapeau est leve).
     */
    public static final int ACTION_INPUT = 1;
    /** Type d'action : fin du pilotage. */
    public static final int ACTION_END = 2;
    /** Type d'action : acces au pilotage (booleen : reserve aux ops), operateurs seulement. */
    public static final int ACTION_OPS_ONLY = 3;
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;
    /** Intervalle minimal entre deux envois de l'etat, en ticks (4 par seconde au plus). */
    public static final int SYNC_INTERVAL = 5;
    /** Longueur maximale des noms synchronises (joueur, corps). */
    private static final int NAME_LENGTH = 64;
    private static final Component OPS_ONLY_REFUSED = Component.literal("Pilotage reserve aux operateurs");
    /** Refus du pilotage sans module Space Pilot dans la memoire active du joueur (D11). */
    public static final Component SPACE_PILOT_REQUIRED = Component.literal("Module Space Pilot requis");
    private static final Component SETTING_REFUSED =
            Component.literal("Seul un operateur peut changer l'acces au pilotage");

    private static Backend backend;

    // ---------------------------------------------------------------- reglage sauvegarde et synchronise

    /** Pilotage reserve aux operateurs (faux par defaut : ouvert a tous). */
    private boolean opsOnly;

    // ---------------------------------------------------------------- etat synchronise

    private boolean environment;
    private UUID pilot;
    private String pilotName = "";
    private boolean pilotHere;
    /** Vitesse nominale signee arrondie, km par tick, relative au referentiel ({@code < 0} : marche arriere). */
    private double speed;
    /** Nom du corps de reference de l'Environment ({@code ""} sans corps). */
    private String bodyName = "";
    /** Altitude arrondie au-dessus du corps de reference, km. */
    private double altitude = Double.POSITIVE_INFINITY;

    // ---------------------------------------------------------------- serveur

    private long lastSync = Long.MIN_VALUE / 2;
    private boolean syncPending;
    /** Environment vu au dernier tick (pour rendre le pilotage si le module est retire). */
    private SpaceEnvironmentModule lastEnvironment;

    private SpaceControllerWindow window;

    // ---------------------------------------------------------------- client : saut affiche (T22)

    /** Phase, cible et seconde restante du saut affiche par la fenetre (texte reconstruit seulement s'ils changent). */
    private SpaceJump.Phase shownJumpPhase = SpaceJump.Phase.NONE;
    private int shownJumpTarget;
    private int shownJumpSeconds;

    private final SpacePilotPad.Link link = new SpacePilotPad.Link()
    {
        @Override
        public void begin() { sendTyped(ACTION_BEGIN, null); }

        @Override
        public void input(SpacePilotInput input) { sendTyped(ACTION_INPUT, Objects.requireNonNull(input)); }

        @Override
        public void end() { sendTyped(ACTION_END, null); }
    };

    // ---------------------------------------------------------------- client

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si la memoire active du joueur local a un module Space Pilot (D11, verifie avant la capture). */
        boolean localSpacePilot();

        /** Vrai si le joueur local est operateur (niveau {@value SpaceControllerModule#OP_LEVEL}). */
        boolean localOp();

        /**
         * Fraction de tick de la derniere frame dessinee (T22) : le pad cherche la cible du saut avec la pose du ciel
         * affichee ({@code pose(partialTick)}).
         */
        float partialTick();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend) { SpaceControllerModule.backend = Objects.requireNonNull(backend); }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    static boolean localOp() { return backend != null && backend.localOp(); }

    /** Vrai si le joueur local a un module Space Pilot ; faux sans backend (serveur dedie). */
    static boolean localSpacePilot() { return backend != null && backend.localSpacePilot(); }

    /** Fraction de tick de la derniere frame dessinee ; {@code 0} sans backend (serveur dedie). */
    static float partialTick() { return backend != null ? backend.partialTick() : 0f; }

    // ---------------------------------------------------------------- cycle de vie

    /**
     * Cree la fenetre (deux cotes, meme cle) et affiche l'etat courant. Les objets {@code joystick_forward} /
     * {@code joystick_aim} d'une sauvegarde d'avant T19 sont ignores (migres dans Space Pilot).
     */
    @Override
    public void onLoad()
    {
        window = addObject(new SpaceControllerWindow("window", link, this::requestOpsOnly));
        refresh();
    }

    @Override
    public void save(CompoundTag tag) { tag.putBoolean("opsOnly", opsOnly); }

    @Override
    public void load(CompoundTag tag) { opsOnly = tag.getBoolean("opsOnly"); }

    /** Serveur : rend le pilotage s'il passe par ce poste (module retire, marker decharge). */
    @Override
    public void onUnload()
    {
        if (host().clientSide() || lastEnvironment == null) return;
        String marker = markerId();
        UUID current = lastEnvironment.pilot();
        if (marker != null && current != null && marker.equals(lastEnvironment.pilotMarker())) {
            lastEnvironment.releasePilot(current);
        }
        lastEnvironment = null;
    }

    /** Id du marker qui porte la memoire, ou {@code null} (module installe ailleurs). */
    private String markerId() { return scope() instanceof HoloScope.Position position ? position.markerId() : null; }

    // ---------------------------------------------------------------- serveur

    /** Recopie l'etat de l'Environment ; envoi si une valeur affichee a change, au plus tous les 5 ticks. */
    @Override
    public void serverTick(ServerLevel level)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(level);
        lastEnvironment = env;
        if (opsOnly && env != null) releaseNonOpPilot(env, level);
        if (update(env, level)) syncPending = true;
        long now = level.getGameTime();
        if (syncPending && (now - lastSync >= SYNC_INTERVAL || now < lastSync)) {
            markDirty();
            lastSync = now;
            syncPending = false;
        }
    }

    /** Copie l'etat affiche depuis {@code env} ; vrai si une valeur a change. */
    private boolean update(SpaceEnvironmentModule env, ServerLevel level)
    {
        boolean newEnvironment = env != null;
        UUID newPilot = null;
        String newPilotName = "";
        boolean newHere = false;
        double newSpeed = 0.0;
        String newBody = "";
        double newAltitude = Double.POSITIVE_INFINITY;
        if (env != null) {
            newPilot = env.pilot();
            if (newPilot != null) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(newPilot);
                newPilotName = player != null ? player.getGameProfile().getName() : "?";
                newHere = Objects.equals(markerId(), env.pilotMarker());
            }
            newSpeed = SpaceFormat.round3(env.speed());
            SpaceBody body = env.referenceBody();
            if (body != null) {
                newBody = body.name();
                newAltitude = SpaceFormat.round3(env.referenceAltitude());
            }
        }
        boolean changed = newEnvironment != environment || !Objects.equals(newPilot, pilot)
                || !newPilotName.equals(pilotName) || newHere != pilotHere
                || Double.compare(newSpeed, speed) != 0 || !newBody.equals(bodyName)
                || Double.compare(newAltitude, altitude) != 0;
        environment = newEnvironment;
        pilot = newPilot;
        pilotName = newPilotName;
        pilotHere = newHere;
        speed = newSpeed;
        bodyName = newBody;
        altitude = newAltitude;
        return changed;
    }

    /** Mode « ops » : rend le pilotage d'un pilote de ce poste qui n'est pas (ou plus) operateur. */
    private void releaseNonOpPilot(SpaceEnvironmentModule env, ServerLevel level)
    {
        UUID current = env.pilot();
        String marker = markerId();
        if (current == null || marker == null || !marker.equals(env.pilotMarker())) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(current);
        if (player == null || player.hasPermissions(OP_LEVEL)) return;
        env.releasePilot(current);
        player.displayClientMessage(OPS_ONLY_REFUSED, true);
    }

    /** Source verifiee par le framework (acces de la fenetre, visibilite, portee du marker) ; contenu a valider. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (source == null || source != window) return;
        int action = buf.readByte();
        if (action == ACTION_OPS_ONLY) {
            setOpsOnly(player, buf.readBoolean());
            return;
        }
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(player.serverLevel());
        String marker = markerId();
        if (env == null || marker == null) return;
        switch (action) {
            case ACTION_BEGIN -> {
                if (opsOnly && !player.hasPermissions(OP_LEVEL)) {
                    player.displayClientMessage(OPS_ONLY_REFUSED, true);
                    return;
                }
                if (SpacePilotModule.of(player) == null) {
                    player.displayClientMessage(SPACE_PILOT_REQUIRED, true);
                    return;
                }
                if (!env.claimPilot(player, marker)) refuse(player, env);
            }
            case ACTION_INPUT -> {
                SpacePilotInput input = SpacePilotInput.read(buf);
                if (marker.equals(env.pilotMarker())) env.pilotInput(player, input);
            }
            case ACTION_END -> {
                if (player.getUUID().equals(env.pilot()) && marker.equals(env.pilotMarker())) {
                    env.releasePilot(player.getUUID());
                }
            }
            default -> {
                return;
            }
        }
        // Etat (pilote) renvoye des que l'intervalle le permet.
        if (update(env, player.serverLevel())) syncPending = true;
    }

    /**
     * Reglage de l'acces au pilotage, par un operateur seulement. Le pilote non-op eventuel est libere au tick
     * suivant ({@link #serverTick}).
     */
    private void setOpsOnly(ServerPlayer player, boolean value)
    {
        if (!player.hasPermissions(OP_LEVEL)) {
            player.displayClientMessage(SETTING_REFUSED, true);
            return;
        }
        if (value == opsOnly) return;
        opsOnly = value;
        syncPending = true;
    }

    private static void refuse(ServerPlayer player, SpaceEnvironmentModule env)
    {
        UUID current = env.pilot();
        ServerPlayer other = current != null ? player.server.getPlayerList().getPlayer(current) : null;
        Component message = other != null
                ? Component.literal("Le monde est deja pilote par " + other.getGameProfile().getName())
                : Component.literal("Pilotage impossible depuis ce poste");
        player.displayClientMessage(message, true);
    }

    // ---------------------------------------------------------------- client

    /**
     * Client : etat du saut lu dans le miroir de l'Environment de la dimension ({@code jumpAt(0)}, temps d'affichage
     * du ciel, prediction du pilote local comprise) ; la fenetre n'est mise a jour que si la phase, la cible ou la
     * seconde restante affichee change. Sans Environment synchronise : pas de saut affiche.
     */
    @Override
    public void clientTick()
    {
        if (window == null) return;
        SpaceEnvironmentModule env = environment ? SpaceEnvironmentModule.client() : null;
        SpaceJump.Phase phase = SpaceJump.Phase.NONE;
        int target = 0;
        int seconds = 0;
        if (env != null && env.synced()) {
            SpaceJump jump = env.jumpAt(0f);
            phase = jump.phase();
            target = jump.target();
            if (phase == SpaceJump.Phase.TRAVEL) seconds = SpaceFormat.wholeSeconds(env.jumpRemainingSeconds(0f));
        }
        if (phase == shownJumpPhase && target == shownJumpTarget && seconds == shownJumpSeconds) return;
        shownJumpPhase = phase;
        shownJumpTarget = target;
        shownJumpSeconds = seconds;
        switch (phase) {
            case CHARGE -> window.showJump(Component.literal("Saut vers " + targetName(env, target)), false);
            case TRAVEL -> window.showJump(Component.literal("Distorsion : " + SpaceFormat.seconds(seconds)), true);
            default -> window.showJump(null, false);
        }
    }

    /** Nom de l'etoile de fond du systeme cible, ou {@code "?"} (inconnue du client). */
    private static String targetName(SpaceEnvironmentModule env, int target)
    {
        BackgroundStar star = env.backgroundStar(target);
        return star != null ? star.name() : "?";
    }

    /** Demande du client (interrupteur de la fenetre) : acces au pilotage reserve aux ops ou ouvert a tous. */
    private void requestOpsOnly(boolean value)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(ACTION_OPS_ONLY);
            buf.writeBoolean(value);
        });
    }

    /** Action typee au nom de la fenetre (client). */
    private void sendTyped(int action, SpacePilotInput input)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            if (input != null) input.write(buf);
        });
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeBoolean(opsOnly);
        buf.writeBoolean(environment);
        if (!environment) return;
        buf.writeBoolean(pilot != null);
        if (pilot != null) {
            buf.writeUUID(pilot);
            buf.writeUtf(pilotName, NAME_LENGTH);
            buf.writeBoolean(pilotHere);
        }
        buf.writeDouble(speed);
        buf.writeUtf(bodyName, NAME_LENGTH);
        buf.writeDouble(altitude);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        opsOnly = buf.readBoolean();
        environment = buf.readBoolean();
        pilot = null;
        pilotName = "";
        pilotHere = false;
        speed = 0.0;
        bodyName = "";
        altitude = Double.POSITIVE_INFINITY;
        if (!environment) return;
        if (buf.readBoolean()) {
            pilot = buf.readUUID();
            pilotName = buf.readUtf(NAME_LENGTH);
            pilotHere = buf.readBoolean();
        }
        speed = buf.readDouble();
        bodyName = buf.readUtf(NAME_LENGTH);
        altitude = buf.readDouble();
    }

    @Override
    public void onSync() { refresh(); }

    private void refresh()
    {
        if (window != null) {
            window.showState(environment, pilot, pilotName, pilotHere, speed, bodyName, altitude, opsOnly);
        }
    }

    // ---------------------------------------------------------------- lecture (T07, T08)

    /** Fenetre de controle (deux cotes). */
    public SpaceControllerWindow window() { return window; }

    /** Vrai si la dimension a un Space Environment (etat synchronise). */
    public boolean environmentPresent() { return environment; }

    /** Pilote courant d'apres l'etat synchronise, ou {@code null}. */
    public UUID pilot() { return pilot; }

    /** Vrai si le pilote courant passe par ce poste (etat synchronise). */
    public boolean pilotHere() { return pilotHere; }

    /** Vrai si le pilotage est reserve aux operateurs (etat synchronise). */
    public boolean opsOnly() { return opsOnly; }
}
