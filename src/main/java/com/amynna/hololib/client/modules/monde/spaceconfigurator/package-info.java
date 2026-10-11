/**
 * Logique client du module {@code hololib:space_configurator} :
 * {@link com.amynna.hololib.client.modules.monde.spaceconfigurator.SpaceConfiguratorClient} installe le backend du
 * module (joueur local operateur, qui decide de l'affichage de la fenetre et des poignees) au setup client.
 * {@link com.amynna.hololib.client.modules.monde.spaceconfigurator.SpaceTextureImport} (T45) : bouton « Importer » ->
 * selecteur natif {@code TinyFileDialogs} sur un fil a part, image PNG / JPEG etiree en 2:1 (au plus 1024 x 512),
 * reencodee en PNG (au plus 2 Mio), envoyee par {@code SpaceTextureClient}, puis appliquee au corps par l'action 7. Le
 * dessin est dans {@code client.render.modules.monde.spaceconfigurator}. Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.monde.spaceconfigurator;
