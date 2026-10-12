# F34 — Caches serveur : chunks, ids, droits, gardes

**Objectif** : supprimer les parcours complets répétés à chaque tick ou à chaque événement côté serveur. Rapport
§ 6.3, recommandation 22.

**Lot** : C   **API** : non   **Dépendances** : F16

## Constats
1. `holo/memory/HoloContainers.onChunkWatch` / `onChunkUnWatch` (`:422-439`) parcourent **tous** les conteneurs de
   position à chaque événement de chunk : O(markers × chunks). Il faut un index `ChunkPos → conteneurs de position`
   (par niveau), maintenu à l'ouverture, à la fermeture et au déplacement d'un marker.
2. `holo/module/HoloModuleType.id()` (`:29`) fait une recherche dans le registre à chaque appel (`find`, `types`,
   `ModuleData.of`, `remove`). Il faut mettre l'id en cache à la première lecture : le registre est figé après le
   chargement.
3. `modules/monde/spaceconfigurator/SpaceConfiguratorModule.updateAccess` (`:558-571`) appelle `hasPermissions`
   sur **tous** les joueurs connectés, à chaque tick, pour chaque Configurator. Il faut recalculer seulement à la
   connexion ou à la déconnexion, au changement d'op (pas d'événement Forge : par exemple toutes les 20 ticks), ou
   au changement de dimension.
4. `modules/camera/attackguard/AttackGuardModule` (`:118-133`) : toutes les 10 ticks, chaque garde teste tous les
   joueurs du niveau et appelle `KeysModule.of` (parcours linéaire), soit O(N²). Il faut un calcul **une fois par
   niveau et par période**, partagé par tous les gardes (cache des clés de chaque joueur, via `find` de F16).

## À lire avant
- Rapport § 6.3 ; la carte F16 (API `find`).
- Les fichiers cités ; `holo/memory/position/PositionHoloContainer.java` (`trackMovement`) ;
  `holo/memory/HoloMarkers.java`.

## Zone
`holo/memory/HoloContainers.java`, `holo/memory/position/*`, `holo/module/HoloModuleType.java`,
`modules/monde/spaceconfigurator/SpaceConfiguratorModule.java`, `modules/camera/attackguard/*`,
`modules/camera/keys/*` (lecture seule ou cache).

## Livrables
1. Les quatre optimisations, sans changement de comportement visible.
2. Pour l'index des chunks : maintenance au déplacement d'un marker (`trackMovement`) et nettoyage à la fermeture
   (cohérent avec F04).
3. **Mesure avant / après** : temps serveur par tick avec 50 markers, 8 joueurs (ou bots, ou simulation décrite), 3
   Configurators et 4 Attack Guards. Si la mesure est impossible sans joueurs, un raisonnement chiffré et un micro
   banc.

## Interdits
- Changer les règles d'accès (qui voit, qui peut agir) ou la latence de protection d'Attack Guard (10 ticks).

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu (LAN) :
  - les markers apparaissent et disparaissent en entrant et sortant de leurs chunks ;
  - marker déplacé : suivi correct ;
  - Configurator visible des ops seulement, mis à jour après `/op` / `/deop` (délai documenté) ;
  - Attack Guard bloque les joueurs protégés.

## Compte rendu attendu
Caches, invalidations, fichiers, mesures ou raisonnement.
