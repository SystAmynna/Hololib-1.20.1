/**
 * Dessin du module {@code hololib:space_pilot} ({@code com.amynna.hololib.modules.camera.spacepilot}), tout pour le
 * <b>pilote local</b> et seulement pendant le pilotage (pad du Space Controller en capture,
 * {@code SpacePilotPad.localPilot()}) ; la surcouche du ciel est passee a Space Knowledge en T33
 * ({@code client.render.modules.camera.spaceknowledge}, C75) :
 * <ul>
 *     <li>le joystick de pilotage
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceJoystickRenderer}, migre du Space
 *     Controller en T19) : deux objets {@code Local} du module dessines par les ponts du mod (projection
 *     {@code STABLE} du pont vue), seulement pendant que le joueur local pilote ({@code shown()}) ; le segment entre
 *     les cercles n'est dessine que dans la famille vue (premiere personne) ;</li>
 *     <li>le saut interstellaire
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceJumpRenderer}, T22) : un
 *     {@code ViewRenderer} (devant le terrain, projection du monde) qui repere l'etoile cible (double anneau en couleur
 *     de selection, des l'appui de la touche du saut), dessine la roue de charge autour d'elle pendant la charge, et
 *     indique en pale l'etoile qui serait choisie pendant le pilotage sans la touche (aide a la visee) ;</li>
 *     <li>le lien et l'anneau du referentiel
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceReferenceLinkRenderer}, T33) : un
 *     {@code ViewRenderer} (devant le terrain, projection du monde) qui trace le lien pointille du bord exterieur du
 *     cercle avant (ramene de la projection {@code STABLE} du joystick a la projection {@code WORLD}) au bord exterieur
 *     de l'anneau du corps de reference, sans ecart, et dessine cet anneau (meme rayon et meme couleur que celui de
 *     Space Knowledge : {@code SpaceOverlay}) ; reglage {@code refLink} ;</li>
 *     <li>la cible du vaisseau
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceTargetRenderer}, T43, D33) : deux
 *     {@code ViewRenderer} (meme classe) : la fleche verte sur le bord exterieur du cercle avant, tournee vers la cible
 *     (enregistree avant le lien du referentiel, qui passe dessus), et l'anneau vert autour de l'astre cible ou de
 *     l'etoile de fond de son systeme (enregistre apres le lien : il recouvre l'anneau du referentiel) ; cible lue dans
 *     l'Environment, posee depuis la Space Map ;</li>
 *     <li>le HUD de navigation ({@code SpacePilotHud}, T20) n'a pas de renderer ici : c'est une fenetre de
 *     {@code HoloLabel}, dessinee par le renderer generique des fenetres ({@code HoloWindowRenderer}, resolu par
 *     heritage dans {@code HoloObjectRenderers}), affichee seulement pendant le pilotage (T33).</li>
 * </ul>
 * Regles :
 * <ul>
 *     <li>seul le module du <b>joueur local</b> est lu ({@code SpacePilotClient.module()}) : un autre joueur ne voit
 *     jamais ces rendus, meme en inspection (C45) ; rien sans Environment synchronise ou sans module ; saut, lien,
 *     anneaux et fleche sont coupes pendant le voyage et la distorsion du saut, flash d'arrivee compris
 *     ({@code SpaceWarpTimeline.active}) ; rien ne depend de Space Knowledge ;</li>
 *     <li>meme repere et meme temps que {@code SpaceSkyRenderer} ({@code pose(partialTick)}, {@code bodyPositions}) ;
 *     directions posees sur une sphere de 1,5 bloc, devant les cercles du joystick (2 blocs) ;</li>
 *     <li>tailles constantes a l'ecran ({@code SpaceOverlay.pixelTan}, projection du renderer) ;</li>
 *     <li>aucune allocation par frame, {@code RenderType} partage sans ecriture de profondeur
 *     ({@code SpaceOverlay.quads()}) pour le saut, le lien, les anneaux et la fleche, {@code HoloRenderTypes} pour le joystick
 *     (aucun etat GL modifie a la main) ; un renderer ne s'abonne jamais a un evenement Forge.</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 6, 8 et 9, decisions C41, C48, C61, C62, C75, C98, cartes
 * {@code docs/space/taches/T19-pilot-joystick.md}, {@code T22-saut-pilotage.md} et {@code T33-space-knowledge.md}.
 * Enregistrement : {@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpacePilotRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.camera.spacepilot;
