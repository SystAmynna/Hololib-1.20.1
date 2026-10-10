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
│   ├── dimension/space/         Space Environment (ciel = systeme stellaire, galaxie, pose du monde, saut) + code
│   │                            commun Space : SpaceArchive (SavedData, persistance), SpaceFormat (formats affiches),
│   │                            model/ (galaxie, systemes, corps STAR/PLANET/MOON/MINOR, orbites, bornes), math/
│   │                            (orbites, pose, mouvement, referentiel, entree du pilote, SpaceJump : saut)
│   ├── monde/spacecontroller/   Space Controller (marker) : fenetre de pilotage, SpacePilotPad (gaz, joystick, saut)
│   ├── monde/spaceconfigurator/ Space Configurator (marker, ops) : cube carte / systeme et fenetre detachee, poignees
│   │                            sondees, SpaceSystemScale (echelle, zoom), SpaceCoordinates (origine, coordonnees)
│   ├── camera/spacepilot/       Space Pilot (joueur) : reglages (Config, Quick Action), cercles du joystick
│   │                            (SpaceJoystickForward / Aim), HUD de navigation (SpacePilotHud)
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
    │                            ConfigController, QuickActionController ; SpacePilotClient (module Space Pilot du
    │                            joueur local, relu chaque tick)
    ├── modules/dimension|monde/ backends client des modules Space (SpaceEnvironmentClient, SpaceControllerClient,
    │                            SpaceConfiguratorClient : miroir de dimension, joueur local, op local, Space Pilot local)
    └── render/                  TOUS les renderers du mod (regle d'architecture)
        ├── package-info.java    doc detaillee : ordre de rendu, regles (source de verite)
        ├── RenderDispatcher.java seul abonne aux evenements de rendu, route vers les passes
        ├── HoloRenderers.java   registre des renderers (register/unregister, fil de rendu)
        ├── sky/                 SkyRenderer, SkyRenderContext, SkyRenderPass
        ├── world/               WorldRenderer<T>, WorldRenderable, WorldRenderContext, WorldRenderPass
        ├── view/                ViewRenderer, ViewRenderContext, ViewRenderPass
        ├── hud/                 HudRenderer, HudRenderContext, HudRenderPass
        ├── demo/                DemoCubeRenderer (objets des demos)
        ├── edit/                HoloGizmoRenderer (segments / cercles des objets World edites)
        ├── modules/             renderers des modules, meme arborescence que modules/ (ModuleRenderers au setup) ;
        │                        Space : dimension/space (SpaceSkyRenderer, SkyRenderer ; SpaceWarpEffect, distorsion
        │                        du saut, provisoire), camera/spacepilot (SpacePilotSkyRenderer : surcouche + lien,
        │                        SkyRenderer enregistre apres le ciel ; SpaceJoystickRenderer ; SpaceJumpRenderer :
        │                        cible et roue de charge, ViewRenderer), monde/spaceconfigurator (SpaceMapCubeRenderer,
        │                        SpaceHandleRenderer, SpaceOrbitHandleRenderer)
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

### Modules Space (`modules/dimension/space`, `modules/monde/space*`, `modules/camera/spacepilot`)

Conception et decisions (D1-D19, choix C, limites L) : `docs/space/` (le code fait foi). Le monde d'une dimension est
un vaisseau : sa pose dans un systeme stellaire simule est la camera du ciel. Seuls fichiers hors packages Space :
les enregistrements de `HoloLibModules` et `ModuleRenderers`.
- `hololib:space_environment` (dimension) : **seule autorite** sur la galaxie (systemes, corps, orbites de Kepler
  animees), le systeme courant, la pose du monde (km, quaternion `double`), la vitesse signee, le referentiel, les
  reglages (dont la vitesse de distorsion, 0,5 al/s), le saut et le pilote. Aucun objet (`sharedWithObservers()`) ;
  ciel dessine par `SpaceSkyRenderer` (fond opaque, etoiles de fond = autres systemes, corps a taille apparente
  reelle, LOD) qui noircit le brouillard (`fogColor`). Acces serveur : `SpaceEnvironmentModule.of(level)` ; client :
  `client()` (backend). API serveur publique (pilotage, edition qui valide puis incremente la revision, reglages) :
  Controller et Configurator ne passent que par elle.
- **Galaxie par defaut** (galaxies creees apres T16 ; une galaxie sauvegardee n'est jamais changee) : Sol (34 corps :
  8 planetes, lunes majeures, objets mineurs `MINOR` Ceres / Pluton / Eris / Haumea / Makemake) et 11 voisines reelles
  avec compagnes et planetes confirmees (`docs/space/donnees-systemes.md`). Bornes : 48 corps / systeme, 256 systemes.
- **Persistance** : `SpaceArchive` (`SavedData` du niveau, `data/hololib_space.dat`) recopie l'etat vivant a chaque
  sauvegarde du niveau et recoit l'etat au retrait du module ; lue au premier tick serveur, elle remplace l'etat d'une
  installation par commande ou d'une memoire plus ancienne (retrait / reinstallation = tout revient ; remise a zero :
  supprimer le fichier, L20). Un voyage en cours reprend au chargement.
- `hololib:space_controller` (marker) : fenetre `World` (`EVERYONE`, interrupteur "tous / ops" verifie par le
  serveur) ; clic sur `SpacePilotPad` = capture : Avancer / Reculer = gaz, Gauche / Droite = roulis, Saut = frein,
  Accroupi = saut, regard = joystick borne a un cone de 30 degres autour de l'axe avant, Echap rend la main.
  **Refuse sans module Space Pilot** dans la memoire active (D11, serveur et client). Actions `BEGIN` / `INPUT`
  (7 octets, 8-12 avec la cible du saut ; envoyee au changement, battement 20 ticks) / `END` / `OPS_ONLY` relayees a
  l'Environment ; un pilote a la fois, libere s'il se tait, part ou si le poste disparait. Pendant un saut, la
  fenetre affiche "Saut vers X" puis "Distorsion : n s" (lu dans le miroir client).
- **Saut** (`SpaceJump`, etat de l'Environment avance dans le pas deterministe) : etoile de fond au reticule (<= 3
  degres, choisie a l'appui), Accroupi maintenu = charge de 60 ticks (alignement de l'avant sur l'etoile en 40 ticks,
  frein force, joystick inactif ; relacher annule), puis voyage de `max(20, ceil(20 D / v_w))` ticks pose figee, puis
  arrivee decidee par le serveur seul : systeme courant = cible, a 10 rayons de son etoile, face a elle, `V = 0`.
  Distorsion (ciel blanc + trainees) : `SpaceWarpEffect`, seule classe de l'effet, **provisoire**.
- `hololib:space_pilot` (joueur, `Configurable` + `QuickActionProvider`, rien pour les observateurs : C45) : surcouche
  du ciel du joueur local (`SpacePilotSkyRenderer` : anneaux des corps et des etoiles, orbites, nom au regard, lien
  pointille avant -> referentiel ; lunes et objets mineurs reperes seulement de pres), cercles du joystick (objets
  `Local` `PRIVATE`, pendant le pilotage seulement ; cercle avant = zone morte), cible et roue de charge du saut
  (`SpaceJumpRenderer`), HUD de navigation (`SpacePilotHud`, `Hud`, deplacable par l'Own HUD Editor). Quick Action
  `toggle` = coupe surcouche, HUD et lien (pas le joystick ni la roue). Module du joueur local : `SpacePilotClient`.
- `hololib:space_configurator` (marker, **ops**, tout masque aux non-ops par `shown()`, op verifie en premier par le
  serveur) : cube editable (carte galactique, systeme courant en vert ; ou systeme edite a temps fige `t_edit`, tout a
  l'echelle, zoom sur un corps, vaisseau en vert) et fenetre **detachee** (placee devant la base du cube par defaut,
  puis editable seule : translation, rotation, oeil) ; croix 3D de la selection avec coordonnees aux faces, origine
  des coordonnees (defaut Sol), vitesse de distorsion. Copie en lecture des en-tetes et du systeme edite, rafraichie
  quand la revision de l'Environment change.
- **Poignees sondees** : pas de callback d'edition ; `serverTick` compare le transform du cube, de `SpaceMapHandle`
  (translation de l'etoile selectionnee) et de `SpaceOrbitHandle` (rotation + echelle de l'orbite) a la derniere
  valeur ecrite par le module et en deduit l'edition. Poignees ajoutees / retirees des deux cotes (serveur au
  changement d'etat avec `markDirty`, client dans `onSync`, seulement si absentes), memes cles (`handle`, `orbit`).
- **Sync froid / chaud** (Environment) : bloc chaud (tick, pose, vitesse, referentiel, entree, pilote, reglages, saut)
  toujours ; revision ; bloc froid (systeme courant, etoiles de fond avec id et nom) **seulement au besoin** (D16) :
  instantane d'un arrivant, changement de revision (edition, saut), demande d'un client qui l'a manque. Sans lui le
  client ignore l'etat (`synced()` faux, ciel reduit au fond). Envois limites (4 ticks) ; en mouvement ou en saut,
  correction toutes les 20 ticks ; rien a l'arret.
- **Referentiel** : la vitesse est relative a un corps choisi par proximite x regard (delai, cooldown, fondu), qui
  entraine le monde ; synchronise et sauvegarde. Pose sauvegardee avec son tick (`time`), recalee sur le referentiel
  au chargement.
- **Lissage client** : simulation deterministe (`SpaceMotion.step`, `StrictMath`) rejouee par chaque client (dead
  reckoning) ; horloge d'affichage propre (jamais le `gameTime` vanilla), historique de 32 ticks interpole dans le
  repere du referentiel, observateur en leger retard, pilote local predit (reconciliation 10 % / tick). Les renderers
  lisent `pose(partialTick)`, `bodyPositions`, `displayDays`.

### Capture de controles (independante de l'interaction)

- `HoloControl` : boutons bruts, `SCROLL`, `MOUSE_MOVE`, `ESCAPE`, `ENTER`, `mapping("key.xxx")` (tout `KeyMapping`,
  modde compris, par son nom), `KEYBOARD` (toutes les touches + texte : `onKeyPress`, `onCharTyped`, codes `HoloKeys`). `HoloCaptor.captures(control)` + callbacks ; `HoloCaptures.begin/end` (facade
  commune, backend installe au setup client).
- `HoloControlCapture` : pile de captures explicites (la plus recente d'abord) puis captors passifs (l'interaction).
  Une capture explicite ne se termine jamais seule et reste active sous un ecran (rien n'est distribue). Boutons
  bruts : `MouseButton.Pre` annule ; mappings : neutralises juste apres `KeyMapping.set/click` (`MouseButton.Post`, `InputEvent.Key`) et a
  chaque debut de tick ; Echap capture : `ScreenEvent.Opening` du `PauseScreen` annule (poste avant `InputEvent.Key`) ;
  souris : rotation verrouillee sans Mixin (remise a chaque debut de frame et de tick, `turnPlayer` precede
  `onRenderTickStart`). `KeyMapping.setAll()` a la fin d'une capture explicite.
- `KEYBOARD` : sans Mixin, seul un `Screen` recoit les caracteres (`KeyboardHandler.charTyped`). Tant qu'un captor le
  prend, `HoloKeyboardScreen` (invisible, sans pause, ne se ferme pas sur Echap) est ouvert ; sous un ecran vanilla ne
  lit aucun `KeyMapping`. Les touches passent par l'ecran : au captor le plus recent qui prend `KEYBOARD` ou un
  controle lie a la touche. `setScreen` libere la souris et `MouseHandler.grabMouse` refermerait l'ecran : la souris est
  reprise en posant `mouseGrabbed` par reflexion (`ObfuscationReflectionHelper`, SRG `f_91520_` / `f_91511_`), donc la
  camera tourne et l'interaction continue (`HoloControlCapture.gameInputActive`).

### Les quatre familles de renderers

| Famille | Stage Forge | Resultat | Gestion de la profondeur |
|---|---|---|---|
| `SkyRenderer` | `AFTER_SKY` (+ `ComputeFogColor` avant) | dans la skybox, derriere tout le monde ; peut fixer la couleur du brouillard (`fogColor` : fond de l'ecran, horizon, terrain lointain) | profondeur entre objets du ciel, **videe apres** la passe ; projection du monde sans la translation du bobbing (comme le soleil) |
| `WorldRenderer<T>` | `AFTER_ENTITIES` | ancre dans le monde, cache par blocs et entites | profondeur du monde ; buffers **vides par vanilla** avec les entites |
| `ViewRenderer` | `AFTER_LEVEL` | dans la vue, au-dessus du monde, sous main et GUI | profondeur **videe avant** ; projection `STABLE` (sans bobbing, FOV des options) ou `WORLD` |
| `HudRenderer` | overlay GUI sous tous les autres | a l'ecran, orthographique, sous le HUD vanilla | profondeur **videe apres** la passe |

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
- Plan lointain = `GameRenderer.getDepthFar()` (128 au minimum) : objets du ciel a ~100 blocs (`SkyRenderContext.SKY_RADIUS`).

## Conventions

- Pas d'OpenGL brut : passer par `RenderType` / `MultiBufferSource` / `RenderSystem` (cache d'etat GL, compatibilite
  Oculus/Embeddium, migration future vers `GpuDevice` en 1.21.5+).
- Pas d'allocation par frame dans les chemins de rendu ; tout etat GL modifie est restaure (y compris sur exception).
- Un renderer lit un etat et dessine ; la logique (interaction, animations d'etat) vit ailleurs.
- Style : commentaires et javadoc en francais **sans accents**, accolades sur une nouvelle ligne, une
  `package-info.java` documente chaque package non trivial.
