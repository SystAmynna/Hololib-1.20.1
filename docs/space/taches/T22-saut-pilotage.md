# T22 — Saut interstellaire : pilotage, cible au réticule, roue de charge

**Objectif** : (D14) côté joueur : pendant le pilotage, placer une étoile dans le réticule, **maintenir Sneak** (touche
choisie par le joueur) → le monde se recadre, le joystick ne répond plus, une **roue de chargement de 3 s** se remplit
autour de l'étoile de destination ; relâcher annule. La fenêtre du Controller montre l'état du saut.

**Dépendances** : T19, T21.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 6, 8 (cible C41), 9 (roue de charge) ;
  [`05-synchronisation.md`](../05-synchronisation.md) § 3 ; [`decisions.md`](../decisions.md) C27, C39–C41, L8.
- `modules/monde/spacecontroller/SpacePilotPad.java` (mappings capturés, `BRAKE = key.jump`, envoi d'`INPUT`,
  prédiction), `SpaceControllerWindow`, `SpaceControllerModule` (libellés d'état).
- `client/modules/camera/focus/FocusController.java` (`SNEAK = key.sneak` : double Sneak ouvre le focus ; vérifier
  que la capture du pad neutralise bien Sneak pour le focus pendant le pilotage — `.claude/CLAUDE.md` § Capture).
- `SpacePilotSkyRenderer` / `SpaceJoystickRenderer` (T18, T19) ; `SpaceEnvironmentModule.jump()` (T21).

## Livrables
1. **Pad** : capture `HoloControl.mapping("key.sneak")` ; à l'appui, cherche la cible (étoile de fond la plus proche du
   centre de l'écran à ≤ 3°, C41, directions identiques à celles du ciel) ; si trouvée, envoie l'entrée avec
   `FLAG_JUMP` + id tant que Sneak est tenu ; relâché → entrée sans drapeau. Pas de cible → rien (pas de message).
   Joystick : pendant `CHARGE` / `TRAVEL`, taux nuls envoyés (la caméra reste bornée au cône).
2. **Repérage de la cible** : tant que Sneak est tenu sans saut accepté, et pendant la charge, l'étoile visée est
   surlignée par la surcouche (couleur de sélection) ; pendant le pilotage sans Sneak, l'étoile qui serait choisie
   peut aussi être indiquée (aide à viser, à juger : le dire).
3. **Roue de charge** (Space Pilot, visible du pilote local seulement, même avec `enabled` faux, C44) : anneau autour
   de la direction de l'étoile cible, rempli selon la progression de `CHARGE` (interpolée), taille constante à
   l'écran (§ 9).
4. **Fenêtre du Controller** : « Saut vers <nom> » pendant la charge, « Distorsion : <n> s » pendant le voyage
   (temps restant), puis l'état normal.
5. Libération de capture pendant le voyage (Échap) : permise, le voyage continue (C40).
6. **HUD de navigation** (report du livrable 4 de T20) : pendant la distorsion, `SpacePilotHud` affiche
   « Distorsion -> <cible> » puis le temps restant à la place de la ligne « Distance » (voir la javadoc de
   `SpacePilotHud` et `SpacePilotModule.refreshHud`).

## Interdits
- Modifier une classe hors des packages Space ; changer la règle des gaz / du frein hors saut.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : en pilotage, viser Sirius, maintenir Sneak → le monde tourne en ≈ 2 s jusqu'à avoir Sirius devant, la roue
  se remplit en 3 s ; relâcher à 2 s → annulé ; tenir 3 s → voyage (ciel de distorsion si T23 est faite), puis arrivée
  au bord de Sirius face à l'étoile ; touche Sneak remappée dans les options → la nouvelle touche marche ; double
  Sneak pendant le pilotage n'ouvre pas le focus ; un second joueur voit le monde tourner et voyager, pas la roue.

## Compte rendu attendu
Fichiers, comportement de la capture de Sneak (focus), choix d'aide à la visée, écarts, scénario.
