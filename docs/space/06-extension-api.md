# 06 — Extension d'API validée : couleur du brouillard

**Statut : validée par l'utilisatrice** ([`decisions.md`](decisions.md), D1). C'est la **seule** modification du
framework autorisée par ce dossier. Implémentée par la tâche [`T03`](taches/T03-extension-brouillard.md).

## Besoin

Le Space Environment masque le ciel vanilla en dessinant un fond opaque à `AFTER_SKY` (sans API). Mais la couleur
du **brouillard** reste celle de vanilla : bleu ciel le jour, ce qui teinte le terrain lointain et l'horizon (la
couleur d'effacement de l'écran est aussi la couleur du brouillard). Dans l'espace, elle doit être noire.

## Mécanisme Forge (vérifié dans les sources 47.4.10)

`FogRenderer.setupColor(...)` calcule `fogRed/Green/Blue` (ciel, biome, lever du soleil, pluie, eau, lave...), puis
appelle `ForgeHooksClient.getFogColor(...)`, qui poste **`ViewportEvent.ComputeFogColor`** (couleur modifiable par
`setRed/setGreen/setBlue`). Le résultat sert à `RenderSystem.clearColor(...)` (fond de l'écran) et au brouillard du
terrain (`FogRenderer.levelFogColor`). L'événement est posté une fois par frame, sur le fil de rendu, **avant**
`AFTER_SKY`.

## Forme de l'extension

1. **`client/render/sky/SkyRenderer.java`** : nouvelle méthode par défaut (aucun renderer existant n'est touché) :

   ```java
   /**
    * Couleur du brouillard et du fond de l'ecran de la frame, appelee avant la passe du ciel (evenement
    * ViewportEvent.ComputeFogColor), dans l'ordre d'enregistrement : chaque renderer recoit la couleur laissee par
    * le precedent et peut la modifier ({@code rgb} entre 0 et 1). Ne rien faire par defaut. Respecter le type de
    * brouillard de la camera (eau, lave, neige poudreuse : laisser vanilla).
    */
   default void fogColor(Camera camera, float partialTick, Vector3f rgb) {}
   ```

2. **`client/render/RenderDispatcher.java`** : un abonné de plus (le dispatcher reste le seul abonné du mod aux
   événements de rendu, comme pour `ComputeFov`) :

   ```java
   private static final Vector3f FOG = new Vector3f();          // reutilise : aucune allocation par frame

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onComputeFogColor(ViewportEvent.ComputeFogColor event)
   {
       List<SkyRenderer> renderers = HoloRenderers.sky();
       if (renderers.isEmpty()) return;
       FOG.set(event.getRed(), event.getGreen(), event.getBlue());
       for (SkyRenderer renderer : renderers) renderer.fogColor(event.getCamera(), (float) event.getPartialTick(), FOG);
       event.setRed(FOG.x); event.setGreen(FOG.y); event.setBlue(FOG.z);
   }
   ```

   Une exception d'un renderer est rattrapée et loguée une fois par renderer ; la couleur reçue est alors conservée.
   C'est **nouveau** : les passes existantes ne rattrapent rien (elles restaurent l'état en `finally` et laissent
   l'exception remonter), mais ici aucun état n'est à restaurer et une exception ferait perdre la couleur de la frame.

3. **Documentation** (même tâche) :
   - javadoc de `SkyRenderer` (la classe) : mentionner `fogColor` ;
   - `client/render/package-info.java` : ordre de rendu (ajout de « couleur du brouillard » avant `AFTER_SKY`) ;
   - `.claude/CLAUDE.md` : ligne `SkyRenderer` du tableau des familles (« peut fixer la couleur du brouillard ») ;
   - `rapport.md` § 6.1 : une ligne dans l'ordre d'une frame.

## Ce que l'extension ne fait pas

- Ne masque ni le ciel vanilla (fait par le fond opaque de T05), ni les **nuages**, ni la **pluie** (limites connues,
  [`decisions.md`](decisions.md) L1).
- Ne change pas la distance du brouillard (`ViewportEvent.RenderFog`) : non demandé.
- Ne change aucun comportement quand aucun renderer ne surcharge `fogColor`.

## Vérification

- `compileJava` ; en jeu sans module Space : brouillard et ciel inchangés (comparaison visuelle jour / nuit, pluie,
  sous l'eau).
- Avec un `SkyRenderer` de test (ou T05) qui met `rgb` à 0 en `FogType.NONE` : horizon et terrain lointain noirs ;
  sous l'eau : brouillard bleu vanilla conservé.
