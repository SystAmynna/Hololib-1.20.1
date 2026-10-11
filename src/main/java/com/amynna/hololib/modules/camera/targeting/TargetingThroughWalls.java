package com.amynna.hololib.modules.camera.targeting;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Couche de compatibilite avec le module {@code hololib:targeting} ({@link TargetingModule}) : un {@code HoloModule}
 * de la memoire du joueur qui implemente cette interface propose des entites visables <b>a travers les murs</b>.
 * L'implementer est la declaration : le ciblage consulte lui-meme les modules de la memoire active du joueur local.
 * A l'ouverture du focus, si le regard ne touche aucune entite visible, une entite proposee que le rayon du regard
 * traverse (jusqu'a {@link TargetingModule#PICK_RANGE}) est ciblee au lieu du bloc entre les deux.
 * <p>
 * Appele cote client, sur le module de la memoire du joueur local seulement.
 */
public interface TargetingThroughWalls
{
    /** Entites visables a travers les murs (vue en lecture ; liste vide si aucune). */
    List<? extends Entity> throughWallTargets();
}
