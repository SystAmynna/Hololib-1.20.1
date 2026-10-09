# T07d — Ciblage distance × regard et affichage sans saccade

**Objectif** : demande de l'utilisatrice (D8, 2026-10-09). (1) Le référentiel (D7) se choisit par un score
proximité × regard, avec délai, cooldown et fondu ; (2) supprimer la saccade des corps à l'affichage (L10).
« Le tout de manière la plus smooth possible. »

**Dépendances** : T07c (modifie ses fichiers, tous dans les packages Space).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 5 (point 0 réécrit : c'est la spécification), § 6 (cône, écart
  du regard), § 4 (direction et rayon angulaire d'un corps).
- [`05-synchronisation.md`](../05-synchronisation.md) (bloc chaud, rejeu, prédiction, affichage interpolé).
- `modules/dimension/space/math/SpaceMotion.java`, `SpacePilotInput.java`, `SpaceSkyFrame.java`,
  `SpaceMotionSettings.java` (axe avant, axes du monde).
- `modules/dimension/space/SpaceEnvironmentModule.java` (état du référentiel, `pose(partialTick)`, rejeu,
  prédiction, NBT, bloc chaud).
- `modules/monde/spacecontroller/SpacePilotPad.java` (`deviationYaw/Pitch`, construction de l'entrée).

## Livrables
1. **Visée dans l'entrée** : `SpacePilotInput` porte l'écart du regard au cercle avant (`aimYaw`, `aimPitch`,
   quantifiés sur le cône, octets signés), rempli par le pad à partir de l'écart réel (pas du taux, nul dans la zone
   morte) ; `ZERO` / sans pilote = visée sur l'axe avant. Envoi : l'entrée change déjà avec le regard (au plus 1 / 2
   ticks) ; ne pas renvoyer pour une variation de visée sous un pas de quantification.
2. **Score et bascule** dans `SpaceMotion` (doc 04 § 5 point 0) : direction de visée dans le repère du système à
   partir de la pose, de l'axe avant (`settings`) et de l'écart ; score `P · G` ; délai, cooldown, fondu. Petit état
   mutable de référentiel (ex. `SpaceFrameState` : ref, ref précédent, ticks depuis bascule, candidat, ticks de
   candidature) passé à `step` et mis à jour par lui ; `write/read/save/load` ; **aucune allocation** par pas.
   Constantes nommées et documentées.
3. **Fondu** : `Δp_frame` mélangé de l'ancien vers le nouveau référentiel (smoothstep sur 40 ticks) ; les deux
   positions viennent des tableaux de travail existants.
4. **Affichage sans saccade (L10)** : `pose(partialTick)` interpole `s − P_frame` entre les deux derniers ticks puis
   rajoute `P_frame` au temps d'affichage (positions des corps au temps d'affichage, comme `bodyPositions`), fondu
   compris ; même chose pour la correction douce. Aucune allocation par frame.
5. **Environment** : l'état de référentiel remplace `referenceId` partout (serveur, rejeu, prédiction, NBT tolérant,
   bloc chaud) ; une bascule déclenche un envoi (≤ 1 / 4 ticks). API inchangée (`referenceBodyId()`,
   `referenceBody()`, `referenceAltitude()`), plus `referenceBlend()` ∈ [0, 1] si utile à l'affichage.
6. Controller : inchangé sauf si nécessaire (il affiche déjà le référentiel).
7. Javadoc / `package-info` à jour.

## Interdits
- Modifier le framework ou une classe hors des packages Space.
- Changer les signes (C28), la règle des gaz / du frein (D6), le garde-fou (C16, C30).

## Critères d'acceptation
- `compileJava` réussit.
- Vérification numérique jetable (scratchpad), Sol, échelle par défaut :
  - très près de la Terre (20 rayons), visée sur le Soleil : la Terre reste le référentiel ;
  - loin de tout (ex. entre Mars et Jupiter), visée sur Jupiter puis sur Mars : le référentiel suit la visée, après
    ≈ 0,5 s, et pas plus d'une bascule par 3 s ;
  - Terre et Lune presque alignées vues de loin, visée qui oscille entre les deux : aucune bascule en rafale ;
  - fondu : la vitesse absolue du monde (`|s(t+1) − s(t)|`) varie continûment pendant une bascule (pas de saut
    supérieur à ce qu'impose le smoothstep) ;
  - à `V = 0` près de la Terre, distance constante (comme T07c) ;
  - affichage : `pose(partialTick)` près de la Lune → l'écart à la position exacte « relative au référentiel » est
    nul (au bit près ou < 1e-6 relatif) à tout `partialTick` ;
  - déterminisme bit à bit (deux instances, 1000 pas) ; allers-retours réseau / NBT de l'entrée et de l'état.
- En jeu : couper les gaz près d'une planète ou d'une lune → elle reste parfaitement immobile à l'écran, sans
  saccade ; loin de tout, viser un corps en fait le référentiel en douceur ; la fenêtre ne clignote jamais.

## Compte rendu attendu
Fichiers modifiés, formats (entrée, bloc chaud, NBT), signature de `step`, résultats numériques, écarts et pourquoi,
réglages proposés à l'utilisatrice, scénario en jeu.
