/**
 * Tous les renderers du mod (client uniquement). Un renderer ne fait que <b>lire un etat et dessiner</b> : la logique
 * (interaction, regard, animations d'etat) vit ailleurs.
 *
 * <h2>Quel renderer pour quel besoin</h2>
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.sky.SkyRenderer} : objet dans le ciel (skybox), dessine juste apres
 *     le ciel vanilla : <b>tout le reste du monde passe devant</b>. Peut aussi fixer la couleur du brouillard (horizon,
 *     terrain lointain, fond de l'ecran).</li>
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
 * <h2>Ordre de rendu d'une frame (Forge 1.20.1, sans shaderpack)</h2>
 * {@code GameRenderer.renderLevel} appelle {@code LevelRenderer.renderLevel}, qui emet les stages de
 * {@code RenderLevelStageEvent} (Embeddium reecrit {@code renderChunkLayer} mais emet les memes stages) :
 * <ol>
 *     <li><b>Couleur du brouillard</b> ({@code ViewportEvent.ComputeFogColor}, poste par {@code FogRenderer.setupColor}
 *     au debut de {@code LevelRenderer.renderLevel}) : couleur du fond de l'ecran et du brouillard du terrain.
 *     {@link com.amynna.hololib.client.render.RenderDispatcher} la relaie aux {@code SkyRenderer}
 *     ({@code fogColor}, ordre d'enregistrement) ; inchangee si aucun ne la modifie.</li>
 *     <li><b>{@code AFTER_SKY}</b> : ciel dessine sans ecrire la profondeur. Le PoseStack de l'evenement contient la
 *     rotation de la camera (roll compris) ; elle est capturee ici par
 *     {@link com.amynna.hololib.client.render.RenderDispatcher}. On y passe les {@code SkyRenderer}, puis la
 *     profondeur est videe.</li>
 *     <li>{@code AFTER_SOLID_BLOCKS}, {@code AFTER_CUTOUT_*} : terrain opaque.</li>
 *     <li>{@code AFTER_ENTITIES}, {@code AFTER_BLOCK_ENTITIES} : <b>pas fiables</b> pour dessiner apres les entites.
 *     Oculus (meme sans shaderpack) remplace {@code RenderBuffers.bufferSource()} pendant tout le niveau par un tampon
 *     qui ignore {@code endBatch(RenderType)} et ne dessine entites et block entities qu'au profiler
 *     {@code "translucent"}, dans un ordre de {@code RenderType} recalcule.</li>
 *     <li><b>{@code AFTER_TRANSLUCENT_BLOCKS}</b> : entites, block entities et eau dessinees. Emis entre
 *     {@code setupRenderState} et {@code clearRenderState} du type translucide : en Fabulous, la cible translucide est
 *     liee. On y passe les {@code WorldRenderer}, dans la cible principale.</li>
 *     <li>{@code AFTER_PARTICLES}, {@code AFTER_WEATHER} : en Fabulous, cibles intermediaires ({@code PARTICLES_TARGET},
 *     {@code WEATHER_TARGET}) recomposees ensuite ; ne pas y dessiner d'overlay.</li>
 *     <li><b>{@code AFTER_LEVEL}</b> (emis par {@code GameRenderer}) : monde entierement compose dans la cible principale.
 *     Attention : Forge y passe la pile de <i>projection</i> comme PoseStack, pas la rotation camera. On y passe les
 *     {@code ViewRenderer}.</li>
 *     <li>Vidage de la profondeur, puis main du joueur.</li>
 *     <li><b>GUI</b> (masque en F1) : notre overlay, enregistre sous tous les overlays vanilla, passe les
 *     {@code HudRenderer} puis vide la profondeur ; le HUD vanilla est dessine ensuite, toujours devant.</li>
 * </ol>
 *
 * <h2>Avec un shaderpack (Oculus)</h2>
 * Detecte par {@link com.amynna.hololib.client.render.ShaderPacks}, fige par frame a {@code AFTER_SKY}.
 * <ol>
 *     <li>Apres le clear : Oculus lie ses gbuffers (la profondeur reste celle de la cible principale), puis passe
 *     d'ombres (aucun stage Forge) et ciel du pack.</li>
 *     <li>Pendant tout le niveau, un shader vanilla que le pack ne remplace pas (dont {@code position_color_lightmap}
 *     de {@code HoloRenderTypes}) est applique avec couleur et profondeur masquees : il ne dessine rien. Le ciel est
 *     recalcule par le pack sur les pixels de profondeur 1. Le mod ne dessine donc rien entre {@code AFTER_SKY} et
 *     {@code AFTER_LEVEL}.</li>
 *     <li>Au profiler {@code "translucent"} : main solide, entites, passes {@code deferred}. A la fin de
 *     {@code LevelRenderer.renderLevel} : main translucide, passes {@code composite} et {@code final} vers la cible
 *     principale (profondeur de la scene conservee, main comprise).</li>
 *     <li><b>{@code AFTER_LEVEL}</b> : shaders vanilla de nouveau actifs. Dans l'ordre : {@code SkyRenderer} dans une
 *     cible hors ecran composee sur les pixels de ciel (profondeur 1), {@code WorldRenderer} testes contre la
 *     profondeur de la scene, puis {@code ViewRenderer} (au-dessus de la main, dessinee par Oculus avant). Rien n'est
 *     eclaire ni embrume par le pack.</li>
 *     <li>Bobbing, secousse de degats et nausee : deplaces par Oculus de la projection vers le model-view ;
 *     {@code RenderDispatcher} reconstruit la rotation camera et remet le bobbing dans la projection du monde.</li>
 * </ol>
 *
 * <h2>Regles</h2>
 * <ul>
 *     <li>Un seul abonne aux evenements de rendu : {@code RenderDispatcher}. Les renderers sont enregistres dans
 *     {@link com.amynna.hololib.client.render.HoloRenderers}, jamais abonnes eux-memes.</li>
 *     <li>Chaque passe dessine dans {@link com.amynna.hololib.client.render.HoloBuffers} (mode immediat : l'ordre
 *     d'emission est l'ordre de dessin), jamais dans {@code RenderBuffers.bufferSource()}, detourne par Oculus.</li>
 *     <li>Dessiner via {@code MultiBufferSource} et des {@code RenderType} (vanilla ou
 *     {@link com.amynna.hololib.client.render.type.HoloRenderTypes}), pas d'OpenGL brut : {@code GlStateManager} met
 *     l'etat GL en cache (un appel direct le desynchronise), les shaderpacks (Oculus) remplacent les shaders des
 *     {@code RenderType}, et Minecraft 1.21.5+ remplace OpenGL direct par {@code GpuDevice}. Le gain de performance
 *     vient du regroupement par {@code RenderType}, du culling et de {@code VertexBuffer} pour la geometrie statique.</li>
 *     <li>Pas d'allocation par frame dans les chemins de rendu ; tout etat GL modifie est restaure.</li>
 * </ul>
 */
package com.amynna.hololib.client.render;
