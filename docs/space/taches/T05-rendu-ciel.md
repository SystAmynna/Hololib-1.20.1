# T05 — Rendu du ciel spatial

**Objectif** : `SpaceSkyRenderer`, qui remplace visuellement le ciel vanilla par le système courant vu depuis la pose
du monde : fond opaque, étoiles de fond, corps du système avec taille apparente et phases, brouillard noir.

**Dépendances** : T03 (méthode `fogColor`), T04.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 2 à 4 ; [`02-architecture.md`](../02-architecture.md) §§ 2, 5, 9.
- `client/render/sky/SkyRenderer.java`, `SkyRenderContext.java`, `SkyRenderPass.java` (repère reçu, profondeur,
  projection, `farPlane`, `hiddenWithSky`).
- `client/render/modules/dimension/planet1/Planet1PlanetRenderer.java` (éclairage manuel, quads colorés),
  `client/render/type/HoloRenderTypes.java`.
- `client/render/HoloRenderers.java` (`register(SkyRenderer)`), `client/render/modules/ModuleRenderers.java`.
- Conventions de rendu de `.claude/CLAUDE.md` (pas d'alloc par frame, pas d'OpenGL brut).

## Livrables
- `client/render/modules/dimension/space/SpaceSkyRenderer.java`, `SpaceRenderers.java` (enregistrement),
  `package-info.java`.
- `client/render/modules/ModuleRenderers.java` : appel de `SpaceRenderers.register()`.

## Spécification
- `shouldRender` : miroir de l'Environment présent (`ClientHoloWorld.find(HoloScope.DIMENSION)` +
  `HoloModes.find`), sinon rien (ciel vanilla intact).
- `hiddenWithSky()` : défaut (vrai).
- Ordre de dessin dans `render` : fond opaque (`SOLID_QUADS`, noir, 120 blocs, sphère grossière ou cube intérieur),
  étoiles de fond (110 blocs), puis corps triés du plus lointain au plus proche (rayons 105 → 50), selon le doc 04 § 4 :
  disque polaire face à l'œil, normales de sphère pour les phases, halo des étoiles, taille minimale.
- Pose interpolée par `ctx.partialTick()` (`SpaceEnvironmentModule.pose(partialTick, dest)`) ; positions des corps au
  même temps : `bodyPositions(partialTick, dest)` / `displayDays(partialTick)` (horloge d'affichage du client, T07e), **jamais** le
  `gameTime` de Minecraft. `synced()` faux = rien à dessiner. `SpaceSkyFrame` pour les directions.
- Le maillage du disque, les tableaux de positions, les vecteurs et matrices : **alloués une fois** (champs).
- `fogColor` : met `rgb` à une couleur spatiale quasi noire (ex. `0.01, 0.01, 0.02`) si un Environment est présent et
  `camera.getFluidInCamera() == FogType.NONE` ; sinon ne touche à rien.
- Couleurs : celles du modèle ; étoiles en pleine lumière, planètes éclairées par l'étoile principale (ou la plus
  proche si binaire).

## Interdits
- Abonnement à des événements Forge ; état GL modifié sans restauration ; dessin au-delà de `ctx.farPlane()`.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu avec `space_environment` installé : ciel noir étoilé, plus de soleil / lune / étoiles vanilla, horizon et
  terrain lointain noirs ; l'étoile et les planètes de l'exemple sont visibles, avec phases ; on voit les orbites
  progresser (une planète intérieure bouge en quelques dizaines de secondes avec l'échelle par défaut).
- `/hololib_module remove dimension here hololib:space_environment` : retour immédiat du ciel vanilla.
- Avec `planet_1` installé en même temps : ses objets restent visibles devant le fond.
- Pas d'erreur de pile déséquilibrée dans le log ; FPS sans chute notable.

## Compte rendu attendu
Fichiers ; choix de rendu (maillages, tailles, rayons) ; captures ou description de ce qui a été vérifié en jeu ;
réglages à exposer plus tard si utiles.
