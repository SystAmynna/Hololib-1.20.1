package com.amynna.hololib.client.modules.camera.attackguard;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.capture.HoloControlCapture;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.render.HoloRenderCanceler;
import com.amynna.hololib.holo.render.HoloRenderElement;
import com.amynna.hololib.modules.camera.attackguard.AttackGuardModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Optional;

/**
 * Attack Guard du <b>joueur local</b> : module {@link AttackGuardModule} de sa memoire active. Fil client.
 * <ul>
 *     <li>regard direct sur un joueur protege, <b>sans limite de distance</b> (armes a distance) : le rayon du regard
 *     coupe la boite d'un joueur protege connu du client, <b>elargie</b> de {@value #MARGIN} bloc (tolerance des
 *     tirs, la meme a toute distance), sans bloc entre l'oeil et le point
 *     touche, et le joueur protege est visible (pas de bloc entre l'oeil et le centre de sa boite) ; le radar n'y
 *     change rien. Calcule en fin de tick ({@link #protectedInSight()}, lu par la croix rouge) et de nouveau a chaque
 *     input de l'attaque ;</li>
 *     <li>captor passif ({@link HoloControlCapture#addPassive}, au setup client) : prend le mapping
 *     {@code key.attack} tant qu'un joueur protege est vise : le jeu ne voit pas l'attaque, ni l'appui maintenu ;
 *     </li>
 *     <li>fin de tick : reticule vanilla annule ({@code HoloRenderCanceler}, {@code CROSSHAIR}) tant qu'un joueur
 *     protege est vise ; la croix rouge est dessinee par {@code AttackGuardCrossRenderer}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class AttackGuardClient
{
    private static final HoloControl ATTACK = HoloControl.mapping("key.attack");

    /** Tolerance autour de la boite du joueur protege, en blocs (en plus de son rayon de visee). */
    private static final double MARGIN = 0.5;
    private static final HoloCaptor GUARD = control -> control.equals(ATTACK) && compute();

    private static AttackGuardModule module;
    private static boolean inSight;

    private AttackGuardClient() {}

    /** Vrai si le joueur local a Attack Guard et vise directement un joueur protege (etat du dernier tick). */
    public static boolean protectedInSight() { return inSight; }

    /**
     * Vrai si le rayon du regard du joueur local coupe un joueur protege (boite gonflee de son rayon de visee) sans
     * bloc entre l'oeil et le point touche, a toute distance.
     */
    private static boolean compute()
    {
        AttackGuardModule current = module;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (current == null || player == null || level == null) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        for (AbstractClientPlayer other : level.players()) {
            if (other == player || other.isSpectator() || !current.isProtected(other.getId())) continue;
            AABB body = other.getBoundingBox();
            Vec3 center = body.getCenter();
            double distance = center.distanceTo(eye);
            AABB box = body.inflate(other.getPickRadius() + MARGIN);
            if (box.contains(eye)) return true;
            Vec3 end = eye.add(look.scale(distance + box.getSize()));
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty() || blocked(level, player, eye, hit.get()) || blocked(level, player, eye, center)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Vrai si un bloc coupe le segment {@code from -> to}. */
    private static boolean blocked(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to)
    {
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        return block.getType() != HitResult.Type.MISS;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        module = Minecraft.getInstance().player == null ? null
                : HoloModes.find(ClientHoloWorld.local(), AttackGuardModule.class);
        inSight = compute();
        if (module == null) return;
        if (inSight) HoloRenderCanceler.cancel(module, HoloRenderElement.CROSSHAIR);
        else HoloRenderCanceler.restore(module, HoloRenderElement.CROSSHAIR);
    }

    /** Installation du captor au setup client. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> HoloControlCapture.addPassive(GUARD));
        }
    }
}
