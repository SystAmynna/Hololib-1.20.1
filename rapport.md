# Rapport de fonctionnement — Amynna Holo Lib

État des sources au commit `a7a182b` (« Mémoires, inspection, holoObjects, transforms »), 5 octobre 2026.
Mod Forge 47.4.10 pour Minecraft 1.20.1 (Java 17, mappings officiels), identifiant `hololib`, package racine
`com.amynna.hololib`. Les chemins ci-dessous sont relatifs à `src/main/java/com/amynna/hololib/`.

---

## Sommaire

1. [Présentation](#1-présentation)
2. [Concepts clés](#2-concepts-clés)
3. [Côté serveur : mémoires, conteneurs, inspection](#3-côté-serveur--mémoires-conteneurs-inspection)
4. [Réseau](#4-réseau)
5. [Côté client : miroirs et routage](#5-côté-client--miroirs-et-routage)
6. [Pipeline de rendu](#6-pipeline-de-rendu)
7. [Scénarios pas à pas](#7-scénarios-pas-à-pas)
8. [Commandes](#8-commandes)
9. [Étendre le mod](#9-étendre-le-mod)
10. [Référence par package](#10-référence-par-package)
11. [Limites connues et points ouverts](#11-limites-connues-et-points-ouverts)

---

## 1. Présentation

Holo Lib est une **bibliothèque d'affichages holographiques** : fenêtres à widgets, objets 3D, posés dans le ciel,
dans le monde, autour de la tête du joueur ou à l'écran. Un mod tiers déclare des **modules** (un état, une logique,
des objets) ; Holo Lib les sauvegarde, les réplique aux bons joueurs et les dessine avec le bon renderer.

### Trois couches

| Couche | Packages | Chargée sur | Rôle |
|---|---|---|---|
| **Commun** (modèle) | `holo.*`, `network`, `command`, `demo` | client et serveur | décrit *ce qui existe* et *où* ; aucune classe client |
| **Serveur** (autorité) | `holo.memory.*`, `holo.inspect` (logique serveur) | serveur logique | persistance, logique des modules, décide qui reçoit quoi |
| **Client** (miroirs, rendu) | `client.holo`, `client.render.*` | client uniquement | recrée les modules reçus, les dessine |

Règle structurante : **aucune classe client dans le code commun**. Un `HoloObject` ne sait pas se dessiner (comme
`Entity` / `EntityRenderer`) ; les widgets dessinent sur une interface commune (`HoloCanvas`) implémentée côté client ;
les paquets délèguent leur traitement client à `client.holo.ClientHoloPackets` via `DistExecutor`. Le serveur dédié
peut donc charger tout le code commun.

### Point d'entrée

[`HoloLib`](src/main/java/com/amynna/hololib/HoloLib.java) (classe `@Mod`) inscrit sur le bus du mod :
- le registre Forge des types de modules (`HoloModuleTypes.REGISTER`) et les modules de démonstration
  (`DemoModules.REGISTER`) ;
- la capability des mémoires de joueur (`HoloContainers::registerCapabilities`) ;
- le canal réseau (`HoloNetwork.register`, au setup commun).

Le reste s'abonne lui-même aux événements Forge (`@Mod.EventBusSubscriber`) : `HoloContainers` (cycle de vie
serveur), `PlayerMemoryCapability`, les commandes, `ClientHoloWorld` (tick client), `RenderDispatcher` (rendu),
`HoloBridges` et `DemoRenderers` (enregistrement des renderers au setup client).

---

## 2. Concepts clés

### 2.1 HoloObject : le modèle, pas le dessin

[`holo/object/HoloObject.java`](src/main/java/com/amynna/hololib/holo/object/HoloObject.java) est ce qui peut être
dessiné. Il porte :

| Donnée | Rôle | Synchronisée / sauvegardée par |
|---|---|---|
| `key` | nom unique dans le module, identique des deux côtés | — (déterminé par le code du module) |
| `anchor` (`HoloAnchor`) | repère d'origine, donc famille de renderers | le framework |
| `transform` (`HoloTransform`) | placement dans le repère de l'ancrage | le framework |
| `visibility` (`HoloVisibility`) | `PRIVATE` (propriétaire seul) ou `PUBLIC` | le framework |
| `boundingRadius()` | rayon englobant, pour le culling | — |
| `tick()` | animations d'état, appelé chaque tick client | — |

Tout le reste du contenu (texte d'une fenêtre, couleur d'un cube...) **découle de l'état du module** : il n'est jamais
envoyé tel quel. Le dessin est assuré par un `HoloObjectRenderer` enregistré pour la classe de l'objet
(`HoloObjectRenderers`, résolution par héritage).

Deux objets concrets existent : `HoloWindow` (fenêtre plate à widgets) et `DemoCube` (cube de démonstration).

### 2.2 Ancrage + transform : où est l'objet

L'**ancrage** ([`HoloAnchor`](src/main/java/com/amynna/hololib/holo/object/HoloAnchor.java), interface scellée,
valeurs immuables) choisit le repère d'origine et ses options. Le **transform**
([`HoloTransform`](src/main/java/com/amynna/hololib/holo/object/HoloTransform.java), 9 flottants **mutables** :
`x, y, z`, `yaw, pitch, roll`, `scaleX, scaleY, scaleZ`) place l'objet dans ce repère. La rotation suit les
conventions de Minecraft (yaw 0 = sud, positif vers la droite ; pitch positif vers le bas), appliquée yaw → pitch →
roll ; avec un roll nul, la face `+Z` de l'objet pointe dans la direction `(yaw, pitch)`.

| Ancrage | Famille | Repère d'origine | `x, y, z` du transform | Rotation / échelle |
|---|---|---|---|---|
| `Sky(celestial)` | ciel | œil de la caméra, axes du monde (ou célestes si `celestial` : suit le cycle jour/nuit) | translation telle quelle | normales |
| `World()` | monde | **hôte** de la mémoire : joueur propriétaire, marker, ou origine du monde (dimension) | translation telle quelle, axes du monde | normales |
| `Local(follow)` | vue (caméra) | œil ; axes du monde (relatif) ou du regard (follow) | `x` = yaw, `y` = pitch de la direction, `z` = distance à l'œil ; l'objet y est tourné vers l'œil | appliquées ensuite, dans le repère de l'objet |
| `Hud(alignX, alignY)` | HUD | point `(alignX × largeur, alignY × hauteur)` de l'écran | `x, y` en pixels GUI (`y` vers le bas, suit l'échelle du GUI des options), `z` = **ordre de dessin** (plus grand = devant) | normales ; échelle = pixels GUI par unité |

Dans tous les cas, l'objet se dessine dans son **repère local** : 1 unité = 1 bloc (1 pixel GUI en HUD à l'échelle 1),
centré à l'origine, face visible vers `+Z`, `+Y` en haut.

Le transform est **possédé** par l'objet : le constructeur copie celui qu'on lui passe, `object.transform()` renvoie
l'instance vivante. Chaque setter (`setTranslation`, `setRotation`, `setScale`, `set`, `setLookingAtOrigin`) ne fait
rien si la valeur ne change pas, sinon lève un drapeau `dirty` ; `HoloObject.syncDirty()` l'inclut. Un module déplace
donc un objet simplement en modifiant son transform côté serveur : l'envoi part au tick suivant.
`setLookingAtOrigin(yaw, pitch, distance)` place l'objet sur une sphère et le tourne vers le centre (cas typique du
ciel).

Bornes des demandes venant d'un client : `HoloAnchor.acceptable(transform)`.

| Ancrage | Conditions |
|---|---|
| tous | composantes finies, échelles dans `]0, 4096]` (`HoloTransform.isValid`) |
| `World` | `‖(x, y, z)‖ ≤ 64` blocs (`MAX_REACH`) |
| `Local` | `z` (distance) dans `[0.1, 16]` |
| `Hud` | `alignX, alignY` dans `[0, 1]`, `|x|, |y| ≤ 4096`, `|z| ≤ 1000` (`MAX_ORDER`) |

### 2.3 Résolution par observateur

L'ancrage donne la famille *vue par le propriétaire en première personne*. Le client la **résout** pour le joueur
local ([`HoloRouting.resolve`](src/main/java/com/amynna/hololib/client/render/holo/HoloRouting.java#L103)) :

| Ancrage | Objet du joueur local (ou d'une mémoire sans propriétaire), 1re personne | Joueur local en F5 | Objet d'un autre joueur (inspecté) |
|---|---|---|---|
| `Sky` | ciel | ciel | ciel |
| `World` | monde | monde | monde (origine = l'autre joueur) |
| `Local` | vue | **monde**, autour de sa propre tête, selon son regard | **monde**, autour de la tête de l'autre joueur |
| `Hud` | HUD | **masqué** | jamais |

### 2.4 Modules

[`HoloModule`](src/main/java/com/amynna/hololib/holo/module/HoloModule.java) = un état + une logique + des objets. Le
module existe **des deux côtés**, comme une entité : l'instance serveur fait autorité (logique, sauvegarde) ; chaque
client concerné en a un **miroir** recréé à partir du type et de l'état synchronisé.

Cycle de vie :
- **Serveur** : `load(tag)` (état sauvegardé) → `onLoad()` (crée les objets *à partir de l'état*) → le framework
  restaure ancrages, transforms et visibilités sauvegardés. Puis `serverTick(level)` chaque tick, `save(tag)` à la
  sauvegarde, `handleAction(player, buf)` sur action d'un client, `onUnload()` au retrait.
- **Client** : `readSync(buf)` → `onLoad()` → restauration des placements reçus. À chaque mise à jour : `readSync`
  puis `onSync()`. `clientTick()` chaque tick.

Points d'API :
- `addObject(o)` / `removeObject(key)` ; un objet ajouté ou retiré plus tard doit l'être **des deux côtés** (le client
  le déduit de l'état reçu).
- `markDirty()` (serveur) : l'état sera renvoyé au prochain tick via `writeSync(buf, forOwner)` ; `forOwner = false`
  pour les observateurs : n'écrire que ce qu'ils ont le droit de voir.
- `sendAction(writer)` (client) : envoie des octets à `handleAction` côté serveur, si le miroir en a le droit
  (`HoloModuleHost.canAct()` : client du propriétaire, ou n'importe quel destinataire d'une mémoire sans
  propriétaire).

Un **type** de module ([`HoloModuleType`](src/main/java/com/amynna/hololib/holo/module/HoloModuleType.java)) est une
fabrique enregistrée dans le registre Forge `hololib:module_type`
([`HoloModuleTypes`](src/main/java/com/amynna/hololib/holo/module/HoloModuleTypes.java), ni sauvegardé ni synchronisé :
les modules sont désignés par leur identifiant). Chaque type a **une seule compatibilité**
([`HoloModuleTarget`](src/main/java/com/amynna/hololib/holo/module/HoloModuleTarget.java) : `DIMENSION`, `POSITION`,
`PLAYER`) et une mémoire contient **au plus une instance** de chaque type.

[`HoloModuleHost`](src/main/java/com/amynna/hololib/holo/module/HoloModuleHost.java) donne le contexte d'une instance :
portée, côté logique, `ownerView` (vrai côté serveur et chez le client propriétaire ; faux chez un observateur, qui ne
reçoit que l'état public).

### 2.5 Mémoires et portées

Une [`HoloMemory`](src/main/java/com/amynna/hololib/holo/memory/HoloMemory.java) est une **sauvegarde** (données
seulement) : une liste d'entrées `(type, instanceId, data)` et, pour une mémoire de joueur, ses accès d'inspection.
Quand elle est chargée, un `HoloContainer` porte les modules vivants ; l'état y est recopié à chaque sauvegarde.

La [`HoloScope`](src/main/java/com/amynna/hololib/holo/memory/HoloScope.java) identifie la mémoire, des deux côtés et
sur le réseau :

| Portée | Stockage | Chargée | Hôte (origine des `World`) | Observateurs |
|---|---|---|---|---|
| `Player(uuid)` | capability du joueur (`HoloMemoryBank` : plusieurs mémoires, une active) | tant que le joueur est connecté | l'entité du joueur | joueurs qui suivent son entité **et l'inspectent** |
| `Dimension` | `SavedData` du niveau (`data/hololib_dimension.dat`) | avec le niveau | origine du monde | tous les joueurs de la dimension |
| `Position(markerId)` | registre global `HoloMarkers` (`data/hololib_markers.dat` de l'overworld) | avec l'entité marker | le marker | joueurs qui suivent le chunk du marker |

Seule la portée joueur a un propriétaire. Pour les autres, `PRIVATE` signifie « invisible de tous ».

### 2.6 Visibilité et partage

Un module est **partagé** s'il a au moins un objet `PUBLIC` (`HoloContainer.isShared`). Seuls les modules partagés
sont envoyés aux observateurs, avec l'état `writeSync(..., false)` ; pour un objet `PRIVATE`, l'observateur reçoit la
visibilité mais ni l'ancrage ni le transform (`ObjectData` les met à `null`), et le client ne le dessine pas.

### 2.7 Inspection

Par défaut, **un joueur ne voit aucun affichage d'un autre joueur**. A voit B si A suit l'entité de B **et** si la
mémoire active de A l'autorise. Il ne voit alors que les objets `PUBLIC` de B, jamais son HUD. Détails en
[§3.4](#34-inspection).

---

## 3. Côté serveur : mémoires, conteneurs, inspection

### 3.1 HoloContainer

[`holo/memory/HoloContainer.java`](src/main/java/com/amynna/hololib/holo/memory/HoloContainer.java) (fil serveur) tient
les modules vivants d'une mémoire chargée.

- **`load(memory)`** ([L167](src/main/java/com/amynna/hololib/holo/memory/HoloContainer.java#L167)) : pour chaque
  entrée, crée le module depuis son type et appelle `loadAll` (état, `onLoad`, placements). Un type inconnu (mod
  retiré) ou un chargement qui lève une exception : l'entrée est gardée telle quelle comme **orpheline** et réécrite à
  la sauvegarde. Puis hook `onMemoryLoaded(memory)`.
- **`saveInto(memory)`** ([L194](src/main/java/com/amynna/hololib/holo/memory/HoloContainer.java#L194)) : hook
  `onMemorySaving(memory)`, puis remplace les entrées par `saveAll()` de chaque module, plus les orphelines.
- **`install(type)` / `uninstall(id)`** : ajout ou retrait en cours de jeu, avec envoi immédiat au propriétaire et,
  si le module est partagé, aux observateurs.
- **`ownerSnapshot()` / `observerSnapshot()`** : état complet (propriétaire) ou modules partagés seulement
  (observateur), avec l'`origin()` de la portée.
- **`flush()`** ([L225](src/main/java/com/amynna/hololib/holo/memory/HoloContainer.java#L225)), en fin de tick : pour
  chaque module,
  1. état sale → `UPDATE` au propriétaire ;
  2. objets sales (ancrage, transform ou visibilité) → `HoloObjectPacket` au propriétaire ;
  3. partage : le module vient de devenir partagé → `ADD` aux observateurs ; a cessé de l'être → `REMOVE` ; reste
     partagé → `UPDATE` d'état et `HoloObjectPacket` des objets sales ;
  4. `clearDirty()` ; si quelque chose a changé, `markPersistDirty()`.

Points d'extension des sous-classes : `target()`, `level()`, `sendToOwner`, `sendToObservers`, `markPersistDirty`,
`origin()`, `onMemoryLoaded`, `onMemorySaving`.

### 3.2 Les trois conteneurs

- [`PlayerHoloContainer`](src/main/java/com/amynna/hololib/holo/memory/player/PlayerHoloContainer.java) : mémoire
  active d'un joueur connecté. Retrouve l'entité par UUID (elle change au respawn). `sendToObservers` envoie à son
  ensemble **`viewers`**, géré par `HoloInspections`. Porte l'**inspection vivante** du joueur, copiée depuis la mémoire
  active au chargement et recopiée dedans à la sauvegarde. `selectMemory` sauvegarde, décharge, active une autre
  mémoire, recharge et renvoie l'état. La persistance passe par la sauvegarde du joueur
  (`markPersistDirty` ne fait rien).
- [`DimensionHoloContainer`](src/main/java/com/amynna/hololib/holo/memory/dimension/DimensionHoloContainer.java) :
  ouvert avec le niveau, fermé à son déchargement ; observateurs = `PacketDistributor.DIMENSION`.
  [`DimensionHoloData`](src/main/java/com/amynna/hololib/holo/memory/dimension/DimensionHoloData.java) recopie l'état
  vivant avant chaque sauvegarde du niveau.
- [`PositionHoloContainer`](src/main/java/com/amynna/hololib/holo/memory/position/PositionHoloContainer.java) : ouvert
  quand l'entité marker rejoint le niveau, fermé quand elle le quitte. Les markers ne sont jamais envoyés aux clients :
  les observateurs sont donc les joueurs qui suivent son **chunk**. `origin()` = position du marker.
  `trackMovement()` ([L72](src/main/java/com/amynna/hololib/holo/memory/position/PositionHoloContainer.java#L72)),
  appelé chaque tick : si le marker a bougé dans le même chunk, envoie `HoloOriginPacket` ; s'il a changé de chunk,
  l'ancien chunk oublie la mémoire et le nouveau reçoit un snapshot.

[`HoloMarkers`](src/main/java/com/amynna/hololib/holo/memory/position/HoloMarkers.java) est la source de vérité des
markers : id (minuscules, chiffres, `_ . -`, 64 caractères max), dimension, dernière position, UUID de l'entité,
mémoire. La mémoire reste modifiable même quand le marker n'est pas chargé. Les entités sont des `minecraft:marker`
portant le tag `hololib` et leur id comme nom.

### 3.3 HoloContainers : index et événements

[`holo/memory/HoloContainers.java`](src/main/java/com/amynna/hololib/holo/memory/HoloContainers.java) indexe les
conteneurs chargés (`PLAYERS`, `DIMENSIONS`, `POSITIONS`), expose l'API serveur et réagit aux événements Forge.

| Événement | Effet |
|---|---|
| `PlayerLoggedInEvent` | crée le `PlayerHoloContainer`, charge la mémoire active, envoie l'`ownerSnapshot`, `HoloInspections.loggedIn` (viewers dans les deux sens), envoie la mémoire de dimension |
| `PlayerLoggedOutEvent` | `HoloInspections.loggedOut` (les viewers l'oublient), recopie l'état dans la mémoire active, décharge |
| `PlayerChangedDimensionEvent`, `PlayerRespawnEvent` | renvoie la mémoire de la dimension |
| `PlayerEvent.StartTracking` / `StopTracking` (cible = joueur) | `HoloInspections.startTracking` / `stopTracking` |
| `LevelEvent.Load` / `Unload` | ouvre / ferme la mémoire de dimension (et oublie les positions du niveau) |
| `EntityJoinLevelEvent` (marker du mod) | ouvre sa mémoire de position ; un marker inconnu, copié ou supprimé hors chargement est annulé |
| `EntityLeaveLevelEvent` | ferme la mémoire de position |
| `ChunkWatchEvent.Watch` / `UnWatch` | snapshot / oubli des mémoires de position du chunk |
| `ServerTickEvent` (fin) | pour chaque conteneur : `trackMovement` (positions), `tick` (`serverTick` des modules), `flush` ([L359](src/main/java/com/amynna/hololib/holo/memory/HoloContainers.java#L359)) |
| `ServerStoppedEvent` | vide les index et l'index de suivi de l'inspection |

Réseau entrant :
- [`handleMove`](src/main/java/com/amynna/hololib/holo/memory/HoloContainers.java#L163) : seul le propriétaire peut
  déplacer un objet de **sa** mémoire de joueur. Si `anchor.acceptable(transform)` est vrai, l'ancrage et le transform
  sont appliqués (puis renvoyés au tick suivant par `flush`) ; sinon le placement serveur est renvoyé au client, qui
  l'avait déjà appliqué localement.
- `handleAction` → [`actionTarget`](src/main/java/com/amynna/hololib/holo/memory/HoloContainers.java#L197) : mémoire de
  joueur seulement pour son propriétaire ; dimension = celle du joueur ; position = même niveau et à moins de
  `(distance de vue + 1) × 16` blocs du marker. Le contenu est ensuite à valider par `handleAction` du module.

Persistance des joueurs :
[`PlayerMemoryCapability`](src/main/java/com/amynna/hololib/holo/memory/player/PlayerMemoryCapability.java) attache la
`HoloMemoryBank` aux joueurs côté serveur. À chaque sérialisation, elle recopie d'abord l'état vivant
(`saveLivePlayer`) ; au clone (respawn, retour de l'End), la nouvelle entité reprend la banque de l'ancienne.

### 3.4 Inspection

Package [`holo/inspect`](src/main/java/com/amynna/hololib/holo/inspect/package-info.java).

**Donnée** : [`HoloInspection`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspection.java), enregistrée dans
chaque `HoloMemory` (clé NBT `inspect`) :
- `all` : tous les joueurs, y compris ceux qui se connecteront plus tard ;
- `players` : joueurs autorisés un par un ;
- `excluded` : joueurs retirés un par un alors que `all` est actif.

`allows(t) = t ∈ players ∨ (all ∧ t ∉ excluded)`. `set(t, on)` ajoute à `players` ou retire (et exclut si `all`) ;
`setAll(true)` active `all` et oublie les exclusions ; `setAll(false)` efface tout. Comme l'inspection vit dans la
mémoire, **changer de mémoire change les accès**.

**Autorité** : [`HoloInspections`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspections.java).
- `TRACKERS` : joueur suivi → joueurs qui suivent son entité. Cet index est alimenté par `StartTracking` /
  `StopTracking`, indépendamment de l'existence des conteneurs, car Forge signale le suivi **avant**
  `PlayerLoggedInEvent` (`ServerLevel.addNewPlayer` précède `firePlayerLoggedIn` dans `PlayerList.placeNewPlayer`).
  `StopTracking` est émis aussi bien quand la cible quitte le niveau que quand le suiveur le quitte
  (`ChunkMap.removeEntity` → `ServerEntity.removePairing`), donc l'index se nettoie seul.
- [`canView(viewer, owner)`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspections.java#L72) :
  `viewer ≠ owner`, viewer ∈ `TRACKERS[owner]`, et l'inspection vivante du viewer autorise owner.
- [`update(owner, viewer)`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspections.java#L135) compare
  `canView` avec l'appartenance à `owner.viewers()`. Si le viewer doit être ajouté, il reçoit
  `owner.observerSnapshot()` ; s'il doit être retiré, il reçoit `HoloForgetPacket`.
- [`refresh(viewer)`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspections.java#L82) applique `update` avec
  chaque autre joueur connecté. Appelée à la connexion du viewer, à son changement de mémoire
  (`HoloContainers.selectMemory`) et après chaque commande qui change quelque chose.
- [`request(viewer, target|null, enable, force, requester)`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspections.java#L56) :
  si `enable && !force`, poste `HoloInspectEvent`, et la demande est refusée (`DENIED`) si un abonné l'annule. Sinon,
  modifie l'inspection vivante du viewer et rafraîchit (`CHANGED`), ou ne fait rien (`UNCHANGED`). Si la mémoire du
  viewer n'est pas disponible, le résultat est `UNAVAILABLE`.

**Politique** : [`HoloInspectEvent`](src/main/java/com/amynna/hololib/holo/inspect/HoloInspectEvent.java),
`@Cancelable`, posté sur `MinecraftForge.EVENT_BUS` pour chaque activation non forcée, avec `viewer`, `target`
(`null` = tous) et `requester` (entité à l'origine, `null` pour la console). Aucun abonné pour l'instant : tout est
accepté. Jamais posté pour un retrait ni pour `/hololib_force_inspect`.

---

## 4. Réseau

Canal `hololib:main`, protocole `"2"` (les deux côtés doivent avoir la même version), défini dans
[`HoloNetwork`](src/main/java/com/amynna/hololib/network/HoloNetwork.java). Tous les paquets serveur → client portent
la `HoloScope` concernée : les mêmes paquets servent au propriétaire et aux observateurs, pour les trois portées.

| Paquet | Sens | Contenu | Émis par | Traitement |
|---|---|---|---|---|
| `HoloSnapshotPacket` | S → C | portée, `forOwner`, modules (état + objets), origine optionnelle | connexion, arrivée d'un viewer ou d'un chunk, changement de mémoire, ouverture d'un marker | remplace tout ce que le client connaissait de la portée |
| `HoloModulePacket` | S → C | `ADD` / `UPDATE` / `REMOVE` d'un module | `install`, `uninstall`, `flush` | crée le miroir ; `readSync` + `onSync` ; décharge |
| `HoloObjectPacket` | S → C | placement (ancrage + transform) et visibilité d'un objet | `flush`, refus de déplacement | `HoloObject.restore` |
| `HoloOriginPacket` | S → C | nouvelle origine d'une portée | marker déplacé dans son chunk | `ClientHoloContainer.origin` |
| `HoloForgetPacket` | S → C | portée à oublier | fin de suivi (viewer retiré, chunk), fermeture d'un marker, déconnexion | `ClientHoloWorld.forget` |
| `HoloMoveObjectPacket` | C → S | portée, module, clé, ancrage, transform | `HoloObject.requestAnchor` / `requestTransform` | `HoloContainers.handleMove` |
| `HoloModuleActionPacket` | C → S | portée, module, octets (32 767 max) | `HoloModule.sendAction` | `HoloContainers.handleAction` → `HoloModule.handleAction` |

[`HoloSyncData`](src/main/java/com/amynna/hololib/network/HoloSyncData.java) construit les données au moment de
l'envoi : `ModuleData.of(module, forOwner, withObjects)` sérialise `writeSync` en octets et ne garde, pour un
observateur, que les objets `PUBLIC` ; `ObjectData.of` copie le transform. À la lecture, tout est copié, car le
buffer réseau est libéré avant que le fil principal ne l'applique.

Ce qui ne circule **jamais** : les widgets, le contenu des objets, l'état d'un objet `PRIVATE` vers un observateur,
la position d'une entité marker (seule l'origine est envoyée).

---

## 5. Côté client : miroirs et routage

### 5.1 ClientHoloWorld et ClientHoloContainer

[`ClientHoloWorld`](src/main/java/com/amynna/hololib/client/holo/ClientHoloWorld.java) tient un
[`ClientHoloContainer`](src/main/java/com/amynna/hololib/client/holo/ClientHoloContainer.java) par portée reçue :
mémoire du joueur local, des joueurs inspectés et suivis, de la dimension, des markers dont le chunk est suivi.
Chaque conteneur client contient des **miroirs** de modules (`HoloModuleHost(scope, CLIENT, ownerView)`) et l'origine
de la portée (`Vec3.ZERO` par défaut, ou la position du marker reçue).

- **Tick client** (fin) : si le jeu n'est pas en pause, `clientTick()` de chaque module puis `tick()` de chaque objet ;
  puis `HoloRouting.refresh`.
- **Respawn / changement de dimension** (`ClientPlayerNetworkEvent.Clone`) : tout est oublié sauf la mémoire du joueur
  local. Le serveur renvoie la dimension, puis les joueurs et les chunks au fur et à mesure du suivi.
- **Déconnexion** : tout est oublié.

Un objet n'est dessiné qu'une fois son placement reçu du serveur (`HoloObject.replicated()`).

### 5.2 HoloRouting

[`HoloRouting.refresh`](src/main/java/com/amynna/hololib/client/render/holo/HoloRouting.java#L59), à chaque tick (jamais
pendant le rendu), répartit les objets connus en quatre listes (ciel, monde, vue, HUD) :
1. ignore les objets non répliqués, et ceux qui ne sont pas `PUBLIC` quand le joueur local n'en est pas propriétaire ;
2. résout la famille (tableau du [§2.3](#23-résolution-par-observateur)), selon que la mémoire est étrangère et que la
   caméra est en première personne (`options.getCameraType().isFirstPerson()`) ;
3. met à jour un `ResolvedHolo` réutilisé par objet (cache par identité) : conteneur, joueur de référence (le
   propriétaire pour une mémoire étrangère, sinon le joueur local), `ownerView`, famille ;
4. trie la liste HUD par `z` du transform (croissant : le dernier dessiné est devant).

Il n'y a pas d'allocation tant que l'ensemble des objets ne change pas.

### 5.3 ResolvedHolo

[`ResolvedHolo`](src/main/java/com/amynna/hololib/client/render/holo/ResolvedHolo.java) est l'objet tel que le voit
le joueur local. Pour la famille monde, il implémente `WorldRenderable` et calcule à chaque frame
([`position(partialTick)`](src/main/java/com/amynna/hololib/client/render/holo/ResolvedHolo.java#L61)) :
- **`World`** : origine = position interpolée du joueur propriétaire (portée joueur ; indisponible si son entité n'est
  pas chargée), ou `container.origin()` (marker, ou origine du monde) ; position = origine + translation ;
  orientation = rotation du transform.
- **`Local` dans le monde** : base = regard interpolé de l'entité de référence (follow) ou identité (relatif), puis la
  même sphère qu'en passe vue (`HoloPlacement.onSphere` version quaternion) autour de `getEyePosition`.
- `renderBounds` : sphère de rayon `boundingRadius × maxScale`.

---

## 6. Pipeline de rendu

### 6.1 Principe

Un seul abonné aux événements de rendu,
[`RenderDispatcher`](src/main/java/com/amynna/hololib/client/render/RenderDispatcher.java) (priorité `LOWEST`), route
les stages vers quatre **passes**. Chaque famille suit le même schéma : `XxxRenderer` (API publique),
`XxxRenderContext` (données de la frame, objet réutilisé) et `XxxRenderPass` (interne). Les renderers sont enregistrés
dans [`HoloRenderers`](src/main/java/com/amynna/hololib/client/render/HoloRenderers.java) (fil de rendu, ordre
d'enregistrement), jamais abonnés eux-mêmes.

Ordre d'une frame :

| # | Moment | Passe | Ce qui se passe |
|---|---|---|---|
| 1 | `AFTER_SKY` | `SkyRenderPass` | capture de la rotation caméra (le PoseStack de ce stage la contient, roll compris) ; renderers du ciel ; profondeur **vidée après** s'ils ont dessiné |
| 2 | `AFTER_ENTITIES` | `WorldRenderPass` | renderers du monde, dans le `BufferSource` du jeu, **vidé par vanilla** avec les entités ; profondeur du monde |
| 3 | `AFTER_LEVEL` | `ViewRenderPass` | profondeur **vidée avant**, brouillard neutralisé, model-view à l'identité ; projection `STABLE` (FOV du jeu, sans bobbing) ou `WORLD` ; tout est restauré après |
| 4 | overlay GUI `holo_hud` (sous tous les overlays vanilla) | `HudRenderPass` | ortho du GUI ; profondeur **vidée après** : le HUD vanilla passe toujours devant |

Le FOV effectif est relevé par `ViewportEvent.ComputeFov` (seulement celui du monde).

Garanties de chaque passe : la PoseStack doit revenir équilibrée (sinon erreur loguée et correction), les buffers sont
vidés après chaque renderer (ciel, vue, HUD), l'état GL modifié est restauré même en cas d'exception, et rien n'est
alloué par frame.

| Famille | Résultat | Repère de la pile reçue |
|---|---|---|
| `SkyRenderer` | dans la skybox, derrière tout le monde ; masqué quand vanilla ne dessine pas le ciel (`hiddenWithSky`) | centré sur l'œil, axes du monde |
| `WorldRenderer<T>` | ancré dans le monde, caché par blocs et entités ; culling distance + frustum | translaté à la position de l'objet (relative à la caméra), axes du monde |
| `ViewRenderer` | dans la vue, au-dessus du monde, sous main et GUI ; masqué en F1 par défaut | espace vue : œil, `-Z` devant, `+X` à droite |
| `HudRenderer` | à l'écran, sous le HUD vanilla ; masqué en F1, aussi dessiné sous un écran ouvert | pixels GUI, origine en haut à gauche, `y` vers le bas, `+Z` vers l'observateur |

### 6.2 Les ponts holographiques

Les quatre ponts ([`client/render/holo`](src/main/java/com/amynna/hololib/client/render/holo/package-info.java)),
enregistrés au setup client par `HoloBridges`, dessinent les listes de `HoloRouting`. Chacun place le repère d'origine
de l'ancrage, puis
[`HoloPlacement.applyLocal`](src/main/java/com/amynna/hololib/client/render/holo/HoloPlacement.java) applique la rotation
puis l'échelle du transform, et `HoloObjectRenderers.render` délègue au renderer de la classe de l'objet, avec un
`HoloDrawContext` (partialTick, famille effective, `ownerView`).

| Pont | Placement |
|---|---|
| `HoloSkyBridge` | [rotation céleste] → `translate(x, y, z)` → `applyLocal` |
| `HoloWorldBridge` (`WorldRenderer<ResolvedHolo>`, 96 blocs max, pleine lumière) | translation par la passe → `mulPose(orientation)` → `scale` |
| `HoloViewBridge` | follow : `rotY(π)` (espace vue → axes caméra de Minecraft) ; relatif : rotation caméra capturée → `onSphere(x, y, z)` → `applyLocal` |
| `HoloHudBridge` | `translate(alignX·w + x, alignY·h + y, z)` → `scale(1, −1, 1)` (GUI → repère objet) → `applyLocal` |

En HUD, `z` sert à la fois d'ordre (tri) et de profondeur GUI, pour que des objets épais se recouvrent dans le même
ordre.

### 6.3 Dessin d'une fenêtre

1. [`HoloWindowRenderer`](src/main/java/com/amynna/hololib/client/render/holo/HoloWindowRenderer.java) passe du repère
   de l'objet au repère GUI de la fenêtre : translation au coin haut gauche, puis échelle `(s, −s, s)` avec
   `s = pixelScale` (taille d'un pixel en blocs). Le texte écrit pour le GUI reste tourné vers `+Z` et est éliminé vu
   de dos.
2. La racine (`HoloPanel`) dessine son fond, sa bordure puis ses enfants dans l'ordre d'ajout ; chaque widget est
   dessiné relativement à son parent (`HoloCanvas.push/pop`).
3. [`BufferHoloCanvas`](src/main/java/com/amynna/hololib/client/render/holo/BufferHoloCanvas.java) implémente
   `HoloCanvas` sur le `MultiBufferSource` : décalages tenus en flottants (pas de `pushPose`), un pas de 0,1 pixel en
   `z` à chaque dessin contre le z-fighting. Les rectangles utilisent `HoloRenderTypes.TRANSLUCENT_QUADS`, le texte
   `Font.drawInBatch`, les textures `RenderType.text(texture)`.

Widgets disponibles ([`holo/widget`](src/main/java/com/amynna/hololib/holo/widget/package-info.java)) :
- `HoloWidget` : base, position relative au parent, visibilité, `contains`, `onClick`, `tick` ;
- `HoloPanel` : conteneur, fond, bordure, `widgetAt` ;
- `HoloLabel` : texte aligné ;
- `HoloButton` : fond, bordure, texte, action client `onPress`.

### 6.4 RenderTypes et règles

[`HoloRenderTypes`](src/main/java/com/amynna/hololib/client/render/type/HoloRenderTypes.java) fournit deux types au
format `POSITION_COLOR_LIGHTMAP` : `SOLID_QUADS` (opaques, faces arrière éliminées) et `TRANSLUCENT_QUADS`
(translucides, double face, triés, écrivent la profondeur). Tous les deux écrivent dans la cible principale.

Règles du projet : pas d'OpenGL brut, tout passe par `RenderType` / `MultiBufferSource` / `RenderSystem`. C'est ce qui
garde le cache d'état GL cohérent, permet la compatibilité Oculus/Embeddium et prépare la migration vers `GpuDevice`
en 1.21.5+. Pour passer devant ou derrière, on vide la profondeur au lieu de désactiver le test.

Pièges vérifiés dans les sources :
- à `AFTER_LEVEL`, Forge passe la pile de **projection** comme PoseStack, d'où la capture de la rotation caméra à
  `AFTER_SKY` ;
- en Fabulous, `AFTER_WEATHER` dessine dans `WEATHER_TARGET` ;
- `ITEM_ENTITY_TARGET` est déjà composé à `AFTER_LEVEL` ;
- le plan lointain vaut au moins 128 blocs (les astres sont à 100).

---

## 7. Scénarios pas à pas

### 7.1 Connexion d'un joueur A

1. Forge ajoute l'entité de A au niveau. Les joueurs proches commencent à se suivre mutuellement : `StartTracking` →
   `HoloInspections.startTracking` remplit `TRACKERS`. Les conteneurs de A n'existent pas encore, donc rien n'est
   envoyé.
2. `PlayerLoggedInEvent` : création du `PlayerHoloContainer` de A, chargement de sa mémoire active (modules + son
   inspection), puis `ownerSnapshot` envoyé à A.
3. `HoloInspections.loggedIn` : chaque joueur qui suit A et l'inspecte devient viewer de A et reçoit son
   `observerSnapshot` ; puis `refresh(A)` : A devient viewer des joueurs proches qu'il inspecte.
4. A reçoit la mémoire de sa dimension (`observerSnapshot` de la dimension). Les mémoires de position arrivent avec
   le suivi des chunks (`ChunkWatchEvent.Watch`).
5. Côté client de A : `ClientHoloContainer.add` recrée chaque module (`readSync` → `onLoad` → `restore` des
   placements) ; au tick suivant, `HoloRouting` répartit les objets et ils sont dessinés.

### 7.2 Ajout d'un module par commande

`/hololib_module add player A current hololib:demo_player` :
1. `ModuleListArgument` vérifie l'identifiant et que la compatibilité du type est `PLAYER`.
2. `ModuleSlot.of(container).add` → `HoloContainer.install` : création, `onLoad`, envoi `ADD` au propriétaire (état
   complet) et, si le module est partagé, aux viewers (état public).
3. Si la mémoire visée n'est pas chargée (mémoire de joueur inactive, marker hors chargement),
   `HoloMemories.install` crée le module le temps de produire sa sauvegarde, sans rien envoyer.

### 7.3 Déplacement d'une fenêtre

- **Par un module (serveur)** : `window.transform().setTranslation(...)` → drapeau `dirty` → au `flush` de fin de tick,
  `HoloObjectPacket` au propriétaire et, si l'objet est `PUBLIC` dans un module partagé, aux observateurs → les clients
  copient le transform → `ResolvedHolo` / les ponts le lisent à la frame suivante. Exemple :
  `DemoPlayerModule.serverTick` oriente la fenêtre `local` vers le regard du joueur au premier tick.
- **Par le client propriétaire** : le code client modifie `transform()` (effet local immédiat) puis appelle
  `requestTransform()` ; ou `requestAnchor(anchor)` pour changer d'ancrage. Dans les deux cas, un
  `HoloMoveObjectPacket` est envoyé. Le serveur vérifie la propriété et `acceptable` : si c'est accepté, le placement
  est appliqué et revient par `flush` (et part aussi aux observateurs) ; si c'est refusé, le placement serveur est
  renvoyé et écrase la modification locale.

### 7.4 A inspecte B

1. A tape `/hololib_inspect on B`. `HoloInspectCommand` → `HoloInspections.request(A, B, true, false, A)`.
2. `HoloInspectEvent` est posté ; personne ne l'annule.
3. `inspection.set(B, true)` dans le conteneur de A → `refresh(A)` → `update(B, A)` : A suit l'entité de B et
   l'inspection autorise B, donc A est ajouté aux viewers de B et reçoit `B.observerSnapshot()` (modules partagés,
   objets `PUBLIC`, état public).
4. Côté client de A : nouvelle portée `Player(B)`, modules recréés avec `ownerView = false`. `HoloRouting` les marque
   étrangers : les fenêtres `Local` de B vont dans la famille monde, autour de **la tête de B** (son regard
   interpolé pour follow) ; ses objets `World` ont pour origine la position de B ; son HUD n'est jamais dessiné.
5. Ensuite, chaque changement de B part à ses viewers (`flush` → `sendToObservers`).
6. Si B s'éloigne hors de portée de suivi, `StopTracking` → `update` → A est retiré des viewers et reçoit
   `HoloForgetPacket`. Quand B revient, `StartTracking` → snapshot. Avec `/hololib_inspect off B`, A est retiré et
   reçoit l'oubli.
7. L'accès est sauvegardé dans la mémoire active de A et persiste après reconnexion.

### 7.5 Passage en F5

Rien n'est envoyé au serveur. Au tick client suivant, `HoloRouting` voit `isFirstPerson() == false` : les objets
`Local` du joueur local passent dans la famille monde (même sphère, autour de ses yeux et selon son regard), ses
objets `Hud` ne sont plus routés. En revenant à la première personne, tout reprend sa famille normale.

### 7.6 Un marker bouge

`/tp @e[tag=hololib] ~ ~ ~5` : au tick serveur suivant, `trackMovement` détecte la nouvelle position. Dans le même
chunk, `HoloOriginPacket` part aux joueurs qui suivent le chunk ; le client met à jour l'origine et le cube de
`demo_world_pos` suit, sans que le module ait rien fait. Si le marker change de chunk, l'ancien chunk oublie la
mémoire et le nouveau reçoit un snapshot avec l'origine.

### 7.7 Changement de mémoire de joueur

`HoloContainers.selectMemory(player, name)` sauvegarde la mémoire active (modules et inspection), décharge, active
l'autre mémoire, recharge, renvoie l'état au joueur et à ses viewers, puis `HoloInspections.refresh` : les accès
d'inspection de la nouvelle mémoire s'appliquent aussitôt. Aucune commande ne l'appelle encore.

### 7.8 Respawn ou changement de dimension

- Serveur : l'ancienne entité quitte le niveau (`StopTracking` dans les deux sens, donc viewers retirés et oublis), la
  nouvelle arrive (`StartTracking`, donc snapshots). Le `PlayerHoloContainer` survit, car il est indexé par UUID. La
  mémoire de la nouvelle dimension est renvoyée.
- Client : `Clone` oublie toutes les portées sauf la mémoire du joueur local.

---

## 8. Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/hololib_marker create <id> [<x> <y> <z>]` | op 2 | crée un marker (position donnée, ou celle de l'exécutant) avec une mémoire vide |
| `/hololib_marker remove <id>` | op 2 | supprime le marker et sa mémoire (l'entité est retirée si elle est chargée, sinon à son prochain chargement) |
| `/hololib_module <add\|remove> dimension <here\|dim> <module...>` | op 2 | mémoire d'une dimension (`here` = celle de l'exécutant, l'overworld depuis la console) |
| `/hololib_module <add\|remove> world <marker> <module...>` | op 2 | mémoire de position d'un marker, chargée ou non |
| `/hololib_module <add\|remove> player <joueur> <current\|mémoire> <module...>` | op 2 | une mémoire d'un joueur connecté |
| `/hololib_inspect <on\|off>` | tous | l'exécutant voit (ou ne voit plus) **tous** les joueurs |
| `/hololib_inspect <on\|off> <joueurs>` | tous | l'exécutant voit (ou ne voit plus) ces joueurs |
| `/hololib_inspect <on\|off> <joueurs> <cibles>` | tous | chacun des `joueurs` voit (ou ne voit plus) chacune des `cibles` |
| `/hololib_force_inspect ...` | op 2 | même syntaxe, sans passer par `HoloInspectEvent` |

`<module...>` accepte plusieurs identifiants. L'autocomplétion ne propose que les modules de la bonne compatibilité,
absents pour un ajout, présents pour un retrait. Pour l'inspection, une paire où le joueur est sa propre cible est
ignorée. Chaque paire produit un message (succès, déjà dans cet état, refus, mémoire indisponible). Une modification
forcée est annoncée aux opérateurs.

Modules de démonstration (`demo/`) :
- `hololib:demo_dimension` : fenêtre dans le ciel ;
- `hololib:demo_world_pos` : cube qui tourne un bloc au-dessus du marker ;
- `hololib:demo_player` : fenêtres HUD, caméra et caméra follow, plus un cube au-dessus de la tête. Ces objets sont
  publics, sauf le HUD.

Pour tester le multijoueur : tâches Gradle `runClient`, `runClient2` (pseudo `Dev2`) et `runClient3` (`Dev3`),
chacune dans son propre dossier de jeu ; ouvrir le monde du premier client en LAN.

---

## 9. Étendre le mod

### Créer un module

```java
public final class MyModule extends HoloModule
{
    private int counter;            // etat
    private HoloLabel label;

    @Override
    public void onLoad()            // deux cotes : creer les objets a partir de l'etat
    {
        HoloWindow window = addObject(new HoloWindow("main", new HoloAnchor.Local(true),
                HoloTransform.translation(0f, 10f, 2f), 160, 48, 1f / 160f));
        window.setVisibility(HoloVisibility.PUBLIC);
        label = window.root().add(new HoloLabel(4, 4, 152, 14, Component.literal("" + counter)));
    }

    @Override public void serverTick(ServerLevel level) { /* logique ; markDirty() si l'etat change */ }
    @Override public void writeSync(FriendlyByteBuf buf, boolean forOwner) { buf.writeVarInt(counter); }
    @Override public void readSync(FriendlyByteBuf buf) { counter = buf.readVarInt(); }
    @Override public void onSync() { label.setText(Component.literal("" + counter)); }
    @Override public void save(CompoundTag tag) { tag.putInt("counter", counter); }
    @Override public void load(CompoundTag tag) { counter = tag.getInt("counter"); }
}
```

Puis l'enregistrer avec sa compatibilité, depuis le mod tiers :

```java
DeferredRegister<HoloModuleType<?>> TYPES = DeferredRegister.create(HoloModuleTypes.REGISTRY_NAME, MODID);
TYPES.register("my_module", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, MyModule::new));
```

### Créer un objet holographique

1. Étendre `HoloObject` (code commun) : constructeur `(key, anchor, transform)`, `boundingRadius()`, état d'animation
   dans `tick()`.
2. Écrire un `HoloObjectRenderer<T>` (client) qui dessine dans le repère local, avec des `RenderType` de la cible
   principale, sans vider les buffers.
3. L'enregistrer au setup client : `HoloObjectRenderers.register(MyObject.class, new MyRenderer())` dans un
   `enqueueWork` (voir `DemoRenderers`).

L'objet fonctionne alors dans les quatre familles, sans renderer de famille à écrire.

### Renderer bas niveau

Pour un dessin hors du système d'objets : implémenter `SkyRenderer`, `ViewRenderer` ou `HudRenderer`, ou étendre
`WorldRenderer<T>`, puis `HoloRenderers.register(...)` sur le fil de rendu. Un objet qui existe côté serveur
(collisions, synchronisation fine) doit plutôt être une vraie `Entity` avec son `EntityRenderer`.

### Politique d'inspection

```java
@SubscribeEvent
public static void onInspect(HoloInspectEvent event)
{
    if (event.target() != null && !areFriends(event.viewer(), event.target())) event.setCanceled(true);
}
```

---

## 10. Référence par package

### `holo.object`
- `HoloObject` : objet dessinable ; clé, ancrage, transform, visibilité ; `requestAnchor` / `requestTransform`
  (propriétaire, côté client) ; `restore` / `syncDirty` (internes).
- `HoloAnchor` : `Sky`, `World`, `Local`, `Hud` ; `family()`, `acceptable(transform)`, codecs réseau et NBT.
- `HoloTransform` : translation, rotation, échelle mutables, drapeau `dirty`, `setLookingAtOrigin`, codecs.
- `HoloVisibility` : `PRIVATE`, `PUBLIC`.

### `holo.window`, `holo.widget`
- `HoloWindow` : objet plat (largeur et hauteur en pixels, `pixelScale`), racine `HoloPanel`, `widgetAt`.
- `HoloWidget`, `HoloPanel`, `HoloLabel`, `HoloButton` : arborescence 2D, dessin via `HoloCanvas`.

### `holo.module`
- `HoloModule` : état, logique, objets ; hooks de cycle de vie ; `markDirty`, `sendAction`.
- `HoloModuleType`, `HoloModuleTypes` (registre Forge), `HoloModuleTarget`, `HoloModuleHost`.

### `holo.memory` (et `player`, `dimension`, `position`)
- `HoloMemory` : sauvegarde (entrées + inspection) ; `HoloMemories` : opérations sur une mémoire au repos ;
  `HoloScope` : identifiant de portée.
- `HoloContainer` (base), `PlayerHoloContainer`, `DimensionHoloContainer`, `PositionHoloContainer`.
- `HoloContainers` : index, événements, API serveur, réseau entrant.
- `HoloMemoryBank` et `PlayerMemoryCapability` (joueur), `DimensionHoloData` (dimension), `HoloMarkers` (markers).

### `holo.inspect`
- `HoloInspection` (donnée), `HoloInspections` (autorité serveur), `HoloInspectEvent` (politique).

### `network`
- `HoloNetwork` (canal), `HoloSyncData` (`ModuleData`, `ObjectData`) et les sept paquets du [§4](#4-réseau).

### `command`
- `HoloMarkerCommand`, `HoloModuleCommand` (+ `ModuleSlot` : mémoire chargée ou au repos ; `ModuleListArgument` :
  liste de modules et autocomplétion), `HoloInspectCommand`.

### `demo`
- `DemoModules` (enregistrement), `DemoDimensionModule`, `DemoWorldPosModule`, `DemoPlayerModule`, `DemoWindows`
  (fabrique de fenêtres), `DemoCube`.

### `client.holo`
- `ClientHoloWorld` (portées connues, tick, oubli), `ClientHoloContainer` (miroirs, origine), `ClientHoloPackets`
  (handlers).

### `client.render`
- `RenderDispatcher`, `HoloRenderers` ; `sky/`, `world/`, `view/`, `hud/` (renderer, contexte, passe) ;
  `type/HoloRenderTypes`.
- `holo/` : `HoloRouting`, `ResolvedHolo`, `HoloPlacement`, `HoloBridges` et les quatre ponts, `HoloDrawContext`,
  `HoloObjectRenderer(s)`, `HoloWindowRenderer`, `BufferHoloCanvas`.
- `demo/` : `DemoCubeRenderer`, `DemoRenderers`.

---

## 11. Limites connues et points ouverts

- **Pas d'interaction** : `HoloWidget.onClick` et `HoloWindow.widgetAt` existent, mais aucun système client ne
  transforme encore un clic ou un regard en appel (le code parle d'un « futur système d'interaction »). Les boutons ne
  sont donc pas cliquables en jeu.
- **Pas de commande de mémoire de joueur** : `createMemory`, `deleteMemory` et `selectMemory` existent dans
  `HoloContainers`, mais aucune commande ne les expose.
- **Aucune politique d'inspection** : `HoloInspectEvent` n'a pas d'abonné, donc toute demande est acceptée. La cible
  n'est pas notifiée quand quelqu'un l'inspecte.
- **Inspection limitée à la portée de suivi** : il faut suivre l'entité de la cible, donc en être proche. Une cible
  hors de portée n'est pas visible, même avec un accès.
- **Ciel masqué avec le ciel vanilla** : `HoloSkyBridge` garde `hiddenWithSky() = true`. Les objets `Sky` disparaissent
  donc dans le Nether, sous la lave, en cécité ou dans un brouillard épais.
- **Bornes de profondeur** : un objet `Sky` au-delà du plan lointain (au moins 128 blocs) est coupé. En HUD, `z` est
  borné à ±1000 et un objet épais près de `−1000` peut toucher le plan lointain du GUI.
- **Pas de migration de sauvegarde** : le format des objets a changé (transform séparé, ancrages simplifiés). Un
  transform absent garde celui de `onLoad`.
- **Protocole réseau `"2"`** : un client d'une version antérieure est refusé.
- **Chargement réseau par module** : `ModuleData` sérialise tout l'état à chaque mise à jour ; rien n'est encore
  envoyé en différentiel.
