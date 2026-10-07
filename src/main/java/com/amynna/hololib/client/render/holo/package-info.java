/**
 * Pont entre les objets holographiques et les trois familles de renderers.
 * {@link com.amynna.hololib.client.render.holo.HoloRouting} repartit a chaque tick les objets connus selon leur
 * ancrage resolu pour le joueur local ; quatre ponts, enregistres une fois dans {@code HoloRenderers}, placent la pile
 * dans le repere local de chaque objet puis appellent son
 * {@link com.amynna.hololib.client.render.holo.HoloObjectRenderer} (registre par classe :
 * {@link com.amynna.hololib.client.render.holo.HoloObjectRenderers}).
 * Chaque pont place le repere d'origine de l'ancrage, puis applique la rotation et l'echelle du {@code HoloTransform}
 * ({@link com.amynna.hololib.client.render.holo.HoloPlacement}).
 * <ul>
 *     <li>Ciel : ancrage {@code Sky}, transform applique tel quel autour de l'oeil.</li>
 *     <li>Monde : {@code World} depuis l'origine de l'hote (joueur proprietaire, marker, origine du monde), et
 *     {@code Local} vu dans le monde (autre joueur, ou F5 : autour de l'oeil du joueur, selon son regard). Pleine
 *     lumiere.</li>
 *     <li>Vue : {@code Local} en premiere personne, sur une sphere autour de l'oeil (angles {@code x, y}, distance
 *     {@code z} ; orientee comme le monde en mode relatif, comme la camera en mode follow).</li>
 *     <li>HUD : {@code Hud}, a plat sur l'ecran, sous le HUD du jeu, trie par {@code z} ; premiere personne
 *     seulement.</li>
 * </ul>
 * Les fenetres sont dessinees par {@link com.amynna.hololib.client.render.holo.HoloWindowRenderer} via un
 * {@code HoloCanvas} sur {@code MultiBufferSource}.
 * <p>
 * Le routage filtre aussi selon l'affichage ({@code HoloDisplay}) et le mode focus du proprietaire de la memoire ; en
 * session d'edition, les objets editables sont reveles et les objets HUD peuvent quitter le HUD pour
 * {@link com.amynna.hololib.client.render.holo.HoloRouting#hudPreview()}, dessines en miniature par
 * {@link com.amynna.hololib.client.render.holo.HoloHudPreview}. Les overlays clients
 * ({@link com.amynna.hololib.client.render.holo.HoloAttachment}, boutons d'edition) sont routes dans la famille de
 * leur hote mais ont leur propre placement : a cote de sa silhouette vue du point de vue, face a celui-ci a l'amorce
 * de l'edition, puis fige (seule la translation de l'hote est suivie).
 * <p>
 * Exception a la regle "que des renderers" : {@link com.amynna.hololib.client.render.holo.HoloPicking} (objet sous le
 * reticule, pour {@code client.interact}) vit ici parce qu'il reconstruit les memes placements que les ponts ; il ne
 * dessine rien et n'est jamais appele pendant le rendu.
 */
package com.amynna.hololib.client.render.holo;
