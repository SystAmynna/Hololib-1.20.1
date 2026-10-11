/**
 * Module {@code hololib:space_knowledge} (memoire de joueur, T33, D25) : connaissance du ciel dans une dimension munie
 * d'un Space Environment ({@code modules.dimension.space}), pour le joueur local seulement (jamais vu par un autre
 * joueur, meme en inspection : C45). Conception : {@code docs/space/01-vision.md} (Space Knowledge),
 * {@code 02-architecture.md} par. 7 ter, {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.camera.spaceknowledge.SpaceKnowledgeModule} : reglages d'affichage
 *     sauvegardes et synchronises au proprietaire (interrupteur general, orbites, reperage des corps, etoiles
 *     exterieures, nom au regard et son angle, distance sous le nom), modifies par {@code sendAction} et valides par le
 *     serveur ; compatible Config ({@code Configurable}) et Quick Action ({@code QuickActionProvider} : action
 *     {@code toggle} qui coupe ou rallume toute la surcouche) ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spaceknowledge.SpaceKnowledgeConfigWindow} : fenetre de Config
 *     « Space Knowledge » (objet {@code PRIVATE}), interrupteurs {@code HoloWidgetSwitch} et barre de l'angle en
 *     degres.</li>
 * </ul>
 * Le dessin (surcouche du ciel : anneaux, orbites, etoiles exterieures, nom et distance au regard) est fait par
 * {@code client.render.modules.camera.spaceknowledge.SpaceKnowledgeSkyRenderer}, qui lit le module du joueur local
 * par {@code client.modules.camera.spaceknowledge.SpaceKnowledgeClient}. Pas necessaire pour piloter : le joystick, le
 * lien vers le referentiel, le saut et le HUD restent a Space Pilot (C75). Code commun : aucune classe client.
 */
package com.amynna.hololib.modules.camera.spaceknowledge;
