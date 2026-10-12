# F22 — Découper `SpaceMapCubeRenderer` et `SpaceKnowledgeSkyRenderer`

**Objectif** : séparer les passes et calculs de ces deux renderers, en utilisant les utilitaires partagés de F23,
sans changer le rendu. Rapport § 5.2, recommandation 14.

**Lot** : B   **API** : non   **Dépendances** : F23

## Constat

**`SpaceMapCubeRenderer` (1417 lignes)**
- cadre et faces ;
- passe galaxie avec cache `Layout` : `:1072-1288` ;
- passe système (orbites, sphères texturées) : `:475-786` ;
- croix et `CoordinateText` sans allocation : `:786-927` ;
- vaisseau : `:928-1012` ;
- graduations : `:1026-1071`.

**`SpaceKnowledgeSkyRenderer` (1061 lignes)**
- règles de visibilité et de fondu : `:398-462` ;
- échantillonnage des orbites : `:464-650` ;
- découpe des orbites par les anneaux, avec tri par insertion : `:654-846` ;
- anneaux, étoiles et étiquette au regard : `:870-1000`.

Les deux classes recopient `basis` et `vertex` (`SpaceKnowledgeSkyRenderer.java:1038` : « copie de
SpaceSkyRenderer »), que F23 met en commun.

## À lire avant
- Rapport § 5.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) §§ 2, 4.
- La carte F23 (utilitaires créés).
- `docs/space/04-mathematiques.md` § 9 ; les deux renderers en entier.

## Zone
`client/render/modules/monde/spacemap/**`, `client/render/modules/camera/spaceknowledge/**`.

## Livrables
1. `SpaceMapCubeRenderer` → `SpaceMapLayout`, `SpaceMapGalaxyPass`, `SpaceMapSystemPass`, `SpaceMapCrossOverlay`
   (+ classe principale qui orchestre).
2. `SpaceKnowledgeSkyRenderer` → `SpaceOrbitClipper` (coupures, tirets, tri), `SpaceGazeLabel` (+ classe
   principale).
3. Utilisation des utilitaires de F23 à la place des copies locales.
4. Aucune allocation par frame introduite (tampons préalloués déplacés avec leur code).
5. Aucune classe de plus de 600 lignes.

## Interdits
- Changer le rendu, l'ordre d'émission ou les `RenderType`.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : captures avant / après identiques :
  - carte en mode galaxie et système, croix, vaisseau, graduations, zoom ;
  - ciel avec Space Knowledge (anneaux, orbites coupées, pointillés, étiquette au regard, lunes de près) ;
  - avec et sans Oculus.

## Compte rendu attendu
Table méthode → classe, tailles, fichiers, comparaison des captures.
