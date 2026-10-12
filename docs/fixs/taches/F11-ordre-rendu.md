# F11 — Ordre explicite des renderers et des captors passifs

**Objectif** : l'ordre de dessin des renderers et l'ordre de consultation des captors passifs sont **écrits** et
ne dépendent plus de l'ordre, non défini, dans lequel Forge appelle les abonnés au setup client. Rapport § 4.5,
recommandation 8.

**Lot** : B   **API** : **oui : à valider** (signature d'enregistrement, voir Livrables)   **Dépendances** : aucune

## Constat (vérifié)
- Pour une même famille, l'ordre de dessin est l'ordre d'enregistrement dans `HoloRenderers`.
- Trois abonnés indépendants à `FMLClientSetupEvent` enregistrent des renderers :
  - `client/render/holo/HoloBridges.java:22-43` : ponts, `HoloFocusBridge`, `HoloGizmoRenderer`, renderer des
    fenêtres ;
  - `client/render/demo/DemoRenderers.java:17-21` ;
  - `client/render/modules/ModuleRenderers.java:26-41`.
  
  L'ordre entre classes abonnées n'est pas défini.
- Conséquences :
  - `HoloFocusBridge` (« au-dessus de tout ») peut passer **sous** des `ViewRenderer` de modules (`SpaceJumpRenderer`,
    `SpaceTargetRenderer`, `TargetLinkRenderer`…) ;
  - l'ordre entre `HoloSkyBridge` et le fond opaque de `SpaceSkyRenderer` est arbitraire.
- Dans `ModuleRenderers`, les commentaires imposent un ordre (Knowledge après Space, Targeting après Radar), mais
  rien ne le garantit.
- Les captors passifs suivent le même schéma : `HoloInteractions.java:85` et `AttackGuardClient.java:121` appellent
  `addPassive` depuis des setups distincts, et `captorFor` les consulte dans l'ordre d'insertion.

## À lire avant
- Rapport § 4.5 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.1 ; [`decisions.md`](../decisions.md)
  FC7.
- `client/render/HoloRenderers.java`, `RenderDispatcher.java`, les quatre passes, `HoloBridges`, `DemoRenderers`,
  `ModuleRenderers` et chaque `XxxRenderers` qu'il appelle ; `client/capture/HoloControlCapture.java` (`addPassive`,
  `captorFor`).

## Demande d'API (à valider avant de commencer)
**Proposition (FC7)** : des couches nommées par famille.
- `HoloRenderers.register(renderer, layer)` avec une enum par famille. Exemple pour `ViewRenderer` :
  `BACKGROUND`, `OBJECTS`, `MODULE`, `MODULE_OVERLAY`, `FOCUS`. Exemple pour `SkyRenderer` : `BACKGROUND` (fonds
  opaques), `OBJECTS` (pont du ciel), `OVERLAY` (surcouches).
- `register(renderer)` reste disponible et vaut la couche « module » par défaut : compatibilité avec un mod tiers.
- L'ordre à l'intérieur d'une couche est l'ordre d'enregistrement.
- Captors passifs : `addPassive(captor, priority)` avec une enum (`GAME_GUARD` avant `INTERACTION`, par exemple).
  Choisir l'ordre en lisant les deux captors.

**Alternative** : pas de nouvelle API. Un seul abonné de setup (par exemple `HoloBridges`) appelle explicitement
`DemoRenderers.register()` puis `ModuleRenderers.register()` aux bons moments. Les deux autres classes ne
s'abonnent plus. Plus simple, mais l'ordre reste implicite pour un mod tiers.

L'orchestrateur présente les deux options. La décision validée devient une FD, puis la carte est exécutée avec
l'option retenue.

## Zone
`client/render/HoloRenderers.java`, les passes et `RenderDispatcher` (lecture des couches), `HoloBridges`,
`DemoRenderers`, `ModuleRenderers` et les `XxxRenderers` (couche passée à chaque enregistrement),
`client/capture/HoloControlCapture.java`, `client/interact/HoloInteractions.java`,
`client/modules/camera/attackguard/AttackGuardClient.java` (appel d'`addPassive`).

## Livrables
1. L'option validée, implémentée sans allocation par frame (listes triées à l'enregistrement, pas au dessin).
2. Chaque renderer existant est placé dans une couche (ou appelé dans l'ordre), selon une table dans le compte rendu
   et dans `client/render/package-info.java` : `HoloFocusBridge` en dernier dans `View` ; `SpaceSkyRenderer` (fond)
   avant `HoloSkyBridge` ; `SpaceKnowledgeSkyRenderer` après ; `RadarRenderer` puis contour de ciblage.
3. Les commentaires d'ordre de `ModuleRenderers` deviennent inutiles : les retirer ou les remplacer par la couche.
4. CLAUDE.md, section « Ajouter un renderer », mise à jour (le texte, pas encore l'intégration complète : F37).

## Interdits
- Changer le contrat des familles (stages, profondeur).
- Abonner un renderer aux événements Forge.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu :
  - fenêtre d'outils visée (focus de l'éditeur) dessinée au-dessus de la cible et de la roue du saut Space ;
  - ciel Space : objets `Sky` visibles devant le fond opaque ;
  - surcouche Space Knowledge au-dessus du ciel ;
  - contour de ciblage au-dessus du radar ;
  - croix d'Attack Guard et interaction au regard : l'attaque est bloquée sur un joueur protégé même quand un widget
    est survolé (vérifier l'ordre attendu avec l'utilisatrice si ambigu).

## Compte rendu attendu
Option appliquée, table des couches (renderer → couche), fichiers, scénarios.
