# F16 — Recherche typée des modules et cache client du module local

**Objectif** : une seule façon de trouver un module dans une mémoire, côté serveur comme côté client. Les quatre
parcours `of()` recopiés et les recherches répétées à chaque frame disparaissent. Rapport § 4.6, recommandation 12.

**Lot** : B   **API** : **oui : à valider** (méthodes publiques ajoutées)   **Dépendances** : aucune

## Constat
- Côté serveur, le parcours linéaire est recopié dans :
  - `TargetingModule.of` (`:62-70`) ;
  - `KeysModule.of` (`:43-51`) ;
  - `AttackGuardModule.of` (`:101-109`) ;
  - `SpaceEnvironmentModule.of` (`:402-411`).
- Côté client, trois façons de trouver le module du joueur local coexistent :
  - parcours de `ClientHoloWorld.local()` : `TargetingClient:63`, `AttackGuardClient:105`, `FocusController` ;
  - scope mis en cache puis `find` : `SpacePilotClient`, `SpaceKnowledgeClient`, `RadarClient` ;
  - `find` à **chaque appel** : `SpaceEnvironmentClient.clientInstance()` (`:33-36`), `SpaceSkyRenderer.find()`
    (`:388-391`, deux fois par frame).

## À lire avant
- Rapport § 4.6 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.2.
- `holo/memory/HoloContainer.java`, `holo/memory/HoloContainers.java` (`player`, `dimension`), `client/holo/
  ClientHoloWorld.java`, `ClientHoloContainer.java`, `client/holo/HoloModes.java` (`find`), les fichiers cités.

## Demande d'API (à valider avant de commencer)
- `HoloContainer.find(HoloModuleType<T>)` et `find(Class<T>)` → `@Nullable T`. La recherche par type (registre)
  est préférée, la recherche par classe sert aux interfaces.
- Équivalent sur `ClientHoloContainer`.
- `ClientHoloWorld.localModule(HoloModuleType<T>)` : module de la mémoire active du joueur local, mis en cache et
  invalidé à chaque ajout, retrait ou changement de mémoire (pas de recalcul par appel).
- Éventuellement `dimensionModule(type)`, avec la même règle de cache.

**Alternative** : une méthode utilitaire statique interne aux modules, sans toucher au framework. Elle supprime les
copies mais pas les recherches répétées côté client.

## Zone
`holo/memory/HoloContainer.java`, `client/holo/ClientHoloWorld.java`, `ClientHoloContainer.java`,
`client/holo/HoloModes.java` ; les quatre `of()` serveur ; les classes client citées (remplacement des recherches).

## Livrables
1. L'API validée, documentée, **sans allocation** (pas de stream ni d'itérateur dans le chemin chaud ; cache côté
   client).
2. Les quatre `of()` serveur s'appuient dessus (leur signature publique reste).
3. Les recherches client citées passent par le cache. `SpaceEnvironmentClient.clientInstance()` et
   `SpaceSkyRenderer.find()` ne parcourent plus rien à chaque appel.
4. Les tables `SpaceMapModule.of(module)` / `AreaDisplayerModule.of(module)` sont laissées en place (FC8), avec une
   note dans le compte rendu : sont-elles encore utiles ?

## Interdits
- Changer l'ordre ou la règle « une instance par type et par mémoire ».

## Critères d'acceptation
- `compileJava` réussit (et `test` si F25 est faite).
- En jeu :
  - ciblage, Attack Guard et clés (KeysEditor sur soi et sur la cible) fonctionnent ;
  - ciel Space, Space Pilot, Space Knowledge et radar fonctionnent ;
  - changement de mémoire active (`/hololib_memory player ...`) : les modules du joueur local sont retrouvés sans
    reconnexion.

## Compte rendu attendu
API, fichiers, recherches remplacées (liste), règle d'invalidation du cache, scénarios.
