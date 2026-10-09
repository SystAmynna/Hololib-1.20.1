# T06 — Module Space Controller

**Objectif** : le module de marker qui affiche la fenêtre de contrôle et relaie le pilotage à l'Environment de sa
dimension. Le captor de pilotage (souris, touches) est T07, les cercles T08 : ici, la fenêtre, l'état, les actions
serveur et un `SpacePilotPad` minimal (clic → capture → `BEGIN`/`END`).

**Dépendances** : T04.

## À lire avant
- [`01-vision.md`](../01-vision.md) § Space Controller ; [`02-architecture.md`](../02-architecture.md) §§ 4, 6, 9 ;
  [`05-synchronisation.md`](../05-synchronisation.md) §§ 3, 4.
- `holo/window/HoloWindow.java`, `holo/widget/HoloLabel.java`, `holo/widget/control/HoloPointerInput.java`,
  `HoloWidgetMouseBox.java` (exemple de trait pointeur).
- `demo/DemoKeyboardModule.java` + `demo/DemoPad.java` (actions sourcées, validation serveur, envoi périodique).
- `demo/DemoWorldPosModule.java` (module de position) ; `holo/memory/HoloContainers.java` (`actionTarget`, `mayUse`).
- `holo/object/HoloAccess.java`, `HoloVisibility.java`.

## Livrables
- `modules/monde/package-info.java` (s'il n'existe pas : modules de position), `modules/monde/spacecontroller/` :
  `package-info.java`, `SpaceControllerModule`, `SpaceControllerWindow`, `SpacePilotPad`.
- `modules/HoloLibModules.java` : `space_controller` (`POSITION`).

## Spécification
- Fenêtre `World()` à `(0, 1.6, 0)` du marker, ≈ 160 × 90 px, `pixelScale` ≈ 1/100, `PUBLIC`, accès `EVERYONE`
  (C2), `setInterceptedInputs(LEFT_CLICK, RIGHT_CLICK)`. Éditable (`HoloEditTranslation`, `HoloEditRotation`,
  `HoloEditScale`, `opRequired = true`, `ownRequired = false`) pour pouvoir la placer.
- Contenu : titre, état (« Libre » / « Piloté par X » / « Aucun Space Environment dans cette dimension »), vitesse
  (unité adaptée : km/s, UA/s), corps le plus proche + altitude, zone `SpacePilotPad` « Cliquer pour piloter ».
- `SpacePilotPad` : traits `HoloPointerInput` (+ survol) ; à `onCaptureStart` → action `BEGIN`, à `onCaptureEnd` →
  `END`. T07 y ajoutera mappings, cône et entrées : prévoir des points d'extension clairs (méthodes vides
  documentées), pas de logique de pilotage ici.
- Serveur `handleAction(player, source, buf)` : `source == window`, type d'action ; `BEGIN` → `claimPilot`, `INPUT` →
  `pilotInput` (lecture `SpacePilotInput.read`, prêt pour T07), `END` → `releasePilot`. Environment absent : ignorer.
- `serverTick` : rafraîchir l'état affiché (doc 05 § 4, ≤ 4 envois / s, valeurs arrondies) ; `onSync` met à jour
  les labels.
- Client : si l'état indique un autre pilote alors que le pad capture, rendre la main (`HoloCaptures.end`).

## Interdits
- Modifier le framework ; stocker la pose ou la galaxie dans le Controller.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : `/hololib_marker create poste`, `/hololib_module add world poste hololib:space_controller` → fenêtre
  visible ; sans Environment, message adéquat ; avec, vitesse 0 et corps le plus proche affichés.
- Clic → l'état passe à « Piloté par <moi> » (caméra figée : comportement normal de `HoloPointerInput` à ce stade) ;
  Échap → « Libre ». Avec `runClient2`, le second joueur voit l'état et ne peut pas prendre la main pendant ce temps.

## Compte rendu attendu
Fichiers ; points d'extension laissés pour T07 ; format des actions ; écarts.
