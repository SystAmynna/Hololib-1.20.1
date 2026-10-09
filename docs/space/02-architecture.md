# 02 — Architecture

Chemins relatifs à `src/main/java/com/amynna/hololib/`. Les signatures citées ont été vérifiées dans le code au
moment de la rédaction ; en cas d'écart, le code fait foi et l'écart est signalé dans le compte rendu.

## 1. Packages

Miroir de l'organisation existante (`modules/package-info.java` : un sous-package par compatibilité — `dimension`,
`monde` pour les modules de position, `camera` pour les modules de joueur — puis un package par module ; le client
en miroir sous `client/`).

```
modules/
├── dimension/space/                  module Space Environment + code commun partage par les 3 modules
│   ├── SpaceEnvironmentModule.java
│   ├── model/                        donnees pures (T01) : SpaceGalaxy, SpaceSystem, SpaceBody, SpaceBodyKind,
│   │                                 SpaceOrbit, SpaceLimits
│   └── math/                         calculs purs (T02) : SpaceUnits, SpaceOrbits, SpacePose, SpaceMotion,
│                                     SpacePilotInput, SpaceSkyFrame
└── monde/
    ├── spacecontroller/              SpaceControllerModule, SpaceControllerWindow, SpacePilotPad,
    │                                 SpaceJoystickForward, SpaceJoystickAim
    └── spaceconfigurator/            SpaceConfiguratorModule, SpaceMapCube, SpaceConfiguratorWindow,
                                      SpaceMapHandle, SpaceOrbitHandle

client/
├── modules/dimension/space/          SpaceEnvironmentClient (backend, recherche du miroir de dimension)
├── modules/monde/spacecontroller/    SpaceControllerClient (backend : pilote local, regard)
├── modules/monde/spaceconfigurator/  SpaceConfiguratorClient (backend : op local)
└── render/modules/
    ├── dimension/space/              SpaceSkyRenderer (SkyRenderer), SpaceRenderers (enregistrement)
    └── monde/
        ├── spacecontroller/          SpaceJoystickRenderer(s)
        └── spaceconfigurator/        SpaceMapCubeRenderer, SpaceHandleRenderer, SpaceConfiguratorRenderers
```

Chaque package reçoit sa `package-info.java` (rôle, règles, renvoi vers ce dossier). Fichiers existants modifiés :
`modules/HoloLibModules.java` (3 enregistrements), `client/render/modules/ModuleRenderers.java` (appel des
`XxxRenderers.register()`), et pour `T03` seulement ceux listés dans [`06-extension-api.md`](06-extension-api.md).

## 2. Enregistrement

```java
// modules/HoloLibModules.java (meme forme que PLANET_1)
SPACE_ENVIRONMENT  = REGISTER.register("space_environment",
        () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, SpaceEnvironmentModule::new));
SPACE_CONTROLLER   = REGISTER.register("space_controller",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceControllerModule::new));
SPACE_CONFIGURATOR = REGISTER.register("space_configurator",
        () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceConfiguratorModule::new));
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
| Fenêtre de contrôle, cercles du joystick | `SpaceControllerModule` (mémoire du marker) | joueurs qui suivent le chunk du marker |
| Cube, poignées, fenêtre, sélection, mode | `SpaceConfiguratorModule` (mémoire du marker) | idem |
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
- **API client** : `pose(partialTick, dest)`, `currentSystem()`, `backgroundStars()`, `bodyPosition(index, time,
  dest)`, `predictPilotInput(input)` (pilote local).

`SpaceSkyRenderer implements SkyRenderer` (T05) : lit le miroir, dessine fond opaque, étoiles de fond, corps ;
surcharge la couleur du brouillard via l'extension T03. Détails dans la carte T05 et [`04`](04-mathematiques.md) § 4.

## 6. Space Controller (T06, T07, T08)

- **Objets** (tous `PUBLIC`, mémoire de position sans propriétaire) :
  - `SpaceControllerWindow extends HoloWindow`, ancrage `World()`, au-dessus du marker ; accès `EVERYONE` par défaut
    (réglable, [`decisions.md`](decisions.md) C2) ; contient un `SpacePilotPad` et des `HoloLabel` d'état.
  - `SpaceJoystickForward` (`Local(false)`, cercle avant) et `SpaceJoystickAim` (`Local(true)`, cercle de visée) :
    `shown()` vrai seulement chez le pilote local (backend).
- **`SpacePilotPad`** : widget composé des traits `HoloPointerInput` + `HoloHoverable` (capture `MOUSE_MOVE`, Échap,
  Entrée au clic) et qui ajoute à `captures` les mappings `key.forward`, `key.back`, `key.left`, `key.right`
  (`HoloControl.mapping(...)` suit les touches du joueur ; constantes déjà dans `HoloDirections.UP/DOWN/LEFT/RIGHT`).
  La capture est liée à la fenêtre (`ctx.capture`, faite par le trait). Dans `onMouseMove`, il tourne lui-même la
  caméra (`HoloCapture.setLook`) en la bornant au cône ([`04`](04-mathematiques.md) § 6).
- **Actions** (`sendAction(window, ...)`, octet de type) : `BEGIN`, `INPUT` (entrée quantifiée), `END`. Le serveur
  vérifie `player.hasPermissions` si l'accès l'exige, la distance (déjà faite par `HoloContainers.actionTarget`), puis
  relaie à `SpaceEnvironmentModule.of(level)`.
- **État synchronisé** : pilote courant (nom), vitesse, corps le plus proche et distance (rafraîchis 4 fois / s au
  plus), présence d'un Environment.

## 7. Space Configurator (T09 à T12)

- **Objets** (`PUBLIC`, `shown()` = joueur local op, via backend) :
  - `SpaceMapCube extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale` ;
    `opRequired() = true`, `ownRequired() = false` (patron `demo/DemoWorldPosModule.java:19`) ; ancrage `World()`.
    Son renderer dessine **tout le contenu** (galaxie ou système) dans le repère local du cube.
  - `SpaceConfiguratorWindow extends HoloWindow`, `World()`, posée devant la base du cube et replacée par le module
    quand le cube bouge ; accès = liste des ops connectés, tenue à jour.
  - `SpaceMapHandle` (`HoloEditTranslation`, op) : n'existe que si un système est sélectionné en mode galaxie ;
    posée sur l'étoile sélectionnée.
  - `SpaceOrbitHandle` (`HoloEditRotation`, `HoloEditScale`, op) : n'existe que si un corps à orbite est sélectionné
    en mode système ; posée sur le parent, représente l'ellipse.
- **Pas de callback d'édition dans le framework** : le module **sonde** dans `serverTick` le transform des objets
  édités (comparaison avec la dernière valeur qu'il a lui-même écrite) et en déduit les modifications
  ([`04`](04-mathematiques.md) § 7).
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
module et le renderer, sans allocation), `SpaceOrbitHandleRenderer`. Actions du module : 0 sélection système, 1 mode,
2 recadrer, 3 ajouter, 4 retirer, 5 renommer, 6 monde ici, 7 avant du monde, 8 sélection corps, 9 ajouter un corps
(type + parent), 10 retirer un corps, 11 modifier **un champ** d'un corps (nom, rayon, couleur, luminosité, période,
phase : une copie client en retard n'écrase pas une orbite modifiée par la poignée).

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
| Rendu du ciel | `SkyRenderer` (`hiddenWithSky`, `shouldRender`, `render`), `SkyRenderContext` (`SKY_RADIUS = 100`, `partialTick`, `farPlane`) | `client/render/sky/` |
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
- **Sync** : `writeSync` est renvoyé **en entier** à chaque `markDirty` → [`05-synchronisation.md`](05-synchronisation.md).
- **Pas de Mixin**, pas d'OpenGL brut, pas d'allocation par frame.
