package com.amynna.hololib.holo.edit;

/**
 * Outil d'edition : bascule d'un objet ancre {@code Local} entre fixe (camera normale, {@code follow = false}) et
 * dynamique (camera follow, {@code follow = true}), sans le deplacer a l'ecran. Ne s'applique qu'aux objets d'une
 * memoire de joueur.
 */
public interface HoloEditFixOrDynamic extends HoloEditable
{
}
