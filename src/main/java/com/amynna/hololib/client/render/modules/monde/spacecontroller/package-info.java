/**
 * Dessin du module {@code hololib:space_controller} ({@code com.amynna.hololib.modules.monde.spacecontroller}) : la
 * mini-interface du joystick
 * ({@link com.amynna.hololib.client.render.modules.monde.spacecontroller.SpaceJoystickRenderer}), deux objets
 * {@code Local} dessines par les ponts du mod (pas de {@code ViewRenderer} bas niveau). La fenetre de controle utilise
 * le renderer generique des fenetres.
 * <p>
 * Regles :
 * <ul>
 *     <li>le renderer lit l'etat (pad, objet) et dessine, sans logique : placement du cercle avant par le module
 *     ({@code clientTick}), affichage decide par {@code shown()} (pilote local du poste) ;</li>
 *     <li>le segment entre les cercles n'est dessine que dans la famille vue (premiere personne) : en F5 les objets
 *     passent dans le monde autour de l'oeil (routage) et seuls les anneaux restent ;</li>
 *     <li>aucune allocation par frame, seulement {@code HoloRenderTypes} (aucun etat GL modifie).</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 6, carte {@code docs/space/taches/T08-joystick.md}.
 * Enregistrement : {@link com.amynna.hololib.client.render.modules.monde.spacecontroller.SpaceControllerRenderers},
 * appele par {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.monde.spacecontroller;
