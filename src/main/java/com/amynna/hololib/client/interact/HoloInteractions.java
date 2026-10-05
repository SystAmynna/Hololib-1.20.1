package com.amynna.hololib.client.interact;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.capture.HoloControlCapture;
import com.amynna.hololib.client.render.holo.HoloPicking;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Controleur client de l'interaction au regard. Fil client uniquement. Il ne lit aucun input lui-meme :
 * <ul>
 *     <li>Debut de chaque frame ({@code RenderTickEvent} START, apres le verrou de rotation de la capture) : rayon
 *     depuis le centre de l'ecran ({@link HoloPicking}) et survol ({@code onHover} / {@code onHoverRelease}), avec la
 *     marge de sortie des elements qui en ont une ; puis {@code onDrag} pour chaque clic intercepte encore enfonce
 *     (regard projete sur le plan de l'objet).</li>
 *     <li>Interception : un captor <b>passif</b> de {@link HoloControlCapture} prend un controle si un element de la
 *     chaine survolee (cible, panneaux parents, objet) l'intercepte, et le route vers le premier qui l'intercepte
 *     ({@code onLeftClick...}, {@code onScroll}, {@code onControl}). Le relachement va a l'element qui a pris
 *     l'appui. Les captures explicites passent avant.</li>
 *     <li>Captures liees a un objet ({@link HoloInteraction#capture}) : terminees si l'objet disparait, n'est plus
 *     dessine ou n'est plus utilisable par le joueur.</li>
 * </ul>
 * Le survol est relache quand un ecran est ouvert ou que la souris n'est pas capturee par le jeu.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class HoloInteractions
{
    private static final Context CONTEXT = new Context();
    private static final HoverCaptor HOVER = new HoverCaptor();

    // Survol courant.
    private static HoloObject hoverObject;
    private static HoloInteractible hoverTarget;
    private static float hoverX;
    private static float hoverY;
    private static float hoverYaw;
    private static float hoverPitch;

    /** Appuis interceptes au survol, par controle. */
    private static final Map<HoloControl, Pressed> PRESSED = new HashMap<>();
    /** Copie reutilisee des appuis pendant {@link #drag}. */
    private static final List<HoloControl> DRAGGING = new ArrayList<>();
    /** Captures explicites liees a un objet. */
    private static final Map<HoloCaptor, HoloObject> BOUND = new IdentityHashMap<>();

    private HoloInteractions() {}

    /** Mise en place au setup client : l'interaction devient un captor passif. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> HoloControlCapture.addPassive(HOVER));
        }
    }

    // ---------------------------------------------------------------- frame

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        dropInvalid(mc.player);
        if (!active(mc)) {
            setHover(null, null);
            return;
        }
        float partialTick = event.renderTickTime;
        HoloPicking.Hit hit = HoloPicking.pick(mc, partialTick);
        if (!keepHover(mc, hit, partialTick)) {
            if (hit == null) {
                setHover(null, null);
            } else if (hit.target() != hoverTarget || hit.object() != hoverObject) {
                setHover(null, null);
                storeHit(hit);
                setHover(hit.object(), hit.target());
            } else {
                storeHit(hit);
            }
        }
        drag(mc, partialTick);
    }

    /**
     * Survol avec marge ({@link HoloInteractible#hoverMargin()}) : l'element survole le reste tant que le regard, sur
     * le fond de sa fenetre ou en dehors, est dans son rectangle elargi de la marge. Viser un autre element y met
     * fin.
     */
    private static boolean keepHover(Minecraft mc, HoloPicking.Hit hit, float partialTick)
    {
        if (hoverTarget == null || hit != null && hit.target() == hoverTarget && hit.object() == hoverObject) {
            return false;
        }
        float margin = hoverTarget.hoverMargin();
        if (margin <= 0f || !(hoverTarget instanceof HoloWidget widget)) return false;
        if (!(hoverObject instanceof HoloWindow window)) return false;
        if (hit != null && hit.target() != hoverObject) return false;
        if (!HoloPicking.project(mc, hoverObject, partialTick)) return false;
        float x = window.pixelX(HoloPicking.projectedX()) - widget.absoluteX();
        float y = window.pixelY(HoloPicking.projectedY()) - widget.absoluteY();
        if (x < -margin || y < -margin || x > widget.width() + margin || y > widget.height() + margin) return false;
        hoverX = HoloPicking.projectedX();
        hoverY = HoloPicking.projectedY();
        return true;
    }

    /** Clics interceptes encore enfonces : regard projete sur leur objet, livre a {@code onDrag}. */
    private static void drag(Minecraft mc, float partialTick)
    {
        if (PRESSED.isEmpty()) return;
        DRAGGING.clear();
        DRAGGING.addAll(PRESSED.keySet());
        for (int i = 0; i < DRAGGING.size(); i++) {
            HoloControl control = DRAGGING.get(i);
            Pressed pressed = PRESSED.get(control);
            if (pressed == null || !HoloPicking.project(mc, pressed.object, partialTick)) continue;
            pressed.receiver.onDrag(context(pressed.object, pressed.receiver, HoloPicking.projectedX(),
                    HoloPicking.projectedY(), pressed.object == hoverObject), control);
        }
    }

    private static void storeHit(HoloPicking.Hit hit)
    {
        hoverX = hit.localX();
        hoverY = hit.localY();
        hoverYaw = hit.objectYaw();
        hoverPitch = hit.objectPitch();
    }

    private static void setHover(HoloObject object, HoloInteractible target)
    {
        if (target == hoverTarget && object == hoverObject) return;
        HoloInteractible previous = hoverTarget;
        HoloObject previousObject = hoverObject;
        hoverTarget = null;
        hoverObject = null;
        if (previous != null) previous.onHoverRelease(context(previousObject, previous, hoverX, hoverY, false));
        hoverTarget = target;
        hoverObject = object;
        if (target != null) target.onHover(context(object, target, hoverX, hoverY, true));
    }

    // ---------------------------------------------------------------- interne

    /** Pas d'ecran (ou seulement l'ecran clavier de la capture) et souris capturee. */
    private static boolean active(Minecraft mc) { return HoloControlCapture.gameInputActive(mc); }

    /**
     * Element qui prend {@code control} : la cible si elle l'intercepte, sinon le premier parent interactible
     * (panneaux, puis l'objet) qui l'intercepte ; {@code null} = personne. {@code MOUSE_MOVE} et {@code KEYBOARD}
     * ne sont jamais pris au survol (ils retiendraient le joueur : capture explicite).
     */
    private static HoloInteractible receiver(HoloInteractible target, HoloObject object, HoloControl control)
    {
        if (control.kind() == HoloControl.Kind.MOUSE_MOVE || control.kind() == HoloControl.Kind.KEYBOARD) return null;
        for (HoloInteractible current = target; current != null; current = parent(current, object)) {
            if (current.intercepts(control)) return current;
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

    /** Relache les appuis et termine les captures liees dont l'objet a disparu, n'est plus dessine ou autorise. */
    private static void dropInvalid(Player player)
    {
        if (!PRESSED.isEmpty()) {
            Iterator<Map.Entry<HoloControl, Pressed>> iterator = PRESSED.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<HoloControl, Pressed> entry = iterator.next();
                if (valid(entry.getValue().object, player)) continue;
                iterator.remove();
                deliverRelease(entry.getKey(), entry.getValue());
                iterator = PRESSED.entrySet().iterator();
            }
        }
        if (!BOUND.isEmpty()) {
            Iterator<Map.Entry<HoloCaptor, HoloObject>> iterator = BOUND.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<HoloCaptor, HoloObject> entry = iterator.next();
                if (!HoloCaptures.isCapturing(entry.getKey())) {
                    iterator.remove();
                } else if (!valid(entry.getValue(), player)) {
                    iterator.remove();
                    HoloCaptures.end(entry.getKey());
                    iterator = BOUND.entrySet().iterator();
                }
            }
        }
    }

    private static boolean valid(HoloObject object, Player player)
    {
        HoloModule module = object.module();
        return player != null && module != null && module.object(object.key()) == object
                && HoloRouting.isRouted(object) && object.access().allows(player.getUUID(), module.owner());
    }

    private static void deliverRelease(HoloControl control, Pressed pressed)
    {
        boolean hovering = pressed.object == hoverObject;
        HoloInteraction ctx = context(pressed.object, pressed.receiver, hovering ? hoverX : pressed.x,
                hovering ? hoverY : pressed.y, hovering);
        switch (control.mouseButton()) {
            case 0 -> pressed.receiver.onLeftClickRelease(ctx);
            case 1 -> pressed.receiver.onRightClickRelease(ctx);
            case 2 -> pressed.receiver.onMiddleClickRelease(ctx);
            default -> pressed.receiver.onControl(ctx, control, false);
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
        CONTEXT.objectYaw = hoverYaw;
        CONTEXT.objectPitch = hoverPitch;
        return CONTEXT;
    }

    /** Appui intercepte au survol. */
    private static final class Pressed
    {
        private final HoloInteractible receiver;
        private final HoloObject object;
        private final float x;
        private final float y;

        private Pressed(HoloInteractible receiver, HoloObject object, float x, float y)
        {
            this.receiver = receiver;
            this.object = object;
            this.x = x;
            this.y = y;
        }
    }

    /** Captor passif : les controles interceptes par l'element survole. */
    private static final class HoverCaptor implements HoloCaptor
    {
        @Override
        public boolean captures(HoloControl control)
        {
            return hoverTarget != null && receiver(hoverTarget, hoverObject, control) != null;
        }

        @Override
        public void onPress(HoloCapture capture, HoloControl control)
        {
            HoloInteractible receiver = hoverTarget != null ? receiver(hoverTarget, hoverObject, control) : null;
            if (receiver == null) return;
            PRESSED.put(control, new Pressed(receiver, hoverObject, hoverX, hoverY));
            HoloInteraction ctx = context(hoverObject, receiver, hoverX, hoverY, true);
            switch (control.mouseButton()) {
                case 0 -> receiver.onLeftClick(ctx);
                case 1 -> receiver.onRightClick(ctx);
                case 2 -> receiver.onMiddleClick(ctx);
                default -> receiver.onControl(ctx, control, true);
            }
        }

        @Override
        public void onRelease(HoloCapture capture, HoloControl control)
        {
            Pressed pressed = PRESSED.remove(control);
            if (pressed != null) deliverRelease(control, pressed);
        }

        @Override
        public void onScroll(HoloCapture capture, double delta)
        {
            HoloInteractible receiver = hoverTarget != null
                    ? receiver(hoverTarget, hoverObject, HoloControl.SCROLL) : null;
            if (receiver != null) receiver.onScroll(context(hoverObject, receiver, hoverX, hoverY, true), delta);
        }
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
        public float frameSeconds() { return HoloControlCapture.frameSeconds(); }

        @Override
        public float yaw()
        {
            Player player = player();
            return player != null ? player.getYRot() : 0f;
        }

        @Override
        public float pitch()
        {
            Player player = player();
            return player != null ? player.getXRot() : 0f;
        }

        @Override
        public float objectYaw() { return objectYaw; }

        @Override
        public float objectPitch() { return objectPitch; }

        @Override
        public boolean capture(HoloCaptor captor)
        {
            HoloObject bound = object;
            if (bound == null || !HoloCaptures.begin(captor)) return false;
            BOUND.put(captor, bound);
            return true;
        }
    }
}
