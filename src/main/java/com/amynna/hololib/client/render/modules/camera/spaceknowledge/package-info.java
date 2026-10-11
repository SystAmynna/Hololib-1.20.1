/**
 * Dessin du module {@code hololib:space_knowledge} ({@code com.amynna.hololib.modules.camera.spaceknowledge}, T33) :
 * la surcouche du ciel
 * ({@link com.amynna.hololib.client.render.modules.camera.spaceknowledge.SpaceKnowledgeSkyRenderer}, migree de Space
 * Pilot), un {@code SkyRenderer} bas niveau (pas un objet holographique) qui repere les corps du systeme courant
 * (anneaux, orbites), les etoiles de fond (double anneau), ecrit le nom de l'objet regarde et, dessous, sa distance
 * (surface d'un corps, annees-lumiere pour une etoile de fond).
 * <p>
 * Regles :
 * <ul>
 *     <li>seul le module du <b>joueur local</b> est lu ({@code SpaceKnowledgeClient.module()}) : un autre joueur ne
 *     voit jamais la surcouche, meme en inspection (C45) ; rien sans Environment synchronise, sans module, ou si
 *     l'interrupteur general ({@code enabled}, Quick Action) est coupe ; chaque partie suit son reglage ; coupee
 *     pendant la distorsion du saut, flashs compris ({@code SpaceWarpTimeline.active}) ; independante du pilotage et
 *     de Space Pilot ;</li>
 *     <li>pas de lien vers le referentiel ici : il appartient a Space Pilot et n'est dessine que pendant le pilotage
 *     ({@code client.render.modules.camera.spacepilot.SpaceReferenceLinkRenderer}, C75) ; l'anneau du referentiel
 *     qu'il dessine a le meme rayon et la meme couleur que celui de la surcouche ({@code SpaceOverlay}) ;</li>
 *     <li>meme repere et meme temps que {@code SpaceSkyRenderer} ({@code pose(partialTick)},
 *     {@code bodyPositions}) ; tout est sur une sphere de 40 blocs, devant les corps (50 a 105 blocs) : la surcouche
 *     doit etre enregistree <b>apres</b> {@code SpaceSkyRenderer} ({@code ModuleRenderers}) ;</li>
 *     <li>tailles constantes a l'ecran, recalculees chaque frame depuis la projection et la hauteur de la fenetre
 *     ({@code SpaceOverlay.pixelTan}) ;</li>
 *     <li>aucune allocation par frame (texte de distance reconstruit au changement de sa valeur arrondie),
 *     {@code RenderType} partage sans ecriture de profondeur ({@code SpaceOverlay.quads()}), aucun etat GL modifie a
 *     la main ; un renderer ne s'abonne jamais a un evenement Forge.</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 9, decisions D15, D18, D19, C46, C47, C75, D30 (lunes
 * reperees de pres, T40), cartes {@code docs/space/taches/T18-pilot-surcouche.md} et {@code T33-space-knowledge.md}.
 * Enregistrement :
 * {@link com.amynna.hololib.client.render.modules.camera.spaceknowledge.SpaceKnowledgeRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.camera.spaceknowledge;
