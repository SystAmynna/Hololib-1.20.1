package com.amynna.hololib.holo.interact;

/**
 * Controles souris qu'un {@link HoloInteractible} peut intercepter. Le survol n'est pas un controle : il est toujours
 * signale.
 */
public enum HoloInput
{
    LEFT_CLICK,
    RIGHT_CLICK,
    MIDDLE_CLICK,
    /** Molette ; intercepte, le changement d'emplacement de la barre d'inventaire est bloque. */
    SCROLL,
    /**
     * Mouvements de la souris ; intercepte, la cible <b>capture</b> la souris des qu'elle est survolee : la camera ne
     * tourne plus et les deltas vont a {@link HoloInteractible#onMouseMove} jusqu'a
     * {@link HoloInteraction#releaseMouse()}.
     */
    MOUSE_MOVE
}
