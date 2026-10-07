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
│   └── camera/focus|editor/     modules joueur : mode focus (H) ; editeurs (G) own/world/dimension/other + panneau
└── client/
    ├── holo/                    ClientHoloWorld (miroirs des modules par portee), handlers des paquets, HoloModes
    ├── capture/                 HoloControlCapture : SEUL abonne aux inputs ; HoloKeyboardScreen (clavier entier)
    ├── interact/                HoloInteractions : picking + survol ; captor passif de la capture
    ├── key/                     HoloKeyMappings (touches du mod, etat physique d'un controle)
    ├── edit/                    HoloEditSession (session d'edition), HoloEditTools (outil actif, poignees, gizmos),
    │                            boutons des objets edites (HoloEditTarget / Panel / EyeButton)
    ├── modules/camera/          logique client des modules joueur : FocusController, EditorController
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
        ├── modules/             renderers des modules, meme arborescence que modules/ (ModuleRenderers au setup)
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
  non-proprietaire, et `viewers` pour la memoire d'un autre joueur). Protocole reseau `"6"`.

### Widgets interactifs (`holo/widget/control`)

- **Regle** : un widget interactif se compose de **traits** (interfaces a methodes par defaut) : `HoloHoverable`,
  `HoloClickable`, `HoloScrollable`, `HoloToggleable`, `HoloDraggable`, `HoloTextInput` (+ `HoloTextBuffer`),
  `HoloPointerInput`, `HoloDirectionalInput` (+ `HoloDirections`). Le composant ne fournit que son etat et ses
  reactions. Les traits ne redefinissent jamais `intercepts` mais `interceptsClick` / `interceptsScroll` /
  `interceptsKey` (pas de conflit de defauts).
- Widgets prets a l'emploi, prefixes `HoloWidget` (`HoloWidgetButton`, `HoloWidgetMultiButton`, `HoloWidgetHoverZone`,
  `HoloWidgetScrollZone`, `HoloWidgetSwitch`, `HoloWidgetValueBar`, `HoloWidgetTextBox`, `HoloWidgetMouseBox`,
  `HoloWidgetDraggable`, `HoloWidgetKeyBox`, `HoloWidgetList`), non finaux. Les demos les utilisent ; ne plus ecrire de widget ad hoc
  dans un module si un trait ou un widget existe.
- Framework : `onDrag` (chaque frame, clic intercepte maintenu, regard projete par `HoloPicking.project`) et
  `hoverMargin()` (survol conserve sur le fond / hors fenetre dans la marge).

### Edition en jeu (`holo/edit`, `client/edit`)

- Un objet editable implemente `HoloEditable` (`ownRequired()` = true, `opRequired()` = false par defaut) via les
  outils `HoloEditTranslation`, `HoloEditRotation`, `HoloEditScale`, `HoloEditVisibility` (bouton oeil, `HoloDisplay`
  OFF / FOCUS / ON), `HoloEditFixOrDynamic` (memoire joueur, `Local` relatif <-> follow). Editable = vu + capacite
  + regles : un module editeur (`HoloEditorModule`) par portee (`own_editor`, `world_editor`, `dimension_editor`) ;
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
  de la fenetre de l'editor. Les `Local` follow (et `HoloRelativeToOpen`) sont **tenus** : affiches fixes dans le
  repere de reference (regard a l'ouverture, `HoloEditMath.rebaseLocal`), reconvertis et envoyes a la fermeture ;
  seuls les groupes modifies changent. Le reste est envoye en fin de manipulation (`HoloEditObjectPacket`) ; le
  placement recu d'un objet tenu ou manipule est ignore (`ClientHoloContainer.apply`).
- Modules `hololib:focus` (etat synchronise, sorties lues sur les touches physiques : saut, sprint, marche > 20 ticks
  sans sneak, double sneak < 10 ticks) et editeurs (G, au moins un equipe ; `editor_panel` les liste et les
  desactive, a partir de deux) : focus et edition jamais ouverts ensemble.

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
| `SkyRenderer` | `AFTER_SKY` | dans la skybox, derriere tout le monde | profondeur entre objets du ciel, **videe apres** la passe ; projection du monde sans la translation du bobbing (comme le soleil) |
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
