package com.amynna.hololib.client.interact;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.holo.HoloPicking;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.holo.interact.HoloInput;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Controleur client de l'interaction au regard : seul abonne du mod aux inputs souris. Fil client uniquement.
 * <ul>
 *     <li>Debut de chaque frame ({@code RenderTickEvent} START, apres {@code MouseHandler.turnPlayer} : verifie dans
 *     {@code Minecraft.runTick}) : rayon depuis le centre de l'ecran ({@link HoloPicking}), survol
 *     ({@code onHover} / {@code onHoverRelease}), capture de la souris.</li>
 *     <li>Clics ({@code InputEvent.MouseButton.Pre}, avant la mise a jour des {@code KeyMapping}) et molette
 *     ({@code MouseScrollingEvent}) : annules s'ils sont interceptes, et livres a l'element qui les prend (l'element
 *     vise, sinon le premier parent interactible qui les intercepte). Le relachement va a l'element qui a pris
 *     l'appui. Les attaques, utilisations et pick continus ({@code InteractionKeyMappingTriggered}) sont annules
 *     pendant le survol d'un element qui intercepte ce clic.</li>
 *     <li>Capture ({@link HoloInput#MOUSE_MOVE}) : la rotation du joueur est verrouillee (au debut de chaque frame et
 *     de chaque tick, pour ne jamais envoyer au serveur une rotation non verrouillee) et l'ecart est livre a
 *     {@code onMouseMove}. Sans Mixin : vanilla tourne le joueur, on lit l'ecart et on le reverse.</li>
 * </ul>
 * Tout est suspendu (survol relache, capture terminee, appuis relaches) quand un ecran est ouvert ou que la souris
 * n'est pas capturee par le jeu : Echap libere toujours la souris.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class HoloInteractions
{
    private static final HoloInput[] BUTTONS = {HoloInput.LEFT_CLICK, HoloInput.RIGHT_CLICK, HoloInput.MIDDLE_CLICK};
    /** Duree de frame maximale transmise, en secondes (apres un gel). */
    private static final float MAX_FRAME_SECONDS = 0.25f;

    private static final Context CONTEXT = new Context();

    // Survol courant.
    private static HoloObject hoverObject;
    private static HoloInteractible hoverTarget;
    private static float hoverX;
    private static float hoverY;
    private static float hoverYaw;
    private static float hoverPitch;

    // Appuis interceptes, par bouton (0 gauche, 1 droit, 2 molette).
    private static final HoloInteractible[] PRESSED = new HoloInteractible[3];
    private static final HoloObject[] PRESSED_OBJECT = new HoloObject[3];
    private static final float[] PRESSED_X = new float[3];
    private static final float[] PRESSED_Y = new float[3];

    // Capture de la souris.
    private static HoloInteractible capture;
    private static HoloObject captureObject;
    private static float captureX;
    private static float captureY;
    private static float captureYaw;
    private static float capturePitch;
    private static float lockYaw;
    private static float lockPitch;
    private static float pendingYaw;
    private static float pendingPitch;
    /** Element qui a rendu la souris : pas de nouvelle capture tant qu'il reste celui qui la prendrait. */
    private static HoloInteractible released;

    private static long lastFrameNanos;
    private static float frameSeconds;

    private HoloInteractions() {}

    // ---------------------------------------------------------------- frame et tick

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(MAX_FRAME_SECONDS, (now - lastFrameNanos) * 1e-9f);
        lastFrameNanos = now;

        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            suspend();
            return;
        }
        lockRotation(mc.player);
        dropInvalid(mc.player);

        HoloPicking.Hit hit = HoloPicking.pick(mc, event.renderTickTime);
        HoloObject object = hit != null ? hit.object() : null;
        HoloInteractible target = hit != null ? hit.target() : null;
        if (target != hoverTarget || object != hoverObject) {
            HoloInteractible previous = hoverTarget;
            HoloObject previousObject = hoverObject;
            hoverTarget = null;
            hoverObject = null;
            if (previous != null) previous.onHoverRelease(context(previousObject, previous, hoverX, hoverY, false));
            hoverTarget = target;
            hoverObject = object;
            if (hit != null) storeHit(hit);
            if (target != null) target.onHover(context(object, target, hoverX, hoverY, true));
        } else if (hit != null) {
            storeHit(hit);
        }

        if (capture == null) {
            HoloInteractible receiver = hoverTarget != null
                    ? receiver(hoverTarget, hoverObject, HoloInput.MOUSE_MOVE) : null;
            if (receiver != released) {
                released = null;
                if (receiver != null) startCapture(mc.player, receiver);
            }
        }
        if (capture != null) {
            float dYaw = pendingYaw;
            float dPitch = pendingPitch;
            pendingYaw = 0f;
            pendingPitch = 0f;
            capture.onMouseMove(context(captureObject, capture, captureX, captureY, hoverObject == captureObject),
                    dYaw, dPitch);
        }
    }

    private static void storeHit(HoloPicking.Hit hit)
    {
        hoverX = hit.localX();
        hoverY = hit.localY();
        hoverYaw = hit.objectYaw();
        hoverPitch = hit.objectPitch();
        if (hit.object() == captureObject) {
            captureX = hoverX;
            captureY = hoverY;
            captureYaw = hoverYaw;
            capturePitch = hoverPitch;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (capture != null && active(mc)) lockRotation(mc.player);
    }

    // ---------------------------------------------------------------- inputs

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event)
    {
        int button = event.getButton();
        if (button < 0 || button >= BUTTONS.length) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (!active(Minecraft.getInstance()) || hoverTarget == null || PRESSED[button] != null) return;
            HoloInteractible receiver = receiver(hoverTarget, hoverObject, BUTTONS[button]);
            if (receiver == null) return;
            event.setCanceled(true);
            PRESSED[button] = receiver;
            PRESSED_OBJECT[button] = hoverObject;
            PRESSED_X[button] = hoverX;
            PRESSED_Y[button] = hoverY;
            HoloInteraction ctx = context(hoverObject, receiver, hoverX, hoverY, true);
            switch (button) {
                case 0 -> receiver.onLeftClick(ctx);
                case 1 -> receiver.onRightClick(ctx);
                default -> receiver.onMiddleClick(ctx);
            }
        } else if (event.getAction() == GLFW.GLFW_RELEASE && PRESSED[button] != null) {
            event.setCanceled(true);
            release(button);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event)
    {
        if (!active(Minecraft.getInstance()) || hoverTarget == null) return;
        HoloInteractible receiver = receiver(hoverTarget, hoverObject, HoloInput.SCROLL);
        if (receiver == null) return;
        event.setCanceled(true);
        receiver.onScroll(context(hoverObject, receiver, hoverX, hoverY, true), event.getScrollDelta());
    }

    /** Attaque, utilisation ou pick continus (bouton maintenu avant de viser l'hologramme). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (hoverTarget == null || !active(Minecraft.getInstance())) return;
        HoloInput input = event.isAttack() ? HoloInput.LEFT_CLICK
                : event.isUseItem() ? HoloInput.RIGHT_CLICK : HoloInput.MIDDLE_CLICK;
        if (receiver(hoverTarget, hoverObject, input) == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ---------------------------------------------------------------- interne

    private static boolean active(Minecraft mc)
    {
        return mc.player != null && mc.level != null && mc.screen == null && mc.getOverlay() == null
                && mc.mouseHandler.isMouseGrabbed();
    }

    /**
     * Element qui prend {@code input} : la cible si elle l'intercepte, sinon le premier parent interactible (panneaux,
     * puis l'objet) qui l'intercepte ; {@code null} = l'input va au jeu.
     */
    private static HoloInteractible receiver(HoloInteractible target, HoloObject object, HoloInput input)
    {
        for (HoloInteractible current = target; current != null; current = parent(current, object)) {
            if (current.intercepts(input)) return current;
        }
        return null;
    }

    private static HoloInteractible parent(HoloInteractible current, HoloObject object)
    {
        if (current == object) return null;
        if (current instanceof HoloWidget widget) {
            for (HoloPanel panel = widget.parent(); panel != null; panel = panel.parent()) {
                if (panel instanceof HoloInteractible interactible) return interactible;
            }
        }
        return object instanceof HoloInteractible interactible ? interactible : null;
    }

    private static void startCapture(LocalPlayer player, HoloInteractible receiver)
    {
        capture = receiver;
        captureObject = hoverObject;
        captureX = hoverX;
        captureY = hoverY;
        captureYaw = hoverYaw;
        capturePitch = hoverPitch;
        lockYaw = player.getYRot();
        lockPitch = player.getXRot();
        pendingYaw = 0f;
        pendingPitch = 0f;
        receiver.onCaptureStart(context(captureObject, receiver, captureX, captureY, true));
    }

    private static void endCapture()
    {
        HoloInteractible ended = capture;
        if (ended == null) return;
        HoloObject object = captureObject;
        capture = null;
        captureObject = null;
        released = ended;
        ended.onCaptureEnd(context(object, ended, captureX, captureY, hoverObject == object));
    }

    /** Pendant une capture : accumule l'ecart de rotation produit par vanilla et remet la rotation verrouillee. */
    private static void lockRotation(LocalPlayer player)
    {
        if (capture == null) return;
        pendingYaw += player.getYRot() - lockYaw;
        pendingPitch += player.getXRot() - lockPitch;
        applyLock(player);
    }

    private static void applyLock(Player player)
    {
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
    }

    private static void release(int button)
    {
        HoloInteractible receiver = PRESSED[button];
        HoloObject object = PRESSED_OBJECT[button];
        PRESSED[button] = null;
        PRESSED_OBJECT[button] = null;
        boolean hovering = object == hoverObject;
        HoloInteraction ctx = context(object, receiver, hovering ? hoverX : PRESSED_X[button],
                hovering ? hoverY : PRESSED_Y[button], hovering);
        switch (button) {
            case 0 -> receiver.onLeftClickRelease(ctx);
            case 1 -> receiver.onRightClickRelease(ctx);
            default -> receiver.onMiddleClickRelease(ctx);
        }
    }

    /** Termine la capture et relache les appuis dont l'objet a disparu, n'est plus dessine ou plus autorise. */
    private static void dropInvalid(LocalPlayer player)
    {
        if (capture != null && !valid(captureObject, player)) endCapture();
        for (int button = 0; button < PRESSED.length; button++) {
            if (PRESSED[button] != null && !valid(PRESSED_OBJECT[button], player)) release(button);
        }
    }

    private static boolean valid(HoloObject object, Player player)
    {
        HoloModule module = object.module();
        return module != null && module.object(object.key()) == object && HoloRouting.isRouted(object)
                && object.access().allows(player.getUUID(), module.owner());
    }

    /** Ecran ouvert ou souris liberee : relache tout. */
    private static void suspend()
    {
        endCapture();
        for (int button = 0; button < PRESSED.length; button++) {
            if (PRESSED[button] != null) release(button);
        }
        if (hoverTarget != null) {
            HoloInteractible previous = hoverTarget;
            HoloObject previousObject = hoverObject;
            hoverTarget = null;
            hoverObject = null;
            previous.onHoverRelease(context(previousObject, previous, hoverX, hoverY, false));
        }
    }

    /** Remplit le contexte partage ; {@code (lx, ly)} dans le plan local de l'objet. */
    private static HoloInteraction context(HoloObject object, HoloInteractible receiver, float lx, float ly,
                                           boolean hovering)
    {
        float x = lx;
        float y = ly;
        if (object instanceof HoloWindow window) {
            x = window.pixelX(lx);
            y = window.pixelY(ly);
            if (receiver instanceof HoloWidget widget) {
                x -= widget.absoluteX();
                y -= widget.absoluteY();
            }
        }
        CONTEXT.object = object;
        CONTEXT.x = x;
        CONTEXT.y = y;
        CONTEXT.hovering = hovering;
        boolean captured = capture != null && object == captureObject;
        CONTEXT.objectYaw = captured ? captureYaw : hoverYaw;
        CONTEXT.objectPitch = captured ? capturePitch : hoverPitch;
        return CONTEXT;
    }

    /** Implementation partagee de {@link HoloInteraction}. */
    private static final class Context implements HoloInteraction
    {
        private HoloObject object;
        private float x;
        private float y;
        private boolean hovering;
        private float objectYaw;
        private float objectPitch;

        @Override
        public Player player() { return Minecraft.getInstance().player; }

        @Override
        public HoloObject object() { return object; }

        @Override
        public boolean hovering() { return hovering; }

        @Override
        public float x() { return x; }

        @Override
        public float y() { return y; }

        @Override
        public float frameSeconds() { return frameSeconds; }

        @Override
        public float yaw()
        {
            Player player = player();
            return capture != null ? lockYaw : player != null ? player.getYRot() : 0f;
        }

        @Override
        public float pitch()
        {
            Player player = player();
            return capture != null ? lockPitch : player != null ? player.getXRot() : 0f;
        }

        @Override
        public float objectYaw() { return objectYaw; }

        @Override
        public float objectPitch() { return objectPitch; }

        @Override
        public boolean mouseCaptured() { return capture != null; }

        @Override
        public void setLook(float yaw, float pitch)
        {
            Player player = player();
            if (capture == null || player == null) return;
            lockYaw = yaw;
            lockPitch = Mth.clamp(pitch, -90f, 90f);
            applyLock(player);
        }

        @Override
        public void releaseMouse() { endCapture(); }
    }
}
