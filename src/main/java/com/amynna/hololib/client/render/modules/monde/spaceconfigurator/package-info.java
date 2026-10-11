/**
 * Dessin du module {@code hololib:space_configurator} ({@code com.amynna.hololib.modules.monde.spaceconfigurator}).
 * La fenetre est dessinee par
 * {@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceConfiguratorWindowRenderer} (T44 :
 * renderer generique des fenetres, puis apercu de la texture du corps selectionne, onglet « Texture ») ; le
 * Configurator n'a plus de cube (T42 : il se branche sur
 * celui de la Space Map du meme marker, dessine par {@code client.render.modules.monde.spacemap}). Ici : la poignee de
 * l'etoile selectionnee (T11,
 * {@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceHandleRenderer} : petit marqueur,
 * orange si le systeme est hors du cadrage) et la poignee d'orbite (T12,
 * {@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceOrbitHandleRenderer} : l'ellipse de
 * l'orbite selectionnee, parent au foyer, decalee du deplacement du parent entre le temps fige de la poignee et le
 * temps vivant de la Map). Voir {@code docs/space/04-mathematiques.md} par. 7.
 * <p>
 * Regles : aucune allocation par frame ; seulement des {@code RenderType}, aucun etat GL modifie. Enregistrement :
 * {@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceConfiguratorRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;
