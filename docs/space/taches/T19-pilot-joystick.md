# T19 — Space Pilot : joystick migré, zone morte, lien vers le référentiel

**Objectif** : (D11, D14) le joystick de pilotage passe par Space Pilot : ses cercles ne sont plus des objets du
Controller mais du module du joueur, dessinés seulement pendant que le joueur local pilote ; le cercle avant devient
la **zone morte** ; sans Space Pilot, le pilotage est **refusé**. Ajoute le **segment en pointillés** entre le
cercle avant (axe avant) et le centre du corps de référence, actif dès que le réglage `refLink` est vrai (même sans
pilotage).

**Dépendances** : T17 (T18 conseillée : primitives de projection partagées).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 6 (joystick, zone morte du lot 2), § 9 (lien) ;
  [`decisions.md`](../decisions.md) D11, C27, C29, C44, C48.
- `modules/monde/spacecontroller/` : `SpaceControllerModule` (objets `forward` / `aim`, `localPilotHere`,
  `clientTick` qui place le cercle avant, `handleAction` / `ACTION_BEGIN`), `SpaceJoystickForward`, `SpaceJoystickAim`,
  `SpacePilotPad` (`DEAD_ZONE`, `CONE_ANGLE`, `beyondDeadZone`, calcul des taux).
- `client/render/modules/monde/spacecontroller/` (`SpaceJoystickRenderer`, `SpaceControllerRenderers`),
  `client/modules/monde/spacecontroller/SpaceControllerClient`.
- `holo/memory/HoloContainers.java` (`player(ServerPlayer)` : mémoire active et ses modules, côté serveur).

## Livrables
1. **Migration** : `SpaceJoystickForward` / `SpaceJoystickAim` déplacés dans `modules/camera/spacepilot/`, objets du
   `SpacePilotModule` (`PRIVATE`), `shown()` vrai seulement si le joueur local est le pilote d'un Controller et en
   capture (même règle que C29, lue via les backends existants, sans dépendance du code commun vers le client).
   Placement du cercle avant fait par le module Space Pilot (même calcul qu'avant). Renderer déplacé dans
   `client/render/modules/camera/spacepilot/` et enregistré par `SpacePilotRenderers` ; le Controller ne crée plus ces
   objets (anciennes sauvegardes de marker : objets absents, sans erreur).
2. **Pilotage refusé sans Space Pilot** (D11) : au `BEGIN`, le serveur vérifie la mémoire active du joueur ; absent →
   refus avec message « Module Space Pilot requis » (sans accents), le client rend la main comme pour un autre refus.
3. **Zone morte** (C48) : `SpacePilotPad` calcule `D` depuis les rayons des deux cercles (constantes publiques des
   objets migrés) ; la commande reste continue au bord ; agrandir le cercle avant si `D` < 1,5° et le noter.
4. **Lien vers le référentiel** (`refLink`, C44 : coupé par `enabled` faux) : segment pointillé sur le ciel entre la
   direction de l'axe avant et le référentiel (§ 9), dessiné par le renderer du ciel de Space Pilot (T18) ou un
   renderer dédié de la même famille ; visible sans pilotage.
5. Docs : 02 (§§ 1, 6, 7 bis) et 04 § 6 alignés ; package `client/render/modules/monde/spacecontroller/` supprimé s'il
   est vide (et son appel dans `ModuleRenderers`).

## Interdits
- Modifier une classe hors des packages Space (le seul fichier commun touché : `ModuleRenderers`).
- Changer les signes, le cône, la règle des gaz / du frein (C27, C28).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : sans Space Pilot, clic sur le pad → message, pas de capture ; avec, les deux cercles apparaissent pendant le
  pilotage seulement ; rotation nulle tant que le cercle de visée est dans le cercle avant, progressive au-delà ; le
  lien pointillé relie l'axe avant au référentiel (pilotage ou non) et suit un changement de référentiel ; un
  second joueur ne voit ni cercles ni lien.

## Compte rendu attendu
Fichiers déplacés / créés / modifiés, valeur de `D`, gestion des anciennes sauvegardes, écarts, scénario.
