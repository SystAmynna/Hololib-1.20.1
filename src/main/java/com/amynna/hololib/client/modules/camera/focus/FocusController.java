package com.amynna.hololib.client.modules.camera.focus;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.capture.HoloControlCapture;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.key.HoloKeyMappings;
import com.amynna.hololib.client.modules.camera.editor.EditorController;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.modules.camera.focus.FocusModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector2fc;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Mode focus du joueur local (module {@link FocusModule} de sa memoire). Fil client, fin de tick.
 * <ul>
 *     <li>Touche focus (H) : ouvre (en fermant l'editor) ou ferme le mode.</li>
 *     <li>Ouverture : chaque objet {@code HoloRelativeToOpen} de la memoire du joueur est place en {@code Local} fixe
 *     d'apres sa position relative au regard courant (selon son {@code HoloRotationFrame}), et demande au serveur ;
 *     le regard de placement est retenu ({@link #placement}) : l'editor, ouvert depuis le focus, garde les objets ou
 *     ils sont.</li>
 *     <li>Sorties, lues sur les <b>inputs</b> (touches physiques liees aux controles, pas le mouvement : un mur ne
 *     bloque rien) et seulement quand le controle va au jeu : saut, sprint, deplacement non accroupi pendant plus de
 *     {@value #WALK_TICKS} ticks consecutifs, deux appuis sur sneak en moins de {@value #DOUBLE_SNEAK_TICKS} ticks.
 *     Se deplacer accroupi ne ferme pas le mode.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class FocusController
{
    static final int WALK_TICKS = 20;
    static final int DOUBLE_SNEAK_TICKS = 10;

    private static final HoloControl JUMP = HoloControl.mapping("key.jump");
    private static final HoloControl SPRINT = HoloControl.mapping("key.sprint");
    private static final HoloControl SNEAK = HoloControl.mapping("key.sneak");
    private static final HoloControl FORWARD = HoloControl.mapping("key.forward");
    private static final HoloControl BACK = HoloControl.mapping("key.back");
    private static final HoloControl LEFT = HoloControl.mapping("key.left");
    private static final HoloControl RIGHT = HoloControl.mapping("key.right");
    private static final HoloAnchor.Local FIXED = new HoloAnchor.Local(false);

    private static final HoloEditMath MATH = new HoloEditMath();
    private static final Quaternionf GAZE = new Quaternionf();
    /** Regard (yaw, pitch) de placement des objets places depuis l'ouverture du focus. */
    private static final Map<HoloObject, Vector2f> PLACED = new IdentityHashMap<>();

    private static boolean jumpDown;
    private static boolean sprintDown;
    private static boolean sneakDown;
    private static int walkTicks;
    private static int ticks;
    private static int lastSneak = -DOUBLE_SNEAK_TICKS - 1;

    private FocusController() {}

    /** Ferme le mode focus du joueur local s'il est ouvert (ouverture de l'editor). */
    public static void close()
    {
        FocusModule module = module();
        if (module != null && module.modeActive()) module.requestActive(false);
    }

    /** Vrai si le mode focus du joueur local est ouvert. */
    public static boolean active()
    {
        FocusModule module = module();
        return module != null && module.modeActive();
    }

    /**
     * Regard (yaw, pitch) avec lequel l'objet a ete place depuis l'ouverture du focus, ou {@code null} (focus ferme,
     * objet pas place). Valeur vivante : ne pas la garder.
     */
    public static Vector2fc placement(HoloObject object) { return active() ? PLACED.get(object) : null; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (HoloKeyMappings.FOCUS.consumeClick()) toggle(mc);
        FocusModule module = module();
        if (module == null || !module.modeActive()) return;
        ticks++;
        watchExits(mc, module);
    }

    private static void toggle(Minecraft mc)
    {
        FocusModule module = module();
        if (module == null) return;
        if (module.modeActive()) {
            module.requestActive(false);
            return;
        }
        EditorController.close();
        module.requestActive(true);
        PLACED.clear();
        placeRelative(mc.player);
        seed(mc);
        walkTicks = 0;
        lastSneak = ticks - DOUBLE_SNEAK_TICKS - 1;
    }

    /** Objets {@code HoloRelativeToOpen} de la memoire du joueur : places devant le regard courant. */
    private static void placeRelative(LocalPlayer player)
    {
        ClientHoloContainer container = ClientHoloWorld.local();
        if (container == null) return;
        for (HoloModule module : container.modules()) {
            for (HoloObject object : module.objects()) placeRelative(object, player.getYRot(), player.getXRot());
        }
    }

    /**
     * Place un objet {@code HoloRelativeToOpen} de la memoire du joueur local devant le regard {@code (yRot, xRot)},
     * d'apres sa position relative et son repere de rotation, en {@code Local} fixe, et le demande au serveur ; retient
     * le regard de placement. Sans effet pour un autre objet.
     */
    public static void placeRelative(HoloObject object, float yRot, float xRot)
    {
        if (object.openOffset() == null || !object.replicated()) return;
        HoloEditMath.yawPitch(GAZE, yRot, xRot);
        MATH.fromOffset(object.openOffset(), GAZE, object.rotationFrame(), object.transform());
        object.requestAnchor(FIXED);
        PLACED.computeIfAbsent(object, o -> new Vector2f()).set(yRot, xRot);
    }

    private static void watchExits(Minecraft mc, FocusModule module)
    {
        if (!HoloControlCapture.gameInputActive(mc) || mc.screen != null) {
            // Ecran ou saisie au clavier : rien ne compte, et les touches deja enfoncees ne feront pas de front.
            seed(mc);
            walkTicks = 0;
            return;
        }
        Options options = mc.options;
        boolean jump = down(mc, options.keyJump, JUMP);
        boolean sprint = down(mc, options.keySprint, SPRINT);
        boolean sneak = down(mc, options.keyShift, SNEAK);
        boolean move = down(mc, options.keyUp, FORWARD) || down(mc, options.keyDown, BACK)
                || down(mc, options.keyLeft, LEFT) || down(mc, options.keyRight, RIGHT);
        boolean exit = jump && !jumpDown || sprint && !sprintDown;
        if (sneak && !sneakDown) {
            if (ticks - lastSneak <= DOUBLE_SNEAK_TICKS) exit = true;
            lastSneak = ticks;
        }
        walkTicks = move && !sneak ? walkTicks + 1 : 0;
        if (walkTicks > WALK_TICKS) exit = true;
        jumpDown = jump;
        sprintDown = sprint;
        sneakDown = sneak;
        if (exit) module.requestActive(false);
    }

    private static void seed(Minecraft mc)
    {
        jumpDown = HoloKeyMappings.physicallyDown(mc, mc.options.keyJump);
        sprintDown = HoloKeyMappings.physicallyDown(mc, mc.options.keySprint);
        sneakDown = HoloKeyMappings.physicallyDown(mc, mc.options.keyShift);
    }

    /** Touche physique enfoncee, et controle non pris par une capture (il irait au jeu). */
    private static boolean down(Minecraft mc, KeyMapping mapping, HoloControl control)
    {
        return HoloKeyMappings.physicallyDown(mc, mapping) && HoloControlCapture.captorFor(control) == null;
    }

    private static FocusModule module() { return HoloModes.find(ClientHoloWorld.local(), FocusModule.class); }
}
