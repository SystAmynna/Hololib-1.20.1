/**
 * Vues secondaires du niveau : le monde rendu depuis une autre camera dans une texture (camera de surveillance), avec
 * les reglages graphiques du joueur local, shaderpack compris. Sans Mixin. API : {@link
 * com.amynna.hololib.client.render.levelview.HoloLevelViews#create()}, {@link
 * com.amynna.hololib.client.render.levelview.HoloLevelView}. Generique : aucune reference aux modules.
 *
 * <h2>Quand</h2>
 * Au debut de la frame ({@code RenderTickEvent} START, priorite {@code LOWEST}, par {@code RenderDispatcher}), avant le
 * rendu principal qui re-installe ensuite camera, statiques des passes et cible. Une vue coute une scene complete : elle
 * est rationnee ({@code HoloLevelViews}) :
 * <ul>
 *     <li>une vue au plus par frame, jamais deux frames de suite ;</li>
 *     <li>seulement si son image a ete dessinee dans le rendu principal depuis peu ({@code markSeen(pixels)}, ignore
 *     pendant une vue : un ecran qui se filme ne se garde pas actif), sur au moins 12 px de haut ; cadence reduite
 *     d'autant que l'image est petite a l'ecran ;</li>
 *     <li>budget : 20 % du temps reel en moyenne (seau a jetons), au-dela les vues attendent ;</li>
 *     <li><b>distance de vue</b> ({@code setViewDistance}, premier levier) : pendant la vue, {@code ComputeFov} abaisse
 *     {@code GameRenderer.renderDistance} (plan lointain = max(distance, 112) blocs : sections, block entities et
 *     entites au-dela non dessinees ; soleil, lune et etoiles gardes) et {@code RenderFog} recalcule le brouillard du
 *     terrain et du ciel a la distance, comme vanilla (avec un shaderpack : distance = plan lointain / 4, brouillard du
 *     pack).</li>
 * </ul>
 * Mesures (rendus par seconde, temps d'une vue et d'une frame, part des vues) dans l'ecran F3 ; section de profiler
 * {@code hololib_level_view} ; {@code /hololib_views pause|resume} fige les vues (diagnostic).
 *
 * <h2>Comment ({@code LevelViewRenderer})</h2>
 * {@code GameRenderer.renderLevel} avec, le temps de la vue (tout restaure dans un {@code finally}) : entite camera
 * ({@code Minecraft.cameraEntity}, affecte directement : {@code setCameraEntity} toucherait aux post-effets) = l'entite
 * suivie ou un {@code Marker} client jamais ajoute au niveau ; camera en premiere personne ; hauteur des yeux de la
 * camera principale (lissee par vanilla) = celle de l'entite (0 pour le marker) ; ni main ni contour du bloc vise ;
 * {@code hitResult} / {@code crosshairPickEntity} sauvegardes ({@code renderLevel} appelle {@code pick}). FOV, et
 * lacet / tangage d'une camera fixe, imposes par {@code RenderDispatcher} ({@code ComputeFov},
 * {@code ComputeCameraAngles}) ; son roulis est pose directement sur la pile passee a {@code renderLevel} (un autre
 * mod peut reecrire celui de l'evenement apres nous). Un joueur suivi ({@code setPlayer}) est retrouve par son UUID a
 * chaque rendu : le signal revient de lui-meme quand le client recree son entite.
 * Passes du mod : ciel et monde oui, vue non ({@code HoloLevelViews.current()} non nul ; un renderer d'effet personnel
 * au joueur local s'abstient).
 * <ul>
 *     <li><b>Chemin reduit</b> (defaut) : {@code Minecraft.mainRenderTarget} (final, reflexion) remplace par une cible
 *     a la resolution de la vue, elargie sur un axe a l'aspect de la fenetre (la projection vanilla prend l'aspect de
 *     la fenetre ; FOV vertical agrandi si l'image est plus large), recadree par les UV. Deux cibles en ping-pong :
 *     l'image affichee n'est jamais celle en cours de rendu (un ecran qui se filme montre l'image precedente). Le cout
 *     suit la resolution. Les post-effets des entites (glowing) remettent le viewport a la taille de la fenetre :
 *     retabli a {@code AFTER_BLOCK_ENTITIES}.</li>
 *     <li><b>Chemin fenetre</b> (Fabulous, dont la chaine de transparence est liee a la vraie cible principale) :
 *     rendu dans la cible principale a la taille de la
 *     fenetre, puis copie recadree / reduite ; le rendu principal vide ensuite la cible. La resolution ne reduit alors
 *     que la texture.</li>
 * </ul>
 * Oculus redimensionne toutes ses cibles des que la taille (ou l'id de profondeur) de la cible principale change
 * ({@code IrisRenderingPipeline.beginLevelRendering}) : le chemin reduit avec un shaderpack coute deux reallocations par
 * vue ; valide en jeu (image correcte, vue principale intacte), il reste le chemin utilise avec un shaderpack.
 *
 * <h2>Chunks visibles et travail induit</h2>
 * Avec Embeddium, la liste est recalculee par camera (parcours du graphe dans la vue, puis dans la frame principale
 * suivante si la camera differe : ses numeros de frame servent aussi au culling des entites) ; {@code LevelViewSodium}
 * empeche la vue de reprogrammer le tri des translucides (ping-pong entre les deux cameras). Sans Embeddium, mode
 * degrade ({@code LevelViewCulling}) : la vue reutilise le graphe de visibilite du joueur, filtre par son frustum, sans
 * jamais planifier de mise a jour (calculee en tache de fond pour une seule camera), et ne retrie aucune section
 * translucide ; une piece que le joueur ne voit pas manque a l'image. Les translucides de la vue restent tries pour le
 * joueur.
 *
 * <h2>Limites</h2>
 * Seuls les chunks charges par le client local ; une entite camera doit etre dans le niveau client ; les objets de la
 * famille vue du joueur local (routes au tick) et les contours glowing (composes hors {@code renderLevel}) n'y sont
 * pas ; la nausee du joueur local deforme aussi la vue.
 */
package com.amynna.hololib.client.render.levelview;
