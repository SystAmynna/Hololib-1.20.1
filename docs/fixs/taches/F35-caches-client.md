# F35 — Caches et boucles côté client

**Objectif** : retirer les allocations et les recalculs inutiles des chemins client exécutés à chaque frame ou à
chaque tick. Rapport § 6.1, § 6.2, recommandation 23.

**Lot** : C   **API** : non   **Dépendances** : F15, F17

## Constats
1. **Itérateurs à chaque frame.** For-each sur `Collections.unmodifiableList` (deux objets par boucle) dans :
   - `SkyRenderPass:98`, `WorldRenderPass:109`, `ViewRenderPass:52`, `HudRenderPass:37` ;
   - `RenderDispatcher:110`, `WorldRenderer:63` ;
   - `HoloControlCapture.captorFor:143`, appelé plusieurs fois par frame.
   
   À remplacer par des boucles indexées. Les ponts le font déjà.
2. **`HoloInteractions.drag`** (`:146`) copie `PRESSED.keySet()` dans un tableau à chaque frame tant qu'un bouton
   est maintenu.
3. **Capacités d'édition.**
   - `HoloEditSession.mayEdit` (`:200-212`) est appelé pour chaque objet routé, à chaque tick (`collect`, `:534`, et
     `reveals`). Chaque appel parcourt `ClientHoloWorld.local()`, crée un `EnumSet` (`HoloEditRules.capabilities`)
     et appelle `hasPermissions(2)`.
   - `HoloEditTarget.tick` (`:137`) refait un `EnumSet` par cible (`bothOwnEditors`).
   
   À calculer une fois par tick. Après F15, ces appels passent par les politiques.
4. **`HoloEditPanel.refresh`** fait jusqu'à 7 `String.format` par cible et par tick (`:174`, `:224-226`). Ne
   reformater que si la valeur a changé (modèle : `RadarClient.status`).
5. **`SpaceMapModule.readSync`** (`:956-984`) recrée la liste d'en-têtes à chaque synchronisation. Comme le cache de
   `SpaceMapCubeRenderer` (ou `SpaceMapLayout` après F22) est indexé sur l'identité de la liste, chaque zoom de
   n'importe quel joueur reconstruit les noms et les `font.width` de jusqu'à 256 systèmes. Il faut garder la liste
   tant que la révision de l'Environment n'a pas changé.
6. **`SpaceEnvironmentModule.client()`** est appelé par 5 renderers et passe par `HoloModes.find` à chaque fois. Si
   F16 ne l'a pas déjà réglé, utiliser le cache par tick.
7. **Découpe des orbites** de `SpaceKnowledgeSkyRenderer` (ou `SpaceOrbitClipper` après F22) : orbites × 128
   échantillons × corps proches à chaque frame, sans réutilisation. Il faut un cache invalidé par la révision, ou
   quand le regard et la pose ont bougé au-delà d'un seuil. **Mesurer d'abord** : ne faire le cache que si le coût
   est significatif (> 0,5 ms par frame).

## À lire avant
Rapport § 6.1, § 6.2 ; les fichiers cités, dans leur état après F15, F16, F17 et F22.

## Zone
Les fichiers cités.

## Livrables
1. Les sept corrections, ou une décision argumentée (mesure à l'appui) de ne pas faire le point 7.
2. **Mesure avant / après** :
   - FPS avec éditeur ouvert sur 30 objets éditables ;
   - ciel Space avec Space Knowledge et Map ;
   - allocations sur 60 s (profileur, ou journaux du GC).

## Interdits
- Changer un comportement ou un rendu.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu : éditeur (panneau, valeurs), carte (zoom en LAN), ciel Space, capture (pilotage, texte) ; mesures jointes.

## Compte rendu attendu
Corrections, mesures, fichiers.
