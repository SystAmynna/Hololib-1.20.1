/**
 * Module {@code hololib:space_pilot} (memoire de joueur) : affichage personnel du pilote dans une dimension munie
 * d'un Space Environment ({@code modules.dimension.space}), pour le joueur local seulement (jamais vu par un autre
 * joueur, meme en inspection : C45). Conception : {@code docs/space/01-vision.md} (Space Pilot),
 * {@code 02-architecture.md} par. 7 bis, {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule} : reglages d'affichage sauvegardes
 *     et synchronises au proprietaire (interrupteur general, orbites, reperage des corps, etoiles exterieures, nom au
 *     regard et son angle, HUD de navigation, lien vers le referentiel), modifies par {@code sendAction} et valides
 *     par le serveur ; compatible Config ({@code Configurable}) et Quick Action ({@code QuickActionProvider} :
 *     action {@code toggle} qui coupe ou rallume tout le rendu, sauf le joystick, C44) ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotConfigWindow} : fenetre de Config (objet
 *     {@code PRIVATE}), interrupteurs {@code HoloWidgetSwitch} et barre de l'angle en degres ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward} (cercle avant = zone morte,
 *     {@code Local} relatif sur l'axe avant, place par le module chaque tick client) et
 *     {@link com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim} (cercle de visee, {@code Local} follow au
 *     centre de l'ecran) : joystick de pilotage (migre du Space Controller en T19), objets {@code PRIVATE} dessines
 *     seulement pendant que le joueur local pilote (pad du Space Controller en capture), pas coupes par
 *     l'interrupteur general (C44) ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.spacepilot.SpacePilotHud} (T20) : HUD de navigation, fenetre
 *     {@code Hud} {@code PRIVATE} au coin haut gauche (vitesse, corps de reference, altitude au-dessus de lui),
 *     deplacable par l'Own HUD Editor ({@code HoloEditTranslation}), rafraichie par le module au plus 4 fois par
 *     seconde depuis le miroir de l'Environment, coupee par l'interrupteur general et le reglage {@code hud} ; dessinee
 *     par le renderer generique des fenetres.</li>
 * </ul>
 * Sans ce module dans la memoire active, le Space Controller refuse le pilotage (D11,
 * {@code SpacePilotModule.of(ServerPlayer)}). Les rendus (surcouche du ciel et lien vers le referentiel, joystick,
 * HUD : T18 a T20) lisent le module du joueur local par {@code client.modules.camera.spacepilot.SpacePilotClient}.
 * Code commun : aucune classe client.
 */
package com.amynna.hololib.modules.camera.spacepilot;
