# T25 — Space Configurator : système courant, carte à l'échelle, vaisseau

**Objectif** : (D13, D14, D17) sur la carte : en mode galaxie, le **système courant** est mis en évidence ; en mode
système, tout est **à l'échelle** (positions réelles, tailles exagérées, plus de loupe des lunes) et le **vaisseau**
est affiché : petite **sphère verte** d'où part un **segment** dans la direction de son axe avant.

**Dépendances** : T24.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 7 (vaisseau, système courant, loupe supprimée) ;
  [`decisions.md`](../decisions.md) C8, C34, C36, C49, C54, D13, D17.
- `modules/monde/spaceconfigurator/SpaceSystemScale.java` (loupe), `SpaceConfiguratorModule` (copie des données,
  conversion des poignées), `SpaceOrbitHandle`.
- `client/render/modules/monde/spaceconfigurator/SpaceMapCubeRenderer.java` (mise en page, cache, étoiles, corps),
  `SpaceOrbitHandleRenderer`.
- `SpaceEnvironmentModule` côté client (système courant, `pose(partialTick)`, référentiel, `bodyPositions`) : le
  Configurator est dans la même dimension, son renderer peut lire le miroir de l'Environment.

## Livrables
1. **Loupe supprimée** : `SpaceSystemScale` sans facteur (ou facteur constant 1 documenté) ; poignée d'orbite et
   affichage cohérents ; javadoc / doc 04 § 7 nettoyés. Les tailles affichées restent exagérées (formule existante,
   à revoir si les lunes deviennent illisibles : les lunes de Sol sont alors collées à leur planète — attendu, D13 ;
   recadrer de près pour les voir, dire si un recadrage sur la sélection serait utile).
2. **Mode galaxie** : étoile du système courant mise en évidence (couleur ou anneau dédié, distinct de la sélection ;
   légende éventuelle dans la fenêtre).
3. **Mode système** : si le système édité est le courant, vaisseau = sphère verte (rayon fixe en unités du cube) en
   `P_ref(t_edit) + (s − P_ref(maintenant))` (C49) + segment de longueur fixe selon l'axe avant ; mis à jour chaque
   frame (interpolé) ; sans allocation.
4. **Zoom sur un corps** (D17) : depuis la liste des corps de la fenêtre, un bouton (ou une action de ligne) recadre la
   carte sur ce corps et son voisinage (ses lunes, son orbite proche), à l'échelle, pour l'éditer avec précision ;
   retour à la vue du système entier. Poignées (d'orbite, de sélection) justes dans les deux vues. Le facteur de zoom
   est un état d'affichage (client, ou sauvegardé avec la sélection si c'est plus simple, à justifier).
5. Le cache de mise en page reste valide (le vaisseau ne le reconstruit pas ; un changement de zoom peut le
   reconstruire).

## Interdits
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : galaxie → Sol (ou le système courant après un saut) en évidence ; Éditer Sol → orbites à l'échelle, Terre et
  Lune quasi confondues, sphère verte près de la Terre, segment qui tourne quand on pilote ; éditer un autre système
  → pas de vaisseau ; zoom sur Mars depuis la liste → Phobos et Deimos distincts et éditables, retour au système ; poignée d'orbite toujours juste (aller-retour de la taille d'une orbite).

## Compte rendu attendu
Fichiers, rendu choisi pour le système courant, tailles du vaisseau, écarts, scénario.
