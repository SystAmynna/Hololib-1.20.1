# F14 — Casser le cycle `spacepilot` ↔ `spacecontroller`

**Objectif** : supprimer la dépendance circulaire entre le module joueur Space Pilot et le module marker Space
Controller. Une petite interface de `dimension/space` porte ce que chacun doit savoir de l'autre. Rapport § 4.3,
recommandation 10.

**Lot** : B   **API** : non (packages Space uniquement)   **Dépendances** : aucune

## Constat (vérifié)
- `modules/camera/spacepilot/SpacePilotModule.java:25` et `SpaceJoystickForward.java:7` importent
  `modules/monde/spacecontroller/SpacePilotPad`.
- `modules/monde/spacecontroller/SpaceControllerModule.java:7` importe `SpacePilotModule` (Space Pilot requis pour
  piloter, D11).
- `SpacePilotPad.java:14-15` importe `SpaceJoystickAim` / `SpaceJoystickForward` (cercles du joystick).
- Les cercles (module joueur) lisent l'état du pad, et le pad (contrôleur) connaît les cercles et le module joueur.

## À lire avant
- Rapport § 4.3, § 2.4 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 5.
- `docs/space/02-architecture.md` § 6 et `docs/space/decisions.md` (D11, C25, C120).
- `modules/camera/spacepilot/*`, `modules/monde/spacecontroller/*`, `client/modules/camera/spacepilot/*`,
  `client/modules/monde/spacecontroller/*`, `modules/dimension/space/math/SpacePilotInput.java`.

## Zone
Les quatre packages lus ci-dessus et `modules/dimension/space/` (ajout d'une interface ou d'un record).

## Livrables
1. Relevé précis de ce que chaque côté utilise de l'autre (méthodes, constantes, à joindre au compte rendu).
2. Une abstraction dans `modules/dimension/space/`, par exemple :
   - `SpacePilotLink` : état du joystick lu par les cercles (angle de cône, déviation, zone morte) ; requête
     « ce joueur a-t-il Space Pilot » ;
   - ou des constantes et un état déplacés dans `SpacePilotInput` / un nouveau record `SpaceJoystickState`.
   
   Choisir la plus petite qui supprime **toutes** les arêtes d'un des deux sens.
3. Après la tâche, `spacepilot` → `spacecontroller` **ou** `spacecontroller` → `spacepilot` n'existe plus ;
   idéalement aucun des deux. Joindre la mesure des imports.
4. Comportement identique : D11 (refus sans Space Pilot, serveur et client), cercles, fin de capture (C120),
   `resetToggleKeys`.
5. `docs/space/02-architecture.md` § 6 mis à jour (paquets, dépendances).

## Interdits
- Modifier une classe hors des packages Space.
- Changer le format réseau des actions du Controller.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu :
  - pilotage complet (gaz, roulis, joystick, frein, boost, saut) ;
  - refus du pilotage sans Space Pilot ;
  - retrait de Space Pilot pendant le pilotage → capture rendue ;
  - cercles du joystick corrects.

## Compte rendu attendu
Relevé des dépendances, abstraction choisie, mesure des imports avant / après, fichiers, scénario.
