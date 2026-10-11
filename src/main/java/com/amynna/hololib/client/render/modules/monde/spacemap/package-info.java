/**
 * Dessin de la Space Map ({@code com.amynna.hololib.modules.monde.spacemap}, T37). La fenetre de navigation utilise le
 * renderer generique des fenetres ; le cube a son renderer
 * ({@link com.amynna.hololib.client.render.modules.monde.spacemap.SpaceMapCubeRenderer}, deplace du Configurator), qui
 * dessine tout son contenu dans le repere local du cube d'apres la {@code SpaceMapView} de son module : carte
 * galactique (graduations en al, etoiles, systeme courant en vert, selection), vue systeme (graduations en UA, orbites
 * entieres ou arcs locaux, corps textures et tournant sur eux-memes avec le cache partage
 * {@code SpaceBodyTextureCache}, vaisseau), croix de la selection et coordonnees. Toutes les tailles suivent l'echelle
 * du cube. L'orbite editee par la poignee du Configurator du meme marker est omise (elle la dessine, T42).
 * <p>
 * Regles : le renderer lit la vue et ne fait que la mise en page ; ce qui ne change qu'a la synchronisation est mis en
 * cache par vue ; aucune allocation par frame ; seulement des {@code RenderType} ({@code HoloRenderTypes}, un type
 * local sans ecriture de profondeur pour les faces et les halos, ceux des textures d'astres), aucun etat GL modifie.
 * Textes billboard en famille {@code WORLD} seulement, a portee de lecture. Enregistrement :
 * {@link com.amynna.hololib.client.render.modules.monde.spacemap.SpaceMapRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.monde.spacemap;
