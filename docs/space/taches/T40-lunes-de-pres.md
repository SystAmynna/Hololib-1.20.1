# T40 — Space Knowledge : lunes repérées de près

**Objectif** : (D30, remplace le seuil de C47) les lunes ne sont repérées (anneau, orbite, nom) que lorsqu'on est
**assez près pour les distinguer** de leur planète : plus de paquet d'anneaux superposés autour d'une planète
lointaine (Jupiter, Saturne).

**Dépendances** : aucune.

## Constat
`SpaceKnowledgeSkyRenderer` repère **toutes** les lunes d'un parent dès que le monde est à moins de
`10 × a_max` (`MOON_REACH_FACTOR`, plus grand demi-grand axe des lunes de ce parent, C47), avec un fondu sur les
derniers 20 %. À cette distance, leurs anneaux (10 px de rayon au minimum, `SpaceOverlay.BODY_RING_MIN_PX`) se
recouvrent.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 9 ; [`decisions.md`](../decisions.md) D15, D18, D19, C46, C47, D30.
- `client/render/modules/camera/spaceknowledge/SpaceKnowledgeSkyRenderer.java` (visibilité, `fades`, `moonReach`),
  `client/render/modules/dimension/space/SpaceOverlay.java` (`pixelTan`, `bodyRingMin`).

## Livrables
1. Critère **par lune** : séparation angulaire de son orbite vue du monde, `atan(a / d_parent)` (`a` = demi-grand axe
   en km, `d_parent` = distance du monde à son parent), comparée en **pixels de référence** (`SpaceOverlay.pixelTan` :
   FOV et longue-vue comptent). Repérée à partir de **24 px** (deux anneaux minimaux + marge), fondu de 24 à 36 px
   (constantes nommées, à juger en jeu). Remplace `MOON_REACH_FACTOR`.
2. Orbite et nom de la lune suivent le même fondu ; le **référentiel** reste toujours repéré (C47) ; lunes d'objets
   mineurs : même règle ; objets mineurs eux-mêmes : inchangés (0,5 UA).
3. Sans allocation, calculé une fois par frame.
4. Doc 04 § 9 ; C47 annotée (seuil des lunes remplacé par D30).

## Interdits
- Modifier une classe hors des packages Space ; allocation par frame.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : Jupiter de loin = un seul anneau ; en approche, Callisto puis Ganymède, Europe, Io apparaissent une à une,
  sans se superposer ; la longue-vue en révèle plus tôt ; la Lune repérée près de la Terre ; une lune référentiel
  toujours repérée.

## Compte rendu attendu
Fichiers, seuils retenus, distances d'apparition mesurées (lunes de Jupiter, Lune), écarts.
