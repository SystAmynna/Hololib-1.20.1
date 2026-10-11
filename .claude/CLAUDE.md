# Amynna Holo Lib

Mod Forge pour Minecraft **1.20.1** (Forge 47.4.10, mappings `official`, Java 17). Mod id `hololib`, package racine
`com.amynna.hololib`. L'utilisatrice ecrit en francais.

Le projet est une **reecriture propre**. `prototype/` (ignore par git) contient l'ancien mod, entierement vibe-code
(package `com.systamynna.hololib`) : s'en servir uniquement pour comprendre une fonctionnalite, jamais pour en
copier la structure.

## Build

```bash
JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava   # compilation
JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew runClient     # lancer le jeu
```

Mods de test : deposer les jars (1.20.1 Forge, dependances comprises) dans `dev-mods/` (ignore par git) ; remappes
par `fg.deobf`, charges par tous les runs (un mod purement client fait planter `runServer`), jamais compiles ni
publies.

Le Java systeme est en version 26, que Gradle 8.8 / ForgeGradle 6 refusent ("Unsupported class file major version 70").

Pour verifier le comportement de Minecraft/Forge, lire les sources mappees plutot que deviner :
`~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`
(ex. `net/minecraft/client/renderer/LevelRenderer.java`, `GameRenderer.java`).

## Architecture

```
com.amynna.hololib
├── HoloLib.java                 classe @Mod (registres, capability, reseau)
├── holo/                        modele COMMUN, aucune classe client (voir holo/package-info.java)
│   ├── object/                  HoloObject, HoloAnchor (Sky/World/Local/Hud), HoloTransform, HoloVisibility, HoloAccess
│   ├── window/ widget/          HoloWindow ; HoloWidget, HoloPanel, HoloLabel, HoloCanvas, HoloTextMetrics
│   │   └── widget/control/      widgets interactifs generiques : traits (HoloClickable...) + HoloWidgetXxx
│   ├── module/                  HoloModule (etat + logique + objets, deux cotes), registre Forge HoloModuleTypes
│   ├── memory/                  HoloMemory, HoloScope, HoloContainer(s) ; player/ dimension/ position/
│   ├── inspect/                 HoloInspection (qui voit qui), HoloInspections (serveur), HoloInspectEvent
│   ├── capture/                 HoloControl, HoloKeys, HoloCaptor, HoloCapture, HoloCaptures (capture, commune)
│   ├── interact/                HoloInteractible, HoloInteraction (interaction au regard, partie commune)
│   ├── edit/                    interfaces d'edition (HoloEditable + outils), HoloDisplay, HoloRelativeToOpen,
│   │                            HoloEditRules (droits, validation), HoloEditMath (rotations, Local relatif <-> follow)
│   └── mode/                    HoloMode (FOCUS, EDITOR), HoloModeModule (module qui porte un mode)
├── network/                     HoloNetwork + paquets (snapshot, module, objet, origine, oubli ; deplacement, action,
│                                edition)
├── command/ demo/               /hololib_marker, /hololib_module, /hololib_(force_)inspect ; demos (1 module par compatibilite + interaction)
├── modules/                     modules du mod (HoloLibModules = registre) : un sous-package par compatibilite
│   ├── dimension/planet1/       (dimension, monde, camera), un package par module (module + objets, code commun)
│   ├── dimension/space/         Space Environment (ciel = systeme stellaire, galaxie, pose du monde, saut, cible,
│   │                            teleportation) + code commun Space : SpaceArchive (SavedData, persistance), SpaceFormat
│   │                            (formats affiches), SpaceBodyTextures (textures integrees, regle auto) ; model/
│   │                            (galaxie, systemes, corps STAR/PLANET/MOON/MINOR, orbites, bornes, SpaceRotation,
│   │                            SpaceTextureRef, SpaceStarField : fond etoile, SpaceKnownMasses, SpaceSolRotations) ;
│   │                            math/ (orbites, pose, mouvement, spheres d'influence, horloge et epoque, entree du
│   │                            pilote, SpaceJump : saut, SpaceSpin : rotation propre, SpaceCruise : croisiere) ;
│   │                            texture/ (canal dedie hololib:space_textures : SpaceTextureNetwork, paquets,
│   │                            SpaceTextureServer, SpaceTextureStore : fichiers par dimension, quota)
│   ├── monde/spacecontroller/   Space Controller (marker) : fenetre de pilotage, SpacePilotPad (gaz, boost, joystick,
│   │                            saut ; capture explicite non liee a la fenetre)
│   ├── monde/spacemap/          Space Map (marker, tous) : cube carte (SpaceMapCube), fenetre de navigation
│   │                            (SpaceMapWindow, arborescence SpaceMapTreeRow, CIBLER), vue partagee (SpaceMapView),
│   │                            SpaceSystemScale (echelle, zoom), SpaceCoordinates (origine, coordonnees)
│   ├── monde/spaceconfigurator/ Space Configurator (marker, ops) : fenetre d'edition (onglets Corps / Rotation /
│   │                            Texture, RESET, Se teleporter, Importer) branchee sur la Map du marker, poignees sondees
│   ├── camera/spacepilot/       Space Pilot (joueur) : reglages (Config), cercles du joystick (SpaceJoystickForward /
│   │                            Aim), HUD de navigation et vitesse de croisiere (SpacePilotHud)
│   ├── camera/spaceknowledge/   Space Knowledge (joueur) : reglages de la surcouche du ciel (Config, Quick Action)
│   └── camera/focus|editor|config|quickaction/ modules joueur : mode focus (H) ; editeurs (G)
│                                own fix/dyn/hud, world/dimension/other + editor_panel (fenetre Config) ; config (ecrou du focus -> liste des modules
│                                Configurable) ; quickaction (roue d'actions du focus, QuickActionProvider)
└── client/
    ├── holo/                    ClientHoloWorld (miroirs des modules par portee), handlers des paquets, HoloModes
    ├── capture/                 HoloControlCapture : SEUL abonne aux inputs ; HoloKeyboardScreen (clavier entier)
    ├── interact/                HoloInteractions : picking + survol ; captor passif de la capture
    ├── key/                     HoloKeyMappings (touches du mod, etat physique d'un controle)
    ├── edit/                    HoloEditSession (session d'edition), HoloEditTools (outil actif, poignees, gizmos),
    │                            boutons des objets edites (HoloEditTarget / Panel / EyeButton)
    ├── modules/camera/          logique client des modules joueur : FocusController, EditorController,
    │                            ConfigController, QuickActionController ; SpacePilotClient / SpaceKnowledgeClient
    │                            (module du joueur local, relu chaque tick), SpacePilotBackend (regard de la camera)
    ├── modules/dimension|monde/ backends client des modules Space (SpaceEnvironmentClient, SpaceControllerClient,
    │                            SpaceConfiguratorClient : miroir de dimension, joueur local, op local, Space Pilot local) ;
    │                            SpaceSoundController (sons du vaisseau), SpaceTextureClient (canal des textures),
    │                            SpaceTextureImport (selecteur de fichier, preparation de l'image importee)
    └── render/                  TOUS les renderers du mod (regle d'architecture)
        ├── package-info.java    doc detaillee : ordre de rendu, regles (source de verite)
        ├── RenderDispatcher.java seul abonne aux evenements de rendu, route vers les passes
        ├── HoloRenderers.java   registre des renderers (register/unregister, fil de rendu)
        ├── HoloBuffers.java     BufferSource immediat du mod (toutes les passes) ; ShaderPacks : shaderpack Oculus actif
        ├── sky/                 SkyRenderer, SkyRenderContext, SkyRenderPass
        ├── world/               WorldRenderer<T>, WorldRenderable, WorldRenderContext, WorldRenderPass
        ├── view/                ViewRenderer, ViewRenderContext, ViewRenderPass
        ├── hud/                 HudRenderer, HudRenderContext, HudRenderPass
        ├── demo/                DemoCubeRenderer (objets des demos)
        ├── edit/                HoloGizmoRenderer (segments / cercles des objets World edites)
        ├── modules/             renderers des modules, meme arborescence que modules/ (ModuleRenderers au setup) ;
        │                        Space : dimension/space (SpaceSkyRenderer, SkyRenderer : fond, fond etoile, corps
        │                        textures ; SpaceBodyTextureCache : textures des astres, integrees et importees ;
        │                        SpaceOverlay : regles des surcouches ; warp/ : distorsion du saut, SpaceWarpTimeline
        │                        et scene), camera/spaceknowledge (SpaceKnowledgeSkyRenderer : surcouche, SkyRenderer
        │                        enregistre apres le ciel), camera/spacepilot (SpaceJoystickRenderer ; ViewRenderer
        │                        WORLD : SpaceJumpRenderer cible et roue du saut, SpaceTargetRenderer x2 fleche /
        │                        anneau de la cible, SpaceReferenceLinkRenderer lien et anneau du referentiel),
        │                        monde/spacemap (SpaceMapCubeRenderer), monde/spaceconfigurator (SpaceHandleRenderer,
        │                        SpaceOrbitHandleRenderer, SpaceConfiguratorWindowRenderer : fenetre + apercu)
        ├── holo/                ponts objets -> familles (HoloRouting, 4 bridges + HoloFocusBridge : fenetre
        │                        d'outils visee au-dessus de tout), HoloObjectRenderer(s), fenetres,
        │                        HoloPicking (objet sous le reticule ; pas un renderer, partage les placements),
        │                        HoloAttachment (overlays clients a cote d'un hote), HoloHudPreview (HUD en miniature)
        └── type/HoloRenderTypes RenderTypes du mod (POSITION_COLOR_LIGHTMAP, opaque / translucide)
```

### Objets holographiques

- Separation modele / rendu (comme `Entity` / `EntityRenderer`) : `HoloObject` est commun, son dessin est un
  `HoloObjectRenderer` client enregistre par classe dans `HoloObjectRenderers`.
- Un objet choisit un **ancrage**, pas un renderer : `Sky` (celeste) et `World` (monde) ancres dans le monde ;
  `Local` sur une sphere autour de l'oeil, relatif (orientation du monde, on peut detourner le regard) ou follow
  (tourne avec la camera) ; `Hud` a plat sur l'ecran. `HoloRouting` le resout par observateur : le `Local` d'un autre
  joueur (ou le sien en F5) est dessine dans le monde autour de son oeil selon son regard ; le `Hud` n'est dessine que
  pour son proprietaire en premiere personne (masque en F5).
- Le placement est le **`HoloTransform`** de l'objet (translation, rotation yaw/pitch/roll, echelle), **mutable** (un
  module deplace un objet en le modifiant ; synchronise au tick suivant), interprete selon l'ancrage : `Sky` tel quel
  autour de l'oeil ; `World` depuis l'hote (joueur proprietaire, marker dont la position est envoyee au client,
  origine du monde pour une dimension) ; `Local` : `x`/`y` = yaw/pitch sur la sphere, `z` = distance, puis rotation et
  echelle ; `Hud` : `x`/`y` pixels GUI depuis le point d'alignement, `z` = ordre de dessin. Bornes des demandes
  clients : `HoloAnchor.acceptable(transform)`.
- `HoloObject.shown()` : condition d'affichage **locale** au client (non synchronisee, vrai par defaut), en plus de
  `HoloDisplay` ; ignoree quand la session d'edition revele l'objet. Pour une fenetre que le proprietaire ouvre et
  ferme : `HoloObject.opened()` (synchronise vers les observateurs, non sauvegarde ; `requestOpened` depuis le client
  du proprietaire, `HoloOpenObjectPacket`, qui fait foi) et `shown()` = `opened()`.
- On replique l'**etat des modules**, jamais les widgets : chaque client recree le module depuis son type, `onLoad`
  reconstruit les objets (meme code, memes cles des deux cotes). Ancrage, transform et visibilite sont synchronises
  par le framework ; seuls les modules ayant un objet `PUBLIC` sont envoyes aux observateurs.
- Trois memoires (`HoloScope`) : **joueur** (capability, plusieurs dont une active ; observateurs = joueurs qui
  suivent son entite **et l'inspectent**), **dimension** (`SavedData` ; tous les joueurs de la dimension), **position** (portee par un
  marker `hololib`, registre global `HoloMarkers` ; chargee avec l'entite ; joueurs qui suivent son chunk). Sans
  proprietaire, `PRIVATE` = invisible de tous.
- Chaque type de module a **une** compatibilite (`HoloModuleTarget` : DIMENSION, POSITION, PLAYER) et au plus une
  instance par memoire. Gestion en jeu : `/hololib_marker create|remove`, `/hololib_module add|remove
  dimension|world|player ...` (op 2).
- **Inspection** : par defaut personne ne voit les affichages d'un autre joueur. A voit B si A suit l'entite de B et
  que la memoire active de A l'autorise (`HoloInspection` : drapeau "tous", liste, exclusions). Serveur autoritaire
  (`HoloInspections`, ensemble `viewers` de chaque `PlayerHoloContainer`). `/hololib_inspect <on|off> [<joueurs>
  [<cibles>]]` (tous ; chaque activation poste `HoloInspectEvent`, annulable) et `/hololib_force_inspect` (op 2,
  ignore l'evenement).

### Interaction au regard

- Le point vise est le **centre de l'ecran**. `HoloPicking` reconstruit le placement de chaque objet route (comme son
  pont), intersecte le rayon avec le plan `z = 0` local (vu de face) et demande `HoloObject.interactibleAt(x, y)`
  (`HoloWindow` : widget `HoloInteractible` le plus profond, sinon la fenetre). Priorite HUD > fenetres d'outils
  (overlays `HoloAttachment`, toutes familles, sans occultation ; la visee devient `HoloPicking.focused()`, dessinee
  au-dessus de tout par `HoloFocusBridge`) > vue > monde (16 blocs, occulte par les blocs) > ciel (seulement si le
  regard ne touche rien).
- `HoloInteractible.intercepts(HoloControl)` choisit controle par controle ce que l'element prend **tant qu'il est
  vise** (tout sauf `MOUSE_MOVE`) ; un controle non pris remonte aux parents (panneaux, fenetre), puis au jeu. Le
  relachement va a qui a pris l'appui. Pour aller au-dela du survol : `ctx.capture(captor)` (capture explicite liee a
  l'objet, terminee s'il disparait).
- Droit d'usage : `HoloObject.access()` (`HoloAccess` : `OWNER` par defaut, liste de joueurs, `EVERYONE` ; le
  proprietaire toujours ; sans proprietaire `OWNER` = personne), synchronise et sauvegarde par le framework. Callbacks
  locaux ; effet sur le jeu via `sendAction(source, ...)`, verifie par le serveur (acces, `PUBLIC` pour un
  non-proprietaire, et `viewers` pour la memoire d'un autre joueur). Protocole reseau `"8"`.

### Widgets interactifs (`holo/widget/control`)

- **Regle** : un widget interactif se compose de **traits** (interfaces a methodes par defaut) : `HoloHoverable`,
  `HoloClickable`, `HoloScrollable`, `HoloToggleable`, `HoloDraggable`, `HoloTextInput` (+ `HoloTextBuffer`),
  `HoloPointerInput`, `HoloDirectionalInput` (+ `HoloDirections`). Le composant ne fournit que son etat et ses
  reactions. Les traits ne redefinissent jamais `intercepts` mais `interceptsClick` / `interceptsScroll` /
  `interceptsKey` (pas de conflit de defauts).
- Widgets prets a l'emploi, prefixes `HoloWidget` (`HoloWidgetButton`, `HoloWidgetMultiButton`, `HoloWidgetHoverZone`,
  `HoloWidgetScrollZone`, `HoloWidgetSwitch`, `HoloWidgetValueBar`, `HoloWidgetTextBox`, `HoloWidgetMouseBox`,
  `HoloWidgetDraggable`, `HoloWidgetKeyBox`, `HoloWidgetList`, `HoloWidgetDragRow` : ligne d'une liste reordonnee au
  glisser), non finaux. Les demos les utilisent ; ne plus ecrire de widget ad hoc
  dans un module si un trait ou un widget existe.
- Framework : `onDrag` (chaque frame, clic intercepte maintenu, regard projete par `HoloPicking.project`) et
  `hoverMargin()` (survol conserve sur le fond / hors fenetre dans la marge).
- Sons (`client/interact/HoloSounds`, joues par `HoloInteractions`) : clic pris par un `HoloClickable` = son generique
  different selon le bouton (gauche / droit / molette), cran pris par un `HoloScrollable` = son de molette ;
  `clickSound()` / `scrollSound()` = false pour un element qui joue le sien (poignees d'edition, cadre HUD : son
  d'edition a la saisie d'un objet).

### Edition en jeu (`holo/edit`, `client/edit`)

- Un objet editable implemente `HoloEditable` (`ownRequired()` = true, `opRequired()` = false par defaut) via les
  outils `HoloEditTranslation`, `HoloEditRotation`, `HoloEditScale`, `HoloEditVisibility` (bouton oeil, `HoloDisplay`
  OFF / FOCUS / ON), `HoloEditFixOrDynamic` (memoire joueur, `Local` relatif <-> follow ; proposee seulement avec les
  deux own editors actifs, `HoloEditRules.bothOwn`). Editable = vu + capacite
  + regles : un module editeur (`HoloEditorModule`) par portee (`own_fix_editor` : objets fixes de sa memoire
  (`Local` relatif, monde, ciel) ; `own_dyn_editor` : objets dynamiques, `Local` follow et `HoloRelativeToOpen`, capture du regard et repere ;
  `own_hud_editor` : objets HUD et cadre HUD ; capacite de l'objet : `HoloEditRules.ownCapability` ;
  `world_editor`, `dimension_editor`) ;
  `other_editor` (ops) leve `ownRequired` hors de sa memoire ; proprietaire = celui de la memoire de joueur, sinon le
  createur du module (`HoloModule.creator()`, joueur qui l'a installe, sauve et synchronise) ; `HoloEditorUi` =
  interface de l'editeur, toujours editable par son proprietaire. L'edition ignore `HoloAccess` (l'objet de l'outil
  actif reste visable : `HoloPicking.candidate`, `HoloInteractions.valid`). Objet `World` : portee d'edition
  (`HoloObject.editRange()`, 1 a 16 blocs, defaut 8, synchronisee, ligne "Portee" du panneau). Le serveur reverifie (`HoloContainers.handleEdit`, `HoloEditRules.accepts` : seules les composantes permises
  changent). RESET = `HoloObject.defaultTransform()` (transform du constructeur).
- `HoloDisplay` est synchronise et sauvegarde comme la visibilite ; `HoloRouting` filtre : `FOCUS` si le proprietaire
  de la memoire (le joueur local pour une memoire sans proprietaire) est en focus, `OFF` jamais, sauf en session
  d'edition pour un objet editable. `HoloModule.sharedWithObservers()` envoie un module sans objet public (focus).
- Session (`HoloEditSession`, ouverte par l'editor) : boutons a droite de chaque objet editable (overlays
  `HoloAttachment`, `pixelScale = 1` ; placement propre, independant de la rotation / echelle de l'hote, calcule a
  la creation de l'overlay puis **fige** (monde : suit la translation de l'hote ; vue / ciel : fixe autour de l'oeil),
  re-place a la fin de chaque transformation (relachement, saisie, RESET, molette arretee ; pas la portee) : face au
  point de vue, sans roulis, hors de la silhouette de l'hote, rectangle si `HoloObject.flat()`, sinon sphere englobante ; point de vue
  dans l'hote, ou ciel : a 2 blocs), un seul outil actif. Outils selon l'ancrage :
  `Local` / `Sky` = l'objet entier est la poignee (regard / souris capturee / molette), `World` = gizmos, `Hud` = cadre
  de la fenetre de l'editor. Avec l'Own Dyn Editor actif (`HoloEditSession.setDynamicEditing`, et toujours pour les
  `HoloEditorUi`), les `Local` follow (et `HoloRelativeToOpen`) sont **tenus** : affiches fixes dans le
  repere de reference (regard a l'ouverture, `HoloEditMath.rebaseLocal`), reconvertis et envoyes a la fermeture ;
  seuls les groupes modifies changent. Editor ouvert depuis le focus : les `HoloRelativeToOpen` deja places gardent
  leur place (regard de leur placement, `FocusController.placement`) et la gardent a la fermeture.
- `HoloRotationFrame` d'un `HoloRelativeToOpen` (synchronise, sauve ; `PLAYER` par defaut) : direction toujours
  relative au regard, rotation relative au joueur (`HoloEditMath.rebaseDirection`, fenetre droite face a l'oeil) ou
  au regard (`rebaseLocal`). Interrupteur "Rot. joueur" du panneau. Panneau : boutons d'outils a icone, valeurs
  X Y Z (et RESET) seulement sur les lignes ouvertes par leur "+" (panneau reconstruit). Le reste est envoye en fin de manipulation (`HoloEditObjectPacket`) ; le
  placement recu d'un objet tenu ou manipule est ignore (`ClientHoloContainer.apply`).
- Modules `hololib:focus` (etat synchronise, sorties lues sur les touches physiques : saut, sprint, marche > 20 ticks
  sans sneak, double sneak < 10 ticks) et editeurs (G, au moins un equipe ; `editor_panel` est `Configurable` : sa
  fenetre "Editeurs" de Config les liste et les active / desactive) : focus et edition jamais ouverts ensemble.

### Configuration en jeu (`modules/camera/config`)

- Module `hololib:config` (joueur) : bouton ecrou (`ConfigNutWindow`, `HoloRelativeToOpen`, affichage `FOCUS` non
  modifiable ; clic = rotation de l'ecrou + ouvre / ferme la liste) et liste (`ConfigListWindow`) des modules de la
  memoire du joueur qui implementent **`Configurable`** (`configName()`, `configWindow()`). Implementer l'interface
  est la declaration : la liste les decouvre chaque tick (`ConfigController`).
- **Regle** : un module configurable detient sa `ConfigWindow` (objet du module cree dans `onLoad`, widgets dans
  `content()`) ; Config l'ouvre devant le regard au clic (`open()`, position relative = `openOffset`, editable dans
  l'editor), la ferme (croix) et l'epingle (epingle : etat sauvegarde du module Config, par id de type). Ouverture
  decidee par le client du proprietaire (`opened()`) ; a l'ouverture du focus, les fenetres epinglees se rouvrent, les
  autres sont fermees. Ecrou, liste, fenetres Config et roue Quick Action sont `PUBLIC` : un joueur qui inspecte le
  proprietaire les voit (sans s'en servir) ; listes et roues des joueurs inspectes sont remplies par les controleurs.
- Icones dessinees en quadrilateres (`HoloCanvas.quad`), sans texture ; une forme en plusieurs quads se dessine dans
  une couche (`HoloCanvas.beginLayer/endLayer` : meme profondeur, sinon elle se deforme en perspective).

### Roue d'actions (`modules/camera/quickaction`)

- Module `hololib:quick_action` (joueur, `Configurable`) : a l'ouverture du focus, roue (`QuickActionWheelWindow`,
  `HoloRelativeToOpen` sans outil d'edition, `FOCUS`) placee au centre du regard en `Local` fixe ; on vise un secteur
  en tournant la camera, clic gauche ou droit = action, puis la roue se masque (et le focus se ferme si "Laisser le
  focus active" est OFF, via `QuickActionModule.Backend`). Zone morte au centre ; masquee si le regard s'eloigne de
  plus de 1,25 x son rayon angulaire ("Masquer si ignoree"). Ouverture decidee par le client du proprietaire
  (`QuickActionController`). La roue entiere est dessinee d'un coup (widget `Wheel`, une couche), les secteurs ne
  servent qu'a viser.
- **Regle** : declarer des actions = implementer **`QuickActionProvider`** (module de la memoire de joueur) et creer
  ses `QuickAction` dans `onLoad` (`id()` stable, `name()`, `drawIcon(canvas, size)` en quads ou
  `QuickActionIcon.texture`, `run()` cote client -> `sendAction`). Decouvertes chaque tick ; ordre et activation
  sauvegardes par cle `<type du module>/<id>`. Pour apparaitre dans la roue vue par un observateur, le provider doit
  lui etre envoye (`sharedWithObservers()`). Demo : `hololib:demo_quick_actions`.

### Modules Space (`modules/dimension/space`, `modules/monde/space*`, `modules/camera/space*`)

Conception et decisions (D1-D34, choix C, limites L) : `docs/space/` (le code fait foi). Le monde d'une dimension est
un vaisseau : sa pose dans un systeme stellaire simule est la camera du ciel. Seuls fichiers hors packages Space :
les enregistrements de `HoloLibModules` (6 modules) et `ModuleRenderers` ; le canal des textures est declare dans un
package Space (D27). Ressources : `assets/hololib/textures/space/` (`sol/`, `generic/` : 53 PNG equirectangulaires
2:1, longitude 0 au centre), `assets/hololib/sounds/space/` (15 `.ogg` mono) + `sounds.json`, chacun avec son
`CREDITS.md` (Solar System Scope CC BY 4.0, USGS domaine public, NASA/JPL, Screaming Brain Studios CC0 ; sons CC0
ou synthetises) : toute ressource ajoutee y est creditee.
- `hololib:space_environment` (dimension) : **seule autorite** sur la galaxie (systemes, corps, orbites de Kepler
  animees, masses, rotations propres, textures), le systeme courant, la pose du monde (km, quaternion `double`), la
  vitesse signee, le referentiel, les reglages (dont l'echelle de temps et la vitesse de distorsion, 0,5 al/s), le
  saut, le pilote, la **cible du vaisseau** et la graine du fond etoile. Aucun objet (`sharedWithObservers()`) ; ciel
  dessine par `SpaceSkyRenderer` : fond opaque, **fond etoile procedural** (`SpaceStarField`, 4 000 etoiles tirees de
  la graine du serveur : meme ciel pour tous, bande et renflement de la Voie lactee), etoiles de fond = autres
  systemes, corps a taille apparente reelle, **textures** (`SpaceBodyTextureCache`, chargement hors du fil de rendu)
  et **rotation propre** (`SpaceSpin`), LOD ; noircit le brouillard (`fogColor`). Acces serveur :
  `SpaceEnvironmentModule.of(level)` ; client : `client()` (backend). API serveur publique (pilotage, edition qui
  valide puis incremente la revision, reglages, `setTarget` / `clearTarget`, `teleportTo`) : Controller, Map et
  Configurator ne passent que par elle.
- **Temps** (D28) : `t = t_e + (gameTime - T_e) * echelle / 86 400` jours, echelle 360 s/tick (1 jour = 12 s), epoque
  `(T_e, t_e)` reposee a chaque changement d'echelle (aucun saut des astres) ; un monde d'avant T38 (3 600 s/tick, sans
  epoque) est migre au premier tick serveur. Echelle reglable au Configurator ("Temps (s/tick)", 0 = astres figes).
- **Galaxie par defaut** (galaxies creees apres T16 ; une galaxie sauvegardee n'est jamais changee) : Sol (34 corps :
  8 planetes, lunes majeures, objets mineurs `MINOR` Ceres / Pluton / Eris / Haumea / Makemake) et 11 voisines reelles
  avec compagnes et planetes confirmees (`docs/space/donnees-systemes.md`). Bornes : 48 corps / systeme, 256 systemes.
  Valeurs par nom pour les galaxies anciennes : masses (`SpaceKnownMasses`, 62 corps, sinon estimees), rotations
  (`SpaceSolRotations`), texture `auto` (`SpaceBodyTextures` : Sol par nom et type, sinon generique selon type, rayon
  et temperature d'equilibre, teinte de l'etoile).
- **Persistance** : `SpaceArchive` (`SavedData` du niveau, `data/hololib_space.dat`) recopie l'etat vivant a chaque
  sauvegarde du niveau et recoit l'etat au retrait du module ; lue au premier tick serveur, elle remplace l'etat d'une
  installation par commande ou d'une memoire plus ancienne (retrait / reinstallation = tout revient, cible comprise ;
  remise a zero : supprimer le fichier, L20). Un voyage en cours reprend au chargement.
- **Referentiel** (D29) : la vitesse est relative au corps le plus profond dont la **sphere d'influence**
  `a (m / M_parent)^0,4` contient le monde (plancher 10 R ; rayon de jeu x 4 pour une lune, x 1,5 planete / mineur,
  plafond 0,5 a ; hysteresis 1,05, delai, cooldown, fondu) ; le regard ne compte plus, aucune gravite sur le vaisseau.
  Il entraine le monde ; synchronise et sauvegarde. Pose sauvegardee avec son tick (`time`), recalee sur le
  referentiel au chargement.
- `hololib:space_controller` (marker) : fenetre `World` (`EVERYONE`, interrupteur "tous / ops" verifie par le
  serveur) ; clic sur `SpacePilotPad` = capture **explicite non liee a la fenetre** (`HoloCaptures.begin(pad)`) :
  Avancer / Reculer = gaz, Gauche / Droite = roulis, Saut = frein, Sprint = boost (pentes x 3, frein rapide),
  Accroupi = saut, regard = joystick borne a un cone de 30 degres autour de l'axe avant ; Sneak / Sprint en mode
  bascule remis au repos (`resetToggleKeys`). Le pad termine lui-meme la capture : Echap / Entree, autre pilote,
  Environment absent, mode ops, delai sans confirmation, Space Pilot retire, acces perdu, `onUnload` client (marker
  retire, chunk quitte, dimension, deconnexion). **Refuse sans module Space Pilot** dans la memoire active (D11,
  serveur et client). Actions `BEGIN` / `INPUT` (7 octets, 8-12 avec la cible du saut ; envoyee au changement,
  battement 20 ticks) / `END` / `OPS_ONLY` relayees a l'Environment ; un pilote a la fois, libere s'il se tait, part
  ou si le poste disparait. Fenetre du poste utilise **masquee pour tous** pendant le pilotage (D31, `shown()`) ; les
  autres postes restent visibles ("Pilote par X"). Pendant un saut : "Saut vers X" puis "Distorsion : n s".
- **Saut** (`SpaceJump`, etat de l'Environment avance dans le pas deterministe) : etoile de fond au reticule (<= 3
  degres, choisie a l'appui), Accroupi maintenu = charge de 60 ticks (alignement de l'avant sur l'etoile en 40 ticks,
  frein force, joystick inactif ; relacher annule), puis voyage de `max(40, ceil(20 D / v_w))` ticks pose figee, puis
  arrivee decidee par le serveur seul : systeme courant = cible, a 10 rayons de son etoile, face a elle, `V = 0`.
  **Distorsion** (D23, client seulement, `client/render/modules/dimension/space/warp/`) : convergence des etoiles vers
  la cible (20 premiers ticks du voyage), flash d'entree, scene du voyage (degrade arc-en-ciel, source a halos et deux
  flares contra-rotatives, particules), flash d'arrivee puis fondu ; phase visuelle `SpaceWarpTimeline` (lue par les
  surcouches, qui se coupent, et par les sons).
- **Cible du vaisseau** (D33) : une par dimension, `(targetSystem, targetBody)` de l'Environment (bloc chaud,
  sauvegardee), posee par CIBLER / DECIBLER au bout de la ligne selectionnee de la Space Map ; retiree si la selection
  de cette Map change (`checkPostedTarget`, chaque tick serveur) ou si l'astre disparait (`validateTarget`) ; gardee
  au saut et a la teleportation. Aux commandes (Space Pilot) : anneau vert autour de l'astre (autre systeme : sur son
  etoile de fond) et fleche verte au bord du cercle avant (`SpaceTargetRenderer`, deux instances autour du lien).
- **Teleportation** (D34, action 12 du Configurator -> `teleportTo`) : pose `SpaceMotion.orbitPose` (`max(1,5 R,
  min(10 R, demi-sphere de jeu))`, cote eclaire), `V = 0`, referentiel = l'astre, saut annule, pilote et cible gardes,
  autre systeme = changement instantane ; octet `teleportSerial` du bloc chaud : les clients sautent sans lissage.
- `hololib:space_pilot` (joueur, `Configurable`, rien pour les observateurs : C45) : requis pour piloter ; Config
  `hud` / `refLink` / `cruise`. Tout n'apparait qu'**aux commandes** du joueur local : cercles du joystick (objets
  `Local` `PRIVATE` ; cercle avant = zone morte), cible, roue de charge et aide a la visee du saut
  (`SpaceJumpRenderer`), lien et anneau du referentiel (`SpaceReferenceLinkRenderer`, `ViewRenderer` `WORLD`,
  conversion `STABLE -> WORLD`), cible du vaisseau, HUD de navigation (`SpacePilotHud`, `Hud`, deplacable par l'Own
  HUD Editor) avec la **vitesse de croisiere** (`SpaceCruise` : astre au regard <= 10 degres, `v*` par interception au
  frein normal avec temps de reaction, fleche haut / bas coloree vert -> rouge, point "viser ici" bleu pale). Module
  du joueur local : `SpacePilotClient` ; regard : `SpacePilotBackend`.
- `hololib:space_knowledge` (joueur, `Configurable` + `QuickActionProvider`, C45 ; pas requis pour piloter) :
  surcouche du ciel du joueur local (`SpaceKnowledgeSkyRenderer`, `SkyRenderer` enregistre apres le ciel) : anneaux
  (planetes bleu, etoiles jaune, lunes gris), orbites (coupees dans l'anneau de leur corps, pointillees a travers les
  autres), etoiles exterieures, nom et distance au regard ; lune reperee quand son orbite se separe de sa planete a
  l'ecran (24 -> 36 px, D30), objet mineur a moins de 0,5 UA, referentiel toujours ; Quick Action `toggle`. Regles
  partagees avec Space Pilot : `SpaceOverlay`. Module du joueur local : `SpaceKnowledgeClient`.
- `hololib:space_map` (marker, **tous**, D20) : cube (`SpaceMapCube`, place par les ops, molette = zoom) et fenetre
  de navigation (220 x 176 : arborescence de la gravite Galaxie > systemes > corps, clic = selection + recentrage,
  zoom vers le centre, Systeme / Galaxie, vue d'ensemble, origine `@`, CIBLER) ; vue **partagee** sauvegardee (mode,
  systeme affiche, selection, centre, zoom, origine) ; temps vivant de l'Environment ; corps textures et tournants ;
  croix 3D et coordonnees de la selection ; systeme courant et vaisseau en vert. Copie des en-tetes et du systeme
  affiche rafraichie a la revision. Actions 0-8 de tout joueur a portee. `SpaceMapModule.of(module)` : Map du meme
  marker (table statique par cote, sans parcours de `modules()`).
- `hololib:space_configurator` (marker, **ops** : tout masque aux non-ops par `shown()`, op verifie en premier par le
  serveur) : fenetre d'edition seule (208 x 192, placee a droite de celle de la Map) branchee sur la Map **du meme
  marker** (sa selection = objet edite ; sans Map : "Space Map requise sur ce marker", rien d'editable) : galaxie
  (ajouter, retirer, renommer, Monde ici, avant du monde, Temps, Distorsion) ; corps (+ par type, onglets Corps /
  Rotation / Texture, RESET a deux clics, Se teleporter, Orbite) ; texture : Auto + 53 integrees, apercu 48 x 24,
  **Importer**. Actions 0-12 portant les ids vises.
- **Poignees sondees** : pas de callback d'edition ; `serverTick` compare le transform de `SpaceMapHandle`
  (translation de l'etoile selectionnee) et de `SpaceOrbitHandle` (rotation + echelle de l'orbite, temps fige
  `t_edit`) a la derniere valeur ecrite par le module et en deduit l'edition (contexte retenu a l'ecriture). Poignees
  dans le repere du cube et de la vue de la Map, ajoutees / retirees des deux cotes (serveur au changement d'etat,
  client chaque tick, seulement si absentes), memes cles (`handle`, `orbit`).
- **Textures importees** (D27, `modules/dimension/space/texture/`) : canal `hololib:space_textures` (protocole "1",
  `HoloNetwork` inchange). L'op choisit un PNG / JPEG (`SpaceTextureImport`, `TinyFileDialogs` sur un fil a part),
  image etiree en 2:1, 64-1024 px, PNG RGB <= 2 Mio, envoyee en morceaux de 30 000 o (4 / tick) ; serveur : op
  reverifie a chaque morceau, validation `javax.imageio` hors du fil serveur, `data/hololib_space_textures/<sha1>.png`
  de la dimension, quota 64 (orphelins supprimes au quota) ; puis action 7 `custom:<sha1>`. Un client demande une
  empreinte inconnue (`SpaceTextureClient`, une fois, nouvel essai 60 s apres `Missing`) ; image mipmappee dans
  `SpaceBodyTextureCache` (`registerCustom`), liberee a la deconnexion.
- **Sons** (D26, `client/modules/dimension/space/SpaceSoundController`) : deduits de l'etat affiche par chaque client
  (rien sur le reseau), entendus par toute la dimension, `AMBIENT` non positionnels, `SoundEvent` cote client :
  moteurs (2 boucles modulees par la vitesse et la poussee), coque en rotation, charge du saut, sequence de la
  distorsion ; arretes a `onUnload` du miroir et par garde-fou (boucle non pilotee depuis 4 ticks).
- **Sync froid / chaud** (Environment) : bloc chaud (tick, pose, vitesse, referentiel, entree, pilote, reglages et
  epoque, saut, cible, compteur de teleportations) toujours ; revision ; bloc froid (systeme courant, etoiles de fond
  avec id et nom, graine du fond etoile) **seulement au besoin** (D16) : instantane d'un arrivant, changement de
  revision (edition, saut, teleportation dans un autre systeme), demande d'un client qui l'a manque. Sans lui le client
  ignore l'etat (`synced()` faux, ciel reduit au fond). Envois limites (4 ticks) ; en mouvement ou en saut, correction
  toutes les 20 ticks ; rien a l'arret.
- **Lissage client** : simulation deterministe (`SpaceMotion.step`, `StrictMath`, `double`) rejouee par chaque client
  (dead reckoning) ; horloge d'affichage propre (jamais le `gameTime` vanilla), historique de 32 ticks interpole dans
  le repere du referentiel, observateur en leger retard, pilote local predit (reconciliation 10 % / tick). Les
  renderers lisent `pose(partialTick)`, `bodyPositions`, `displayDays`.

### Capture de controles (independante de l'interaction)

- `HoloControl` : boutons bruts, `SCROLL`, `MOUSE_MOVE`, `ESCAPE`, `ENTER`, `mapping("key.xxx")` (tout `KeyMapping`,
  modde compris, par son nom), `KEYBOARD` (toutes les touches + texte : `onKeyPress`, `onCharTyped`, codes `HoloKeys`). `HoloCaptor.captures(control)` + callbacks ; `HoloCaptures.begin/end` (facade
  commune, backend installe au setup client).
- `HoloControlCapture` : pile de captures explicites (la plus recente d'abord) puis captors passifs (l'interaction).
  Une capture explicite ne se termine jamais seule et reste active sous un ecran (rien n'est distribue). Boutons
  bruts : `MouseButton.Pre` annule ; mappings : neutralises juste apres `KeyMapping.set/click` (`MouseButton.Post`, `InputEvent.Key`) et a
  chaque debut de tick ; Echap capture : `ScreenEvent.Opening` du `PauseScreen` annule (poste avant `InputEvent.Key`), sons relances a la
  frame suivante (`pauseGame` les met en pause meme si l'ecran est annule) ;
  souris : rotation verrouillee sans Mixin (remise a chaque debut de frame et de tick, `turnPlayer` precede
  `onRenderTickStart`). `KeyMapping.setAll()` a la fin d'une capture explicite.
- `KEYBOARD` : sans Mixin, seul un `Screen` recoit les caracteres (`KeyboardHandler.charTyped`). Tant qu'un captor le
  prend, `HoloKeyboardScreen` (invisible, sans pause, ne se ferme pas sur Echap) est ouvert ; sous un ecran vanilla ne
  lit aucun `KeyMapping`. Les touches passent par l'ecran : au captor le plus recent qui prend `KEYBOARD` ou un
  controle lie a la touche. `setScreen` libere la souris et `MouseHandler.grabMouse` refermerait l'ecran : la souris est
  reprise en posant `mouseGrabbed` par reflexion (`ObfuscationReflectionHelper`, SRG `f_91520_` / `f_91511_`), donc la
  camera tourne et l'interaction continue (`HoloControlCapture.gameInputActive`).

### Les quatre familles de renderers

| Famille | Stage Forge (sans shaderpack / avec) | Resultat | Gestion de la profondeur |
|---|---|---|---|
| `SkyRenderer` | `AFTER_SKY` (+ `ComputeFogColor` avant) / `AFTER_LEVEL`, cible hors ecran composee sur les pixels de profondeur 1 | dans la skybox, derriere tout le monde ; peut fixer la couleur du brouillard (`fogColor` : fond de l'ecran, horizon, terrain lointain) | profondeur entre objets du ciel, **videe apres** la passe ; projection du monde sans la translation du bobbing (comme le soleil) |
| `WorldRenderer<T>` | `AFTER_TRANSLUCENT_BLOCKS` (cible principale, meme en Fabulous) / `AFTER_LEVEL` apres le ciel | ancre dans le monde, cache par blocs, entites, eau ; teinte ce qui est derriere | profondeur du monde (de la scene composee avec shaderpack) |
| `ViewRenderer` | `AFTER_LEVEL` | dans la vue, au-dessus du monde, sous main et GUI | profondeur **videe avant** ; projection `STABLE` (sans bobbing, FOV des options) ou `WORLD` |
| `HudRenderer` | overlay GUI sous tous les autres | a l'ecran, orthographique, sous le HUD vanilla | profondeur **videe apres** la passe |

Toutes les passes dessinent dans `HoloBuffers` (mode immediat : ordre d'emission = ordre de dessin), jamais dans
`RenderBuffers.bufferSource()`. Shaderpack detecte par `ShaderPacks` (reflexion sur `IrisApi`, fige par frame a
`AFTER_SKY`) ; avec un pack, Sky et World ne sont ni eclaires ni embrumes par lui, et les `ViewRenderer` passent sur la
main (dessinee par Oculus avant `AFTER_LEVEL`).

Chaque famille suit le meme schema dans son sous-package : `XxxRenderer` (API publique), `XxxRenderContext` (donnees
de la frame, objet reutilise, setters package-private) et `XxxRenderPass` (interne, appelee par `RenderDispatcher`).

**Ajouter un renderer** : implementer `SkyRenderer` / `ViewRenderer` / `HudRenderer` ou etendre `WorldRenderer<T>`, puis
`HoloRenderers.register(...)` sur le fil de rendu. Ne jamais abonner un renderer aux evenements Forge lui-meme.
Un objet qui existe cote serveur doit plutot etre une vraie `Entity` + `EntityRenderer`.

### Pieges connus (verifies dans les sources)

- A `AFTER_LEVEL`, Forge passe la pile de **projection** comme PoseStack : la rotation camera est capturee a
  `AFTER_SKY` par `RenderDispatcher`.
- En Fabulous, `AFTER_WEATHER` dessine dans `WEATHER_TARGET` (recompose ensuite) : ne pas y dessiner d'overlay.
- `ITEM_ENTITY_TARGET` est deja compose a `AFTER_LEVEL` : les `ViewRenderer` doivent utiliser la cible principale.
- Les `RenderType` imposent leur test de profondeur : pour passer devant/derriere, vider la profondeur
  (`RenderSystem.clear(GL_DEPTH_BUFFER_BIT, ...)`) plutot que `disableDepthTest()`.
- Oculus, meme sans shaderpack : pendant `LevelRenderer.renderLevel`, `RenderBuffers.bufferSource()` est un
  `FullyBufferedMultiBufferSource` qui ignore `endBatch(RenderType)`, ne dessine entites et block entities qu'au
  profiler `"translucent"` (apres `AFTER_ENTITIES` / `AFTER_BLOCK_ENTITIES`) et reordonne les `RenderType`.
- Oculus avec shaderpack : un shader vanilla non remplace par le pack (`position_color_lightmap` notamment) est
  applique avec couleur et profondeur masquees (`DepthColorStorage`) jusqu'a la fin du niveau ; le pack recalcule le
  ciel sur les pixels de profondeur 1 ; composite et final sont faits avant `AFTER_LEVEL` (profondeur principale =
  scene, main comprise) ; bobbing, secousse et nausee passent de la projection au model-view.
- `AFTER_TRANSLUCENT_BLOCKS` (et chaque stage de terrain) est emis entre `setupRenderState` et `clearRenderState` du
  type : en Fabulous la cible translucide est liee.
- Plan lointain = `GameRenderer.getDepthFar()` (128 au minimum) : objets du ciel a ~100 blocs (`SkyRenderContext.SKY_RADIUS`).

## Conventions

- Pas d'OpenGL brut : passer par `RenderType` / `MultiBufferSource` / `RenderSystem` (cache d'etat GL, compatibilite
  Oculus/Embeddium, migration future vers `GpuDevice` en 1.21.5+).
- Pas d'allocation par frame dans les chemins de rendu ; tout etat GL modifie est restaure (y compris sur exception).
- Un renderer lit un etat et dessine ; la logique (interaction, animations d'etat) vit ailleurs.
- Style : commentaires et javadoc en francais **sans accents**, accolades sur une nouvelle ligne, une
  `package-info.java` documente chaque package non trivial.
