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
 *     backend client (module Space Pilot du joueur local, op, fraction de tick de la derniere frame) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerWindow} : fenetre {@code World}
 *     au-dessus du marker, editable par les operateurs, interrupteur « Pilotage : tous / ops » ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad} : zone de la fenetre dont le clic
 *     capture les controles du pilote (traits {@code HoloPointerInput}) : gaz (vitesse signee, Reculer = marche
 *     arriere) et roulis aux touches de deplacement, frein prioritaire a la touche Saut maintenue, joystick au
 *     regard borne a un cone autour de l'axe avant du monde, envoi des entrees quantifiees et prediction locale
 *     ({@code docs/space/04-mathematiques.md} par. 5 et 6), zone morte deduite des cercles du joystick (C48) ;
 *     saut interstellaire a la touche Accroupi maintenue (T22 : cible au reticule choisie a l'appui, drapeau et
 *     cible envoyes tant que la touche est tenue, joystick inactif pendant le saut) ; boost a la touche Sprint
 *     maintenue (T32 : pentes du gaz et du frein renforcees) ; Sneak et Sprint jamais transmis au personnage, mode
 *     bascule compris ; pad du pilote local lu par Space Pilot ({@code SpacePilotPad.localPilot()}).</li>
 * </ul>
 * Pendant un saut, la fenetre affiche « Saut vers X » puis « Distorsion : n s » a la place de l'etat (lu par chaque
 * client dans le miroir de l'Environment, T22). Pendant le pilotage, la fenetre du poste utilise est masquee pour tous
 * (T41, D31) ; la capture du pad n'est donc pas liee a la fenetre ({@code HoloCaptures.begin}) et le pad la termine
 * lui-meme (C120).
 * Pilotage refuse sans module Space Pilot dans la memoire active du joueur (D11, T19) ; les cercles du joystick sont
 * des objets de Space Pilot ({@code modules.camera.spacepilot}) depuis T19. Code commun : aucune classe client (le
 * client passe par {@code SpaceControllerModule.Backend}).
 */
package com.amynna.hololib.modules.monde.spacecontroller;
