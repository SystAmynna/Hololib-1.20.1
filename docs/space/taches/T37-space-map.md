# T37 — Module Space Map : carte et fenêtre de navigation

**Objectif** : (D20) extraire du Configurator un module de marker **Space Map** (`hololib:space_map`), **visible et
utilisable par tous** : il garde le rendu de la carte (cube : galaxie et systèmes, à l'échelle, système courant,
vaisseau, croix 3D, coordonnées, origine) et gagne une **fenêtre de navigation** (objet séparé, éditable à part) :
- la carte **s'adapte à la taille du cube** ;
- on **zoome vers le centre** de la carte ;
- la **liste des astres** est une **arborescence de la gravité** (Galaxie > système > étoile > planète > lune ; ex. la
  Terre et Vénus enfants du Soleil, la Lune enfant de la Terre) ; sélectionner une ligne **recentre** la carte dessus.

**Dépendances** : T30 (les corps de la carte sont texturés et tournent, avec le même cache de textures).

## À lire avant
- [`01-vision.md`](../01-vision.md) § Space Map (lot 3) ; [`02-architecture.md`](../02-architecture.md) §§ 3, 7 ;
  [`04-mathematiques.md`](../04-mathematiques.md) § 7 ; [`05-synchronisation.md`](../05-synchronisation.md) § 4 ;
  [`decisions.md`](../decisions.md) C9, C11, C33–C36, C49, C50, C65–C68, D13, D17, D20, C77, L19, L22.
- `modules/monde/spaceconfigurator/` (tout : ce qui part dans la Map, ce qui reste), `client/render/modules/monde/
  spaceconfigurator/` ; `holo/widget/control/HoloWidgetList.java`, `HoloWidgetButton`, `HoloWidgetScrollZone` ;
  `modules/camera/config/ConfigListWindow.java` (liste) ; `client/render/modules/dimension/space/SpaceBodyTextureCache`
  (T30).

## Livrables
1. `modules/monde/spacemap/` (+ `package-info.java`) : `SpaceMapModule` (`POSITION`, enregistré `space_map`),
   `SpaceMapCube` (déplacé), `SpaceMapWindow` (navigation), `SpaceSystemScale`, `SpaceCoordinates` (déplacés) ; miroirs
   client `client/modules/monde/spacemap/`, `client/render/modules/monde/spacemap/` (`SpaceMapCubeRenderer` déplacé,
   `SpaceMapRenderers`). Objets `PUBLIC`, accès `EVERYONE` (navigation) ; placement du cube et de la fenêtre éditables
   par les ops au `world_editor` (translation, rotation, échelle pour le cube ; translation, rotation, œil pour la
   fenêtre), comme aujourd'hui.
2. **Vue partagée** (C77) : état du marker (sauvegardé, synchronisé) = mode (galaxie / système), système affiché,
   sélection, centre, zoom continu (borné, par pas multiplicatifs), origine des coordonnées. Actions de navigation acceptées
   de tout joueur à portée ; recentrage sur une ligne = sélection + centre (un corps → vue du système centrée sur lui).
3. **Arborescence** : lignes indentées et dépliables (galaxie → systèmes → corps par parenté), système courant en vert
   (C67), origine marquée `@` (C68) ; défilement ; mise à jour à la révision de l'Environment.
4. Carte ajustée au cube quelle que soit son échelle (vérifier l'existant, corriger) ; zoom ± vers le centre (boutons
   et molette sur la carte ou la fenêtre) ; le zoom sur un corps de T25 devient un cas du recentrage.
5. Copie des données (en-têtes + système affiché) déplacée du Configurator vers la Map, avec la même politique de
   révision ; **API de lecture** pour le Configurator (T42) : sélection, système affiché, repère du cube et de la vue
   (`SpaceSystemScale`), copie de la galaxie.
6. Le Configurator reste compilable et fonctionnel tant que T42 n'est pas faite (il peut temporairement garder son
   cube) : dire comment la transition est gérée.
7. Docs 01, 02 §§ 1–3, 7 (nouveau « Space Map »), 04 § 7, 05 § 4.

## Interdits
- Modifier une classe hors des packages Space (sauf enregistrements).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : `/hololib_marker create carte` puis `/hololib_module add world carte hololib:space_map` ; un non-op voit la
  carte et la fenêtre, déplie Sol, clique la Lune → la carte se recentre sur la Terre et la Lune ; zoom ± ; agrandir
  le cube agrandit la carte ; un second joueur voit la même vue.

## Compte rendu attendu
Fichiers déplacés / créés, format d'état (octets), actions, écarts, scénario.
