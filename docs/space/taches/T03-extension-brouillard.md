# T03 — Extension d'API : couleur du brouillard

**Objectif** : permettre à un `SkyRenderer` de fixer la couleur du brouillard et du fond de l'écran. **Seule
modification du framework autorisée** par le dossier (validée, D1).

**Dépendances** : aucune.

## À lire avant
- [`06-extension-api.md`](../06-extension-api.md) (spécification complète, code indicatif).
- `client/render/RenderDispatcher.java`, `client/render/HoloRenderers.java`, `client/render/sky/SkyRenderer.java`,
  `client/render/sky/SkyRenderPass.java` (`try/finally` : restauration de l'état, pile déséquilibrée loguée), `client/render/package-info.java`.
- Sources Forge : `net/minecraft/client/renderer/FogRenderer.java` (`setupColor`),
  `net/minecraftforge/client/event/ViewportEvent.java` (`ComputeFogColor`), dans le jar de sources cité par CLAUDE.md.

## Livrables
- `client/render/sky/SkyRenderer.java` : méthode par défaut `fogColor(Camera, float, Vector3f)` + javadoc de la classe.
- `client/render/RenderDispatcher.java` : abonné `onComputeFogColor` (priorité `LOWEST`, `Vector3f` réutilisé,
  exception d'un renderer rattrapée et loguée une seule fois par renderer).
- Documentation : `client/render/package-info.java`, `.claude/CLAUDE.md` (tableau des familles), `rapport.md` § 6.1.

## Interdits
- Toucher à quoi que ce soit d'autre dans le framework. Modifier le comportement quand aucun renderer ne surcharge
  `fogColor` (l'abonné sort tout de suite si la liste est vide, et ne réécrit la couleur que si elle a changé).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (`runClient`), sans module Space : ciel, horizon, brouillard identiques à avant (jour, nuit, pluie, sous
  l'eau, Nether).
- Si l'utilisatrice veut un test avant T05 : un `SkyRenderer` temporaire (non commité) qui met `rgb` à 0 quand
  `camera.getFluidInCamera() == FogType.NONE` noircit horizon et terrain lointain, et laisse le brouillard de l'eau.

## Compte rendu attendu
Diff résumé ; vérification des sources Forge (où l'événement est posté, type de `getPartialTick`) ; résultat des
tests visuels ou scénario à faire par l'utilisatrice.
