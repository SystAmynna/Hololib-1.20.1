# T07b — Vitesse signée et frein (Espace)

**Objectif** : demande de l'utilisatrice (D6, 2026-10-09) après T08. La vitesse du monde devient signée (Reculer =
marche arrière) et la touche Saut (Espace par défaut) maintenue freine jusqu'à l'arrêt, en primant sur les gaz.

**Dépendances** : T02, T04, T06, T07, T08 (modifie leurs fichiers, tous dans les packages Space).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 5, point 1 (nouvelle règle des gaz : c'est la spécification).
- `modules/dimension/space/math/SpacePilotInput.java`, `SpaceMotion.java`, `SpaceMotionSettings.java`.
- `modules/dimension/space/SpaceEnvironmentModule.java` (validation de `speed`, `moving()`, NBT, `pilotInput`).
- `modules/monde/spacecontroller/SpacePilotPad.java`, `SpaceControllerModule.java`, `SpaceControllerWindow.java`
  (affichage de la vitesse).
- `holo/capture/HoloControl.java` (`mapping("key.jump")`).

## Livrables
- `SpacePilotInput` : champ `brake` (booléen) ; `of(...)`, `sanitize`, `isZero`, `write` / `read` (format réseau
  agrandi, lecture stricte), javadoc des signes. `ZERO` sans frein.
- `SpaceMotion.step` : règle du doc 04 § 5 point 1 (vitesse signée, frein prioritaire, `|V| ≤ V_max`, non fini → 0).
  Le reste du pas (freinage d'approche, garde-fou) travaille sur le vecteur `v = V · F` : vérifier qu'il reste juste
  pour `V < 0` (le corps visé est alors derrière).
- `SpaceEnvironmentModule` : vitesse négative acceptée partout (`readSync`, `load`, `moving()` = `V ≠ 0` ou rotation,
  envoi à l'arrêt) ; rien d'autre ne change.
- `SpacePilotPad` : capture aussi `HoloControl.mapping("key.jump")` (constante dédiée) ; Saut maintenu → `brake` ;
  gaz ignorés pendant le frein (le calcul est dans `SpaceMotion`, le pad envoie juste le drapeau) ; relâchement /
  fin de capture → frein levé (entrée nulle avant `END`, déjà en place). Pas de saut du joueur pendant le pilotage
  (la capture neutralise le mapping).
- Fenêtre du Controller : vitesse négative affichée avec son signe (ou « arrière »), unités inchangées.
- Javadoc et `package-info` concernés à jour.

## Interdits
- Modifier le framework ou une classe hors des packages Space.
- Changer les signes de rotation (C28) ou les constantes 1,05 / 0,92 / 1 km/tick.

## Critères d'acceptation
- `compileJava` réussit.
- Vérification numérique jetable (scratchpad) : depuis `V = 0`, Reculer → `V` négative croissante en valeur absolue
  jusqu'à `−V_max` ; Avancer depuis `V < 0` → freine jusqu'à 0 puis accélère vers l'avant ; frein maintenu depuis
  `±V_max` → atteint exactement 0 et y reste ; frein + Avancer → freine quand même ; approche d'un corps en marche
  arrière → jamais dedans ; allers-retours réseau / NBT de `SpacePilotInput` et de l'état.
- En jeu : Reculer à l'arrêt fait reculer le monde ; Espace maintenu ramène la vitesse affichée à 0 depuis l'avant
  comme depuis l'arrière ; le joueur ne saute pas.

## Compte rendu attendu
Fichiers modifiés, nouveau format réseau de `SpacePilotInput`, résultats numériques, écarts et pourquoi, scénario en
jeu.
