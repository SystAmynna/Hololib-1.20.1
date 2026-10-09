package com.amynna.hololib.client.capture;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Controleur client de la capture de controles : <b>seul abonne du mod aux inputs</b>. Fil client uniquement.
 * <p>
 * Captors consultes pour chaque controle : la pile des captures explicites (la plus recente d'abord), puis les captors
 * passifs permanents ({@link #addPassive}, ex. l'interaction au regard). Le premier dont {@code captures} est vrai
 * prend le controle, le jeu ne le voit pas ; le relachement va au captor qui a pris l'appui. Rien n'est distribue
 * quand un ecran est ouvert ou que la souris n'est pas capturee par le jeu (les appuis en cours sont alors relaches),
 * mais les captures explicites restent actives.
 * <p>
 * Comment chaque controle est retenu (Forge 47.4.10, verifie dans les sources) :
 * <ul>
 *     <li>boutons bruts : {@code InputEvent.MouseButton.Pre}, annulable, avant la mise a jour des {@code KeyMapping} ;
 *     </li>
 *     <li>mappings : {@code MouseButton.Post} et {@code InputEvent.Key} ne sont pas annulables mais sont postes
 *     juste apres {@code KeyMapping.set/click} : le mapping est neutralise aussitot ({@code setDown(false)}, clics
 *     consommes), et encore au debut de chaque tick (touche enfoncee avant la capture) ;</li>
 *     <li>Echap / Entree : {@code InputEvent.Key} ; les mappings lies a la touche sont neutralises ; Echap capture
 *     annule l'ouverture du menu pause ({@code ScreenEvent.Opening}, poste par {@code pauseGame} <b>avant</b>
 *     {@code InputEvent.Key}) ;</li>
 *     <li>molette : {@code MouseScrollingEvent} ; attaque / utilisation / pick maintenus :
 *     {@code InteractionKeyMappingTriggered} ;</li>
 *     <li>mouvements de souris (sans Mixin) : vanilla tourne le joueur, l'ecart est lu et reverse au debut de chaque
 *     frame et de chaque tick ({@code MouseHandler.turnPlayer} precede {@code onRenderTickStart} dans
 *     {@code Minecraft.runTick}), puis livre une fois par frame.</li>
 * </ul>
 * A la fin d'une capture explicite, {@code KeyMapping.setAll()} resynchronise les touches encore enfoncees.
 * <p>
 * <b>Mode clavier</b> : tant qu'un captor prend {@code HoloControl.KEYBOARD}, l'ecran invisible
 * {@link HoloKeyboardScreen} est ouvert (au debut de la frame ou du tick suivant) et ferme des qu'aucun captor ne le
 * prend plus. Les touches arrivent alors par l'ecran ({@code InputEvent.Key} est ignore) et vont au captor le plus
 * recent qui prend le clavier ou un controle lie a la touche ; la souris reste capturee (camera, clics, molette).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class HoloControlCapture
{
    /** Duree de frame maximale transmise, en secondes (apres un gel). */
    private static final float MAX_FRAME_SECONDS = 0.25f;

    /** Captures explicites, de la plus ancienne a la plus recente. */
    private static final List<HoloCaptor> EXPLICIT = new ArrayList<>();
    private static final List<HoloCaptor> PASSIVE = new ArrayList<>();
    /** Controle appuye -> captor qui a pris l'appui. */
    private static final Map<HoloControl, HoloCaptor> PRESSED = new HashMap<>();
    /** Mode clavier : touche brute appuyee -> captor du clavier qui l'a recue. */
    private static final Map<Integer, HoloCaptor> KEYS_DOWN = new HashMap<>();
    /** Mode clavier : captor qui recoit les caracteres de la derniere touche ({@code null} = caracteres ignores). */
    private static HoloCaptor charTarget;
    /** Moitie haute d'une paire de substitution UTF-16 en attente. */
    private static char highSurrogate;
    private static final Context CONTEXT = new Context();

    // Controles des options : cache reconstruit quand la liste change (mods qui en ajoutent).
    private static KeyMapping[] mappingsSource;
    private static final Map<KeyMapping, HoloControl> MAPPING_CONTROLS = new IdentityHashMap<>();

    // Capture de la souris.
    private static HoloCaptor mouseCaptor;
    private static float lockYaw;
    private static float lockPitch;
    private static float pendingYaw;
    private static float pendingPitch;

    private static long lastFrameNanos;
    private static float frameSeconds;

    private HoloControlCapture() {}

    // ---------------------------------------------------------------- API

    /** Capture explicite, au-dessus des autres ; faux si le captor capture deja. */
    public static boolean begin(HoloCaptor captor)
    {
        if (EXPLICIT.contains(captor)) return false;
        EXPLICIT.add(captor);
        captor.onCaptureStart(context(captor));
        return true;
    }

    /** Termine la capture explicite du captor ; ses appuis en cours iront au jeu. */
    public static void end(HoloCaptor captor)
    {
        if (!EXPLICIT.remove(captor)) return;
        PRESSED.values().removeIf(pressed -> pressed == captor);
        KEYS_DOWN.values().removeIf(down -> down == captor);
        if (charTarget == captor) charTarget = null;
        if (mouseCaptor == captor) mouseCaptor = null;
        captor.onCaptureEnd(context(captor));
        closeKeyboardIfUnused(Minecraft.getInstance());
        KeyMapping.setAll();
    }

    public static boolean isCapturing(HoloCaptor captor) { return EXPLICIT.contains(captor); }

    /** Captor permanent, consulte apres les captures explicites (pas de debut ni de fin). */
    public static void addPassive(HoloCaptor captor)
    {
        if (!PASSIVE.contains(captor)) PASSIVE.add(captor);
    }

    public static void removePassive(HoloCaptor captor) { PASSIVE.remove(captor); }

    /** Captor qui prend {@code control}, ou {@code null} (le controle va au jeu). */
    public static HoloCaptor captorFor(HoloControl control)
    {
        for (int i = EXPLICIT.size() - 1; i >= 0; i--) {
            HoloCaptor captor = EXPLICIT.get(i);
            if (captor.captures(control)) return captor;
        }
        for (HoloCaptor captor : PASSIVE) {
            if (captor.captures(control)) return captor;
        }
        return null;
    }

    /** Duree de la frame precedente, en secondes. */
    public static float frameSeconds() { return frameSeconds; }

    /**
     * Vrai si les controles du joueur vont au jeu ou aux captors : joueur present, souris capturee, et aucun ecran sauf
     * l'ecran clavier du framework (qui ne masque que le clavier).
     */
    public static boolean gameInputActive(Minecraft mc)
    {
        return mc.player != null && mc.level != null && mc.getOverlay() == null
                && (mc.screen == null || mc.screen instanceof HoloKeyboardScreen) && mc.mouseHandler.isMouseGrabbed();
    }

    /** Mise en place au setup client : branche {@link HoloCaptures} sur ce controleur. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> HoloCaptures.install(new HoloCaptures.Backend()
            {
                @Override
                public boolean begin(HoloCaptor captor) { return HoloControlCapture.begin(captor); }

                @Override
                public void end(HoloCaptor captor) { HoloControlCapture.end(captor); }

                @Override
                public boolean isCapturing(HoloCaptor captor) { return HoloControlCapture.isCapturing(captor); }
            }));
        }
    }

    // ---------------------------------------------------------------- frame et tick

    /** Avant le picking de l'interaction : le regard est deja verrouille. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(MAX_FRAME_SECONDS, (now - lastFrameNanos) * 1e-9f);
        lastFrameNanos = now;
        Minecraft mc = Minecraft.getInstance();
        if (!updateActivity(mc)) return;
        HoloCaptor captor = mouseCaptor;
        if (captor == null) return;
        float dYaw = pendingYaw;
        float dPitch = pendingPitch;
        pendingYaw = 0f;
        pendingPitch = 0f;
        captor.onMouseMove(context(captor), dYaw, dPitch);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (!updateActivity(mc)) return;
        // Touches enfoncees avant la capture : neutralisees avant handleKeybinds et les entrees du joueur.
        refreshMappings(mc);
        for (Map.Entry<KeyMapping, HoloControl> entry : MAPPING_CONTROLS.entrySet()) {
            KeyMapping mapping = entry.getKey();
            if (mapping.isDown() && captorFor(entry.getValue()) != null) neutralize(mapping);
        }
    }

    /**
     * Relache tout si l'interaction est impossible ; sinon met a jour le captor de la souris et verrouille la rotation.
     * Vrai si actif.
     */
    private static boolean updateActivity(Minecraft mc)
    {
        if (mc.screen instanceof HoloKeyboardScreen) {
            closeKeyboardIfUnused(mc);
            // Retour du focus : l'ecran clavier reste ouvert, la souris doit etre reprise.
            if (mc.screen instanceof HoloKeyboardScreen) HoloKeyboardScreen.grabMouse(mc);
        }
        if (!active(mc)) {
            releaseAll();
            mouseCaptor = null;
            return false;
        }
        if (mc.screen == null && captorFor(HoloControl.KEYBOARD) != null) mc.setScreen(new HoloKeyboardScreen());
        LocalPlayer player = mc.player;
        HoloCaptor captor = captorFor(HoloControl.MOUSE_MOVE);
        if (captor != mouseCaptor) {
            mouseCaptor = captor;
            assert player != null;
            lockYaw = player.getYRot();
            lockPitch = player.getXRot();
            pendingYaw = 0f;
            pendingPitch = 0f;
        }
        if (mouseCaptor != null) {
            assert player != null;
            pendingYaw += player.getYRot() - lockYaw;
            pendingPitch += player.getXRot() - lockPitch;
            applyLock(player);
        }
        return true;
    }

    // ---------------------------------------------------------------- inputs

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event)
    {
        HoloControl control = HoloControl.mouseButton(event.getButton());
        if (control == null) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (!active(Minecraft.getInstance()) || PRESSED.containsKey(control)) return;
            HoloCaptor captor = captorFor(control);
            if (captor == null) return;
            event.setCanceled(true);
            press(captor, control);
        } else if (event.getAction() == GLFW.GLFW_RELEASE && PRESSED.containsKey(control)) {
            event.setCanceled(true);
            release(control);
        }
    }

    /** Bouton non pris en brut : mappings lies a ce bouton. */
    @SubscribeEvent
    public static void onMouseButtonPost(InputEvent.MouseButton.Post event)
    {
        if (event.getAction() == GLFW.GLFW_REPEAT || keyboardMode(Minecraft.getInstance())) return;
        mappings(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction() == GLFW.GLFW_PRESS);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event)
    {
        int action = event.getAction();
        // En mode clavier, la touche est passee par l'ecran clavier.
        if (action == GLFW.GLFW_REPEAT || keyboardMode(Minecraft.getInstance())) return;
        boolean pressed = action == GLFW.GLFW_PRESS;
        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        HoloControl raw = switch (event.getKey()) {
            case GLFW.GLFW_KEY_ESCAPE -> HoloControl.ESCAPE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> HoloControl.ENTER;
            default -> null;
        };
        if (raw != null) {
            HoloCaptor captor = null;
            if (pressed) {
                if (active(Minecraft.getInstance()) && !PRESSED.containsKey(raw)) captor = captorFor(raw);
                if (captor != null) press(captor, raw);
            } else if (PRESSED.containsKey(raw)) {
                captor = release(raw);
            }
            if (captor != null) {
                neutralizeKey(key);
                return;
            }
        }
        mappings(key, pressed);
    }

    /** Echap capture : pas de menu pause (une pause par perte de focus passe). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getNewScreen() instanceof PauseScreen) || !active(mc)) return;
        if (!InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) return;
        if (captorFor(HoloControl.ESCAPE) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event)
    {
        if (!active(Minecraft.getInstance())) return;
        HoloCaptor captor = captorFor(HoloControl.SCROLL);
        if (captor == null) return;
        event.setCanceled(true);
        captor.onScroll(context(captor), event.getScrollDelta());
    }

    /** Attaque, utilisation ou pick continus (bouton maintenu avant la capture). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!active(Minecraft.getInstance())) return;
        HoloControl raw = event.isAttack() ? HoloControl.LEFT_CLICK
                : event.isUseItem() ? HoloControl.RIGHT_CLICK : HoloControl.MIDDLE_CLICK;
        HoloControl mapping = MAPPING_CONTROLS.get(event.getKeyMapping());
        if (captorFor(raw) == null && (mapping == null || captorFor(mapping) == null)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ---------------------------------------------------------------- mode clavier (appele par l'ecran)

    /**
     * Touche (ou repetition) sous l'ecran clavier : au premier captor, du plus recent au plus ancien, qui prend tout le
     * clavier (touche brute, puis ses caracteres) ou un controle lie a cette touche (Echap, Entree, mapping : appui
     * apparie, caracteres ignores). Sans preneur, la touche est perdue (le jeu ne lit rien sous un ecran).
     */
    static void screenKeyPressed(int key, int scanCode, int modifiers)
    {
        Minecraft mc = Minecraft.getInstance();
        charTarget = null;
        highSurrogate = 0;
        if (!active(mc)) return;
        List<HoloControl> controls = controlsForKey(mc, key, scanCode);
        int count = EXPLICIT.size() + PASSIVE.size();
        for (int i = 0; i < count; i++) {
            HoloCaptor captor = i < EXPLICIT.size() ? EXPLICIT.get(EXPLICIT.size() - 1 - i)
                    : PASSIVE.get(i - EXPLICIT.size());
            if (captor.captures(HoloControl.KEYBOARD)) {
                charTarget = captor;
                KEYS_DOWN.put(key, captor);
                captor.onKeyPress(context(captor), key, scanCode, modifiers);
                return;
            }
            for (int j = 0; j < controls.size(); j++) {
                HoloControl control = controls.get(j);
                if (!captor.captures(control)) continue;
                if (!PRESSED.containsKey(control)) press(captor, control);
                return;
            }
        }
    }

    static void screenKeyReleased(int key, int scanCode, int modifiers)
    {
        Minecraft mc = Minecraft.getInstance();
        HoloCaptor captor = KEYS_DOWN.remove(key);
        if (captor != null) captor.onKeyRelease(context(captor), key, scanCode, modifiers);
        List<HoloControl> controls = controlsForKey(mc, key, scanCode);
        for (int i = 0; i < controls.size(); i++) {
            if (PRESSED.containsKey(controls.get(i))) release(controls.get(i));
        }
    }

    static void screenCharTyped(char character, int modifiers)
    {
        HoloCaptor captor = charTarget;
        if (captor == null || !active(Minecraft.getInstance()) || !captor.captures(HoloControl.KEYBOARD)) return;
        if (Character.isHighSurrogate(character)) {
            highSurrogate = character;
            return;
        }
        int codePoint = character;
        if (Character.isLowSurrogate(character)) {
            if (highSurrogate == 0) return;
            codePoint = Character.toCodePoint(highSurrogate, character);
            highSurrogate = 0;
        }
        captor.onCharTyped(context(captor), codePoint, modifiers);
    }

    static void screenScrolled(double delta)
    {
        if (!active(Minecraft.getInstance())) return;
        HoloCaptor captor = captorFor(HoloControl.SCROLL);
        if (captor != null) captor.onScroll(context(captor), delta);
    }

    /** Controles designant une touche : Echap / Entree bruts et mappings lies a la touche. */
    private static List<HoloControl> controlsForKey(Minecraft mc, int key, int scanCode)
    {
        List<HoloControl> controls = new ArrayList<>(2);
        if (key == GLFW.GLFW_KEY_ESCAPE) controls.add(HoloControl.ESCAPE);
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) controls.add(HoloControl.ENTER);
        refreshMappings(mc);
        InputConstants.Key input = InputConstants.getKey(key, scanCode);
        for (Map.Entry<KeyMapping, HoloControl> entry : MAPPING_CONTROLS.entrySet()) {
            if (entry.getKey().getKey().equals(input)) controls.add(entry.getValue());
        }
        return controls;
    }

    private static boolean keyboardMode(Minecraft mc) { return mc.screen instanceof HoloKeyboardScreen; }

    /** Ferme l'ecran clavier si plus aucun captor ne prend le clavier (la souris reste capturee). */
    private static void closeKeyboardIfUnused(Minecraft mc)
    {
        if (!keyboardMode(mc) || captorFor(HoloControl.KEYBOARD) != null) return;
        KEYS_DOWN.clear();
        charTarget = null;
        mc.setScreen(null);
    }

    // ---------------------------------------------------------------- interne

    private static boolean active(Minecraft mc) { return gameInputActive(mc); }

    /** Mappings lies a {@code key} : appui pris par un captor, ou relachement d'un appui pris. */
    private static void mappings(InputConstants.Key key, boolean pressed)
    {
        Minecraft mc = Minecraft.getInstance();
        refreshMappings(mc);
        boolean active = active(mc);
        for (Map.Entry<KeyMapping, HoloControl> entry : MAPPING_CONTROLS.entrySet()) {
            KeyMapping mapping = entry.getKey();
            if (!mapping.getKey().equals(key)) continue;
            HoloControl control = entry.getValue();
            if (pressed) {
                if (!active || PRESSED.containsKey(control)) continue;
                HoloCaptor captor = captorFor(control);
                if (captor == null) continue;
                neutralize(mapping);
                press(captor, control);
            } else if (PRESSED.containsKey(control)) {
                neutralize(mapping);
                release(control);
            }
        }
    }

    private static void press(HoloCaptor captor, HoloControl control)
    {
        PRESSED.put(control, captor);
        captor.onPress(context(captor), control);
    }

    /** Livre le relachement au captor qui a pris l'appui ; le renvoie. */
    private static HoloCaptor release(HoloControl control)
    {
        HoloCaptor captor = PRESSED.remove(control);
        if (captor != null) captor.onRelease(context(captor), control);
        return captor;
    }

    private static void releaseAll()
    {
        charTarget = null;
        while (!KEYS_DOWN.isEmpty()) {
            Map.Entry<Integer, HoloCaptor> entry = KEYS_DOWN.entrySet().iterator().next();
            KEYS_DOWN.remove(entry.getKey());
            entry.getValue().onKeyRelease(context(entry.getValue()), entry.getKey(), 0, 0);
        }
        if (PRESSED.isEmpty()) return;
        Iterator<Map.Entry<HoloControl, HoloCaptor>> iterator = PRESSED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HoloControl, HoloCaptor> entry = iterator.next();
            iterator.remove();
            entry.getValue().onRelease(context(entry.getValue()), entry.getKey());
            // Un callback a pu modifier la table.
            iterator = PRESSED.entrySet().iterator();
        }
    }

    private static void neutralize(KeyMapping mapping)
    {
        mapping.setDown(false);
        while (mapping.consumeClick()) {
            // Clics deja comptes par vanilla : jetes.
        }
    }

    private static void neutralizeKey(InputConstants.Key key)
    {
        refreshMappings(Minecraft.getInstance());
        for (KeyMapping mapping : MAPPING_CONTROLS.keySet()) {
            if (mapping.getKey().equals(key)) neutralize(mapping);
        }
    }

    private static void refreshMappings(Minecraft mc)
    {
        KeyMapping[] mappings = mc.options.keyMappings;
        if (mappings == mappingsSource && mappings.length == MAPPING_CONTROLS.size()) return;
        mappingsSource = mappings;
        MAPPING_CONTROLS.clear();
        for (KeyMapping mapping : mappings) MAPPING_CONTROLS.put(mapping, HoloControl.mapping(mapping.getName()));
    }

    private static void applyLock(Player player)
    {
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
    }

    private static HoloCapture context(HoloCaptor captor)
    {
        CONTEXT.captor = captor;
        return CONTEXT;
    }

    /** Implementation partagee de {@link HoloCapture}. */
    private static final class Context implements HoloCapture
    {
        private HoloCaptor captor;

        @Override
        public Player player() { return Minecraft.getInstance().player; }

        @Override
        public HoloCaptor captor() { return captor; }

        @Override
        public float frameSeconds() { return frameSeconds; }

        @Override
        public float yaw()
        {
            Player player = player();
            return mouseCaptor != null ? lockYaw : player != null ? player.getYRot() : 0f;
        }

        @Override
        public float pitch()
        {
            Player player = player();
            return mouseCaptor != null ? lockPitch : player != null ? player.getXRot() : 0f;
        }

        @Override
        public void setLook(float yaw, float pitch)
        {
            Player player = player();
            if (captor != mouseCaptor || player == null) return;
            lockYaw = yaw;
            lockPitch = Mth.clamp(pitch, -90f, 90f);
            applyLock(player);
        }

        @Override
        public void release() { end(captor); }

        @Override
        public String clipboard()
        {
            String text = Minecraft.getInstance().keyboardHandler.getClipboard();
            return text != null ? text : "";
        }

        @Override
        public void setClipboard(String text) { Minecraft.getInstance().keyboardHandler.setClipboard(text); }
    }
}
