/**
 * Dessin du module {@code hololib:space_configurator} ({@code com.amynna.hololib.modules.monde.spaceconfigurator}).
 * La fenetre utilise le renderer generique des fenetres ; le cube a son renderer
 * ({@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceMapCubeRenderer}), qui dessine tout
 * son contenu dans le repere local du cube ({@code [-1, 1]^3}, translation, orientation et echelle deja appliquees par
 * le pont monde) ; la poignee de l'etoile selectionnee (T11) a le sien
 * ({@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceHandleRenderer} : petit marqueur,
 * orange si le systeme est hors du cadrage) ; la poignee d'orbite (T12) aussi
 * ({@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceOrbitHandleRenderer} : l'ellipse de
 * l'orbite selectionnee, parent au foyer).
 * <p>
 * Etat actuel : mode galaxie (T10 : aretes, faces teintees, graduations en al, etoiles billboard, anneau du systeme
 * courant, surbrillance de la selection, noms) et mode systeme (T12 : graduations en UA, orbites en rubans, corps aux
 * positions de Kepler du temps d'affichage, etoiles avec halo, selection, noms). Voir
 * {@code docs/space/04-mathematiques.md} par. 7 et {@code docs/space/taches/}.
 * <p>
 * Regles : le renderer lit la copie synchronisee du module et ne fait que la mise en page ; ce qui ne change qu'a la
 * synchronisation est mis en cache par module ; aucune allocation par frame ; seulement des {@code RenderType}
 * ({@code HoloRenderTypes}, plus un type local sans ecriture de profondeur pour les faces et les halos), aucun etat GL
 * modifie. Textes billboard en famille {@code WORLD} seulement, a portee de lecture. Enregistrement :
 * {@link com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceConfiguratorRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;
