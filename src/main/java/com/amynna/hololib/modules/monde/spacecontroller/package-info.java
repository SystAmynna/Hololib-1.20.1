/**
 * Module {@code hololib:space_controller} (memoire de position, marker) : fenetre de controle du monde dans le
 * systeme stellaire. Il ne porte ni la pose ni la galaxie : il affiche un etat recopie depuis le Space Environment de
 * la dimension du marker ({@code modules.dimension.space}) et lui relaie le pilotage par ses methodes serveur
 * ({@code claimPilot}, {@code pilotInput}, {@code releasePilot}). Conception : {@code docs/space/02-architecture.md}
 * par. 6 et {@code 05-synchronisation.md} par. 3 et 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerModule} : etat synchronise (au plus
 *     4 envois par seconde), actions serveur {@code BEGIN} / {@code INPUT} / {@code END} / {@code OPS_ONLY}, reglage
 *     sauvegarde « pilotage reserve aux ops » (verifie par le serveur : {@code HoloAccess} ne sait pas l'exprimer) ;
 *     backend client (joueur local, op) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerWindow} : fenetre {@code World}
 *     au-dessus du marker, editable par les operateurs, interrupteur « Pilotage : tous / ops » ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad} : zone de la fenetre dont le clic
 *     capture les controles du pilote (traits {@code HoloPointerInput}) : gaz (vitesse signee, Reculer = marche
 *     arriere) et roulis aux touches de deplacement, frein prioritaire a la touche Saut maintenue, joystick au
 *     regard borne a un cone autour de l'axe avant du monde, envoi des entrees quantifiees et prediction locale
 *     ({@code docs/space/04-mathematiques.md} par. 5 et 6) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickForward} (cercle avant, {@code Local}
 *     relatif sur l'axe avant) et {@link com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickAim} (cercle
 *     de visee, {@code Local} follow au centre de l'ecran) : mini-interface du joystick, dessinee seulement chez le
 *     pilote local de ce poste ({@code shown()}), par {@code client.render.modules.monde.spacecontroller}.</li>
 * </ul>
 * Code commun : aucune classe client (le client passe par {@code SpaceControllerModule.Backend}).
 */
package com.amynna.hololib.modules.monde.spacecontroller;
