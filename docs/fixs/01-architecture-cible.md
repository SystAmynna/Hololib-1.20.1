# Architecture cible

État visé une fois les tâches du dossier faites. C'est la référence des cartes : une carte dit « § 3 » au lieu de
répéter la cible.

Ce document ne fait **pas** autorité sur l'API. Tant que la décision FD correspondante n'existe pas dans
[`decisions.md`](decisions.md), chaque point marqué **(à valider)** reste une **proposition**. L'orchestrateur met
ce document à jour après chaque validation ou refus.

Chemins relatifs à `src/main/java/com/amynna/hololib/`. Constats : [`rapport_mod1.md`](../../rapport_mod1.md).

---

## 1. Ce qui ne change pas

Les fondations sont saines (rapport § 3.7) et restent telles quelles :

- séparation modèle commun / rendu client ;
- réplication de l'état des modules, jamais des widgets ;
- serveur autoritaire ;
- mémoires équipées / dormantes / orphelines ;
- façades `Backend` ;
- quatre familles de renderers et `RenderDispatcher` seul abonné ;
- `HoloControlCapture` seul abonné aux inputs ;
- traits de widgets ;
- thèmes ;
- aucun Mixin.

Aucune tâche ne remet ces principes en cause.

## 2. Packages ajoutés ou déplacés

| Cible | Contenu | Origine | Tâche |
|---|---|---|---|
| `holo/config/` **(à valider)** | `Configurable`, `ConfigWindow` (fenêtre de configuration générique, son `Backend`) | `modules/camera/config/` | F12 |
| `holo/quickaction/` **(à valider)** | `QuickActionProvider`, `QuickAction`, `QuickActionIcon` | `modules/camera/quickaction/` | F12 |
| `holo/targeting/` **(à valider)** | `TargetingThroughWalls` | `modules/camera/targeting/` | F12 |
| `holo/window/HoloWindowStyle` **(à valider)** | accent, alpha de fond, motif, bordure, barre de titre des fenêtres de module | constantes de `ConfigWindow` et code recopié dans 4 fenêtres de marker | F13 |
| `client/render/util/` **(à valider)** | `HoloRenderMath.basis`, `HoloVertices.vertex` sans allocation, `FullscreenQuad`, `CelestialRotation` | 6 + 7 + 3 + 3 copies | F23 |
| `client/render/holo/HoloPlacements` (interne) | un `Matrix4f` par famille (HUD, vue, ciel, monde, overlay), utilisé par les ponts (`mulPose`) et par `HoloPicking` | ponts et `HoloPicking` | F24 |
| `holo/memory/HoloServerHandlers` (interne) | `handleMove`, `handleOpen`, `handleAction`, `handleEdit`, permissions | `HoloContainers` | F28 |
| `holo/object/ObjectState` **(à valider)** | état d'un objet : ancrage, transform, visibilité, accès, affichage, ouverture, offset, repère, portée ; `save` / `load` / `write` / `read` | `HoloObject.restore` (9 paramètres), `ObjectData`, `HoloModule.saveAll` / `loadAll` | F18 |

Après F12, `modules/camera/config`, `quickaction` et `targeting` ne gardent que leur module (`ConfigModule`, listes,
écrou, roue, `TargetingModule`…). Les autres modules dépendent de `holo/`, plus de ces modules.

## 3. Règles visées

### 3.1 Ordre de rendu et de capture explicite (F11, à valider)

- **Un seul** abonné à `FMLClientSetupEvent` enregistre les renderers du framework et appelle les enregistrements
  des modules et des démos, dans un ordre écrit en un seul endroit.
- Ou bien `HoloRenderers.register(renderer, layer)` avec des **couches nommées** (FC7). Exemple pour les
  `ViewRenderer` : `BACKGROUND < OBJECTS < MODULE_OVERLAY < FOCUS`. L'ordre à l'intérieur d'une couche est l'ordre
  d'enregistrement.
- `HoloFocusBridge` est toujours dessiné en dernier dans sa famille. `HoloSkyBridge` est toujours au-dessus du fond
  opaque de Space.
- Les captors passifs suivent la même règle : `addPassive(captor, priority)`, ou un enregistrement unique.

### 3.2 Recherche des modules (F16, F17, à valider)

- `HoloContainer.find(Class<T>)` / `find(HoloModuleType<T>)` typés, côté serveur et côté client
  (`ClientHoloContainer`).
- Côté client, `ClientHoloWorld.localModule(type)` est mis en cache **par tick** et invalidé à chaque
  ajout / retrait.
- Les parcours `of(uuid)` recopiés (`TargetingModule`, `KeysModule`, `AttackGuardModule`, `SpaceEnvironmentModule`)
  s'appuient sur `find`. Les tables statiques `SpaceMapModule.of` / `AreaDisplayerModule.of` restent possibles, mais
  sont vidées à l'arrêt (F04).
- **Un seul style de branchement client** : chaque module qui a une logique client expose un `Backend` installé au
  setup. Le polling d'un module par une classe client passe par le cache ci-dessus.

### 3.3 État d'objet et version de format (F18, à valider)

- `ObjectState` réunit les champs d'un objet. `HoloObject.restore(...)` à 9 paramètres positionnels disparaît.
- Ajouter un champ = un seul endroit (plus la migration).
- Le NBT d'une mémoire porte un entier `format`. L'absence de `format` = format actuel (`0`), relu à l'identique.
- Chaque changement de format est accompagné d'une migration testée (F25 / F26).

### 3.4 Droits des actions (F19, à valider)

- `HoloModule.acceptsAction(ServerPlayer player, @Nullable HoloObject source)` est appelé par le framework après ses
  propres vérifications.
- Défaut **restrictif**, selon la mémoire :
  - joueur : le propriétaire, ou un viewer avec une source (comme aujourd'hui) ;
  - dimension et position **sans source** : op niveau 2 ;
  - avec une source : la règle actuelle (`PUBLIC` + `HoloAccess`).
- Les modules qui veulent plus large (Space Map, tout joueur à portée) le surchargent.
- Les vérifications « par redondance » du propriétaire dans les modules sont retirées (une seule source de vérité).

### 3.5 Édition découplée du cœur client (F15, à valider)

- `client/holo`, `client/render/holo` et `client/interact` n'importent plus `client/edit`.
- `client/edit` installe au setup des **politiques** :
  - `HoloRoutingPolicy` : `reveals(object)`, `hudSuspended()` ;
  - `HoloMirrorPolicy` : `holds(object)` (placement reçu ignoré) ;
  - `HoloPickingOverride` : gizmos et poignées visés avant les objets ;
  - `HoloInteractionPolicy` : `stillValid(object)`.
- Sans politique installée, le cœur se comporte comme sans session d'édition.
- `ClientHoloWorld` n'appelle plus `HoloRouting.refresh` directement. Le routage s'abonne au tick client, ou
  `ClientHoloWorld` publie un signal « miroirs mis à jour ».

### 3.6 Placement unique (F24)

- Une seule fonction par famille calcule la matrice de placement d'un objet (`Matrix4f`).
- Les ponts l'appliquent au `PoseStack` (`mulPose(Matrix4f)`), `HoloPicking` l'inverse, `HoloHudPreview` la
  réutilise.
- Le picking et le dessin ne peuvent plus diverger.
- La position et l'orientation d'un objet monde sont calculées **une fois par frame** (F32).

## 4. Découpes des god classes

Aucun changement de comportement (README, « Refactorisation »).

| Classe (lignes) | Classes visées | Tâche |
|---|---|---|
| `SpaceEnvironmentModule` (2198) | `SpaceGalaxyEditor` (API d'édition de la galaxie), `SpaceSyncCodec` (blocs chaud / froid, `writeSync` / `readSync` / `rebuildCold`), `SpaceClientMirror` (prédiction, réconciliation, historique 32 ticks, horloge d'affichage, `pose` / `displayDays` / `bodyPositions`) ; le module garde la simulation serveur, le pilote et l'archive | F20 |
| `SpaceConfiguratorModule` (1365) | `SpaceConfiguratorActions` (handler et validation), `SpaceHandleController` (poignées sondées) ; les encodeurs de requêtes vont avec la fenêtre | F21 |
| `SpaceMapModule` (1160) | `SpaceMapGeometry` (cube ↔ marker, points), `SpaceMapTargetPost` (CIBLER / DÉCIBLER) | F21 |
| `SpaceMapCubeRenderer` (1417) | `SpaceMapLayout`, `SpaceMapGalaxyPass`, `SpaceMapSystemPass`, `SpaceMapCrossOverlay` | F22 |
| `SpaceKnowledgeSkyRenderer` (1061) | `SpaceOrbitClipper`, `SpaceGazeLabel` | F22 |
| `HoloContainers` (473) | `HoloServerHandlers` | F28 |
| `HoloEditSession` (647) | `HoloEditHeld` (objets tenus : repère, `toReal` / `toDisplayed`, `release`) | F28 |
| `HoloEditTools` (646) | `HoloGizmoMath` (`raySegment`, `rayRing`, `lineParam`, `ringAngle`) | F28 |

Cible indicative : moins de 600 lignes par classe, une responsabilité nommée par classe.

## 5. Dépendances entre modules visées

```
camera/attackguard ──> camera/keys
camera/keyseditor  ──> camera/keys
camera/spacepilot  ──> dimension/space
monde/spacecontroller ──> dimension/space            (plus de cycle : F14)
monde/spacemap     ──> dimension/space
monde/spaceconfigurator ──> dimension/space, monde/spacemap
monde/areaconfigurator  ──> monde/areadisplayer
(tous les modules configurables, à actions rapides ou ciblables ──> holo/ seulement : F12, F13)
```

F14 casse le cycle `spacepilot` ↔ `spacecontroller` **dans les packages Space**. Une interface de `dimension/space`
(par exemple `SpacePilotLink` ou un état de joystick lu par les cercles) remplace les imports croisés de
`SpacePilotPad` et de `SpacePilotModule`.

## 6. Tests (F25, F26)

- `src/test/java/com/amynna/hololib/...`, JUnit 5 (FC6), lancés par `./gradlew test`.
- Seulement du code pur, sans `Minecraft` ni niveau. Les classes qui ont besoin d'un `CompoundTag` ou d'un
  `FriendlyByteBuf` les créent directement (classes vanilla sans état global).
- Cible : `space/math` (déterminisme bit à bit de `SpaceMotion.step`, Kepler, saut, croisière, spin),
  `HoloEditRules`, `HoloEditMath`, `HoloTextBuffer`, `AreaGrid`, `AreaMesher`, `SpaceFormat`, relecture NBT des
  formats anciens (F18).
