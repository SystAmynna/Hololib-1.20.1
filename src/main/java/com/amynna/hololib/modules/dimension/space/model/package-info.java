/**
 * Modele de donnees des modules Space : galaxie, systemes, corps, orbites, bornes. Code <b>commun et pur</b> : aucune
 * classe client, aucun acces au monde ni au serveur ; dependances limitees au NBT, a {@code FriendlyByteBuf} et a
 * {@code Component} (motifs de refus). Specification : {@code docs/space/03-modele-donnees.md}.
 * <p>
 * <b>Style retenu</b> : {@link com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy} est <b>mutable</b> (tenue
 * par le Space Environment seul, jamais exposee en ecriture) ; tous ses elements sont des records <b>immuables</b>
 * ({@link com.amynna.hololib.modules.dimension.space.model.SpaceSystem},
 * {@link com.amynna.hololib.modules.dimension.space.model.SpaceBody},
 * {@link com.amynna.hololib.modules.dimension.space.model.SpaceOrbit}, vues reseau {@code SystemHeader} et
 * {@code BackgroundStar}). Une modification remplace un element par une copie ({@code withXxx}) ; seules les
 * operations de la galaxie (doc 03 par. 6) le font, en renvoyant un
 * {@link com.amynna.hololib.modules.dimension.space.model.SpaceEditResult}. Elles n'incrementent pas la revision :
 * c'est le role de l'Environment.
 * <p>
 * Regles :
 * <ul>
 *     <li><b>Invariants par construction</b> : le constructeur de chaque record nettoie le nom, borne les nombres
 *     ({@link com.amynna.hololib.modules.dimension.space.model.SpaceLimits}) et leve sur une valeur non finie ou une
 *     structure invalide (parente, ordre, ids). Tout element existant est donc valide, et un serveur n'ecrit jamais
 *     rien d'invalide sur le reseau.</li>
 *     <li><b>Ids</b> jamais reutilises dans leur conteneur ({@code next} monotone, sauvegarde).</li>
 *     <li><b>Parent avant enfant</b> dans la liste des corps : une seule passe suffit pour les positions.</li>
 *     <li><b>NBT tolerant</b> ({@code save()} / {@code static load(CompoundTag)}) : cles manquantes = valeurs par
 *     defaut, valeurs hors bornes bornees, elements invalides ignores avec un log ; ne leve jamais au niveau de la
 *     galaxie. Cles courtes et stables : ne jamais les renommer sans migration.</li>
 *     <li><b>Reseau strict</b> ({@code write} / {@code static read}) : meme ordre de champs dans les deux sens,
 *     toute valeur invalide leve. Cote client, une exception dans {@code readSync} n'est pas journalisee par le mod :
 *     a l'appelant de la rattraper et de la logger s'il le souhaite. Les nombres qui influencent le mouvement
 *     (rayons, orbites) circulent en {@code double} pour que serveur et clients calculent les memes positions.</li>
 * </ul>
 */
package com.amynna.hololib.modules.dimension.space.model;
