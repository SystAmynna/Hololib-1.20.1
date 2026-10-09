# T07c — Vitesse relative au corps de référence

**Objectif** : demande de l'utilisatrice (D7, 2026-10-09) après un essai en jeu : en coupant les gaz près d'une
planète, elle s'éloignait (vitesse relative au centre du système). La vitesse devient relative au **corps de
référence** (le plus proche, avec hystérésis) et le monde est entraîné par son mouvement orbital.

**Dépendances** : T02, T04, T06, T07b (modifie leurs fichiers, tous dans les packages Space).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 5, points 0, 3, 4 et la fin du § (c'est la spécification).
- [`05-synchronisation.md`](../05-synchronisation.md) §§ 1 à 3 (bloc chaud, rejeu, prédiction, correction douce).
- `modules/dimension/space/math/SpaceMotion.java`, `SpacePose.java`, `SpaceOrbits.java`.
- `modules/dimension/space/SpaceEnvironmentModule.java` (état, NBT, `writeSync` / `readSync`, rejeu client,
  prédiction du pilote, correction douce, `setCurrentSystem`, éditions qui retirent un corps, `defaultPose`).
- `modules/monde/spacecontroller/SpaceControllerModule.java`, `SpaceControllerWindow.java` (affichage).

## Livrables
- `SpaceMotion` : référentiel au début du pas (règle du point 0, constante `REFERENCE_HYSTERESIS = 0.75`), déplacement
  `D = v + Δp_ref` (point 3), garde-fou sur `D` (point 4). Le référentiel entre et sort du pas (paramètre + résultat
  lisible après le pas, ou petit état mutable réutilisé ; **aucune allocation**). Positions des corps à `t` et `t+1`
  dans des tableaux de travail réutilisés. Le freinage d'approche (point 3) doit rester correct pour le référentiel
  (vitesse relative = `v`) ; pour les autres corps, utiliser la vitesse relative `D − Δp_i` si c'est simple, sinon
  documenter l'approximation (le garde-fou reste la garantie).
- `defaultPose` : fixe aussi le référentiel (le plus proche à la pose par défaut).
- `SpaceEnvironmentModule` : état `referenceBodyId` (serveur, rejeu client, prédiction du pilote : chacun le sien,
  mis à jour par `step`) ; bloc chaud (`varint`, 0 = aucun → recalcul) ; NBT (clé `ref`, tolérant) ; remis à jour
  par `setCurrentSystem` / pose par défaut ; corps de référence supprimé → recalcul au pas suivant. API :
  `referenceBody()` / `referenceBodyId()` (deux côtés) et l'altitude au-dessus de lui (serveur, calculée chaque tick
  comme `nearestAltitude`). Un changement de référentiel côté serveur déclenche un envoi (au plus 1 / 4 ticks).
- Controller : la fenêtre affiche « Référentiel : <nom> » et « Altitude : … » au-dessus de lui (texte sans accents
  dans le code : « Referentiel ») ; la vitesse affichée est `V` (déjà relative).
- Javadoc / `package-info` concernés à jour.

## Interdits
- Modifier le framework ou une classe hors des packages Space.
- Changer les signes (C28), les constantes de gaz (1,05 / 0,92 / 1 km/tick) ou la règle du frein (D6).

## Critères d'acceptation
- `compileJava` réussit.
- Vérification numérique jetable (scratchpad), échelle de temps par défaut, système Sol :
  - pose près de la Terre (ex. 20 rayons), `V = 0`, aucune entrée, 2000 ticks : distance à la Terre constante (écart
    relatif < 1e-9) alors que la Terre parcourt son orbite ;
  - même chose près de la Lune (référentiel Lune une fois assez proche) ;
  - hystérésis : point à mi-distance Terre / Lune → pas de bascule à chaque tick (nombre de bascules sur 2000 ticks) ;
  - approche de la Terre en avant puis frein → arrêt **par rapport à la Terre**, jamais dans un corps ;
  - loin de tout (référentiel Soleil) : comportement identique à avant (même trajectoire au bit près qu'avant T07c si
    le Soleil est la référence) ;
  - déterminisme : deux instances, 1000 pas, identiques bit à bit ; allers-retours réseau / NBT de l'état.
- En jeu : couper les gaz près d'une planète → elle reste en place à l'écran (seule la rotation du joystick la fait
  bouger) ; la fenêtre affiche le référentiel et il ne clignote pas entre deux corps.

## Compte rendu attendu
Fichiers modifiés, signature de `step`, format du bloc chaud, résultats numériques, écarts et pourquoi, scénario en
jeu.
