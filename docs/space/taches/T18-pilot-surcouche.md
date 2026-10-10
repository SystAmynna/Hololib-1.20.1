# T18 — Space Pilot : surcouche du ciel

**Objectif** : la surcouche informative de Space Pilot (D14, D15) : anneaux autour des corps du système courant,
orbites, anneaux différents autour des étoiles des autres systèmes, nom de l'objet regardé ; visible du **joueur
local seulement**, avec ou sans Space Controller, selon les réglages du module (T17).

**Dépendances** : T17.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 2, 3, 4 (projection du ciel), **9** (spécification de la
  surcouche) ; [`decisions.md`](../decisions.md) C46, C47, D15.
- `client/render/modules/dimension/space/SpaceSkyRenderer.java` (projection, `pose(partialTick)`, `bodyPositions`,
  étoiles de fond, rayons de dessin 105 → 50 : la surcouche doit passer **devant** les corps et le fond, dans la même
  passe du ciel) ; `client/render/sky/SkyRenderer.java`, `SkyRenderContext.java`, `SkyRenderPass.java` (profondeur
  partagée, ordre d'enregistrement : L7).
- `client/render/edit/HoloGizmoRenderer.java` (anneaux / segments en quads, privés : copier),
  `client/render/holo/BufferHoloCanvas.java` (`text`, `font.drawInBatch`), `SpaceMapCubeRenderer` (texte sans
  allocation, `RenderType` local).
- `client/modules/camera/spacepilot/SpacePilotClient` (T17).

## Livrables
1. `client/render/modules/camera/spacepilot/SpacePilotSkyRenderer implements SkyRenderer` (+ `SpacePilotRenderers`
   appelé depuis `ModuleRenderers`, + `package-info.java`), enregistré **après** `SpaceSkyRenderer` (vérifier l'ordre
   dans `HoloRenderers` ; sinon vider la profondeur n'est pas permis : dessiner sans test de profondeur via un
   `RenderType` local, et le dire).
2. Ne dessine rien si : pas d'Environment dans la dimension, pas de module Space Pilot chez le joueur local, `enabled`
   faux, ou distorsion en cours (T21 : à brancher quand la phase existe, sinon noter le point).
3. **Corps** (`bodies`) : anneau simple autour de chaque corps visible (règle de visibilité § 9 / C47 : étoiles et
   planètes toujours, lune près de son parent, objet mineur près de lui-même, référentiel toujours ; fondu), taille et
   épaisseur constantes à l'écran (calcul depuis le FOV et la hauteur de la fenêtre, sans allocation).
4. **Orbites** (`orbits`) : ellipse de chaque corps visible autour de la position actuelle de son parent (§ 9), en
   pointillé ou trait fin, couleur atténuée du corps ; même règle de visibilité que l'anneau.
5. **Étoiles** (`stars`) : double anneau fin (C46) autour de chaque étoile de fond.
6. **Noms** (`names`) : pour l'objet (corps visible ou étoile) le plus proche du regard et à moins de `nameAngle`,
   nom écrit à côté de son anneau, face caméra, lisible sur fond noir. Nom d'étoile = nom du système (T15).
7. Aucune allocation par frame (tableaux statiques d'échantillons, vecteurs réutilisés), état GL restauré.

## Interdits
- Modifier une classe hors des packages Space ; abonner le renderer à un événement Forge.
- Changer le rendu de `SpaceSkyRenderer` (seulement lire ses données, ou en factoriser une primitive privée).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : près de la Terre, anneaux sur Soleil, planètes, Lune ; Phobos / Déimos absents jusqu'à s'approcher de Mars ;
  Pluton absent sauf de près ; étoiles de fond doublement entourées ; regarder Jupiter → « Jupiter » ; chaque
  interrupteur de Config coupe sa partie, l'angle change la tolérance ; Quick Action OFF coupe tout ; un second
  joueur (même en inspection) ne voit rien ; FPS sans chute notable avec Sol complet (34 corps).

## Compte rendu attendu
Fichiers, ordre d'enregistrement vérifié, coût en sommets (pire cas), seuils réels, écarts, scénario.
