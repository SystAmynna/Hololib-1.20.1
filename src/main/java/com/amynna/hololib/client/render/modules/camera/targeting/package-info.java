/**
 * Rendu du module {@code hololib:targeting}, pour le joueur local seulement (cible lue dans
 * {@code client.modules.camera.targeting.TargetingClient}) :
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.modules.camera.targeting.TargetOutlineRenderer} :
 *     {@code WorldRenderer} a une instance (la cible) : contour vert facon glowing, visible a travers les murs, de
 *     l'entite (redessinee dans le masque) ou de la forme du bloc (utilitaire {@code client.render.outline}) ;</li>
 *     <li>{@link com.amynna.hololib.client.render.modules.camera.targeting.TargetLinkRenderer} : {@code ViewRenderer}
 *     ({@code WORLD}) : segment vert du centre de l'ecran a la cible quand le regard ne passe plus par elle.</li>
 * </ul>
 */
package com.amynna.hololib.client.render.modules.camera.targeting;
