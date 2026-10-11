# T38 — Temps de l'espace ralenti (÷ 10)

**Objectif** : (D28) le temps de l'espace (orbites, rotation propre des astres, mouvement d'entraînement du
référentiel) s'écoule **10 fois moins vite** : échelle de temps par défaut 3 600 → **360 s simulées par tick**
(1 jour simulé = 240 ticks = 12 s ; année terrestre ≈ 73 min ; mois lunaire ≈ 5,5 min). Un monde existant passe à la
nouvelle échelle **sans saut** des astres.

**Dépendances** : aucune.

## Constat
Le temps simulé est **absolu** : `days = gameTime · timeScale / 86 400` (`SpaceUnits.days`,
`SpaceMotionSettings.days`). Changer l'échelle déplacerait d'un coup tous les corps sur leurs orbites et laisserait le
vaisseau loin de son référentiel (sa pose est en km dans le repère du système).

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 1, 4, 5 ; [`04-mathematiques.md`](../04-mathematiques.md) §§ 3, 5,
  10 ; [`05-synchronisation.md`](../05-synchronisation.md) §§ 2, 5 ; [`decisions.md`](../decisions.md) C20, D28.
- `math/SpaceMotionSettings.java` (`DEFAULT_TIME_SCALE`, `days`, réseau, NBT `tscale`), `math/SpaceUnits.java`,
  `SpaceEnvironmentModule` (`setTimeScale`, `setSettings`, `displayDays`, `pose`, historique, chargement / archive),
  `SpaceArchive`, `monde/spaceconfigurator/SpaceConfiguratorModule.displayDays` (animation à l'échelle de
  l'Environment), `math/SpaceSpin` (rotation propre au temps simulé).

## Livrables
1. `DEFAULT_TIME_SCALE = 360`.
2. **Époque** : le temps simulé devient `days(t) = epochDays + (t − epochTick) · timeScale / 86 400`, `epochTick`
   (`long`) et `epochDays` (`double`) portés par les réglages : déterministes, synchronisés (bloc chaud, avec les autres
   réglages) et sauvegardés (NBT tolérant : absents = époque `(0, 0)`, soit la formule actuelle). Tous les appels de
   `days` passent par là (serveur, rejeu client, prédiction, affichage, Configurator).
3. `setTimeScale` re-pose l'époque au tick courant de l'état (`epochDays` = temps simulé à ce tick avec l'ancienne
   échelle) : aucun saut d'orbite. Dire comment l'historique d'affichage (32 ticks) traverse le changement (accepter
   un léger accroc d'un tick si nécessaire, le documenter).
4. **Migration** d'un monde existant : réglages chargés **sans époque** et `tscale == 3 600` (ancien défaut) → 360,
   avec époque posée au premier tick serveur sur le temps simulé courant (comme `setTimeScale`). Une autre échelle
   (réglée à la main) est gardée telle quelle. Même règle pour l'archive (`SpaceArchive`).
5. Budget réseau du bloc chaud remesuré (+16 o attendus) ; docs 03 §§ 1, 4, 5, 04 § 3, 05 §§ 2, 5 alignés.

## Interdits
- Modifier une classe hors des packages Space ; `Math` dans les calculs du pas (rester en `StrictMath` / `double`).

## Critères d'acceptation
- `compileJava` réussit.
- Banc : `days` continu au changement d'échelle et à la migration ; serveur et client bit à bit sur 200 ticks autour
  d'un changement.
- En jeu : un monde du lot 3 se charge sans que les planètes ni le vaisseau ne sautent ; les orbites (la Lune autour
  de la Terre) tournent 10 fois moins vite ; la rotation propre aussi ; l'animation du Configurator suit.

## Compte rendu attendu
Fichiers, format réseau / NBT, comportement de l'historique au changement, banc, écarts, points à vérifier en jeu.
