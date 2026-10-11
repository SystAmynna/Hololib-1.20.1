package com.amynna.hololib.client.modules.camera.targeting;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.modules.camera.focus.FocusController;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.modules.camera.focus.FocusModule;
import com.amynna.hololib.modules.camera.targeting.TargetingModule;
import com.amynna.hololib.modules.camera.targeting.TargetingThroughWalls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Ciblage du <b>joueur local</b> : module {@link TargetingModule} de sa memoire active. Fil client, fin de tick.
 * <ul>
 *     <li>Sans module {@link FocusModule} dans la memoire, rien n'est cible.</li>
 *     <li>Ouverture du focus (front montant de {@link FocusController#active()}) : choix de la cible ({@link #pick}).
 *     </li>
 *     <li>Fermeture du focus, changement de niveau : cible retiree. Entite retiree du monde, bloc devenu air : cible
 *     retiree.</li>
 * </ul>
 * Lu par les renderers du ciblage : {@link #entity()}, {@link #block()} (etat du tick).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class TargetingClient
{
    private static boolean focusWas;
    private static ClientLevel level;
    private static Entity entity;
    private static BlockPos block;

    private TargetingClient() {}

    /** Entite ciblee par le joueur local, ou {@code null}. */
    public static Entity entity() { return entity; }

    /** Bloc cible par le joueur local, ou {@code null}. */
    public static BlockPos block() { return block; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientHoloContainer container = ClientHoloWorld.local();
        TargetingModule module = HoloModes.find(container, TargetingModule.class);
        if (mc.player == null || mc.level == null || module == null) {
            reset();
            return;
        }
        boolean focus = HoloModes.find(container, FocusModule.class) != null && FocusController.active();
        if (mc.level != level) {
            module.requestClear();
            level = mc.level;
        }
        if (focus && !focusWas) pick(mc.player, mc.level, container, module);
        if (!focus) module.requestClear();
        focusWas = focus;
        validate(mc.level, module);
        entity = module.targetEntity(mc.level);
        block = module.targetBlock();
    }

    private static void reset()
    {
        focusWas = false;
        level = null;
        entity = null;
        block = null;
    }

    /**
     * Cible ce que le joueur regarde, jusqu'a {@link TargetingModule#PICK_RANGE} blocs : entite visible, sinon entite
     * proposee par un module {@link TargetingThroughWalls} que le rayon traverse (la plus proche), sinon bloc touche,
     * sinon rien.
     */
    private static void pick(LocalPlayer player, ClientLevel level, ClientHoloContainer container,
                             TargetingModule module)
    {
        double range = TargetingModule.PICK_RANGE;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        double blockDistance = hitBlock ? blockHit.getLocation().distanceTo(eye) : range;
        Vec3 blockEnd = eye.add(look.scale(blockDistance));
        AABB area = player.getBoundingBox().expandTowards(look.scale(blockDistance)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, blockEnd, area,
                e -> !e.isSpectator() && e.isPickable(), blockDistance * blockDistance);
        if (entityHit != null) {
            module.requestEntity(entityHit.getEntity());
            return;
        }
        Entity through = throughWalls(player, container, eye, end);
        if (through != null) {
            module.requestEntity(through);
            return;
        }
        if (hitBlock) module.requestBlock(blockHit.getBlockPos());
        else module.requestClear();
    }

    /** Entite proposee a travers les murs que le segment {@code eye -> end} traverse, la plus proche ; ou rien. */
    private static Entity throughWalls(LocalPlayer player, ClientHoloContainer container, Vec3 eye, Vec3 end)
    {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (HoloModule holder : container.modules()) {
            if (!(holder instanceof TargetingThroughWalls provider)) continue;
            for (Entity candidate : provider.throughWallTargets()) {
                if (candidate == player || candidate.isRemoved() || candidate.level() != player.level()) continue;
                Optional<Vec3> hit = candidate.getBoundingBox().inflate(candidate.getPickRadius()).clip(eye, end);
                if (hit.isEmpty()) continue;
                double distance = hit.get().distanceToSqr(eye);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** Cible disparue : entite retiree (ou inconnue du niveau), bloc devenu air. */
    private static void validate(ClientLevel level, TargetingModule module)
    {
        if (module.targetEntityId() >= 0) {
            Entity target = module.targetEntity(level);
            if (target == null || target.isRemoved()) module.requestClear();
        } else if (module.targetBlock() != null && level.getBlockState(module.targetBlock()).isAir()) {
            module.requestClear();
        }
    }
}
