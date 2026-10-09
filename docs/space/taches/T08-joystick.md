# T08 — Mini-interface du joystick

**Objectif** : afficher au pilote les deux cercles reliés : cercle avant fixe dans le monde (`Local` relatif), cercle
de visée au centre de l'écran (`Local` follow), et le segment qui les relie, via la famille vue (caméra).

**Dépendances** : T07.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 6 (placement, segment par inverse de la pose).
- `holo/object/HoloAnchor.java` (`Local(follow)` : `x` = yaw, `y` = pitch, `z` = distance 0,1 à 16).
- `client/render/holo/HoloViewBridge.java`, `HoloPlacement.java` (`onSphere`), `HoloRouting.java` (routage d'une
  mémoire de position, `shown()`).
- `modules/camera/editor/EditorReference.java` + `client/render/modules/camera/editor/EditorReferenceRenderer.java`
  (objet `Local` follow dessiné en quads : cercle de 48 segments).
- `client/render/edit/HoloGizmoRenderer.java` (`segment`, `ring` privés : copier si utile).

## Livrables
- `modules/monde/spacecontroller/SpaceJoystickForward.java`, `SpaceJoystickAim.java`.
- `client/render/modules/monde/spacecontroller/SpaceJoystickRenderer(s).java`, `SpaceControllerRenderers.java`,
  `package-info.java` ; appel dans `client/render/modules/ModuleRenderers.java`.
- Backend client (pilote local) si pas déjà fait en T07.

## Spécification
- Deux objets du Controller, créés dans `onLoad` (deux côtés), `PUBLIC`, non éditables, `flat() = true`,
  `boundingRadius` petit. `shown()` : vrai seulement si le joueur local est le pilote de ce Controller (backend).
- Cercle avant : `Local(false)`, transform `(ψ_F, θ_F, 2)` d'après l'axe avant de l'Environment (le client le met à
  jour localement dans `clientTick` ; le serveur n'a pas besoin de le déplacer).
- Cercle de visée : `Local(true)`, `(0, 0, 2)`.
- Renderer du cercle avant : anneau (≈ 0,06 bloc de rayon à 2 blocs), et **segment** jusqu'au centre de l'écran par
  l'inverse de `pose.last().pose()` (matrice réutilisée), seulement si `ctx.family() == VIEW`. Renderer du cercle de
  visée : anneau plus petit. Couleur : accent du mod (`ConfigWindow.ACCENT`) ; l'anneau avant s'éclaircit quand
  l'écart dépasse la zone morte (lecture de l'état du pad via le backend, sans logique dans le renderer).
- En F5 : les objets passent dans la famille monde (comportement du routage) : ne pas dessiner le segment.

## Interdits
- Un `ViewRenderer` bas niveau (les objets `Local` suffisent) ; allocation par frame ; modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : pendant le pilotage, le cercle avant reste fixe sur l'avant du monde, le cercle de visée suit le centre de
  l'écran, le segment les relie et ne dépasse jamais le cône ; rien n'est affiché hors pilotage, ni chez un autre
  joueur (`runClient2`).

## Compte rendu attendu
Fichiers ; méthode du segment retenue ; vérifications en jeu.

## Ajout validé (D5, 2026-10-09)
Interrupteur « Pilotage : tous / ops » (`HoloWidgetSwitch`) dans la fenêtre du Controller : bascule l'accès de la fenêtre
entre `HoloAccess.EVERYONE` et réservé aux ops. Seul un op peut le changer (action vérifiée côté serveur) ; état
sauvegardé et synchronisé ; `EVERYONE` par défaut. Si le contrôle « ops seulement » ne peut pas s'exprimer avec
`HoloAccess`, le vérifier dans `handleAction` (BEGIN refusé aux non-ops) — pas de modification de `HoloAccess`.

## API fournie par T07
`SpacePilotPad` (via `SpaceControllerModule.window().pad()`) : `piloting()`, `confirmed()`, `recentering()`,
`deviationYaw()` / `deviationPitch()` (degrés ; yaw `+` = à droite, pitch `+` = en dessous, convention Minecraft),
`deflection()` ∈ [0, 1], `beyondDeadZone()`, constantes `CONE_ANGLE` (30°) et `DEAD_ZONE` (2°). Axe avant :
`SpaceEnvironmentModule.client().settings().forwardYaw()/forwardPitch()`. Le backend « pilote local » pour `shown()`
reste à faire ici.
