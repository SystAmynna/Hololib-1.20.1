/**
 * Tous les renderers du mod (client uniquement). Un renderer ne fait que <b>lire un etat et dessiner</b> : la logique
 * (interaction, regard, animations d'etat) vit ailleurs.
 *
 * <h2>Quel renderer pour quel besoin</h2>
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.sky.SkyRenderer} : objet dans le ciel (skybox), dessine juste apres
 *     le ciel vanilla : <b>tout le reste du monde passe devant</b>.</li>
 *     <li>{@link com.amynna.hololib.client.render.view.ViewRenderer} : dessin dans la vue du joueur, base sur la camera
 *     de rendu, <b>par-dessus le monde</b> mais sous la main et le GUI.</li>
 *     <li>{@link com.amynna.hololib.client.render.world.WorldRenderer} : objet ancre dans le monde, dessine comme une
 *     entite : <b>cache par les blocs et les entites</b> places devant.</li>
 *     <li>{@link com.amynna.hololib.client.render.hud.HudRenderer} : a l'ecran, en orthographique, comme un HUD,
 *     <b>sous tous les elements du HUD vanilla</b> (overlay Forge enregistre sous tous les autres).</li>
 *     <li>Objet qui existe cote serveur (synchronisation, collisions) : une vraie {@code Entity} et son
 *     {@code EntityRenderer} ({@code EntityRenderersEvent.RegisterRenderers}), pas un {@code WorldRenderer}.</li>
 *     <li>Objet holographique ({@code HoloObject}, fenetre...) : pas de renderer a ecrire pour une famille, un
 *     {@code HoloObjectRenderer} suffit ; les ponts de {@code holo} le dessinent dans la famille de son ancrage.</li>
 * </ul>
 *
 * <h2>Ordre de rendu d'une frame (Forge 1.20.1)</h2>
 * {@code GameRenderer.renderLevel} appelle {@code LevelRenderer.renderLevel}, qui emet les stages de
 * {@code RenderLevelStageEvent} :
 * <ol>
 *     <li><b>{@code AFTER_SKY}</b> : ciel dessine sans ecrire la profondeur. Le PoseStack de l'evenement contient la
 *     rotation de la camera (roll compris) ; elle est capturee ici par
 *     {@link com.amynna.hololib.client.render.RenderDispatcher}. On y passe les {@code SkyRenderer}, puis la
 *     profondeur est videe.</li>
 *     <li>{@code AFTER_SOLID_BLOCKS}, {@code AFTER_CUTOUT_*} : terrain opaque.</li>
 *     <li><b>{@code AFTER_ENTITIES}</b> : entites dessinees, profondeur = terrain opaque + entites. Le
 *     {@code BufferSource} du jeu est encore ouvert et sera vide par vanilla avec les batches d'entites : on y passe
 *     les {@code WorldRenderer}.</li>
 *     <li>{@code AFTER_BLOCK_ENTITIES}, {@code AFTER_TRANSLUCENT_BLOCKS}, {@code AFTER_TRIPWIRE_BLOCKS},
 *     {@code AFTER_PARTICLES} : en graphismes Fabulous, ces stages ecrivent dans des cibles intermediaires.</li>
 *     <li>{@code AFTER_WEATHER} : en Fabulous la cible liee est {@code WEATHER_TARGET}, recomposee ensuite ; ne pas y
 *     dessiner d'overlay.</li>
 *     <li><b>{@code AFTER_LEVEL}</b> (emis par {@code GameRenderer}) : monde entierement compose dans la cible principale.
 *     Attention : Forge y passe la pile de <i>projection</i> comme PoseStack, pas la rotation camera. On y passe les
 *     {@code ViewRenderer}.</li>
 *     <li>Vidage de la profondeur, puis main du joueur.</li>
 *     <li><b>GUI</b> (masque en F1) : notre overlay, enregistre sous tous les overlays vanilla, passe les
 *     {@code HudRenderer} puis vide la profondeur ; le HUD vanilla est dessine ensuite, toujours devant.</li>
 * </ol>
 *
 * <h2>Regles</h2>
 * <ul>
 *     <li>Un seul abonne aux evenements de rendu : {@code RenderDispatcher}. Les renderers sont enregistres dans
 *     {@link com.amynna.hololib.client.render.HoloRenderers}, jamais abonnes eux-memes.</li>
 *     <li>Dessiner via {@code MultiBufferSource} et des {@code RenderType} (vanilla ou
 *     {@link com.amynna.hololib.client.render.type.HoloRenderTypes}), pas d'OpenGL brut : {@code GlStateManager} met
 *     l'etat GL en cache (un appel direct le desynchronise), les shaderpacks (Oculus) remplacent les shaders des
 *     {@code RenderType}, et Minecraft 1.21.5+ remplace OpenGL direct par {@code GpuDevice}. Le gain de performance
 *     vient du regroupement par {@code RenderType}, du culling et de {@code VertexBuffer} pour la geometrie statique.</li>
 *     <li>Pas d'allocation par frame dans les chemins de rendu ; tout etat GL modifie est restaure.</li>
 * </ul>
 */
package com.amynna.hololib.client.render;
