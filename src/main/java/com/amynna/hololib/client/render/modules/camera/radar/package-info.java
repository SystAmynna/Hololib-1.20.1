/**
 * Rendu du module {@code hololib:radar}, pour le joueur local seulement (etat lu dans
 * {@code client.modules.camera.radar.RadarClient}).
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.modules.camera.radar.RadarRenderer} : {@code WorldRenderer} a une
 *     instance (origine de l'onde) : copie de la profondeur, masque des entites reperees, composition plein ecran
 *     (trait de l'onde au rayon sur la scene, interieur bleu a travers les blocs, contour bleu facon glowing), sphere
 *     en triangles dont les segments s'amincissent avec le rayon ;</li>
 *     <li>masque des entites : utilitaire partage {@code client.render.outline.HoloOutlineMask} (cibles hors ecran,
 *     types de contour vanilla rediriges vers la cible du masque) ;</li>
 *     <li>{@link com.amynna.hololib.client.render.modules.camera.radar.RadarSphere} : icosphere precalculee ;</li>
 *     <li>{@link com.amynna.hololib.client.render.modules.camera.radar.RadarShaders} : shader de composition
 *     {@code hololib:radar_composite} ({@code assets/hololib/shaders/core/}), charge par {@code RegisterShadersEvent}
 *     (premier shader custom du mod).</li>
 * </ul>
 * Sans Mixin ni glowing vanilla : le glowing ne peut etre force cote client (drapeau synchronise par le serveur,
 * couleur de l'equipe), d'ou le masque hors ecran et le contour calcule par le shader.
 * <p>
 * Limites : l'onde colore aussi les entites (la profondeur a ce stage les contient ; s'en tenir aux blocs demanderait
 * un stage {@code AFTER_CUTOUT_BLOCKS} dans {@code RenderDispatcher}) ; avec un shaderpack, la composition passe sur la
 * main (comme les {@code ViewRenderer} dans ce mode) et la concordance des profondeurs reste a verifier ; les entites
 * reperees sont celles connues du client (portee de suivi).
 */
package com.amynna.hololib.client.render.modules.camera.radar;
