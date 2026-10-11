/**
 * Module {@code hololib:space_pilot} (memoire de joueur) : aides au pilotage dans une dimension munie d'un Space
 * Environment ({@code modules.dimension.space}), pour le joueur local seulement (jamais vu par un autre joueur, meme
 * en inspection : C45). Reduit en T33 (D25, C75) : la surcouche du ciel et la Quick Action sont passees a Space
 * Knowledge ({@code modules.camera.spaceknowledge}). Conception : {@code docs/space/01-vision.md} (Space Pilot),
 * {@code 02-architecture.md} par. 7 bis, {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule} : reglages sauvegardes et synchronises
 *     au proprietaire (HUD de navigation, lien vers le referentiel, vitesse de croisiere), modifies par
 *     {@code sendAction} et valides par le serveur ; compatible Config ({@code Configurable}) ; calcul client de la
 *     vitesse de croisiere (T34 : cible au regard de la camera, lu par un {@code Backend}, et vitesse cible
 *     {@code SpaceCruise}) ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotConfigWindow} : fenetre de Config (objet
 *     {@code PRIVATE}), trois interrupteurs {@code HoloWidgetSwitch} ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward} (cercle avant = zone morte,
 *     {@code Local} relatif sur l'axe avant, place par le module chaque tick client) et
 *     {@link com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim} (cercle de visee, {@code Local} follow au
 *     centre de l'ecran) : joystick de pilotage (migre du Space Controller en T19), objets {@code PRIVATE} dessines
 *     seulement pendant que le joueur local pilote (pad du Space Controller en capture) ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotHud} (T20) : HUD de navigation, fenetre
 *     {@code Hud} {@code PRIVATE} au coin haut gauche (vitesse et, avec la vitesse de croisiere, fleche et vitesse
 *     cible colorees, T34 ; corps de reference ; altitude au-dessus de lui ; cible de croisiere), deplacable par l'Own
 *     HUD Editor ({@code HoloEditTranslation}), rafraichie par le module depuis le miroir de l'Environment (vitesse
 *     chaque tick, le reste au plus 4 fois par seconde), affichee seulement pendant le pilotage et si le reglage
 *     {@code hud} est actif (T33) ; dessinee par le renderer generique des fenetres.</li>
 * </ul>
 * Sans ce module dans la memoire active, le Space Controller refuse le pilotage (D11,
 * {@code SpacePilotModule.of(ServerPlayer)}) ; Space Knowledge n'est pas requis. Les rendus (joystick, saut, lien et
 * anneau du referentiel : {@code client.render.modules.camera.spacepilot}) lisent le module du joueur local par
 * {@code client.modules.camera.spacepilot.SpacePilotClient}. Code commun : aucune classe client.
 */
package com.amynna.hololib.modules.camera.spacepilot;
