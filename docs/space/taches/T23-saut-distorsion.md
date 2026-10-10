# T23 — Saut interstellaire : rendu de la distorsion (provisoire)

**Objectif** : (D14) pendant le voyage, le ciel **vire au blanc** avec des **traînées de vitesse** dans la direction
de l'étoile cible, pour tous les joueurs de la dimension. L'utilisatrice a prévenu : **cet effet sera modifié plus
tard** — il doit être isolé, simple et facile à remplacer.

**Dépendances** : T21.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 4, 8 ; [`decisions.md`](../decisions.md) C23, D1 (`fogColor`).
- `client/render/modules/dimension/space/SpaceSkyRenderer.java` (fond opaque, `fogColor`, ordre de dessin) ;
  `client/render/sky/SkyRenderer.java`, `SkyRenderContext.java`.
- `SpaceEnvironmentModule.jump()` (T21 : phase, progression, direction de la cible).

## Livrables
1. `client/render/modules/dimension/space/SpaceWarpEffect` : classe **seule responsable** de l'effet (javadoc :
   « provisoire, a remplacer »), appelée par `SpaceSkyRenderer` quand la phase est `TRAVEL` (et en fondu sur les
   ~10 premiers et derniers ticks : entrée et sortie de distorsion) à la place du fond, des étoiles et des corps.
2. Effet : fond blanc (légèrement bleuté), traînées = segments fins radiaux qui partent du point de fuite (direction
   de la cible), s'allongent et défilent vers l'arrière ; graine fixe, positions précalculées (aucune allocation par
   frame) ; vitesse de défilement liée au temps d'affichage du client (pas au `gameTime` vanilla, C20).
3. `fogColor` : brouillard et fond d'écran blancs pendant le voyage (hors fluides, comme C23).
4. La surcouche de Space Pilot (T18) et le HUD (T20) se coupent ou s'adaptent pendant le voyage (brancher ce que T18 /
   T20 ont laissé en attente).

## Interdits
- Modifier une classe hors des packages Space ; disperser l'effet dans plusieurs renderers.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : au déclenchement du voyage, fondu vers le blanc, traînées vers l'étoile cible ; horizon et terrain lointain
  blancs ; à l'arrivée, fondu vers le ciel du nouveau système ; un second joueur voit la même chose ; FPS stables ;
  sous l'eau, brouillard vanilla conservé.

## Compte rendu attendu
Fichiers, paramètres de l'effet (nombre de traînées, durées de fondu), points à revoir quand l'effet sera redessiné.
