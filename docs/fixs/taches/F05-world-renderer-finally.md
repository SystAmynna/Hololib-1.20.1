# F05 — `try/finally` dans `WorldRenderer.renderAll`, politique d'erreur des passes

**Objectif** : une exception dans un renderer monde ne doit pas déséquilibrer la pile `PoseStack` de vanilla ; les
quatre passes traitent les erreurs de la même façon. Rapport § 7.2, recommandation 5.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat (vérifié)
- `client/render/world/WorldRenderer.java:61-69` : `pushPose` / `render` / `popPose` sans `try/finally`.
- Sans shaderpack, la pile est celle de Forge (`levelPose`, `WorldRenderPass:41`) : une exception laisse la pile
  vanilla déséquilibrée pour le reste de la frame.
- Les passes Sky, View et HUD réparent leur pile en `finally`, pas celle-ci.
- Politique d'erreur incohérente :
  - `fogColor` : exception attrapée et journalisée une fois (`RenderDispatcher:121`, ensemble à clés faibles) ;
  - `render` : exception propagée partout, ce qui fait planter le jeu.

## À lire avant
- Rapport § 7.2 ; `client/render/package-info.java` (règles).
- `client/render/RenderDispatcher.java`, `client/render/world/WorldRenderer.java`, `WorldRenderPass.java`,
  `sky/SkyRenderPass.java`, `view/ViewRenderPass.java`, `hud/HudRenderPass.java`.

## Zone
`client/render/world/*`, `client/render/sky/SkyRenderPass.java`, `client/render/view/ViewRenderPass.java`,
`client/render/hud/HudRenderPass.java`, `client/render/RenderDispatcher.java`.

## Livrables
1. `WorldRenderer.renderAll` : `try/finally` autour de chaque `pushPose` / `popPose`.
2. Une **politique unique**, documentée dans `client/render/package-info.java` : une exception d'un renderer est
   attrapée par la passe, journalisée **une fois** par renderer (même mécanisme que `fogColor`), et ce renderer est
   ignoré pour le reste de la frame. L'état GL et les piles sont réparés comme aujourd'hui.
   - Option, à confirmer avec l'utilisatrice : le renderer est désactivé après N échecs.
   - Par défaut, aucune désactivation : seulement le journal unique.
3. Le buffer immédiat (`HoloBuffers`) est vidé ou abandonné proprement après une exception, pour ne pas mélanger
   des sommets à moitié émis avec le renderer suivant. Lire `HoloBuffers` pour choisir.
4. En développement (`!FMLLoader.isProduction()`), la première exception est **relancée** après le journal, pour ne
   pas masquer les bugs. Option à garder si l'utilisatrice le souhaite : le noter en question.

## Interdits
- Attraper `Throwable` (seulement `RuntimeException`).
- Allocation par frame dans le chemin sans erreur.

## Critères d'acceptation
- `compileJava` réussit.
- Banc jetable (non livré) : un `WorldRenderer` qui lève une exception une frame sur deux. Le jeu continue, le log
  n'a qu'une entrée, les autres renderers et le monde s'affichent correctement, avec ou sans Oculus.

## Compte rendu attendu
Fichiers, politique retenue, résultat du banc, question éventuelle sur la relance en développement.
