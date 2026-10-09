# T07 — Captor de pilotage

**Objectif** : compléter `SpacePilotPad` pour que les contrôles du pilote pilotent le monde : gaz (avancer /
reculer), roulis (gauche / droite), joystick au regard borné dans un cône, envoi des entrées au serveur et prédiction
locale.

**Dépendances** : T06.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 5, 6 ; [`05-synchronisation.md`](../05-synchronisation.md) §§ 2
  (pilote local), 3.
- `holo/capture/HoloCaptor.java`, `HoloCapture.java` (`yaw()`, `pitch()`, `setLook`, `release`, `frameSeconds`),
  `HoloControl.java` (`mapping`), `holo/widget/control/HoloDirections.java` (constantes des 4 mappings).
- `client/capture/HoloControlCapture.java` (verrou de rotation, neutralisation des mappings, `updateActivity`,
  `Context.setLook`).
- `demo/DemoPad.java` (envoi toutes les 2 ticks, envoi final, prédiction).
- `modules/camera/quickaction/QuickActionWheelWindow.java` (`gazeBeyond`, angle entre deux regards).

## Livrables
- `modules/monde/spacecontroller/SpacePilotPad.java` (complété).
- Si besoin d'un accès client (joueur local) : backend dans `SpaceControllerModule` +
  `client/modules/monde/spacecontroller/SpaceControllerClient.java` (+ `package-info.java`).

## Spécification
- `captures` : défaut de `HoloPointerInput` (`MOUSE_MOVE`, Échap, Entrée) **plus** `HoloDirections.UP/DOWN/LEFT/RIGHT`
  (ces `mapping("key.forward")`... suivent les touches choisies par le joueur). Redéfinir `onPress` / `onRelease` pour
  tenir l'état des 4 touches, et appeler `HoloPointerInput.super.onPress` pour Échap / Entrée. Ne **pas** redéfinir
  `intercepts` (règle des traits).
- `onMouseMove(ctx, dYaw, dPitch)` : nouveau regard = `ctx.yaw() + dYaw`, `ctx.pitch() + dPitch`, borné au cône du doc
  04 § 6 autour de l'axe avant de l'Environment (miroir client : `SpaceEnvironmentModule.client()`), puis
  `ctx.setLook`. À la prise de capture, ramener le regard dans le cône en douceur (quelques frames).
- Entrée quantifiée (`SpacePilotInput`) : gaz et roulis depuis les touches, taux depuis l'écart (doc 04 § 6).
- Envoi : à chaque tick (`tick()` du widget), si l'entrée a changé depuis le dernier envoi et ≥ 2 ticks écoulés →
  `INPUT` ; battement toutes les 20 ticks ; envoi d'une entrée nulle avant `END`.
  **Déjà en place depuis T06** dans `SpacePilotPad` : battement (entrée nulle toutes les 20 ticks), `sendInput`,
  `ticksSinceSend()`, et les points d'extension `protected` vides : `capturesPilotControl`, `onPilotPress` /
  `onPilotRelease`, `onMouseMove`, `onPilotStart` / `onPilotEnd`, `pilotTick()`, `currentInput()`. Les remplir plutôt
  que de réécrire le pad.
- Prédiction : `SpaceEnvironmentModule.client().predictPilotInput(input)` à chaque changement.
- Fin de capture : Échap, Entrée, fenêtre plus routée (fin automatique du framework), autre pilote dans l'état (T06).
- Vérifier en jeu les **sens** (doc 04 § 5, point 2) et corriger le signe dans `SpaceMotion` (T02) si besoin : c'est
  la seule modification autorisée hors de ce package pour cette tâche, à signaler.

## Interdits
- Lire les touches en GLFW ou via `KeyMapping.isDown` : passer par la capture. Modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (Environment + Controller installés) : clic sur la zone → la caméra se tourne vers l'avant du monde et reste
  dans le cône ; avancer (quelle que soit sa touche, tester en la changeant dans les options) accélère, reculer
  ralentit, le joueur ne marche pas ; gauche / droite font rouler le ciel ; regarder à droite fait tourner le ciel en
  continu, revenir au centre l'arrête ; Échap rend la main et la caméra redevient libre.
- Approche d'une planète : la vitesse vers elle tend vers 0, on ne la touche jamais ; s'en éloigner rend la vitesse.
- `runClient2` : le second joueur voit le ciel bouger de façon fluide (dead reckoning), sans à-coups notables.

## Compte rendu attendu
Fichiers ; signes corrigés éventuels ; réglages (cône, zone morte, taux) ; ce qui a été vérifié en jeu.
