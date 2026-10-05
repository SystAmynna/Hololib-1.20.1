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
├── HoloLib.java                 classe @Mod
└── client/render/               TOUS les renderers du mod (regle d'architecture)
    ├── package-info.java        doc detaillee : ordre de rendu, regles (source de verite)
    ├── RenderDispatcher.java    seul abonne aux evenements de rendu, route vers les passes
    ├── HoloRenderers.java       registre des renderers (register/unregister, fil de rendu)
    ├── sky/                     SkyRenderer, SkyRenderContext, SkyRenderPass
    ├── world/                   WorldRenderer<T>, WorldRenderable, WorldRenderContext, WorldRenderPass
    ├── view/                    ViewRenderer, ViewRenderContext, ViewRenderPass
    └── type/HoloRenderTypes     RenderTypes du mod (POSITION_COLOR_LIGHTMAP, opaque / translucide)
```

### Les trois familles de renderers

| Famille | Stage Forge | Resultat | Gestion de la profondeur |
|---|---|---|---|
| `SkyRenderer` | `AFTER_SKY` | dans la skybox, derriere tout le monde | profondeur entre objets du ciel, **videe apres** la passe |
| `WorldRenderer<T>` | `AFTER_ENTITIES` | ancre dans le monde, cache par blocs et entites | profondeur du monde ; buffers **vides par vanilla** avec les entites |
| `ViewRenderer` | `AFTER_LEVEL` | dans la vue, au-dessus du monde, sous main et GUI | profondeur **videe avant** ; projection `STABLE` (sans bobbing) ou `WORLD` |

Chaque famille suit le meme schema dans son sous-package : `XxxRenderer` (API publique), `XxxRenderContext` (donnees
de la frame, objet reutilise, setters package-private) et `XxxRenderPass` (interne, appelee par `RenderDispatcher`).

**Ajouter un renderer** : implementer `SkyRenderer` / `ViewRenderer` ou etendre `WorldRenderer<T>`, puis
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
