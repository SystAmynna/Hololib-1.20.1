# 02 — Architecture

Chemins relatifs à `src/main/java/com/amynna/hololib/`. Les signatures citées ont été vérifiées dans le code au
moment de la rédaction ; en cas d'écart, le code fait foi et l'écart est signalé dans le compte rendu.

## 1. Packages

Arborescence **à la fin du lot 3** (réalisée, relue en T46 ; les classes nouvelles des lots 2 et 3 sont suivies de
leur tâche). Miroir de
l'organisation existante (`modules/package-info.java` : un sous-package par compatibilité — `dimension`,
`monde` pour les modules de position, `camera` pour les modules de joueur — puis un package par module ; le client
en miroir sous `client/`).

```
modules/
├── dimension/space/                  module Space Environment + code commun partage par les 3 modules
│   ├── SpaceEnvironmentModule.java
│   ├── SpaceArchive.java             SavedData par niveau : copie de l'etat, restauree a la reinstallation (T14)
│   ├── SpaceFormat.java              formats affiches (vitesse, unite imposee T34, distance, 3 chiffres) : Controller
│   │                                 et HUD (T20)
│   ├── SpaceBodyTextures.java        textures integrees (ids, table nom -> id de Sol, regle auto generique),
│   │                                 resolution reference -> ResourceLocation, sans allocation (T29)
│   ├── texture/                      (T45, D27) canal reseau dedie aux textures importees : SpaceTextureNetwork
│   │                                 (SimpleChannel hololib:space_textures, abonne FMLCommonSetupEvent),
│   │                                 SpaceTexturePackets, SpaceTextureServer (imports, envois), SpaceTextureStore
│   │                                 (fichiers par dimension, validation, quota)
│   ├── model/                        donnees pures : SpaceGalaxy, SpaceSystem, SpaceBody, SpaceBodyKind (+ MINOR, T16),
│   │                                 SpaceOrbit, SpaceLimits, SpaceEditResult, SpaceRotation (rotation propre, T28),
│   │                                 SpaceTextureRef (auto / builtin / custom, T28), SpaceStarField (fond etoile
│   │                                 procedural genere depuis la graine, T31), SpaceSolRotations (rotations de Sol
│   │                                 par nom, C83), SpaceKnownMasses (masses des 62 corps par defaut par nom, T39)
│   └── math/                         calculs purs : SpaceUnits, SpaceOrbits, SpacePose, SpaceMotion, SpaceFrameState,
│                                     SpacePilotInput, SpaceSkyFrame, SpaceRotations, SpaceMotionSettings,
│                                     SpaceJump (etat + pas deterministe du saut, T21), SpaceSpin (orientation
│                                     propre d'un corps au temps t, T28), SpaceCruise (vitesse de croisiere :
│                                     cible au regard, vitesse cible, couleur, T34)
├── camera/spacepilot/                SpacePilotModule (reglages, Configurable, T17 ; reduit en T33 : plus de
│                                     QuickActionProvider), SpacePilotConfigWindow (T17, 3 interrupteurs en T33),
│                                     SpacePilotHud (objet Hud, T20), SpaceJoystickForward, SpaceJoystickAim (T19)
├── camera/spaceknowledge/            SpaceKnowledgeModule (Configurable, QuickActionProvider, T33),
│                                     SpaceKnowledgeConfigWindow (T33)
└── monde/
    ├── spacecontroller/              SpaceControllerModule, SpaceControllerWindow, SpacePilotPad
    ├── spacemap/                     (lot 3, T37) SpaceMapModule (vue partagee, copie de la galaxie), SpaceMapCube
    │                                 (deplace), SpaceMapView (ce que lit le renderer du cube), SpaceMapWindow
    │                                 (navigation), SpaceMapTreeRow (ligne de l'arborescence), SpaceSystemScale et
    │                                 SpaceCoordinates (deplaces)
    └── spaceconfigurator/            SpaceConfiguratorModule (branche sur la Space Map du meme marker, T42),
                                      SpaceConfiguratorWindow (fenetre d'edition seule, T42), SpaceMapHandle,
                                      SpaceOrbitHandle

client/
├── modules/dimension/space/          SpaceEnvironmentClient (backend, recherche du miroir de dimension),
│                                     SpaceSoundController (sons du vaisseau, T36), SpaceTextureClient (canal des
│                                     textures cote client : demandes, reception, transport d'un import, T45)
├── modules/camera/spacepilot/        SpacePilotClient (module du joueur local, T17), SpacePilotBackend (backend :
│                                     regard de la camera pour la vitesse de croisiere, T34)
├── modules/camera/spaceknowledge/    SpaceKnowledgeClient (module Space Knowledge du joueur local, T33)
├── modules/monde/spacecontroller/    SpaceControllerClient (backend : pilote local, regard)
├── modules/monde/spaceconfigurator/  SpaceConfiguratorClient (backend : op local, import), SpaceTextureImport
│                                     (selecteur de fichier, preparation de l'image, T45)
└── render/modules/
    ├── dimension/space/              SpaceSkyRenderer (SkyRenderer), SpaceRenderers,
    │   │                             SpaceBodyTextureCache (textures des astres chargees a la demande, T30),
    │   │                             SpaceOverlay (regles partagees des surcouches : pixel de reference, rayon et
    │   │                             couleur de l'anneau d'un corps, RenderType sans ecriture de profondeur, T33)
    │   └── warp/                     distorsion du saut (T35, remplace SpaceWarpEffect provisoire de T23) :
    │                                 SpaceWarpTimeline (chronologie, phase visuelle pour les sons), SpaceWarpConvergence
    │                                 (etoiles en convergence, pour le ciel), SpaceWarpEffect (scene du voyage, flashs,
    │                                 brouillard), SpaceWarpSpectrum, SpaceWarpSource, SpaceWarpStreaks, SpaceWarpTypes,
    │                                 SpaceWarpVertices
    ├── camera/spacepilot/            SpaceJoystickRenderer (migre, T19), SpaceJumpRenderer (ViewRenderer : cible du
    │                                 saut, roue de charge, aide a la visee, T22), SpaceReferenceLinkRenderer
    │                                 (ViewRenderer : lien et anneau du referentiel pendant le pilotage, T33),
    │                                 SpaceTargetRenderer (ViewRenderer, deux instances : fleche et anneau verts de
    │                                 la cible du vaisseau, T43), SpacePilotRenderers (le HUD, fenetre, est dessine par le renderer generique des
    │                                 fenetres, T20) ; SpacePilotSkyRenderer deplace en T33 :
    ├── camera/spaceknowledge/        SpaceKnowledgeSkyRenderer (surcouche du ciel, ex-SpacePilotSkyRenderer sans le
    │                                 lien, + distance au regard, T33), SpaceKnowledgeRenderers
    └── monde/
        ├── spacemap/                 (T37) SpaceMapCubeRenderer (deplace), SpaceMapRenderers
        └── spaceconfigurator/        SpaceHandleRenderer, SpaceOrbitHandleRenderer, SpaceConfiguratorWindowRenderer
                                      (fenetre generique + apercu de la texture, T44), SpaceConfiguratorRenderers
```

Le package `client/render/modules/monde/spacecontroller/` a été **supprimé en T19** (le Controller n'a plus d'objet
propre à dessiner : sa fenêtre utilise le renderer générique des fenêtres) ; son appel a été retiré de
`ModuleRenderers`.

Chaque package reçoit sa `package-info.java` (rôle, règles, renvoi vers ce dossier). Fichiers hors Space modifiés :
`modules/HoloLibModules.java` (6 enregistrements depuis T37), `client/render/modules/ModuleRenderers.java` (appel
des `XxxRenderers.register()`) ; rien d'autre ([`README.md`](README.md), règle d'API). L'extension `fogColor` (D1, T03)
est faite. Le canal des textures (T45, D27) est entièrement dans le package Space `modules/dimension/space/texture/` :
`HoloNetwork` et `HoloLib` ne changent pas.

## 2. Enregistrement

```java
// modules/HoloLibModules.java (meme forme que PLANET_1)
SPACE_ENVIRONMENT  = REGISTER.register("space_environment",
        () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, SpaceEnvironmentModule::new));
SPACE_CONTROLLER   = REGISTER.register("space_controller",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceControllerModule::new));
SPACE_CONFIGURATOR = REGISTER.register("space_configurator",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceConfiguratorModule::new));
SPACE_MAP          = REGISTER.register("space_map",                         // lot 3, T37
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceMapModule::new));
SPACE_PILOT        = REGISTER.register("space_pilot",                       // lot 2, T17
        () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, SpacePilotModule::new));
SPACE_KNOWLEDGE    = REGISTER.register("space_knowledge",                   // lot 3, T33
        () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, SpaceKnowledgeModule::new));
```

- Renderers d'objets : `HoloObjectRenderers.register(Classe.class, renderer)` dans un `XxxRenderers.register()`
  appelé depuis `ModuleRenderers.onClientSetup` (`enqueueWork`), comme `Planet1Renderers`.
- Renderer de ciel (bas niveau) : `HoloRenderers.register(SkyRenderer)` (`client/render/HoloRenderers.java`), sur le
  fil de rendu, depuis le même `enqueueWork`.
- Canal des textures (T45, D27) : `SpaceTextureNetwork` est son propre abonné `@Mod.EventBusSubscriber(bus = MOD)` :
  `FMLCommonSetupEvent` → `enqueueWork(register)` (6 paquets, comme `HoloNetwork.register` appelé par `HoloLib`) ;
  `SpaceTextureServer` (bus Forge : `ServerTickEvent`, `PlayerLoggedOutEvent`, `ServerStoppedEvent`) et
  `SpaceTextureClient` (bus Forge client : `ClientTickEvent`, `ClientPlayerNetworkEvent.LoggingOut` ; bus MOD :
  demandeur du cache installé au `FMLClientSetupEvent`) s'abonnent eux-mêmes.
- Backends client : classe `@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = MOD)` qui fait
  `event.enqueueWork(() -> XxxModule.install(...))` au `FMLClientSetupEvent`, comme
  `client/modules/camera/config/ConfigController.java:46` (`ConfigWindow.install`).

## 3. Rôles et propriété des données

| Donnée | Propriétaire (autorité serveur) | Copies client |
|---|---|---|
| Galaxie complète (systèmes, corps, orbites) | `SpaceEnvironmentModule` (mémoire de dimension, sauvegardée) | jamais complète : voir lignes suivantes |
| Système courant + étoiles de fond | `SpaceEnvironmentModule` | miroir de dimension (tous les joueurs de la dimension) |
| Pose du monde (origine), vitesse, entrées du pilote, pilote | `SpaceEnvironmentModule` | miroir de dimension (dead reckoning, [`05`](05-synchronisation.md)) |
| Réglages : axe avant, vitesse max, échelle de temps (éditée par le Configurator, C119) | `SpaceEnvironmentModule` | miroir de dimension |
| Saut (phase, cible, ticks, durée, origine / destination) | `SpaceEnvironmentModule` (T21) | miroir de dimension (bloc chaud) |
| Vitesse de distorsion | `SpaceEnvironmentModule` (réglages, T15) | miroir de dimension |
| Fenêtre de contrôle | `SpaceControllerModule` (mémoire du marker) | joueurs qui suivent le chunk du marker |
| Réglages du pilote (HUD, lien, croisière), HUD, cercles du joystick | `SpacePilotModule` (mémoire du joueur, objets `PRIVATE`) | le joueur lui-même seulement (C45) |
| Réglages de la surcouche du ciel (T33) | `SpaceKnowledgeModule` (mémoire du joueur, objets `PRIVATE`) | le joueur lui-même seulement (C45) |
| Cube de la carte, fenêtre de navigation, vue partagée (mode, système affiché, sélection, centre, zoom, origine des coordonnées) | `SpaceMapModule` (mémoire du marker, T37) | joueurs qui suivent le chunk du marker |
| En-têtes de tous les systèmes + système affiché (vue système ou déplié dans l'arborescence) | copiés depuis l'Environment par la Map (révision, T37) | idem |
| Poignées, fenêtre d'édition, temps figé de la poignée d'orbite (T42) ; la sélection éditée est celle de la Map du même marker | `SpaceConfiguratorModule` (mémoire du marker) | idem |
| Images des textures importées (T45) | fichiers `data/hololib_space_textures/<sha1>.png` de la dimension (`SpaceTextureStore`) ; la référence `custom:<sha1>` est dans la galaxie de l'Environment | cache en mémoire de chaque client qui en a eu besoin (`SpaceBodyTextureCache`), vidé à la déconnexion |

Règle : **une seule autorité** pour la galaxie et la pose (l'Environment). Controller, Map et Configurator n'ont que des
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
    default void tickSounds(SpaceEnvironmentModule env) {}   // T36 : fin de chaque clientTick
    default void released(SpaceEnvironmentModule env) {}     // T36 : onUnload du miroir
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
  `double`), vitesse nominale, réglages (axe avant, vitesse max, échelle de temps), révision de la galaxie, graine du
  fond étoilé (T31, clé `seed`).
- **Non sauvegardé** : pilote courant (UUID + id du marker du Controller), entrée courante du pilote. Le tick de la
  pose (`time`) **est** sauvegardé (T13, C37).
- **`serverTick`** : intègre le mouvement (`SpaceMotion.step`, [`04`](04-mathematiques.md) § 5), libère un pilote
  absent ou muet, décide des envois ([`05`](05-synchronisation.md)).
- **`clientTick`** : même intégration (dead reckoning), garde la pose précédente pour l'interpolation par
  `partialTick`.
- **API serveur publique** (utilisée par Controller et Configurator) : pilotage (`claimPilot`, `releasePilot`,
  `pilotInput`), lecture (`galaxy()` en lecture seule, `galaxyRevision()`, `pose()`, `speed()`, `nearestBody()`),
  édition (opérations de [`03`](03-modele-donnees.md) § 6, dont `setBodyMass`, T39), réglages. Toute édition valide, incrémente la révision et
  marque le bloc froid sale.
- **API client** : `pose(partialTick, dest)`, `currentSystem()`, `backgroundStars()`, `backgroundStar(systemId)`
  (T15, recherche sans allocation), `warpSpeed()` (T15, deux côtés), `bodyPosition(index, time, dest)`,
  `predictPilotInput(input)` (pilote local), `synced()` (faux tant que le bloc froid de la révision annoncée manque,
  T15b), `starSeed()` et `starField()` (T31 : fond étoilé `SpaceStarField` de la graine, généré à la première lecture
  après un changement de graine, `null` si elle est inconnue ; lecture sans allocation, pour le ciel et la distorsion
  de T35). Réglage serveur `setWarpSpeed(double)` (T15). **Saut** (T21, deux côtés, lecture seule) : `jump()`
  (`SpaceJump` : phase, cible, compteur, durée, positions ; client = saut de la pose affichée), `jumpAt(partialTick)`
  et `jumpFraction(partialTick)` (même temps que `pose(partialTick)`), `jumpChargeProgress`, `jumpTravelProgress`,
  `jumpRemainingSeconds` (au temps d'affichage) ; `displayTicks(partialTick)` (T23 : horloge d'affichage en ticks,
  continue, pour animer un effet sans le temps de jeu de Minecraft ; distorsion de T35).
- **Lot 2** : étoiles de fond identifiées (id, nom, T15) ; réglage `warpSpeed` (T15) ; **saut** (T21) : état
  `SpaceJump` porté par l'Environment, avancé dans le même pas déterministe que le mouvement, déclenché par l'entrée
  du pilote (drapeau + id de cible, [`05`](05-synchronisation.md) § 3) ; **archive** (T14) : `SpaceArchive extends
  SavedData` (`level.getDataStorage().computeIfAbsent`, clé `hololib_space`), branchée au premier tick serveur,
  recopie de l'état vivant à chaque sauvegarde du niveau et au `onUnload` ; au premier tick serveur, un Environment
  installé sans état (commande) ou chargé d'une mémoire plus ancienne qu'elle s'y recharge (`onLoad` ne connaît pas le
  niveau).

- **Cible du vaisseau** (lot 3, T43, D33) : état `(targetSystem, targetBody)`, `targetSystem = 0` = aucune cible,
  `targetBody = 0` = le système entier (montré par son étoile principale) ; une seule par dimension. API serveur
  publique : `setTarget(systemId, bodyId)` (refusée si le système ou le corps n'existe pas ; `SpaceEditResult`),
  `clearTarget()` ; lecture deux côtés : `targetSystemId()`, `targetBodyId()`, `hasTarget()`, `isTarget(s, b)`. Retirée
  par l'Environment lui-même quand son système ou son corps disparaît (`validateTarget`, appelé après toute édition
  réussie et sur un état chargé ou restauré) ; **gardée** après un saut (même vers son propre système) et après un
  changement de système courant. Bloc chaud (envoyée au changement, même à l'arrêt : `sendPending`, au plus une fois
  / 4 ticks ; rien par tick, [`05`](05-synchronisation.md) § 2) ; sauvegardée (clés `targetSystem`, `targetBody`,
  absentes sans cible ; lecture tolérante : absentes = aucune), donc aussi dans `SpaceArchive` (copie de `save`). Posée
  par la Space Map (§ 7 quater), lue par Space Pilot (§ 7 bis) ; indépendante de la cible de la vitesse de croisière et
  de celle du saut.
- **Téléportation** (lot 3, D34, C135) : API serveur publique `teleportTo(systemId, bodyId)` (`SpaceEditResult` ;
  refusée pour un système ou un corps inconnu et avant le premier tick serveur) : pose `SpaceMotion.orbitPose`
  ([`04`](04-mathematiques.md) § 5 bis), `V = 0`, référentiel = le corps sans fondu, saut en cours annulé, pilote et
  cible gardés ; corps d'un autre système : il devient le système courant (révision, bloc froid). Envoi à la fin du
  tick (`send`, sans l'attente de 4 ticks) ; octet `teleportSerial` du bloc chaud incrémenté : les clients sautent
  ([`05`](05-synchronisation.md) § 2). Appelée par le Configurator seul (action 12, op vérifié d'abord).

`SpaceSkyRenderer implements SkyRenderer` (T05) : lit le miroir, dessine fond opaque, étoiles de fond, corps ;
surcharge la couleur du brouillard via l'extension T03. Détails dans [`04`](04-mathematiques.md) § 4 (carte T05 du
lot 1 dans l'historique git).
**Lot 3 (T30, fait)** : corps **texturés** et **tournant sur eux-mêmes** (disque polaire inchangé, coordonnées de
texture par sommet depuis la normale exacte ramenée dans les axes du corps par `SpaceSpin` au temps
`displayDays(partialTick)`, [`04`](04-mathematiques.md) § 4). Textures fournies par `SpaceBodyTextureCache`
(client, `client/render/modules/dimension/space/`, instance partagée `instance()` pour la Space Map de T37) :
`texture(system, index)` résout la référence du corps (importée prête, sinon `SpaceBodyTextures.builtinFor` : intégrée
ou `auto`) et rend une texture prête ou `null` (repli en couleur unie) ; le ciel la demande pour **tous les corps du
système courant** à chaque frame. Décodage du PNG et mipmaps hors du fil de rendu (`Util.backgroundExecutor`),
téléversement par `beginFrame()` (appelé par le ciel à chaque frame, au plus 2 par frame) ; texture enregistrée dans le
`TextureManager` sous `hololib:space_body/builtin/<id>` (filtre linéaire + mipmaps, `u` répété, `v` borné) avec un
`RenderType` local privé par texture (`POSITION_COLOR_TEX`, opaque, faces arrière éliminées, profondeur de la passe du
ciel) ; libérée après 60 s sans demande. Point d'entrée de T45 : `registerCustom(sha1, NativeImage)` (image RGBA dont
le cache prend possession, même texture mipmappée enregistrée par empreinte), `hasCustom`, `releaseCustom` ; T45 y
ajoute `setCustomRequester` (empreinte d'un corps `custom` sans image passée au demandeur, `SpaceTextureClient.request`)
et `releaseAllCustom` (déconnexion) ; § 7 quinquies.
Chaque texture prête expose aussi sa couleur moyenne (`averageColor()`, dernier niveau de mipmap, calculée hors du fil
de rendu) et sa luminance (`luminance()`) : couleur des points lointains et éclat des étoiles texturées (§ 4 de
[`04`](04-mathematiques.md)).
**Lot 3 (T31, fait)** : **fond étoilé procédural** dessiné entre le fond opaque et les étoiles des systèmes, à
112,5 blocs (`STAR_PATTERN_RADIUS`) : 4 000 quads opaques (`SOLID_QUADS`, 16 000 sommets par frame) dont les coins sont
précalculés une fois par graine dans les axes du système, puis transformés par une seule matrice par frame (celle du
ciel tournée par `Q⁻¹`) : fixe dans le repère du système, il ne tourne qu'avec l'orientation du monde et ne change pas
d'un système à l'autre. `SpaceStarField` (modèle commun et pur) : directions — 25 % en **bande** autour du plan de la
Voie lactée réelle (latitude gaussienne, σ = 7°), 6 % en **renflement** autour du centre galactique (Sagittaire,
α 266,4°, δ −28,94° ; gaussienne aplatie dans le plan, σ = 15° en longitude, 7,5° en latitude), 69 % uniformes ;
repère équatorial du modèle, C93. Mesuré (graine de banc) : 161 étoiles à moins de 10° du centre (5,3 × l'uniforme),
64 autour de l'anticentre (2,1 ×), 1 482 à moins de 10° du plan (2,1 ×) —, éclat (magnitudes 1 à 7, loi de puissance), couleur de corps noir
par classe spectrale ; le ciel en tire taille (0,11° à 0,24°) et éclat (0,22 à 0,9).
**Distorsion du saut (lot 3, T35, D23, C73)** : sous-package `client/render/modules/dimension/space/warp/`, client
seulement, rien de plus sur le réseau (chaque client la déduit du saut affiché : `jumpAt`, `jumpFraction`,
`displayTicks`). Chronologie et dessin : [`04`](04-mathematiques.md) § 8.
- `SpaceWarpTimeline` (publique) : `at(env, partialTick)` met à jour et rend l'instance partagée, à lire aussitôt ;
  phase visuelle `NONE` / `CONVERGE` / `WARP` / `ARRIVAL`, `phaseTicks()`, `phaseProgress()`, `elapsed()`,
  `duration()` (instants clés pour les sons de T36 : début de `CONVERGE` = moteur, début de `WARP` = flash d'entrée,
  début d'`ARRIVAL` = flash d'arrivée), fractions de convergence (tête, queue), blanc des flashs (`whiteness`),
  apparition de la scène (`sceneRamp`), direction de la cible (axes du monde `vanishing`, axes du système `target`) ;
  `active(env, partialTick)` : vrai de la convergence à la fin du flash d'arrivée. Le flash d'arrivée est chronométré
  ici, en ticks d'affichage depuis la dernière lecture faite en voyage (comme l'ancien C64).
- `SpaceSkyRenderer` lit la chronologie une fois par frame : pendant `CONVERGE`, il place le fond étoilé procédural
  et les étoiles des autres systèmes avec `SpaceWarpConvergence` (glissement sur le grand cercle vers la cible, quad
  de la queue à la tête ; corps inchangés) ; pendant `WARP` (et sous un flash entièrement blanc) il ne dessine plus le
  ciel ; puis `SpaceWarpEffect.render` par-dessus : scène du voyage (`SpaceWarpSpectrum` : dégradé arc-en-ciel opaque
  à 100 blocs ; `SpaceWarpSource` : noyau, halos et deux flares contra-rotatives additifs dans le plan tangent à
  90 blocs ; `SpaceWarpStreaks` : 240 particules blanches à 40 blocs) et flash blanc (sphère à 35 blocs, devant les
  corps). Deux `RenderType` locaux sans écriture de profondeur (`SpaceWarpTypes` : translucide, additif
  `SRC_ALPHA, ONE`) ; l'ordre d'envoi fait l'ordre de dessin. Brouillard : `SpaceWarpEffect.fogColor` (couleur du
  dégradé dans le regard pendant le voyage, mélangée vers le blanc des flashs ; hors fluides, C23).
- La surcouche de Space Knowledge, `SpaceJumpRenderer` et `SpaceReferenceLinkRenderer` de Space Pilot se coupent tant
  que `SpaceWarpTimeline.active` est vrai.

**Sons du vaisseau (lot 3, T36, D26, C80)** : `client/modules/dimension/space/SpaceSoundController`, client seulement,
rien sur le réseau : chaque client déduit les sons de l'état affiché par son miroir (tous les joueurs de la dimension
entendent la même chose). Appelé à la fin de chaque `SpaceEnvironmentModule.clientTick` par le backend
(`Backend.tickSounds`, méthode par défaut implémentée par `SpaceEnvironmentClient`), coupé par `Backend.released`
appelé depuis `onUnload` du miroir (retrait du module, changement de dimension, déconnexion) ; pas appelé en pause
(la pause solo suspend aussi les sons en cours). Sons non positionnels (relatifs, sans atténuation), catégorie
`AMBIENT`, `SoundEvent.createVariableRangeEvent` (aucun registre), `assets/hololib/sounds.json` sans sous-titres,
fichiers mono Ogg Vorbis sous `assets/hololib/sounds/space/` (sources CC0 : `CREDITS.md`).
- **Moteurs** (retouche de T36) : deux boucles (`AbstractTickableSoundInstance`) modulées en continu, aucun son
  ponctuel : ronronnement grave (`engine_hum`, synthétisé) et sifflement tonal de turbine (`engine_whine`). Entrées :
  niveau de vitesse `L = ln(1 + |V|) / ln(1 + V_max)` et poussée lissée `T` (signe de l'évolution de `|V|` d'un tick
  à l'autre, confirmé 2 ticks, ×1,6 en boost reconnu au rapport des vitesses : > 1,1 ou < 0,85 par tick ; lissage
  0,08 / tick). Ronronnement : volume `0,18 + 0,22 L + 0,12 |T|` en marche, veille `0,06` à l'arrêt si un pilote est
  aux commandes, sinon silence ; hauteur `0,8 + 0,3 L + 0,08 T`. Sifflement : volume `0,03 + 0,1 L + 0,08 |T|` en
  marche, sinon silence ; hauteur `0,6 + 0,8 L + 0,22 T`. Lissage lent (volume 0,06, hauteur 0,05 par tick : environ
  une seconde, montées et descentes glissées) ; muets pendant la distorsion.
- **Coque** : gémissements métalliques sourds et lointains (`space.hull_groan`, 4 variantes) et « tonks » étouffés
  (`space.hull_tonk`, 3 variantes, 25 % des sons) quand le taux de rotation de l'orientation affichée (lissé, sauts
  d'affichage > 10°/tick ignorés) dépasse 0,35°/tick : chance 0,012 à 0,05 par tick jusqu'à 2°/tick, 30 ticks au
  moins entre deux, volume 0,1 à 0,26 (tonks 0,08 à 0,18) ; pendant le voyage, chance 0,025, 25 ticks d'écart.
- **Charge du saut** : boucle `jump_charge` (synthétisée) pendant la phase `CHARGE` du saut affiché, volume
  `0,1 → 0,5` et hauteur `0,55 → 1,8` (progression `jumpChargeProgress` à la puissance 1,3), coupée en fondu
  (~0,5 s) à l'annulation ou au départ.
- **Saut** : instance **séparée** de `SpaceWarpTimeline` (`separate()`, `read(env, 0f)` dans le tick : le suivi du
  flash d'arrivée de l'instance partagée du rendu n'est pas déplacé) ; début de `CONVERGE` = moteur du saut, début de
  `WARP` = souffle du flash, puis boucles de grondement et d'effet de vitesse (hauteur qui monte avec la progression)
  et sons de coque, début d'`ARRIVAL` = flash d'arrivée ; un son de début n'est pas rejoué pour une phase
  entamée depuis plus de 6 ticks (joueur arrivé en cours).
- Garde-fou : une boucle qui n'est plus pilotée depuis 4 ticks du moteur de son s'arrête seule ; une boucle abandonnée
  par le moteur de son (volume de catégorie nul, canaux pleins) est relancée 20 ticks plus tard.

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
  **Contrôles (T32)** : le pad capture aussi `key.sprint` (`SpacePilotPad.BOOST`) : drapeau `boost` de l'entrée
  (pentes renforcées, [`04`](04-mathematiques.md) § 5) ; Sneak et Sprint en **mode bascule** sont remis au repos par
  `SpaceControllerModule.Backend.releaseToggleKeys()` (`KeyMapping.resetToggleKeys()` vanilla, dans
  `SpaceControllerClient`) au début du pilotage, à chaque appui pris et à chaque tick, car la neutralisation de la
  capture (`setDown(false)`) est ignorée par un `ToggleKeyMapping`.
  Une ancienne sauvegarde de marker qui contient encore `joystick_forward` / `joystick_aim` est relue sans erreur
  (`HoloModule.loadAll` ignore les clés sans objet).
- **`SpacePilotPad`** : widget composé des traits `HoloPointerInput` + `HoloHoverable` (capture `MOUSE_MOVE`, Échap,
  Entrée au clic) et qui ajoute à `captures` les mappings `key.forward`, `key.back`, `key.left`, `key.right`
  (`HoloControl.mapping(...)` suit les touches du joueur ; constantes déjà dans `HoloDirections.UP/DOWN/LEFT/RIGHT`).
  Dans `onMouseMove`, il tourne lui-même la caméra (`HoloCapture.setLook`) en la bornant au cône
  ([`04`](04-mathematiques.md) § 6).
  **Capture indépendante de la fenêtre (T41, D31, C120)** : au clic, le pad démarre une capture **explicite non
  liée** (`HoloCaptures.begin(this)`, le pad est son propre captor) au lieu de la capture liée du trait
  (`ctx.capture`), que `HoloInteractions` terminerait dès que la fenêtre n'est plus routée. Il la termine lui-même :
  Échap / Entrée (trait), autre pilote, Environment absent, pilotage réservé aux ops (`syncState`), délai C25
  (`tick`), module Space Pilot retiré de la mémoire active du joueur local ou accès (`HoloAccess`) de la fenêtre
  perdu (`tick`), module déchargé (`SpaceControllerModule.onUnload` côté client : module ou marker retiré, chunk
  plus suivi, changement de dimension, déconnexion). Le `tick` du pad continue fenêtre masquée (objets tickés par
  leur module, pas par le rendu).
- **Fenêtre masquée pendant le pilotage (T41, D31)** : `SpaceControllerWindow.shown()` est faux tant que
  `pilot() != null && pilotHere()` (état synchronisé du module, chez tous) : la fenêtre du poste utilisé disparaît
  pour le pilote et pour les autres joueurs dès l'état qui désigne le pilote (≤ 5 ticks après `BEGIN`) et revient à
  l'état qui le libère. Les fenêtres des autres postes restent affichées (« Pilote par X », zone « Pilote depuis un
  autre poste », clic pris sans capture, C25). En session d'édition, la fenêtre reste révélée par le framework.
- **Actions** (`sendAction(window, ...)`, octet de type) : `BEGIN`, `INPUT` (entrée quantifiée), `END`, `OPS_ONLY`
  (réglage « tous / ops », op seulement). La distance est vérifiée par le framework (`HoloContainers.actionTarget`) ;
  pour `BEGIN`, le serveur vérifie dans l'ordre le mode « ops » (`player.hasPermissions`, D5), puis la présence de
  Space Pilot (D11), puis relaie à `SpaceEnvironmentModule.of(level)` (`claimPilot`) ; `INPUT` et `END` ne sont pris
  que du pilote de ce poste.
- **État synchronisé** : pilote courant (nom), vitesse, corps le plus proche et distance (rafraîchis 4 fois / s au
  plus), présence d'un Environment.

## 7. Space Configurator (T09 à T12 ; lot 2 : T24 à T26 ; lot 3 : T42)

> **Lot 3 (T42, D20, C78)** : le Configurator n'a plus de carte. Cube, renderer, copie de la galaxie, sélection, mode,
> zoom, origine, temps figé de la vue et « Animer » sont passés dans la **Space Map** (§ 7 quater) ; le Configurator
> garde sa **fenêtre d'édition** (ops) et ses **poignées**, et se branche sur la Map **du même marker** : la sélection
> de la Map est l'objet édité, son cube et sa vue (centre, demi-étendue, cadrage galactique) portent les poignées. Il
> n'implémente plus `SpaceMapView` (C110 levée). L'historique des lots 1 et 2 (cube propre, modes, zoom T25, origine
> T26) est dans l'historique git de ce document.

- **Recherche de la Map** : `SpaceMapModule.of(module)` (deux côtés) lit une table statique des Maps chargées, par
  portée (`HoloScope.Position` du marker), une table par côté, remplie par `SpaceMapModule.onLoad` et vidée par
  `onUnload` ; côté serveur, l'entrée n'est rendue que si `HoloContainers.position(markerId).module(id)` est bien ce
  module (Map d'un monde quitté sans déchargement ignorée). Aucun parcours de `modules()`, aucune allocation ;
  `HoloContainer.find` reste privé. Relue à chaque tick (serveur et client).
- **Sans Map** (C78) : fenêtre affichée avec « Space Map requise sur ce marker », boutons désactivés, champs masqués,
  aucune poignée ; toute action refusée avec ce message (après le contrôle op).
- **Objets** (`PUBLIC`, `shown()` = joueur local op, via backend, C9 ; la Map reste visible de tous) :
  - `SpaceConfiguratorWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
    HoloEditVisibility`, `World()`, `opRequired()` vrai, `ownRequired()` faux ; accès = ops connectés, tenu à jour ;
    **208 × 192 px** (plus de liste ; 174 px avant T44, +18 px pour la rangée d'onglets de la vue système, C130). Placement par défaut (aussi RESET) : à droite de la fenêtre de la Map placée
    devant la base du cube par défaut (`defaultWindowTransform`, constant), puis indépendante. Une sauvegarde d'avant
    T42 (sans clé `mapped`) remet la fenêtre une fois à ce placement au premier tick serveur (elle était devant
    l'ancien cube, à la place de la fenêtre de la Map).
    - **Carte galactique de la Map** : nom du système sélectionné, « Ajouter » (au centre de la carte galactique de la
      Map, puis la Map le sélectionne et le centre), « Retirer » (confirmation), « Monde ici », « Avant du monde »,
      « Temps (s/tick) » (C119), « Distorsion (al/s) ».
    - **Vue système de la Map** : corps sélectionné : nom et **« Se teleporter »** (D34, C135) au bout de sa ligne,
      « +Étoile / +Planète / +Lune / +Mineur » (le corps ajouté est sélectionné par la Map, cadrage gardé), puis trois
      **onglets** (T44, C130 : choix local à chaque client, non synchronisé ; masqués sans corps sélectionné,
      « Choisir un corps dans la Map » à leur place), avec **« RESET »** (D34, C134 : second clic de confirmation) au
      bout de leur rangée :
      - « Corps » : « Retirer » (confirmation), **« Orbite »** (la Map centre sa vue système sur le parent du corps à
        `nice(1,1 × apoapside)` : l'orbite entière tient dans le cube et ne dérive pas, la poignée d'orbite
        apparaît), rayon, luminosité, masse (T39), période, couleur, phase, lecture de l'orbite ;
      - « Rotation » (T44, C71, C131) : « Rotation (h) » (signée, `0` = synchrone, `< 0` = rétrograde),
        « Obliquite », « Noeud », « Phase rot. » (degrés), un champ texte par valeur (toute valeur finie, bornée par
        le serveur), ligne d'état (sens et durée d'un tour, ou synchrone) ;
      - « Texture » (T44, C132) : aperçu 48 × 24 px de l'image entière de la texture affichée pour le corps
        (`SpaceConfiguratorWindowRenderer` : renderer générique des fenêtres + quad texturé tiré de
        `SpaceBodyTextureCache`, couleur du corps tant qu'elle n'est pas chargée), texture actuelle, bouton
        « Importer », liste « Auto (texture retenue) » + textures intégrées
        (`SpaceBodyTextures.all()`, nom lisible), choix en vert ; clic = texture envoyée. **T45** : « Importer »
        actif (op, corps sélectionné, aucun import en cours) : image de l'ordinateur de l'op envoyée au serveur puis
        appliquée au corps (§ 7 quinquies) ; libellé « ... » pendant le choix du fichier, puis avancement « n % ».
    - Ligne d'information : ce qui est édité ; en vue système, si la poignée d'orbite est sur la carte ou si l'orbite
      est hors de la vue. Les coordonnées de la sélection sont dans la fenêtre de la Map.
  - `SpaceMapHandle` (`HoloEditTranslation`, op, C4) : existe si la Map montre sa carte galactique avec un système
    sélectionné ; posée sur son étoile dans le repère du cube de la Map (`q = (g − c) / H` borné à `[−1, 1]³`).
  - `SpaceOrbitHandle` (`HoloEditRotation`, `HoloEditScale`, op, C5) : existe si la Map montre un système dont le corps
    sélectionné a une orbite affichée à l'échelle de sa vue : demi-petit axe ≥ `1e-5` unité du cube, apoapside ≤ 4
    unités (la Map dessine alors l'ellipse entière, C109), parent affiché à moins de 2 unités du centre. Posée sur le
    parent affiché au **temps figé** `t_edit`, dans la vue de la Map (centre, demi-étendue).
- **Temps figé de la poignée d'orbite** (T42, 4 bis ; C121) : la Map suit le temps vivant, mais la poignée ne peut pas
  suivre le parent tick après tick : le serveur la relit et `HoloEditRules.accepts` refuse toute édition dont la
  translation diffère de la sienne (pas d'outil Translation) ; la replacer à chaque tick serait un envoi par tick et
  casserait les éditions en cours. `t_edit` (`days()` de l'Environment, état synchronisé du Configurator) n'est repris
  qu'au **changement de contexte** (système montré ou sa copie, corps sélectionné, centre ou demi-étendue de la vue de
  la Map : sélection, zoom, édition). Le renderer de la poignée décale l'ellipse du déplacement affiché du parent entre
  `t_edit` et le temps vivant (gizmos et visée restent à `t_edit`) ; avec « Orbite », le parent est au centre de la vue
  et rien ne dérive.
- **Pas de callback d'édition dans le framework** : le module **sonde** dans `serverTick` le transform des poignées
  (comparaison avec la dernière valeur qu'il a lui-même écrite, [`04`](04-mathematiques.md) § 7). La conversion
  utilise le **contexte retenu à l'écriture** (système ou corps visé, transform du cube, cadrage `(c, H)` ou unité de
  la vue) : une sélection, un zoom ou un cube changés entre l'écriture et l'édition ne détournent pas l'édition.
- **Objets dynamiques** : poignées créées / retirées des deux côtés, mêmes clés (`handle`, `orbit`), seulement si
  absentes / présentes : serveur chaque tick et après chaque action ; client **chaque tick** (l'état de la Map arrive
  dans ses propres paquets, pas dans ceux du Configurator). Le serveur les replace dès que leur placement voulu
  change ; le client ne les place lui-même qu'à leur création (le transform envoyé par le serveur avant la création
  locale est perdu par le framework) et quand le cube de la Map bouge (elles le suivent pendant sa manipulation) ;
  sinon le transform du serveur fait foi. Client : l'orbite éditée est masquée dans la Map chez un op tant que la
  poignée existe (`SpaceMapModule.hideOrbit`, lu par `hiddenOrbitBody()`).
- **Données** : le Configurator ne copie plus la galaxie ; il lit la copie de la Map (`headers()`, `shownSystem()`,
  `selectedHeader()`, `selectedBodyData()`), qu'il rafraîchit côté serveur avant de la lire et après chacune de ses
  éditions (`SpaceMapModule.refreshFrom(env)` : copie à jour dans le même tick). Il garde une copie des réglages
  (avant du monde, distorsion, échelle de temps), relue au changement de l'instance des réglages.
- **Vue orientée par le Configurator** (méthodes serveur publiques de la Map) : `focusSystem(id)` (système ajouté),
  `selectInView(systemId, bodyId)` (corps ajouté, cadrage gardé), `frameOrbit(systemId, bodyId)` (« Orbite »).
- **Actions** (table dans la javadoc de `SpaceConfiguratorModule`, renumérotées en T42) ; toutes refusées d'abord à un
  non-op (refus silencieux), puis si la source n'est pas la fenêtre, puis sans Map ou sans Environment (message). Les
  actions portent les ids visés (une sélection changée entre-temps par un autre joueur ne détourne pas l'édition) :
  0 ajouter, 1 retirer, 2 renommer, 3 monde ici, 4 avant du monde, 5 ajouter un corps (système, type, parent),
  6 retirer un corps (système, corps), 7 modifier **un champ** d'un corps (système, corps, champ, valeur : nom, rayon,
  couleur, luminosité, période, phase, masse ; T44 : période de rotation, obliquité, nœud, phase de rotation, texture
  — `auto`, `builtin:<id>` d'une texture intégrée connue ou `custom:<sha1>` **stockée pour la dimension** (T45) —,
  appliqués à la valeur courante du serveur, seul ce champ change), 8 vitesse de distorsion, 9 échelle de temps (C119), 10 « Orbite »
  (système, corps), **11 « RESET »** (système, corps ; D34 : `updateBody` avec le corps de mêmes ids, même type et même
  parent de la galaxie par défaut — `SpaceGalaxy.createDefault`, créée une fois —, sinon un astre neuf de son type,
  orbite actuelle gardée), **12 « Se teleporter »** (système, corps ; D34 : `env.teleportTo`, API serveur publique de
  l'Environment, doc 04 § 5 bis).
- **Ancienne sauvegarde** (L22) : les clés du lot 2 (`mode`, `system`, `body`, `zoom`, `origin`, `tedit`, `frame`,
  `detached`) sont ignorées sans erreur ; les objets `cube`, `handle`, `orbit` sauvegardés aussi (`HoloModule.loadAll`
  ignore les clés sans objet). La vue est à recréer dans la Map (ajoutée par commande).

## 7 bis. Space Pilot (lot 2 : T17 à T20, T22 ; réduit en T33)

- `SpacePilotModule extends HoloModule implements Configurable` (mémoire de **joueur**). `sharedWithObservers()` faux,
  tous ses objets `PRIVATE` : rien n'est envoyé aux observateurs (C45). **T33** (D25, C75) : la surcouche du ciel, ses
  réglages et la Quick Action sont passés à Space Knowledge (§ 7 ter) ; Space Pilot reste requis pour piloter (D11).
- **État sauvegardé et synchronisé au propriétaire** (T33) : `hud`, `refLink`, `cruise` (« Vitesse de croisiere »,
  T34), tous vrais par défaut. Modifié par `sendAction` (fenêtre de Config), validé par le serveur. Les
  clés du lot 2 qui ne servent plus (`enabled`, `orbits`, `bodies`, `stars`, `names`, `nameAngle`) sont ignorées au
  chargement sans erreur (L22) ; `hud` et `refLink` gardent leur valeur.
- **Objets** : `ConfigWindow` (Config, T17 ; trois interrupteurs « HUD », « Lien referentiel », « Vitesse de
  croisiere » depuis T33) ; `SpacePilotHud` (`HoloWindow` ancrée `Hud`, clé `hud`, coin haut gauche,
  `HoloEditTranslation` : l'Own HUD Editor le déplace ; `HoloLabel` rafraîchis par `SpacePilotModule.clientTick` :
  ligne de vitesse chaque tick (T34), référentiel et distance toutes les 5 ticks, altitude recalculée côté client au
  temps d'affichage ; `shown()` = `hudShown()` : Environment présent, `hud` actif et **joueur local aux commandes**
  (`SpacePilotPad.localPilot() != null`, T33), T20) ;
  `SpaceJoystickForward` / `SpaceJoystickAim` (`Local`, `PRIVATE`, clés `joystick_forward` / `joystick_aim`, T19) :
  `shown()` = `SpacePilotModule.joystickShown()` (miroir du propriétaire et `SpacePilotPad.localPilot() != null`),
  cercle avant placé par `SpacePilotModule.clientTick` sur l'axe avant de l'Environment.
- **Vitesse de croisière** (T34, D25, C76, [`04`](04-mathematiques.md) § 11) : si `cruise`, chaque tick client,
  `SpacePilotModule` lit le regard de la caméra (`SpacePilotModule.Backend.cameraLook`, installé par
  `client/modules/camera/spacepilot/SpacePilotBackend` : `Camera.getLookVector()`), le ramène dans les axes du système
  (`Q` de `pose(1)` : même tick que `speed()`), choisit la cible (`SpaceCruise.pickTarget`, hystérésis sur l'id gardé)
  et calcule `v*` (`SpaceCruise.targetSpeed` : distance à la surface, rayon, `V_max` ; consigne sûre au frein normal
  avec un temps de réaction humain, retouche T34) ; depuis T39 (C118), `v*` et le **point d'interception** viennent
  de `SpaceCruise.interceptSpeed` (mouvement complet de la cible relatif à l'entraînement du monde, fondu compris,
  positions un tick plus tard ; [`04`](04-mathematiques.md) § 11), exposé au renderer par
  `SpacePilotModule.cruiseIntercept(dest)` (axes du système), et la cible est gardée tant que le regard vise ce point à
  moins de 11° ; aucune cible pendant un saut. HUD : `Vitesse : v - ↑ v*` (même unité des deux côtés, `SpaceFormat.speedInAu` sur la plus grande ;
  flèches U+2191 / U+2193 de la police vanilla, `nonlatin_european.png` ; flèche et `v*` colorées par
  `SpaceCruise.redness`, 8 paliers de `0x55FF55` à `0xFF5555`, `Style` précalculés), quatrième ligne « Cible : nom »
  (« aucune » sans cible). Fenêtre déclarée à 240 × 50 ; sans `cruise`, le panneau racine est ramené à 150 × 39 (T20)
  depuis le coin haut gauche et la ligne « Cible » masquée. Textes reconstruits seulement quand une valeur arrondie
  (donc la flèche ou le palier de couleur) change. Le transform d'un `Hud` place le centre de la fenêtre : un état
  sauvegardé avant T34 (clé `hudLayout` absente, clé `hud` présente) voit son HUD décalé une fois de (+45, +5,5) px
  au premier tick serveur (`serverTick`, puis `markDirty`), pour garder son coin haut gauche.
- **Rendus** (tous pour le pilote local, pendant le pilotage, via `SpacePilotRenderers`) : le joystick est dessiné par
  `SpaceJoystickRenderer` (renderers d'objets `HoloObjectRenderers`, projection `STABLE` du pont vue). Le saut du
  pilote local (T22) est dessiné par `SpaceJumpRenderer`, un `ViewRenderer` (projection `WORLD`, devant le terrain
  comme le joystick) : double anneau de sélection autour de l'étoile cible dès l'appui, roue de charge pendant
  `CHARGE`, aide à la visée en pâle (toujours affichée depuis T33 : Space Pilot n'a plus d'interrupteur général), et
  depuis T39 (C118) le **point d'interception** de la croisière hors charge (anneau bleu pâle « viser ici », 5 px, dans
  la direction conseillée tournée par `SpaceSkyFrame.rotateToWorld`, seulement s'il s'écarte du disque de la cible,
  coupé avec `cruise`) ; rien pendant le voyage. **Lien vers le référentiel** (T33) : `SpaceReferenceLinkRenderer`, `ViewRenderer` en projection
  `WORLD` (sphère de 1,5 bloc, `SpaceOverlay.quads()`), seulement si `SpacePilotPad.localPilot() != null`, `refLink`
  actif, hors voyage et distorsion ; il dessine l'**anneau du référentiel** (rayon et couleur de
  `SpaceOverlay.bodyRing` / `kindColor`, comme Space Knowledge) et, en première personne, le lien du bord extérieur
  du cercle avant (ramené de `STABLE` à `WORLD`, [`04`](04-mathematiques.md) § 9) au bord extérieur de cet anneau, sans
  écart. Les renderers lisent le module **du joueur local** (`SpacePilotClient.module()` :
  `HoloModes.find(ClientHoloWorld.find(new HoloScope.Player(uuid)), SpacePilotModule.class)`, mis en cache une fois par
  tick) : un autre joueur ne les voit jamais. Le HUD remplace la ligne « Distance » par « Distorsion -> cible : n s »
  pendant le voyage.
  **Cible du vaisseau** (T43, D33, C125–C128) : `SpaceTargetRenderer`, `ViewRenderer` en projection `WORLD`
  (`SpaceOverlay.quads()`, espace vue, sphère de 1,5 bloc), **deux instances** de la même classe (`Part.ARROW`,
  `Part.RING`) enregistrées autour du lien du référentiel : saut, **flèche**, lien, **anneau** (l'ordre
  d'enregistrement fait l'ordre de dessin, `endBatch` après chaque renderer). Mêmes conditions que le saut : pilote
  local aux commandes (`SpacePilotPad.localPilot()`), Space Pilot dans la mémoire active, Environment synchronisé avec
  une cible, rien pendant le voyage et la distorsion (`SpaceWarpTimeline.active`) ; ni `refLink` ni Space Knowledge
  requis. Anneau vert `0x40FF40` (`SpaceOverlay.TARGET_COLOR`) : cible dans le système courant → rayon de
  `SpaceOverlay.bodyRing` (celui de Space Knowledge et du référentiel), épaisseur 2 px (au lieu de 1,5) : il les
  recouvre ; cible dans un autre système → anneau de 12 px autour de l'étoile de fond de ce système (direction comme
  `SpaceJumpRenderer`), au-delà du double anneau de l'aide au saut (6 / 9 px). Flèche (première personne seulement,
  comme le lien) : triangle plein, base à 1 px au-delà du bord extérieur du cercle avant, pointe 8 px plus loin,
  largeur 9 px, tourné selon l'angle de la cible autour de l'axe avant ; même conversion `STABLE → WORLD` que le lien
  (base `M c`, le point où le lien vers la cible quitterait le cercle, puis le grand cercle `F' → R`) ; masquée quand
  la direction de la cible (préimage `M⁻¹ R`) est dans le cône du bord extérieur du cercle avant ; dessinée avant le
  lien, dont les tirets passent par-dessus.
- **Compatibilités** : modèles à imiter `demo/DemoConfigurableModule.java` (Config), `demo/DemoPlayerModule.java`
  (objet `Hud`) ; capacité d'édition déduite par `HoloEditRules.ownCapability` (`Hud` → `OWN_HUD`).

## 7 ter. Space Knowledge (lot 3 : T33)

- `SpaceKnowledgeModule extends HoloModule implements Configurable, QuickActionProvider` (mémoire de **joueur**,
  `hololib:space_knowledge`, D25). `sharedWithObservers()` faux, objets `PRIVATE` (C45). Pas requis pour piloter.
- **État sauvegardé et synchronisé au propriétaire** : `enabled` (Quick Action), `orbits`, `bodies` (repérage),
  `stars`, `names`, `nameAngle` (degrés, 1 à 30, défaut 5), `distance`, tous vrais par défaut. Modifié par
  `sendAction` (fenêtre de Config, action de la roue), validé par le serveur.
- **Objets** : `SpaceKnowledgeConfigWindow` (`ConfigWindow` « Space Knowledge » : interrupteurs Orbites, Repérage des
  corps, Étoiles extérieures, Nom au regard, barre de l'angle du nom, Distance). **Quick Action** `toggle`
  (« Space Knowledge : OFF / ON »), qui coupe toute la surcouche.
- **Rendu** : `SpaceKnowledgeSkyRenderer`, `SkyRenderer` bas niveau (l'ancien `SpacePilotSkyRenderer` sans le lien),
  enregistré après `SpaceSkyRenderer` par `SpaceKnowledgeRenderers` ; lit `SpaceKnowledgeClient.module()` (module du
  joueur local, mis en cache une fois par tick) et le miroir de l'Environment ; anneaux, orbites, étoiles extérieures,
  nom au regard et, dessous si `distance`, la distance à la surface de l'objet (`SpaceFormat.distance`) ou, pour une
  étoile extérieure, la distance en al (`SpaceFormat.lightYears`), texte reconstruit seulement quand la valeur
  arrondie à trois chiffres change. Coupé pendant la distorsion (`SpaceWarpTimeline.active`, T35). Règles partagées avec
  Space Pilot (pixel de référence, rayon et couleur des anneaux, `RenderType`) : `SpaceOverlay`
  (`client/render/modules/dimension/space/`).
- **Compatibilités** : modèles `demo/DemoConfigurableModule.java` (Config), `demo/DemoQuickActionsModule.java`
  (Quick Action).

## 7 quater. Space Map (lot 3 : T37)

`SpaceMapModule extends HoloModule implements SpaceMapView` (mémoire de **position**, marker, `hololib:space_map`,
D20, C77) : carte de la galaxie **visible et utilisable par tous**, vue partagée par tous les joueurs.

- **Objets** (`PUBLIC`, `shown()` vrai pour tous) :
  - `SpaceMapCube` (déplacé du Configurator ; `HoloEditTranslation`, `HoloEditRotation`, `HoloEditScale`,
    `opRequired()` vrai, `ownRequired()` faux : placé par les ops au `world_editor` ; échelle uniforme, C11) ; accès
    `EVERYONE` ; il implémente `HoloScrollable` : la molette, regard sur son plan médian `z = 0`, zoome la vue si
    sa `SpaceMapView` est `zoomable()`. Il lit sa vue par `view()` (module qui l'implémente : la Map seule depuis
  T42).
  - `SpaceMapWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation, HoloEditVisibility`
    (220 × 176 px, 1/100 bloc par pixel) : accès `EVERYONE`, placement par les ops ; placée à sa création devant la
    base du cube (aussi son RESET), puis indépendante. Contenu : ligne d'information (coordonnées de la sélection : al
    relatives à l'origine, ou UA relatives à l'étoile principale au temps vivant, rafraîchies toutes les 10 ticks ;
    sinon cadre et légendes), **arborescence de la gravité** (`HoloWidgetList` de `SpaceMapTreeRow` : un
    `HoloWidgetMultiButton` à texte aligné à gauche, retrait par niveau, repère `+` / `-`), boutons « Zoom - »,
    zone de molette (`HoloWidgetScrollZone`, affiche la demi-étendue), « Zoom + », « Systeme » / « Galaxie »,
    « Vue d'ensemble », « Origine @ » ; molette sur le fond de la fenêtre = zoom (la liste garde la sienne).
- **Arborescence** : « Galaxie (n) » > systèmes (courant en vert, C67 ; origine précédée de `@ `, C68) > corps par
  parenté. Clic sur un nom = sélection + recentrage (action) ; clic sur le repère (ou clic droit) = déplier. Un seul
  système déplié à la fois : celui dont la Map copie les corps (« système affiché ») ; déplier un autre système est une
  action (partagée), replier est local, comme le dépliage des corps (défaut : étoiles dépliées, ancêtres de la sélection
  dépliés). Lignes recréées à la synchronisation (révision de l'Environment comprise) et au dépliage.
- **Vue partagée** (état sauvegardé et synchronisé) : mode, système affiché, système sélectionné, corps sélectionné,
  corps au centre (vue système), demi-étendue de la vue système (UA, continue, `[1e-6 ; 2e4]`), centre et demi-étendue
  de la carte galactique (al, `[0,05 ; 200 000]`), origine des coordonnées. Recentrage : système → carte galactique
  centrée sur lui ; corps → vue système centrée sur lui à `SpaceSystemScale.framingHalf` (le zoom sur un corps de T25,
  ou, pour une lune sans descendant, au moins son orbite : la Terre et la Lune). Zoom : `÷ / × 1,5` par cran, vers le
  centre.
- **Temps** : la vue système suit le **temps vivant** de l'Environment (`displayDays(partialTick)` du miroir client ;
  `days()` côté serveur, `viewDays`) ; plus de temps figé ni d'« Animer » (outils d'édition du Configurator).
- **Actions** (source = cube ou fenêtre, tout joueur à portée) : 0 « Galaxie », 1 sélection d'un système, 2 dépliage,
  3 sélection d'un corps, 4 mode, 5 zoom (octet signé, ±8 crans au plus), 6 vue d'ensemble, 7 origine, **8 cible**
  (T43 : booléen) — table dans la javadoc de `SpaceMapModule`.
- **Cible du vaisseau** (T43, D33) : la **sélection ciblable** (`selectionTargetSystem()`, `selectionTargetBody()`,
  deux côtés) est celle de la ligne sélectionnée : en vue système, le corps sélectionné (rien sans corps) ; sur la
  carte galactique, le système sélectionné (`bodyId = 0`) ; jamais « Galaxie ». Action `8 TARGET + bool` : `true` →
  `env.setTarget(sélection)` (refus affiché), la Map retient la cible **posée** (`postedSystem`, `postedBody`, état
  serveur sauvegardé — clés `targetSystem`, `targetBody` —, non synchronisé ; un `markDirty` limité à 4 ticks pour le
  faire sauvegarder) ; `false` → cible retirée si c'est la sélection. À **chaque tick serveur** (`checkPostedTarget`) :
  cible posée oubliée si elle n'est plus celle du vaisseau (posée par une autre Map, retirée, astre disparu) ; retirée
  du vaisseau si la sélection ciblable a changé (clic, « Galaxie », mode, `focusSystem` / `selectInView` /
  `frameOrbit`, sélection invalidée par la copie) : un seul point de contrôle couvre tous les chemins, au plus un tick
  après le changement. Fenêtre : la ligne sélectionnée porte au bout une zone de bouton de 50 px
  (`SpaceMapTreeRow.setTargetButton`, `TARGET_WIDTH`, décidée par `clickX` comme le repère `+` / `-`) : « CIBLER »
  (blanc) ou « DECIBLER » (vert `0xFF40FF40`) si la sélection est la cible lue dans le miroir client ; le nom est
  tronqué d'autant (`textWidth(width, depth, true)`) ; clic gauche dans la zone = action 8, ailleurs inchangé. Lignes
  reconstruites quand la cible du miroir change (comparée à chaque tick client de la fenêtre, jamais par frame).
- **API pour le Configurator** (T42) : recherche `SpaceMapModule.of(module)` (§ 7) ; lecture : `selectedSystem()`,
  `selectedBody()`, `selectedHeader()`, `selectedBodyData()`, `displayedSystem()` / `displayedSystemData()` / `shownSystem()`, `headers()`, `copiedRevision()`, `cube()`,
  `cubeToMarker` / `markerToCube` (repère du cube, doc 04 § 7), `galaxyPoint`, `bodyPoints(days, dest)`,
  `centerIndex()`, `viewHalf()`, `frameX..frameHalf()`, `viewDays(partialTick)` ; serveur : `refreshFrom(env)`,
  `focusSystem`, `selectInView`, `frameOrbit` ; client : `hideOrbit` (orbite omise par le renderer,
  `hiddenOrbitBody()`).
- **Rendu** : `SpaceMapCubeRenderer` (déplacé, `client/render/modules/monde/spacemap/`, enregistré par
  `SpaceMapRenderers`), lit la `SpaceMapView` du cube (la Map). Corps texturés et tournant
  sur eux-mêmes avec le cache partagé `SpaceBodyTextureCache.instance()` (textures demandées pour tous les corps du
  système montré ; `beginFrame()` appelé par cube dessiné en vue système, en plus du ciel). Détails : doc 04 § 7.
- **Client** : aucune logique client propre (pas de backend : tout est visible de tous, le temps vient de
  `SpaceEnvironmentModule.client()`) ; pas de package `client/modules/monde/spacemap/`.

## 7 quinquies. Textures importées (lot 3 : T45, D21, D27)

Canal réseau **dédié** (D27) : `SpaceTextureNetwork`, `SimpleChannel` `hololib:space_textures`, protocole `"1"`,
déclaré et enregistré dans `modules/dimension/space/texture/` ; `HoloNetwork` et son protocole (`"8"`) inchangés. Le
transport du framework ne convient pas (action ≤ 32 767 o, synchro d'un module envoyée à toute la dimension).

| # | Paquet | Sens | Contenu |
|---|---|---|---|
| 0 | `Begin` | C → S | empreinte (20 o), taille (varint) |
| 1 | `Chunk` | C → S | décalage (varint), octets (≤ 30 000) |
| 2 | `Request` | C → S | empreinte (20 o) |
| 3 | `Result` | S → C | empreinte, état (`ACCEPT` / `DONE` / `REFUSED`), message (utf ≤ 256) |
| 4 | `Data` | S → C | empreinte, taille totale, décalage (varints), octets (≤ 30 000) |
| 5 | `Missing` | S → C | empreinte |

**Import** (op, onglet « Texture » du Configurator) :
1. Client de l'op (`SpaceTextureImport`) : sélecteur natif `TinyFileDialogs` sur un **fil à part** (le jeu continue) ;
   fichier ≤ 64 Mo, PNG ou JPEG reconnu à sa signature, ≤ 16 384 px de côté (lu avant décodage) ; décodé en RGB
   (`STBImage`), **étiré** en 2:1 (jamais recadré, C138) à la largeur `2^round(log2(max(w, 2h)))` bornée à
   `[64, 1024]` (`STBImageResize`), réencodé en PNG RGB (`STBImageWrite`) ; au-delà de 2 Mio, largeur ÷ 2 ; SHA-1.
2. `Begin` → serveur (`SpaceTextureServer`) : op vérifié, taille ≤ 2 Mio, un import à la fois par joueur, Environment
   présent dans la dimension du joueur ; fichier déjà stocké → `DONE` aussitôt (rien à envoyer) ; quota de 64 atteint →
   orphelins supprimés (empreintes qu'aucun corps de la galaxie de l'Environment ne référence), puis refus s'il reste
   plein ; sinon `ACCEPT`.
3. Client : `Chunk` de 30 000 o, **4 par tick** (`ClientTickEvent`, ≈ 2,4 Mo/s) ; serveur : op revérifié à chaque
   morceau, morceaux dans l'ordre et dans la taille annoncée (sinon abandon), délai de 200 ticks sans morceau →
   abandon (`REFUSED`).
4. Dernier morceau : validation **hors du fil serveur** (`SpaceTextureStore.validate` : empreinte recalculée, signature
   PNG, dimensions lues dans l'en-tête avant décodage — exactement 2:1, 16 à 1024 px de large —, décodage complet par
   `javax.imageio`, métadonnées ignorées), écriture atomique (`.tmp` puis déplacement, quota recompté sous verrou) ;
   `DONE` ou `REFUSED` + motif.
5. `DONE` : le client enregistre son image dans son cache, puis demande l'application à l'astre par l'**action 7** du
   Configurator (`custom:<sha1>`), qui garde ses contrôles (op d'abord, source, Map, Environment, distance par le
   framework) et vérifie en plus que le fichier est stocké pour la dimension. Messages dans la barre d'action.

**Distribution** : `SpaceBodyTextureCache.texture` passe au **demandeur** (`SpaceTextureClient.request`, installé au
setup client) l'empreinte d'un corps `custom` sans image enregistrée ; une demande par empreinte (nouvelle demande
après 60 s si le serveur ne l'avait pas). Serveur : file par joueur (≤ 64 empreintes, doublons ignorés), une texture en
mémoire par joueur (lue hors du fil serveur dans le dossier de la dimension du joueur), envoyée **à lui seul** en
`Data` de 30 000 o, 4 par tick ; absente → `Missing`. Client : réassemblage dans l'ordre, empreinte vérifiée, décodage
`NativeImage` RGBA hors du fil de rendu, `registerCustom` (texture mipmappée du cache de T30, C139) ; repli `auto` en
attendant ; tout est libéré à la déconnexion (`releaseAllCustom`), pas au changement de dimension (contenu adressé par
empreinte).

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
| RenderTypes | `HoloRenderTypes.SOLID_QUADS`, `TRANSLUCENT_QUADS` (`POSITION_COLOR_LIGHTMAP`) ; un renderer Space peut définir un `RenderType` **local privé** si aucun ne convient (ex. `GlowType` de `SpaceMapCubeRenderer` (Space Map depuis T37) : translucide sans écriture de profondeur, T10 ; textures d'astres : `SpaceBodyTextureCache.Texture.renderType()`, `POSITION_COLOR_TEX`, T30) — l'ajouter à `HoloRenderTypes` serait une demande d'API | `client/render/type/HoloRenderTypes.java` |
| Exemples à imiter | rendu d'astre éclairé à la main : `Planet1PlanetRenderer` ; cercle en quads : `EditorReferenceRenderer` ; anneaux / segments : `HoloGizmoRenderer` (privés : copier) ; texte : `BufferHoloCanvas.text` (`font.drawInBatch`) ; envoi prédictif : `demo/DemoPad.java` ; liste : `ConfigListWindow` | `client/render/...`, `demo/`, `modules/camera/config/` |

## 9. Points d'attention connus

- **Mémoire de position** : `ownerView = false` pour tous → objets non `PUBLIC` jamais dessinés ;
  `HoloAccess.OWNER` = personne ; `HoloObject.requestTransform` sans effet (rien n'est envoyé hors de la mémoire du
  joueur local) → passer par `sendAction`.
- **Capture liée à un objet** : terminée si l'objet est retiré, n'est plus routé (`shown()` faux, mémoire du marker
  oubliée — chunk plus suivi) ou si son accès ne l'autorise plus. C'est pourquoi le pad du Controller n'utilise plus
  la capture liée depuis T41 (fenêtre masquée pendant le pilotage) : il gère lui-même ces fins (§ 6).
- **Édition `World`** : translation limitée à `‖t‖ ≤ 64` (`HoloAnchor.MAX_REACH`), gizmos sur les axes du monde (pas
  ceux du cube), taille min des gizmos 0,5 bloc, portée d'édition 1 à 16 blocs (défaut 8).
- **Ciel** : `hiddenWithSky()` vrai par défaut — garder ce défaut (pas de ciel spatial dans la lave, la neige
  poudreuse, en cécité / obscurité, sous brouillard épais ou dans une dimension sans ciel ; sous l'eau le ciel reste
  dessiné, seul le brouillard garde la couleur vanilla).
  Profondeur partagée entre renderers du ciel, vidée après la passe ; rien au-delà de `farPlane()` (≥ 128).
- **Sync** : `writeSync` est rappelé à chaque `markDirty` (un tampon pour toute la dimension) et à chaque instantané ; il ne connaît pas le destinataire. L'Environment n'y joint son bloc froid qu'au besoin (T15b) → [`05-synchronisation.md`](05-synchronisation.md) §§ 1, 2.
- **Pas de Mixin**, pas d'OpenGL brut, pas d'allocation par frame.
