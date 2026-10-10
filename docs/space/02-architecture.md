# 02 — Architecture

Chemins relatifs à `src/main/java/com/amynna/hololib/`. Les signatures citées ont été vérifiées dans le code au
moment de la rédaction ; en cas d'écart, le code fait foi et l'écart est signalé dans le compte rendu.

## 1. Packages

Arborescence **à la fin du lot 2** (réalisée, relue en T27 ; les classes nouvelles du lot 2 sont suivies de leur
tâche). Miroir de
l'organisation existante (`modules/package-info.java` : un sous-package par compatibilité — `dimension`,
`monde` pour les modules de position, `camera` pour les modules de joueur — puis un package par module ; le client
en miroir sous `client/`).

```
modules/
├── dimension/space/                  module Space Environment + code commun partage par les 3 modules
│   ├── SpaceEnvironmentModule.java
│   ├── SpaceArchive.java             SavedData par niveau : copie de l'etat, restauree a la reinstallation (T14)
│   ├── SpaceFormat.java              formats affiches (vitesse, distance, 3 chiffres) : Controller et HUD (T20)
│   ├── model/                        donnees pures : SpaceGalaxy, SpaceSystem, SpaceBody, SpaceBodyKind (+ MINOR, T16),
│   │                                 SpaceOrbit, SpaceLimits, SpaceEditResult
│   └── math/                         calculs purs : SpaceUnits, SpaceOrbits, SpacePose, SpaceMotion, SpaceFrameState,
│                                     SpacePilotInput, SpaceSkyFrame, SpaceRotations, SpaceMotionSettings,
│                                     SpaceJump (etat + pas deterministe du saut, T21)
├── camera/spacepilot/                SpacePilotModule (reglages, Configurable, QuickActionProvider, T17),
│                                     SpacePilotConfigWindow (T17), SpacePilotHud (objet Hud, T20),
│                                     SpaceJoystickForward, SpaceJoystickAim (migres du Controller, T19)
└── monde/
    ├── spacecontroller/              SpaceControllerModule, SpaceControllerWindow, SpacePilotPad
    └── spaceconfigurator/            SpaceConfiguratorModule, SpaceMapCube, SpaceConfiguratorWindow (detachee, T24),
                                      SpaceMapHandle, SpaceOrbitHandle, SpaceSystemScale,
                                      SpaceCoordinates (origine et format des coordonnees, T26)

client/
├── modules/dimension/space/          SpaceEnvironmentClient (backend, recherche du miroir de dimension)
├── modules/camera/spacepilot/        SpacePilotClient (backend : module du joueur local, etat du pilotage, T17)
├── modules/monde/spacecontroller/    SpaceControllerClient (backend : pilote local, regard)
├── modules/monde/spaceconfigurator/  SpaceConfiguratorClient (backend : op local)
└── render/modules/
    ├── dimension/space/              SpaceSkyRenderer (SkyRenderer), SpaceWarpEffect (distorsion, T23), SpaceRenderers
    ├── camera/spacepilot/            SpacePilotSkyRenderer (surcouche + lien, T18 / T19), SpaceJoystickRenderer
    │                                 (migre, T19), SpaceJumpRenderer (ViewRenderer : cible du saut, roue de charge,
    │                                 aide a la visee, T22), SpacePilotRenderers (le HUD, fenetre, est dessine par le
    │                                 renderer generique des fenetres, T20)
    └── monde/
        └── spaceconfigurator/        SpaceMapCubeRenderer, SpaceHandleRenderer, SpaceOrbitHandleRenderer,
                                      SpaceConfiguratorRenderers
```

Le package `client/render/modules/monde/spacecontroller/` a été **supprimé en T19** (le Controller n'a plus d'objet
propre à dessiner : sa fenêtre utilise le renderer générique des fenêtres) ; son appel a été retiré de
`ModuleRenderers`.

Chaque package reçoit sa `package-info.java` (rôle, règles, renvoi vers ce dossier). Fichiers hors Space modifiés :
`modules/HoloLibModules.java` (4 enregistrements), `client/render/modules/ModuleRenderers.java` (appel des
`XxxRenderers.register()`) ; rien d'autre ([`README.md`](README.md), règle d'API). L'extension `fogColor` (D1, T03)
est faite.

## 2. Enregistrement

```java
// modules/HoloLibModules.java (meme forme que PLANET_1)
SPACE_ENVIRONMENT  = REGISTER.register("space_environment",
        () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, SpaceEnvironmentModule::new));
SPACE_CONTROLLER   = REGISTER.register("space_controller",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceControllerModule::new));
SPACE_CONFIGURATOR = REGISTER.register("space_configurator",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceConfiguratorModule::new));
SPACE_PILOT        = REGISTER.register("space_pilot",                       // lot 2, T17
        () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, SpacePilotModule::new));
```

- Renderers d'objets : `HoloObjectRenderers.register(Classe.class, renderer)` dans un `XxxRenderers.register()`
  appelé depuis `ModuleRenderers.onClientSetup` (`enqueueWork`), comme `Planet1Renderers`.
- Renderer de ciel (bas niveau) : `HoloRenderers.register(SkyRenderer)` (`client/render/HoloRenderers.java`), sur le
  fil de rendu, depuis le même `enqueueWork`.
- Backends client : classe `@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = MOD)` qui fait
  `event.enqueueWork(() -> XxxModule.install(...))` au `FMLClientSetupEvent`, comme
  `client/modules/camera/config/ConfigController.java:46` (`ConfigWindow.install`).

## 3. Rôles et propriété des données

| Donnée | Propriétaire (autorité serveur) | Copies client |
|---|---|---|
| Galaxie complète (systèmes, corps, orbites) | `SpaceEnvironmentModule` (mémoire de dimension, sauvegardée) | jamais complète : voir lignes suivantes |
| Système courant + étoiles de fond | `SpaceEnvironmentModule` | miroir de dimension (tous les joueurs de la dimension) |
| Pose du monde (origine), vitesse, entrées du pilote, pilote | `SpaceEnvironmentModule` | miroir de dimension (dead reckoning, [`05`](05-synchronisation.md)) |
| Réglages : axe avant, vitesse max, échelle de temps | `SpaceEnvironmentModule` | miroir de dimension |
| Saut (phase, cible, ticks, durée, origine / destination) | `SpaceEnvironmentModule` (T21) | miroir de dimension (bloc chaud) |
| Vitesse de distorsion | `SpaceEnvironmentModule` (réglages, T15) | miroir de dimension |
| Fenêtre de contrôle | `SpaceControllerModule` (mémoire du marker) | joueurs qui suivent le chunk du marker |
| Réglages d'affichage du pilote, HUD, cercles du joystick | `SpacePilotModule` (mémoire du joueur, objets `PRIVATE`) | le joueur lui-même seulement (C45) |
| Cube, poignées, fenêtre, sélection, mode, origine des coordonnées | `SpaceConfiguratorModule` (mémoire du marker) | idem |
| En-têtes de tous les systèmes + corps du système édité | copiés depuis l'Environment par le Configurator (révision) | joueurs qui suivent le chunk du marker |

Règle : **une seule autorité** pour la galaxie et la pose (l'Environment). Controller et Configurator n'ont que des
états d'interface et des copies ; ils modifient l'Environment par ses méthodes serveur publiques.

## 4. Accès entre modules

**Serveur** (fil serveur, dans `serverTick(ServerLevel level)` ou `handleAction(ServerPlayer player, ...)`) :

```java
static SpaceEnvironmentModule environment(ServerLevel level)
{
    DimensionHoloContainer container = HoloContainers.dimension(level);   // null avant LevelEvent.Load
    if (container == null) return null;
    for (HoloModule module : container.modules())
        if (module instanceof SpaceEnvironmentModule env) return env;
    return null;
}
```

À placer en méthode statique `SpaceEnvironmentModule.of(ServerLevel)` (T04). `HoloContainer.find` est privé : ne pas le
rendre public, parcourir `modules()`. Le niveau d'un module de marker est celui reçu par `serverTick`, ou
`player.serverLevel()` dans `handleAction`.

**Client** : le code commun ne voit pas `ClientHoloWorld`. `SpaceEnvironmentModule` expose un backend :

```java
public interface Backend
{
    SpaceEnvironmentModule clientInstance();   // miroir de la dimension du joueur local
    long gameTime();                           // temps du niveau client (rejeu)
    UUID localPlayer();                        // isLocalPilot
}
public static void install(Backend backend) { ... }
public static SpaceEnvironmentModule client() { ... }                    // null cote serveur / sans backend
```

implémenté par `client/modules/dimension/space/SpaceEnvironmentClient` avec
`HoloModes.find(ClientHoloWorld.find(HoloScope.DIMENSION), SpaceEnvironmentModule.class)`
(`client/holo/HoloModes.java:23`, `client/holo/ClientHoloWorld.java:46`). Le renderer du ciel, lui, est client et
fait cette recherche directement.

## 5. Space Environment (T04, T05)

`SpaceEnvironmentModule extends HoloModule` — **aucun objet holographique** : le ciel est dessiné par un
`SkyRenderer` bas niveau (la vue du système n'est ni éditable ni interactible, et doit couvrir tout le ciel).

- **État sauvegardé** : galaxie, id du système courant, pose (position `double` en km, orientation quaternion
  `double`), vitesse nominale, réglages (axe avant, vitesse max, échelle de temps), révision de la galaxie.
- **Non sauvegardé** : pilote courant (UUID + id du marker du Controller), entrée courante du pilote. Le tick de la
  pose (`time`) **est** sauvegardé (T13, C37).
- **`serverTick`** : intègre le mouvement (`SpaceMotion.step`, [`04`](04-mathematiques.md) § 5), libère un pilote
  absent ou muet, décide des envois ([`05`](05-synchronisation.md)).
- **`clientTick`** : même intégration (dead reckoning), garde la pose précédente pour l'interpolation par
  `partialTick`.
- **API serveur publique** (utilisée par Controller et Configurator) : pilotage (`claimPilot`, `releasePilot`,
  `pilotInput`), lecture (`galaxy()` en lecture seule, `galaxyRevision()`, `pose()`, `speed()`, `nearestBody()`),
  édition (opérations de [`03`](03-modele-donnees.md) § 6), réglages. Toute édition valide, incrémente la révision et
  marque le bloc froid sale.
- **API client** : `pose(partialTick, dest)`, `currentSystem()`, `backgroundStars()`, `backgroundStar(systemId)`
  (T15, recherche sans allocation), `warpSpeed()` (T15, deux côtés), `bodyPosition(index, time, dest)`,
  `predictPilotInput(input)` (pilote local), `synced()` (faux tant que le bloc froid de la révision annoncée manque,
  T15b). Réglage serveur `setWarpSpeed(double)` (T15). **Saut** (T21, deux côtés, lecture seule) : `jump()`
  (`SpaceJump` : phase, cible, compteur, durée, positions ; client = saut de la pose affichée), `jumpAt(partialTick)`
  et `jumpFraction(partialTick)` (même temps que `pose(partialTick)`), `jumpChargeProgress`, `jumpTravelProgress`,
  `jumpRemainingSeconds` (au temps d'affichage) ; `displayTicks(partialTick)` (T23 : horloge d'affichage en ticks,
  continue, pour animer un effet sans le temps de jeu de Minecraft).
- **Lot 2** : étoiles de fond identifiées (id, nom, T15) ; réglage `warpSpeed` (T15) ; **saut** (T21) : état
  `SpaceJump` porté par l'Environment, avancé dans le même pas déterministe que le mouvement, déclenché par l'entrée
  du pilote (drapeau + id de cible, [`05`](05-synchronisation.md) § 3) ; **archive** (T14) : `SpaceArchive extends
  SavedData` (`level.getDataStorage().computeIfAbsent`, clé `hololib_space`), branchée au premier tick serveur,
  recopie de l'état vivant à chaque sauvegarde du niveau et au `onUnload` ; au premier tick serveur, un Environment
  installé sans état (commande) ou chargé d'une mémoire plus ancienne qu'elle s'y recharge (`onLoad` ne connaît pas le
  niveau).

`SpaceSkyRenderer implements SkyRenderer` (T05) : lit le miroir, dessine fond opaque, étoiles de fond, corps ;
surcharge la couleur du brouillard via l'extension T03. Détails dans [`04`](04-mathematiques.md) § 4 (carte T05 du
lot 1 dans l'historique git).
Distorsion du saut (T23, provisoire) : `SpaceWarpEffect`, seule responsable de l'effet (intensité `strength` avec
fondus d'entrée et de sortie, `fogColor`, dessin), appelée par `SpaceSkyRenderer` par-dessus le ciel, qu'elle remplace
à pleine intensité ; la surcouche et `SpaceJumpRenderer` de Space Pilot se coupent tant que l'intensité est non nulle.

## 6. Space Controller (T06, T07, T08 ; lot 2 : T19, T22)

- **Objets** (tous `PUBLIC`, mémoire de position sans propriétaire) :
  - `SpaceControllerWindow extends HoloWindow`, ancrage `World()`, au-dessus du marker ; accès `EVERYONE` par défaut
    (réglable, [`decisions.md`](decisions.md) C2) ; contient un `SpacePilotPad` et des `HoloLabel` d'état.
  - ~~`SpaceJoystickForward` / `SpaceJoystickAim`~~ : **migrés dans Space Pilot en T19** (D11).
- **Lot 2** : `BEGIN` refusé si la mémoire active du joueur (`SpacePilotModule.of(ServerPlayer)`, qui parcourt
  `HoloContainers.player(player).modules()`) n'a pas de `SpacePilotModule` (D11, T19 : message « Module Space Pilot
  requis ») ; le client le vérifie aussi avant de capturer (`SpaceControllerModule.Backend.localSpacePilot()`, qui
  remplace `localPlayer()` ; implémenté par `SpaceControllerClient` avec `SpacePilotClient.module()`) : clic pris,
  message, pas de capture ; un refus serveur (mémoire changée entre-temps) rend la main après `CLAIM_TIMEOUT` comme
  les autres refus. La zone morte est `SpacePilotPad.DEAD_ZONE`, calculée depuis les rayons des cercles (C48, T19) ;
  `SpacePilotPad.localPilot()` (statique, client) rend le pad en capture du pilote local (règle de C29), lu par le
  joystick de Space Pilot. **Saut (T22)** : le pad capture aussi `key.sneak` (`SpacePilotPad.JUMP`) ; à l'appui, il
  choisit la cible (`SpaceJump.pickTarget`, C41, ciel de la dernière frame : `Backend.partialTick()`) et envoie le
  drapeau et la cible tant que la touche est tenue ; taux du joystick nuls pendant un saut ; lecture
  `jumpHeld()` / `jumpTarget()` / `jumpLocked()` pour Space Pilot. La fenêtre remplace la ligne d'état par « Saut
  vers X » (charge) puis « Distorsion : n s » (voyage), lus par chaque client dans le miroir de l'Environment
  (`SpaceControllerModule.clientTick`, rien de plus dans l'état synchronisé).
  Une ancienne sauvegarde de marker qui contient encore `joystick_forward` / `joystick_aim` est relue sans erreur
  (`HoloModule.loadAll` ignore les clés sans objet).
- **`SpacePilotPad`** : widget composé des traits `HoloPointerInput` + `HoloHoverable` (capture `MOUSE_MOVE`, Échap,
  Entrée au clic) et qui ajoute à `captures` les mappings `key.forward`, `key.back`, `key.left`, `key.right`
  (`HoloControl.mapping(...)` suit les touches du joueur ; constantes déjà dans `HoloDirections.UP/DOWN/LEFT/RIGHT`).
  La capture est liée à la fenêtre (`ctx.capture`, faite par le trait). Dans `onMouseMove`, il tourne lui-même la
  caméra (`HoloCapture.setLook`) en la bornant au cône ([`04`](04-mathematiques.md) § 6).
- **Actions** (`sendAction(window, ...)`, octet de type) : `BEGIN`, `INPUT` (entrée quantifiée), `END`, `OPS_ONLY`
  (réglage « tous / ops », op seulement). La distance est vérifiée par le framework (`HoloContainers.actionTarget`) ;
  pour `BEGIN`, le serveur vérifie dans l'ordre le mode « ops » (`player.hasPermissions`, D5), puis la présence de
  Space Pilot (D11), puis relaie à `SpaceEnvironmentModule.of(level)` (`claimPilot`) ; `INPUT` et `END` ne sont pris
  que du pilote de ce poste.
- **État synchronisé** : pilote courant (nom), vitesse, corps le plus proche et distance (rafraîchis 4 fois / s au
  plus), présence d'un Environment.

## 7. Space Configurator (T09 à T12 ; lot 2 : T24 à T26)

- **Objets** (`PUBLIC`, `shown()` = joueur local op, via backend). **Lot 2 (T24, fait)** : la fenêtre n'est plus
  replacée par le module quand le cube bouge ; elle a son propre transform, éditable (translation, rotation) et
  masquable (`HoloEditVisibility`, bouton œil du `world_editor` : à `OFF`, seul le cube reste hors session
  d'édition ; C9 inchangée).
  - `SpaceMapCube extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale` ;
    `opRequired() = true`, `ownRequired() = false` (patron `demo/DemoWorldPosModule.java:19`) ; ancrage `World()`.
    Son renderer dessine **tout le contenu** (galaxie ou système) dans le repère local du cube.
  - `SpaceConfiguratorWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
    HoloEditVisibility`, `World()`, `opRequired() = true`, `ownRequired() = false` (comme le cube) ; accès = liste des
    ops connectés, tenue à jour. Placement initial = devant la base du cube **par défaut** (`windowPlacement`), qui
    est aussi son transform de RESET ; ensuite le module n'y touche plus (transform sauvegardé et synchronisé par le
    framework). Ancienne sauvegarde (état sans `detached`) : replacée **une fois** devant la base du cube chargé au
    premier tick serveur. Mode galaxie : ligne « Distorsion (al/s) » (champ texte numérique, C36) → action 12
    `WARP_SPEED` → `SpaceEnvironmentModule.setWarpSpeed` ; valeur lue dans les réglages de l'Environment (copie
    synchronisée du Configurator, comme l'avant du monde).
  - `SpaceMapHandle` (`HoloEditTranslation`, op) : n'existe que si un système est sélectionné en mode galaxie ;
    posée sur l'étoile sélectionnée.
  - `SpaceOrbitHandle` (`HoloEditRotation`, `HoloEditScale`, op) : n'existe que si un corps à orbite est sélectionné
    en mode système ; posée sur le parent, représente l'ellipse.
- **Pas de callback d'édition dans le framework** : le module **sonde** dans `serverTick` le transform des objets
  édités (comparaison avec la dernière valeur qu'il a lui-même écrite) et en déduit les modifications
  ([`04`](04-mathematiques.md) § 7). La fenêtre n'est pas sondée (T24) : son transform ne porte aucune donnée.
- **Objets dynamiques** : les poignées sont créées dans `onLoad` (deux côtés) si l'état chargé / reçu les demande,
  puis ajoutées / retirées au changement d'état (sélection, mode) : serveur au changement, client dans `onSync`,
  **seulement si absentes** (`addObject` lève sur une clé déjà utilisée), mêmes clés (`handle`, `orbit`).
  `addObject` / `removeObject` sont `protected final` dans `HoloModule`.
- **Données affichées** : le Configurator recopie depuis l'Environment les en-têtes de tous les systèmes et les corps
  du système édité quand `galaxyRevision()` change, et les synchronise à ses observateurs.

Poignée de la carte (`SpaceMapHandle`, clé `handle`, T11) : créée dans `onLoad` **côté client** si l'état le demande ;
côté serveur, au premier tick (la copie de la galaxie n'existe pas encore au chargement : la position sauvegardée de
la poignée est ignorée et recalculée).

Mode système (T12) : `SpaceOrbitHandle` (clé `orbit`, Rotation + Échelle), `SpaceSystemScale` (échelle partagée par le
module et le renderer, sans allocation), `SpaceOrbitHandleRenderer`. **Lot 2 (T25, fait)** : tout à l'échelle (loupe des
lunes supprimée, D13) ; **zoom sur un corps** (D17) : id du corps zoomé dans l'état du module (sauvegardé, synchronisé :
le serveur place et relit la poignée d'orbite à l'échelle de la vue), bouton « Zoom » / « Dezoom » de la fenêtre,
`SpaceSystemScale.viewHalf` / `positions(…, center)` ; la poignée d'orbite n'existe en zoom que pour un corps dont le
parent est le corps zoomé ou un descendant ; **vaisseau** et **système courant** dessinés par `SpaceMapCubeRenderer`,
qui lit le miroir client de l'Environment (`SpaceEnvironmentModule.client()` : `currentSystem`, `pose(partialTick)`,
`referenceBodyId`, `bodyPositions`, `settings().forward`) ; couleur commune `SpaceMapCube.WORLD_COLOR`. Actions du
module (toutes refusées d'abord à un non-op, puis si la source n'est pas la fenêtre) : 0 sélection système, 1 mode,
2 recadrer, 3 ajouter, 4 retirer, 5 renommer, 6 monde ici, 7 avant du monde, 8 sélection corps, 9 ajouter un corps
(type + parent), 10 retirer un corps, 11 modifier **un champ** d'un corps (nom, rayon, couleur, luminosité, période,
phase : une copie client en retard n'écrase pas une orbite modifiée par la poignée), 12 vitesse de distorsion
(`double`, al/s, T24), 13 zoom (varint id du corps du système édité, 0 = système entier, mode système, T25),
14 origine des coordonnées (varint id d'un système, 0 = défaut Sol / premier, T26). **Lot 2 (T26, fait)** : croix de
la sélection et coordonnées aux faces dessinées par `SpaceMapCubeRenderer` ; origine (état `origin`, sauvegardé,
synchronisé, remise à 0 si le système disparaît) résolue par `SpaceCoordinates` (commune au module, à la fenêtre et au
renderer) ; fenêtre : « Definir comme origine » à côté de « Recadrer », origine précédée de « @ » dans la liste,
coordonnées de la sélection sur la ligne d'information : mode galaxie, système sélectionné en al relatives à l'origine ;
mode système, corps sélectionné en UA relatives à l'étoile principale au temps affiché (mêmes calculs que la croix,
exacts en zoom), recalculées à la synchronisation et au basculement de « Animer » seulement.

## 7 bis. Space Pilot (lot 2 : T17 à T20, T22)

- `SpacePilotModule extends HoloModule implements Configurable, QuickActionProvider` (mémoire de **joueur**).
  `sharedWithObservers()` faux, tous ses objets `PRIVATE` : rien n'est envoyé aux observateurs (C45).
- **État sauvegardé et synchronisé au propriétaire** : `enabled` (Quick Action), `orbits`, `bodies` (repérage),
  `stars`, `names`, `nameAngle` (degrés), `hud`, `refLink`. Modifié par `sendAction` (fenêtre de Config, action de
  la roue), validé par le serveur, comme tout module configurable.
- **Objets** : `ConfigWindow` (Config, T17) ; `SpacePilotHud` (`HoloWindow` ancrée `Hud`, clé `hud`, coin haut gauche,
  `HoloEditTranslation` : l'Own HUD Editor le déplace ; trois `HoloLabel` rafraîchis par `SpacePilotModule.clientTick`
  toutes les 5 ticks, altitude recalculée côté client au temps d'affichage ; `shown()` = `hudShown()`, T20) ; `SpaceJoystickForward` / `SpaceJoystickAim` (`Local`, `PRIVATE`, clés `joystick_forward` /
  `joystick_aim`, T19) : `shown()` = `SpacePilotModule.joystickShown()` (miroir du propriétaire et
  `SpacePilotPad.localPilot() != null`), cercle avant placé par `SpacePilotModule.clientTick` sur l'axe avant de
  l'Environment ; non coupés par `enabled` (C44).
- **Rendus** : la surcouche et le lien sont un `SkyRenderer` bas niveau (`SpacePilotSkyRenderer`, via
  `HoloRenderers`) qui lit le module **du joueur local** (`SpacePilotClient.module()` : `HoloModes.find(ClientHoloWorld.find(new
  HoloScope.Player(uuid)), SpacePilotModule.class)`, mis en cache une fois par tick) et le miroir de l'Environment : un autre joueur ne le
  voit jamais, même en inspectant (le renderer ne lit que le joueur local). Le joystick est dessiné par
  `SpaceJoystickRenderer` (renderers d'objets `HoloObjectRenderers`), enregistré avec la surcouche par
  `SpacePilotRenderers`. Le saut du pilote local (T22) est dessiné par `SpaceJumpRenderer`, un `ViewRenderer`
  (projection `WORLD`, devant le terrain comme le joystick) : double anneau de sélection autour de l'étoile cible dès
  l'appui, roue de charge pendant `CHARGE`, aide à la visée en pâle (coupée par `enabled`) ; rien pendant le voyage.
  Le HUD remplace la ligne « Distance » par « Distorsion -> cible : n s » pendant le voyage.
- **Compatibilités** : modèles à imiter `demo/DemoConfigurableModule.java` (Config), `demo/DemoQuickActionsModule.java`
  (Quick Action), `demo/DemoPlayerModule.java` (objet `Hud`) ; capacité d'édition déduite par
  `HoloEditRules.ownCapability` (`Hud` → `OWN_HUD`).

## 8. API du mod réutilisée (référence rapide)

| Besoin | API | Fichier |
|---|---|---|
| Module : cycle de vie, sync, actions | `onLoad`, `onUnload`, `serverTick(ServerLevel)`, `clientTick`, `save/load(CompoundTag)`, `writeSync(buf, forOwner)`, `readSync`, `onSync`, `handleAction(ServerPlayer, HoloObject, FriendlyByteBuf)`, `markDirty`, `sendAction(source, writer)`, `addObject`, `removeObject`, `object(key)`, `host()`, `scope()`, `creator()` | `holo/module/HoloModule.java` |
| Objet | constructeur `(key, anchor, transform)`, `boundingRadius()`, `halfWidth/halfHeight`, `flat()`, `shown()`, `tick()`, `interactibleAt`, `setVisibility`, `setAccess`, `setEditRange`, `transform()`, `module()` | `holo/object/HoloObject.java` |
| Placement | `HoloTransform` (9 flottants mutables, `set`, `setTranslation`, `setRotation`, `setScale`, `copy`) ; `new HoloAnchor.World()`, `new HoloAnchor.Local(follow)`, `new HoloAnchor.Sky(celestial)` (records d'une interface scellée) ; `acceptable` | `holo/object/` |
| Rotation d'un transform | `HoloEditMath.rotation(Quaternionf dest, HoloTransform)` (statique, YXZ, `-yaw`) ; `new HoloEditMath().setRotation(HoloTransform, Quaternionfc)` (méthode **d'instance** : une instance par fil, gardée en champ) ; `yawOf/pitchOf(Vector3f)` (statiques) | `holo/edit/HoloEditMath.java` |
| Édition | `HoloEditTranslation`, `HoloEditRotation`, `HoloEditScale` (marqueurs), `opRequired()`, `ownRequired()` | `holo/edit/` |
| Fenêtre et widgets | `HoloWindow(key, anchor, transform, w, h, pixelScale)`, `root()`, `setInterceptedInputs` ; `HoloPanel`, `HoloLabel`, `HoloWidgetButton`, `HoloWidgetList`, `HoloWidgetTextBox`, `HoloWidgetValueBar`, `HoloWidgetSwitch` | `holo/window`, `holo/widget`, `holo/widget/control` |
| Capture | `HoloCaptor`, `HoloCapture.setLook/yaw/pitch/release`, `HoloControl.mapping`, `HoloPointerInput`, `HoloDirections` | `holo/capture`, `holo/widget/control` |
| Rendu d'objet | `HoloObjectRenderer<T>.render(T, PoseStack, MultiBufferSource, int light, HoloDrawContext)`, `HoloObjectRenderers.register` | `client/render/holo/` |
| Rendu du ciel | `SkyRenderer` (`hiddenWithSky`, `shouldRender`, `render`, `fogColor` — extension D1), `SkyRenderContext` (`SKY_RADIUS = 100`, `partialTick`, `farPlane`) | `client/render/sky/` |
| Compatibilités joueur | `Configurable` (`configName`, `configWindow`) + `ConfigWindow` (`content()`) ; `QuickActionProvider` (`quickActions()`) + `QuickAction` / `QuickActionIcon` ; module de mémoire de joueur, objets `Hud` | `modules/camera/config`, `modules/camera/quickaction`, `demo/` |
| Conteneurs | `HoloContainers.player(ServerPlayer)` / `dimension(ServerLevel)` (serveur) ; `ClientHoloWorld.find(HoloScope)`, `HoloModes.find(container, Classe)` (client) | `holo/memory/HoloContainers.java`, `client/holo/` |
| RenderTypes | `HoloRenderTypes.SOLID_QUADS`, `TRANSLUCENT_QUADS` (`POSITION_COLOR_LIGHTMAP`) ; un renderer Space peut définir un `RenderType` **local privé** si aucun ne convient (ex. `GlowType` de `SpaceMapCubeRenderer` : translucide sans écriture de profondeur, T10) — l'ajouter à `HoloRenderTypes` serait une demande d'API | `client/render/type/HoloRenderTypes.java` |
| Exemples à imiter | rendu d'astre éclairé à la main : `Planet1PlanetRenderer` ; cercle en quads : `EditorReferenceRenderer` ; anneaux / segments : `HoloGizmoRenderer` (privés : copier) ; texte : `BufferHoloCanvas.text` (`font.drawInBatch`) ; envoi prédictif : `demo/DemoPad.java` ; liste : `ConfigListWindow` | `client/render/...`, `demo/`, `modules/camera/config/` |

## 9. Points d'attention connus

- **Mémoire de position** : `ownerView = false` pour tous → objets non `PUBLIC` jamais dessinés ;
  `HoloAccess.OWNER` = personne ; `HoloObject.requestTransform` sans effet (rien n'est envoyé hors de la mémoire du
  joueur local) → passer par `sendAction`.
- **Capture liée à un objet** : terminée si l'objet est retiré, n'est plus routé (`shown()` faux, mémoire du marker
  oubliée — chunk plus suivi) ou si son accès ne l'autorise plus.
  Ne pas masquer la fenêtre de contrôle pendant le pilotage.
- **Édition `World`** : translation limitée à `‖t‖ ≤ 64` (`HoloAnchor.MAX_REACH`), gizmos sur les axes du monde (pas
  ceux du cube), taille min des gizmos 0,5 bloc, portée d'édition 1 à 16 blocs (défaut 8).
- **Ciel** : `hiddenWithSky()` vrai par défaut — garder ce défaut (pas de ciel spatial dans la lave, la neige
  poudreuse, en cécité / obscurité, sous brouillard épais ou dans une dimension sans ciel ; sous l'eau le ciel reste
  dessiné, seul le brouillard garde la couleur vanilla).
  Profondeur partagée entre renderers du ciel, vidée après la passe ; rien au-delà de `farPlane()` (≥ 128).
- **Sync** : `writeSync` est rappelé à chaque `markDirty` (un tampon pour toute la dimension) et à chaque instantané ; il ne connaît pas le destinataire. L'Environment n'y joint son bloc froid qu'au besoin (T15b) → [`05-synchronisation.md`](05-synchronisation.md) §§ 1, 2.
- **Pas de Mixin**, pas d'OpenGL brut, pas d'allocation par frame.
