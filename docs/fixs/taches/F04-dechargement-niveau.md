# F04 — Déchargement de niveau : fermer les positions, vider les tables

**Objectif** : au déchargement d'un niveau et à l'arrêt du serveur, chaque conteneur de position est **fermé**
(`onUnload` de ses modules appelé, tickets et scans libérés). Les tables statiques par marker ne retiennent plus
l'ancien monde. Rapport § 7.1-4, recommandation 4.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat (vérifié)
- `holo/memory/HoloContainers.java:381-387` (`onLevelUnload`) : la dimension est fermée (`close()`), mais les
  positions sont retirées par `POSITIONS.values().removeIf(...)` **sans `close()`** ni remise à zéro de
  `record.live`.
- `onServerStopped` (`:466`) se contente de vider les tables.
- Vanilla ne poste `EntityLeaveLevelEvent` que pour les markers des chunks masqués : pour les markers encore
  visibles, `onUnload` n'est jamais appelé.
- Tables statiques concernées :
  - `modules/monde/spacemap/SpaceMapModule.java:172` (`SERVER_MAPS`) ;
  - `modules/monde/areadisplayer/AreaDisplayerModule.java:96` (`SERVER_DISPLAYERS`).

  Elles gardent les anciens modules. `AreaDisplayerModule` tient `lastLevel` (un `ServerLevel`) et jusqu'à 16 Mo de
  voxels.
- Conséquences :
  - en solo, l'ancien serveur peut rester en mémoire après avoir quitté le monde ;
  - avec des dimensions chargées dynamiquement, un niveau rechargé voit son marker refusé (`record.live() != null`,
    `onEntityJoin` `:400`).

## À lire avant
- Rapport § 7.1-4 ; [`decisions.md`](../decisions.md) FC8.
- `holo/memory/HoloContainers.java` (`onLevelUnload`, `onEntityJoin`, `onEntityLeave`, `onServerStopped`),
  `holo/memory/position/PositionHoloContainer.java`, `HoloMarkers` (`record.live`, `save`),
  `holo/memory/HoloContainer.java` (`close`).
- `SpaceMapModule.of` (`:270-287`, commentaire sur l'entrée périmée), `AreaDisplayerModule.of` (`:194-214`),
  `AreaScanner.stop`.

## Zone
`holo/memory/HoloContainers.java`, `holo/memory/HoloMarkers.java` (si `live` doit être remis à zéro par une méthode
package-private), `modules/monde/spacemap/SpaceMapModule.java`, `modules/monde/areadisplayer/AreaDisplayerModule.java`
(gestion des tables uniquement).

## Livrables
1. `onLevelUnload` : pour chaque conteneur de position du niveau, **sauvegarde** (comme à la sortie d'un marker),
   puis `close()`, remise à zéro de `record.live`, puis retrait. L'ordre doit garantir que `HoloMarkers.save` ne relit
   pas un conteneur fermé.
2. `onServerStopped` (ou `ServerStoppingEvent` si la sauvegarde l'exige : à justifier) ferme de même les conteneurs
   restants avant de vider.
3. `SpaceMapModule` et `AreaDisplayerModule` retirent leur entrée de la table à `onUnload` côté serveur (vérifier
   qu'ils le font déjà ; sinon l'ajouter). Les deux tables serveur sont vidées à `ServerStoppedEvent` (FC8), par un
   abonné dans chaque package de module, pas dans le framework.
4. Un message `debug` de log par conteneur fermé au déchargement (utile à la recette, retirable).

## Interdits
- Changer le format de sauvegarde des markers.
- Remplacer les tables par `HoloContainer.find` (c'est F16).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (solo) :
  - un marker avec Space Map et Area Displayer (zone de taille moyenne) ;
  - quitter le monde → le log montre la fermeture ;
  - rouvrir le monde → tout est restauré (vue de la Map, terrain de la zone) ;
  - après plusieurs allers-retours, la mémoire ne croît pas (F3 : mémoire utilisée après un GC, ou VisualVM).
- Le compte rendu explique pourquoi le changement est sans risque pour l'ordre `save` / `close`.

## Compte rendu attendu
Fichiers, ordre des opérations retenu, résultat des allers-retours, points à vérifier en jeu.
