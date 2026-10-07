package com.amynna.hololib.holo.edit;

import com.amynna.hololib.holo.object.HoloTransform;

/**
 * Objet d'une memoire de joueur replace a chaque ouverture du mode focus, relativement au regard du joueur a cet
 * instant : il devient alors un {@code Local} fixe (camera normale), ne suit pas la camera, mais se replace a la
 * prochaine ouverture.
 * <p>
 * La position relative est {@link #openOffset()}, exprimee comme un transform {@code Local} follow : {@code x} /
 * {@code y} = yaw / pitch par rapport au regard, {@code z} = distance, puis rotation. Elle vaut au depart le transform
 * passe au constructeur de l'objet ; le framework la sauvegarde et la synchronise, et l'editor la modifie (l'objet est
 * alors place par rapport au repere de l'editor). Implementee par {@code HoloObject} : un objet n'a rien a ecrire.
 */
public interface HoloRelativeToOpen
{
    /** Position relative au regard a l'ouverture du focus (transform vivant de l'objet). */
    HoloTransform openOffset();
}
