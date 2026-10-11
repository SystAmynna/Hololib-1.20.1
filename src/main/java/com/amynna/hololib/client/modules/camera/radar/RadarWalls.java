package com.amynna.hololib.client.modules.camera.radar;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Epaisseur de mur entre l'oeil du joueur et une cible : parcours voxel du segment ({@code BlockGetter.traverseBlocks},
 * le DDA du raycast vanilla) qui compte les blocs occultants ({@code BlockState.canOcclude()} : air, verre, feuilles,
 * vitres et autres blocs transparents ignores). Arret des que la limite est depassee.
 */
final class RadarWalls
{
    private RadarWalls() {}

    /** Vrai si le segment {@code from -> to} traverse au plus {@code limit} blocs occultants. */
    static boolean within(BlockGetter level, Vec3 from, Vec3 to, int limit)
    {
        Counter counter = new Counter(level, limit);
        return BlockGetter.traverseBlocks(from, to, counter, (c, pos) -> {
            if (c.level.getBlockState(pos).canOcclude() && ++c.count > c.limit) return Boolean.FALSE;
            return null;
        }, c -> Boolean.TRUE);
    }

    /** Contexte du parcours (un par test : seulement au reperage d'une entite). */
    private static final class Counter
    {
        final BlockGetter level;
        final int limit;
        int count;

        Counter(BlockGetter level, int limit)
        {
            this.level = level;
            this.limit = limit;
        }
    }
}
