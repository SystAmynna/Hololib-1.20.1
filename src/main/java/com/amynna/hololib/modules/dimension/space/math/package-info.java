/**
 * Calculs purs et deterministes des modules Space (T02) : unites, orbites de Kepler, pose du monde, pas de mouvement,
 * entree du pilote, referentiel, saut, passage systeme -> ciel, rotation propre des corps (T28), vitesse de croisiere
 * (T34). Specification : {@code docs/space/04-mathematiques.md} par. 1 a 5, 8, 10, 11 et
 * {@code 05-synchronisation.md} par. 2 (la meme fonction tourne sur le serveur et sur les clients).
 * <p>
 * Contenu :
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceUnits} : constantes (UA, annee-lumiere, jour),
 *     temps de jeu -> jours simules, fraction d'orbite ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceOrbits} : positions des corps (km, repere
 *     systeme), Kepler resolu par Newton borne ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpacePose} : pose du monde dans le systeme
 *     (position km, orientation monde -> systeme), interpolation, reseau, NBT ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpacePilotInput} : entree quantifiee du pilote (gaz,
 *     roulis, taux de lacet et de tangage, frein, boost (T32), visee, saut et sa cible) ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings} : reglages du mouvement et de
 *     l'horloge (axe avant, vitesse max, taux de rotation, echelle de temps) et vitesse de distorsion du saut (T15),
 *     avec leurs valeurs par defaut ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceFrameState} : etat du referentiel (corps de
 *     reference, precedent, ticks depuis la bascule, candidat), reseau, NBT ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceJump} : etat du saut interstellaire (phase
 *     charge / voyage, cible, compteur, duree, orientation et positions galactiques figees au debut), progressions,
 *     reseau, NBT ; fonctions pures : orientation de la charge, duree du voyage, pose d'arrivee (T21, doc 04 par. 8)
 *     ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceMotion} : un pas de tick (referentiel par spheres
 *     d'influence, T39, avec hysteresis, delai, cooldown et fondu ; masses des corps, aucune gravite appliquee, gaz a
 *     vitesse signee et frein, pentes renforcees par le boost (T32), rotation, entrainement par le referentiel,
 *     freinage d'approche, garde-fou ; variante avec le saut : charge verrouillee, voyage fige), pose par defaut d'un
 *     systeme (et son referentiel), corps le plus proche, interpolation d'affichage dans le repere du referentiel,
 *     distance d'arret au frein (T32) ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame} : repere du ciel d'une frame (point du
 *     systeme -> direction monde + distance, direction d'un autre systeme) ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceSpin} : rotation propre des corps (T28, doc 04
 *     par. 10) : orientation (axes locaux du corps -> axes du systeme) au temps simule, rotation synchrone tiree de la
 *     position orbitale, variante a tampon sans allocation ;</li>
 *     <li>{@link com.amynna.hololib.modules.dimension.space.math.SpaceCruise} : vitesse de croisiere du HUD de Space
 *     Pilot (T34, doc 04 par. 11) : cible au centre de l'ecran avec hysteresis, vitesse cible (frein normal, temps de
 *     reaction du pilote, marge), couleur de l'indication. Calcul client seulement.</li>
 * </ul>
 * <p>
 * Regles :
 * <ul>
 *     <li><b>Code commun pur</b> : aucune classe client, aucun acces au monde. Dependances : modele
 *     ({@code space.model}), JOML (types {@code Vector3d}, {@code Quaterniond} comme conteneurs), NBT et
 *     {@code FriendlyByteBuf}.</li>
 *     <li><b>Determinisme bit a bit</b> des calculs simules ({@link
 *     com.amynna.hololib.modules.dimension.space.math.SpaceOrbits#positions}, {@link
 *     com.amynna.hololib.modules.dimension.space.math.SpaceMotion#step}, {@link
 *     com.amynna.hololib.modules.dimension.space.math.SpaceSpin#orientation}) : trigonometrie par {@link java.lang.StrictMath}
 *     (meme resultat sur toute JVM), arithmetique des quaternions et vecteurs ecrite ici
 *     ({@code SpaceRotations}, sans {@code Math.fma} : les methodes JOML changent de resultat selon
 *     {@code joml.useMathFma}), aucun hasard, aucune horloge, aucun ordre de {@code HashMap}. Seule l'interpolation
 *     d'affichage ({@link com.amynna.hololib.modules.dimension.space.math.SpacePose#lerp}, {@link
 *     com.amynna.hololib.modules.dimension.space.math.SpaceMotion#interpolate}) utilise JOML.</li>
 *     <li><b>Aucune allocation</b> dans les fonctions appelees par tick ou par frame : tableaux et vecteurs
 *     {@code dest} fournis par l'appelant ; {@link com.amynna.hololib.modules.dimension.space.math.SpaceMotion} et
 *     {@link com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame} sont des <b>instances</b> qui portent leur
 *     espace de travail : une instance par fil (serveur, client, rendu), jamais partagee (en solo, le serveur integre
 *     et le client tournent en meme temps).</li>
 *     <li>Calculs en {@code double} ; conversion en {@code float} seulement pour dessiner.</li>
 * </ul>
 * <p>
 * Conventions (doc 04 par. 1, 2) : axes Minecraft ({@code +X} est, {@code +Y} haut, {@code +Z} sud) ; yaw 0 = sud,
 * positif vers la droite ; pitch positif vers le bas ; direction d'un regard {@code (-sin yaw cos pitch, -sin pitch,
 * cos yaw cos pitch)}. L'orientation {@code Q} d'une pose envoie une direction du monde dans le systeme
 * ({@code v_S = Q v_W}). Axes du monde : avant {@code F} (reglage), droite {@code R = F x Y} normalise, haut
 * {@code U = R x F} ({@code (0, 1, 0)} pour un avant horizontal). Signes du pilotage (a verifier en jeu en T07) :
 * {@code yawRate > 0} tourne l'avant vers la droite, {@code pitchRate > 0} leve l'avant, {@code roll > 0} roule a
 * droite. Vitesse nominale <b>signee</b> {@code V} dans {@code [-V_max, V_max]} ({@code V < 0} : le monde part en
 * arriere), <b>relative au corps de reference</b> (spheres d'influence, D29 ; le monde suit son mouvement
 * orbital) : {@code throttle > 0} la pousse vers {@code +V_max}, {@code throttle < 0} vers {@code -V_max} (en
 * freinant d'abord jusqu'a 0 si elle est de l'autre signe) ; le frein la ramene a 0 et prime sur les gaz.
 */
package com.amynna.hololib.modules.dimension.space.math;
