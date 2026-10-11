# T35 — Distorsion redessinée

**Objectif** : (D23) remplacer l'effet provisoire du lot 2 (T23) par la séquence demandée par l'utilisatrice, vue par
tous les joueurs de la dimension :
1. **Départ** (la charge de 3 s est validée) : bruit de moteur (son : T36) ; toutes les étoiles du fond **convergent**
   visuellement vers la cible en **1 seconde** ; quand elles l'atteignent, **flash** : la distorsion commence.
2. **Pendant** : la cible est une **grosse source lumineuse** lointaine avec plusieurs **halos** et **flares** (deux
   étoiles à **4 branches**, un peu floues, superposées, qui **tournent sur elles-mêmes en sens opposés**) ; des
   **particules de vitesse blanches** (comme les traînées actuelles) ; le ciel prend un **dégradé arc-en-ciel** de la
   destination jusqu'à l'arrière (spectre étiré autour du vaisseau, comme au-delà de la vitesse de la lumière).
3. **Arrivée** : **flash** (toute la skybox blanche), puis le blanc s'estompe sur la destination.

**Dépendances** : T31 (fond étoilé à faire converger).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 4, 8 ; [`decisions.md`](../decisions.md) C57, C58, C64, D23, C73.
- `client/render/modules/dimension/space/SpaceWarpEffect.java`, `SpaceSkyRenderer.java` (étoiles de fond, fond
  procédural T31, `fogColor`) ; `math/SpaceJump.java` (`MIN_TRAVEL_TICKS`, `travelTicks`) ;
  `SpaceEnvironmentModule` (`jumpAt`, `jumpTravelProgress`, `displayTicks`).

## Livrables
1. Chronologie (C73) : convergence = 20 premiers ticks de `TRAVEL` (purement visuelle), flash d'entrée à la fin ;
   `MIN_TRAVEL_TICKS` porté à 40 (déterministe, doc 04 § 8 et tests du saut mis à jour) ; flash d'arrivée puis fondu
   (≈ 10–20 ticks après l'arrivée, chronométré comme C64). Expose aux sons (T36) les instants clés (phase visuelle
   courante, en lecture seule).
2. Convergence : chaque étoile (systèmes + fond procédural) glisse sur le grand cercle vers la direction de la cible,
   avec une accélération ; dessinée par le ciel à partir d'une fraction fournie par l'effet (aucune allocation).
3. Voyage : dégradé arc-en-ciel sur la sphère du fond (violet / bleu vers la destination → rouge vers l'arrière, angle
   paramétrable), source lumineuse (noyau + halos concentriques additifs) et deux flares à 4 branches contra-rotatifs ;
   particules blanches défilant vers l'arrière ; brouillard accordé au dégradé (hors fluides, C23).
4. Effet isolé dans un sous-package `client/render/modules/dimension/space/warp/` (plusieurs classes permises), plus
   « provisoire » ; surcouche Space Knowledge et repérage du saut coupés comme aujourd'hui.
5. Docs 02 § 5, 04 § 8 ; C64 remplacé par C73 dans `decisions.md`.

## Interdits
- Modifier une classe hors des packages Space ; shader ajouté au framework ; allocation par frame.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : après la charge, les étoiles se rassemblent sur la cible en 1 s, flash, voyage arc-en-ciel avec la source
  lumineuse et ses flares tournants, particules blanches ; arrivée : ciel blanc puis apparition du nouveau système ; un
  second joueur LAN voit la même chose ; sous l'eau, brouillard vanilla ; FPS stables.

## Compte rendu attendu
Fichiers, durées, paramètres visuels exposés, écarts, réglages à juger en jeu.
