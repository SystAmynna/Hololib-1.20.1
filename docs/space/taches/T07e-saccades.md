# T07e — Saccades des corps à l'approche (diagnostic + lissage client)

**Objectif** : signalé par l'utilisatrice en jeu (2026-10-09, après T07d) : « le déplacement de certains corps
célestes saccade à l'approche ». Trouver la ou les causes **par la mesure**, puis corriger côté client par
prédiction / interpolation / correction douce. Les orbites sont déterministes et calculables par le client à tout
instant : seul l'état du monde (pose, vitesse, entrée, référentiel) vient du serveur.

**Dépendances** : T07d.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 3 à 5 ; [`05-synchronisation.md`](../05-synchronisation.md) en entier
  (envois, rejeu, prédiction du pilote, correction douce, affichage).
- `modules/dimension/space/SpaceEnvironmentModule.java` (côté client : `readSync` / `applyState`, `clientTick`,
  `clientTime()`, rejeu `MAX_REPLAY`, `beginCorrection` et ses seuils `SOFT_*`, `pose(partialTick)`,
  `displayDays`, `bodyPositions`), `math/SpaceMotion.java` (`interpolate`), `math/SpaceFrameState.java`.
- `client/modules/dimension/space/SpaceEnvironmentClient.java` (temps client) ;
  `client/render/modules/dimension/space/SpaceSkyRenderer.java` (comment pose et corps sont lus par frame).
- Sources mappées (jar de CLAUDE.md) : `ClientLevel.setGameTime`, `ClientPacketListener.handleSetTime` (synchro du
  temps vanilla toutes les 20 ticks), `Minecraft.runTick` / `Timer` (partialTick).

## Pistes à vérifier (ne pas supposer : mesurer)
1. **Horloge client** : le `gameTime` du niveau client est recalé par le serveur (`ClientboundSetTimePacket`). Un
   recalage fait sauter `clientTime()` → rejeu de 0 ou 2 pas d'un coup, `stateTime` / `displayDays` sautent d'un
   tick (les corps sautent de leur déplacement relatif d'un tick, énorme à l'échelle par défaut). Piste de correction :
   horloge d'affichage propre au client, avancée d'un tick par `clientTick`, qui rattrape un écart avec le temps du
   niveau **progressivement** (ex. en étirant légèrement `partialTick`) au lieu de sauter.
2. **Corrections serveur** : seuil de saut « 1 % de l'altitude » → à basse altitude presque toute correction devient
   un saut. Revoir : correction exprimée dans le repère du référentiel (`s − p_ref`), absorbée en douceur sur
   plusieurs ticks, saut seulement pour un écart vraiment grand (en angle vu depuis le monde, pas en km).
3. **Écarts de rejeu** : un client non pilote rejoue avec la dernière entrée connue alors que le serveur a changé
   d'entrée entre-temps ; le pilote prédit avec ses entrées envoyées. Mesurer l'ampleur des écarts à chaque envoi.
4. **Changement de référentiel / fondu, `snapDisplay`** (L12), passage point ↔ disque, tri par distance (rayons de
   dessin) dans le renderer : vérifier qu'aucun ne produit de saut visible d'une frame à l'autre.
5. Toute autre cause trouvée en lisant le code.

## Livrables
- **Banc de mesure jetable** (scratchpad, hors build) : serveur + client simulés avec les vraies classes (hôtes
  liés par réflexion comme en T07c / T07d), envois au rythme réel (≤ 1 / 4 ticks, 1 / 20 ticks en mouvement),
  latence réseau (ex. 2 à 5 ticks, gigue), recalage du temps toutes les 20 ticks avec dérive, frames à 60 et 144 FPS
  (`partialTick` réalistes), pilote qui change ses entrées, approche de la Terre et de la Lune. Mesure : saut
  angulaire par frame de la direction de chaque corps vu depuis le monde, comparé à la variation « idéale »
  (trajectoire continue) ; même chose pour la pose affichée.
- **Corrections** dans les packages Space uniquement (Environment côté client, backend client, `SpaceMotion` si
  nécessaire sans changer le résultat de `step`). Aucune allocation par frame, déterminisme de la simulation
  inchangé (le lissage ne touche que l'affichage).
- Compte rendu : causes mesurées (avant / après, chiffres), corrections, réglages.

## Interdits
- Modifier le framework ou une classe hors des packages Space ; changer les règles de mouvement (gaz, frein,
  rotation, garde-fou, référentiel) ou le format réseau sans nécessité démontrée (alors : le signaler).

## Critères d'acceptation
- `compileJava` réussit.
- Banc : après correction, aucun saut angulaire par frame supérieur à 2 × la variation idéale (hors changement de
  système), à basse altitude (100 km au-dessus de la Lune, 500 km au-dessus de la Terre) comme loin.
- En jeu : approche de la Terre et de la Lune, en pilote et en observateur (`runClient2`) : mouvement fluide.
