# F21 — Découper `SpaceConfiguratorModule` et `SpaceMapModule`

**Objectif** : séparer les responsabilités des deux modules marker Space, sans changer de comportement. Rapport
§ 5.2, recommandation 14.

**Lot** : B   **API** : non (packages Space)   **Dépendances** : F20

## Constat

**`SpaceConfiguratorModule` (1365 lignes)**
- actions et validation : `:574-810` ;
- poignées sondées : `:871-1135` ;
- accès des ops (`updateAccess`, `:558`) ;
- gel du temps (`:531`) ;
- encodeurs de requêtes client : `:1138-1275`.

**`SpaceMapModule` (1160 lignes)**
- vue partagée et zoom : `:623-680`, `:765-806` ;
- copie de l'Environment : `:371-420` ;
- publication de la cible : `:690-760` ;
- API pilotée par le Configurator : `:498-556` ;
- géométrie cube ↔ marker : `:1120-1160` ;
- placement de la fenêtre : `:807-855`.

## À lire avant
- Rapport § 5.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 4.
- `docs/space/02-architecture.md` § 7, `docs/space/decisions.md` (C121–C124, D33, D34).
- `modules/monde/spaceconfigurator/*`, `modules/monde/spacemap/*`, leurs backends et renderers.

## Zone
`modules/monde/spaceconfigurator/**`, `modules/monde/spacemap/**`, leurs miroirs `client/...` si nécessaire.

## Livrables
1. Configurator :
   - `SpaceConfiguratorActions` : handler serveur, validation, op d'abord ;
   - `SpaceHandleController` : sondage et placement de `SpaceMapHandle` / `SpaceOrbitHandle`, contexte retenu ;
   - les encodeurs de requêtes vont avec `SpaceConfiguratorWindow` ou une petite classe `SpaceConfiguratorRequests`.
2. Map :
   - `SpaceMapGeometry` : `cubeToMarker`, `markerToCube`, `galaxyPoint`, `bodyPoints` ;
   - `SpaceMapTargetPost` : CIBLER / DÉCIBLER, `checkPostedTarget`.
3. Identifiants d'actions, format réseau et NBT inchangés.
4. Aucune classe de plus de 700 lignes. Table des tailles jointe.
5. `docs/space/02-architecture.md` § 7 mis à jour.

## Interdits
- Changer le comportement (C121 temps figé, C122 table statique, poignées, ciblage).
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu :
  - Map : sélection, zoom, recentrage, vue d'ensemble, origine, CIBLER / DÉCIBLER ;
  - Configurator : ajouter, retirer, renommer, Monde ici, onglets, RESET à deux clics, Se téléporter, Orbite, import
    de texture ;
  - poignées d'étoile et d'orbite ;
  - LAN.

## Compte rendu attendu
Table méthode → nouvelle classe, tailles, fichiers, scénario.
