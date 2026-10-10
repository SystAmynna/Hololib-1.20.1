# T20 — Space Pilot : HUD de navigation

**Objectif** : (D14) les données importantes de pilotage affichées sur le HUD du joueur : **vitesse actuelle**,
**corps de référence** (référentiel), **distance** à ce corps ; le bloc est **déplaçable** avec le HUD Editor
(`own_hud_editor`). Actif sans Space Controller, selon les réglages `hud` et `enabled`.

**Dépendances** : T17.

## À lire avant
- [`02-architecture.md`](../02-architecture.md) § 7 bis ; `.claude/CLAUDE.md` §§ « Objets holographiques » (ancrage
  `Hud`), « Edition en jeu » (Own HUD Editor, cadre HUD).
- `demo/DemoPlayerModule.java` (objet `Hud` d'un module joueur, modèle à suivre), `holo/edit/HoloEditTranslation.java`,
  `holo/edit/HoloEditRules.java` (`ownCapability` : `Hud` → `OWN_HUD`).
- `modules/monde/spacecontroller/SpaceControllerModule.java` (formatage de la vitesse et de l'altitude affichées :
  réutiliser ou extraire en utilitaire commun Space), `SpaceEnvironmentModule` (accès client : `speed()`, référentiel,
  `nearestAltitude`, `bodyPositions`, `displaySpeed`).

## Livrables
1. `SpacePilotHud` (`modules/camera/spacepilot/`) : objet `Hud` `PRIVATE`, `HoloEditTranslation` (rien d'autre), créé
   dans `onLoad` des deux côtés ; `shown()` = Environment présent dans la dimension du joueur **et** `enabled` **et**
   `hud`. Placement par défaut : coin haut gauche, sous le HUD vanilla.
2. Contenu (3 lignes, sans accents) : « Vitesse : <valeur> » (unité adaptée km/s, km/tick ou fraction de c ; la même
   que la fenêtre du Controller), « Reference : <nom> », « Distance : <valeur> » (km / UA selon la grandeur).
   Valeurs lues dans le miroir client de l'Environment, rafraîchies au plus 4 fois par seconde (pas d'allocation de
   chaîne par frame).
3. Renderer (`HoloObjectRenderer` du HUD ou widget `HoloLabel` si l'objet est une `HoloWindow` : choisir le plus simple
   et le justifier) enregistré par `SpacePilotRenderers`.
4. Pendant la distorsion (T21 : brancher si la phase existe) : « Distorsion -> <cible> » et temps restant à la place
   de la distance.

## Interdits
- Modifier une classe hors des packages Space (les éditeurs, le cadre HUD, `HoloEditRules` restent intacts).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : avec Space Pilot dans une dimension Space, les trois lignes s'affichent et suivent le pilotage (vitesse,
  changement de référentiel) ; G avec l'Own HUD Editor → le bloc se déplace et garde sa place après reconnexion ;
  « Donnees de navigation » OFF ou Quick Action OFF le masquent ; masqué en F5 (règle du `Hud`), invisible d'un
  inspecteur.

## Compte rendu attendu
Fichiers, choix objet / fenêtre, formats affichés, écarts, scénario.
