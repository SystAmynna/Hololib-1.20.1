package com.amynna.hololib.client.modules.camera.radar;

import com.amynna.hololib.client.render.world.WorldRenderable;
import com.amynna.hololib.modules.camera.radar.RadarModule;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Etat du radar du joueur local (fil client) : onde lancee depuis un point fige, entites reperees. Une seule instance
 * ({@link RadarClient#scan()}), lue par le renderer du radar ({@link WorldRenderable} : position = origine de l'onde).
 * <p>
 * Le rayon de l'onde croit de {@value #SPEED} bloc par tick jusqu'au rayon du module, puis l'onde s'efface en
 * {@value #FADE_TICKS} ticks. Chaque tick de croissance, une entite atteinte pour la premiere fois (distance a
 * l'origine sous le rayon) est testee une seule fois : filtre des reglages, puis epaisseur de mur entre l'oeil du
 * joueur et elle ({@link RadarWalls}). Reperee, elle le reste (et est suivie) jusqu'a Desactiver ou une relance ;
 * retiree du monde, elle est oubliee. Horloge : {@code gameTime} du niveau (figee en pause, comme {@code partialTick}).
 */
public final class RadarScan implements WorldRenderable
{
    /** Vitesse de l'onde, en blocs par tick (32 blocs/s). */
    public static final float SPEED = 1.6f;
    /** Duree de l'effacement de l'onde une fois le rayon maximal atteint, en ticks. */
    public static final int FADE_TICKS = 12;
    /** Nombre maximal d'entites reperees. */
    public static final int MAX_DETECTED = 64;

    private boolean active;
    private ClientLevel level;
    private Vec3 origin = Vec3.ZERO;
    private long startTick;
    private int maxRadius = RadarModule.DEFAULT_RADIUS;
    private final List<Entity> detected = new ArrayList<>();
    private final List<Entity> detectedView = Collections.unmodifiableList(detected);
    /** Ids des entites deja atteintes par l'onde (testees une fois). */
    private final IntOpenHashSet reached = new IntOpenHashSet();

    RadarScan() {}

    /** Vrai tant que le radar est lance (jusqu'a Desactiver). */
    public boolean active() { return active; }

    public Vec3 origin() { return origin; }

    /** Rayon maximal de l'onde (reglage du module, relu chaque tick). */
    public int maxRadius() { return maxRadius; }

    /** Entites reperees (vue non modifiable). */
    public List<Entity> detected() { return detectedView; }

    /** Age de l'onde en ticks, interpole. */
    public float age(float partialTick)
    {
        return level == null ? 0f : (level.getGameTime() - startTick) + partialTick;
    }

    /** Rayon de l'onde, en blocs, borne au rayon maximal. */
    public float radius(float partialTick) { return Math.min(maxRadius, SPEED * age(partialTick)); }

    /** Opacite de l'onde : 1 pendant la croissance, puis effacement ; 0 une fois l'onde finie. */
    public float waveAlpha(float partialTick)
    {
        if (!active) return 0f;
        float past = age(partialTick) - maxRadius / SPEED;
        return past <= 0f ? 1f : Mth.clamp(1f - past / FADE_TICKS, 0f, 1f);
    }

    /** Vrai pendant la croissance de l'onde (detection en cours). */
    boolean growing() { return active && SPEED * age(0f) <= maxRadius + SPEED; }

    @Override
    public Vec3 position(float partialTick) { return origin; }

    @Override
    public AABB renderBounds(float partialTick) { return new AABB(origin, origin).inflate(maxRadius); }

    // ---------------------------------------------------------------- client

    void start(ClientLevel level, Vec3 origin, int maxRadius)
    {
        clear();
        this.active = true;
        this.level = level;
        this.origin = origin;
        this.startTick = level.getGameTime();
        this.maxRadius = maxRadius;
    }

    void clear()
    {
        active = false;
        level = null;
        detected.clear();
        reached.clear();
    }

    /** Fin de tick client : rayon relu du module, entites oubliees, puis nouvelles entites atteintes. */
    void tick(ClientLevel current, LocalPlayer player, RadarModule module)
    {
        if (!active) return;
        if (current != level) {
            // Changement de dimension ou de partie : le radar s'arrete.
            clear();
            return;
        }
        maxRadius = module.radius();
        detected.removeIf(entity -> entity.isRemoved() || entity.level() != current);
        if (!growing()) return;
        float r = radius(0f);
        double r2 = (double) r * r;
        Vec3 eye = player.getEyePosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || reached.contains(entity.getId())) continue;
            if (origin.distanceToSqr(entity.getX(), entity.getY(0.5), entity.getZ()) > r2) continue;
            reached.add(entity.getId());
            if (detected.size() >= MAX_DETECTED || !relevant(entity, module)) continue;
            if (!module.infiniteThickness()
                    && !RadarWalls.within(current, eye, entity.getBoundingBox().getCenter(), module.thickness())) {
                continue;
            }
            detected.add(entity);
        }
    }

    /** Filtre des reglages : autres joueurs (hors spectateurs), entites vivantes hors joueurs et porte-armures. */
    private static boolean relevant(Entity entity, RadarModule module)
    {
        if (entity instanceof Player player) return module.players() && !player.isSpectator();
        if (entity instanceof ArmorStand) return false;
        return module.entities() && entity instanceof LivingEntity living && living.isAlive();
    }
}
