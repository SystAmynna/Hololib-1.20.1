/**
 * Dessin du module {@code hololib:space_pilot} ({@code com.amynna.hololib.modules.camera.spacepilot}) :
 * <ul>
 *     <li>la surcouche du ciel
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpacePilotSkyRenderer}, T18), un
 *     {@code SkyRenderer} bas niveau (pas un objet holographique) qui repere les corps du systeme courant (anneaux,
 *     orbites), les etoiles de fond (double anneau), ecrit le nom de l'objet regarde et trace le lien pointille entre
 *     l'axe avant et le corps de reference (T19, reglage {@code refLink}, visible sans pilotage) ;</li>
 *     <li>le joystick de pilotage
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceJoystickRenderer}, migre du Space
 *     Controller en T19) : deux objets {@code Local} du module dessines par les ponts du mod, seulement pendant que le
 *     joueur local pilote ({@code shown()}) ; le segment entre les cercles n'est dessine que dans la famille vue
 *     (premiere personne) ;</li>
 *     <li>le saut interstellaire du pilote local
 *     ({@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpaceJumpRenderer}, T22) : un
 *     {@code ViewRenderer} (devant le terrain, projection du monde) qui repere l'etoile cible (double anneau en couleur
 *     de selection, des l'appui de la touche du saut), dessine la roue de charge autour d'elle pendant la charge, et
 *     indique en pale l'etoile qui serait choisie pendant le pilotage sans la touche (aide a la visee) ;</li>
 *     <li>le HUD de navigation ({@code SpacePilotHud}, T20) n'a pas de renderer ici : c'est une fenetre de
 *     {@code HoloLabel}, dessinee par le renderer generique des fenetres ({@code HoloWindowRenderer}, resolu par
 *     heritage dans {@code HoloObjectRenderers}).</li>
 * </ul>
 * Regles :
 * <ul>
 *     <li>seul le module du <b>joueur local</b> est lu ({@code SpacePilotClient.module()}) : un autre joueur ne voit
 *     jamais la surcouche ni le lien, meme en inspection (C45) ; rien sans Environment synchronise, sans module, ou si
 *     l'interrupteur general ({@code enabled}) est coupe ; chaque partie suit son reglage. Le joystick, le reperage
 *     de la cible du saut et la roue de charge ne dependent pas de {@code enabled} (C44) ; l'aide a la visee, si ;
 *     surcouche et saut sont coupes pendant la distorsion du saut, fondus compris ({@code SpaceWarpEffect.strength},
 *     T23) ;</li>
 *     <li>meme repere et meme temps que {@code SpaceSkyRenderer} ({@code pose(partialTick)},
 *     {@code bodyPositions}) ; tout est sur une sphere de 40 blocs, devant les corps (50 a 105 blocs) : la surcouche
 *     doit etre enregistree <b>apres</b> {@code SpaceSkyRenderer} ({@code ModuleRenderers}) ;</li>
 *     <li>tailles constantes a l'ecran, recalculees chaque frame depuis la projection et la hauteur de la fenetre ;</li>
 *     <li>aucune allocation par frame, {@code RenderType} local sans ecriture de profondeur pour la surcouche et le
 *     saut, {@code HoloRenderTypes} pour le joystick (aucun etat GL modifie a la main) ; un renderer ne s'abonne
 *     jamais a un evenement Forge.</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 6, 8 et 9, decisions D15, C41, C44, C46, C47, C48, cartes
 * {@code docs/space/taches/T18-pilot-surcouche.md}, {@code T19-pilot-joystick.md} et {@code T22-saut-pilotage.md}.
 * Enregistrement : {@link com.amynna.hololib.client.render.modules.camera.spacepilot.SpacePilotRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.camera.spacepilot;
