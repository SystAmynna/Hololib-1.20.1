package com.amynna.hololib.client.render.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Objet dessine dans le monde par un {@link WorldRenderer}. */
public interface WorldRenderable
{
    /** Position (origine du dessin) dans le monde, interpolee pour la frame. */
    Vec3 position(float partialTick);

    /** Boite englobant tout ce qui est dessine, dans le monde ; sert au culling par le frustum. */
    AABB renderBounds(float partialTick);
}
