# T39 — Masse des astres, référentiel par sphère d'influence

**Objectif** : (D29, remplace D8) chaque astre reçoit une **masse** ; le corps de référence (celui dont le vaisseau
garde la vitesse relative et qui l'entraîne) devient l'astre qui a **le plus d'influence gravitationnelle** sur le
vaisseau, au sens des **sphères d'influence** (comme Kerbal Space Program). Le regard ne compte plus. **Aucune gravité
n'est appliquée au vaisseau** : la masse ne sert qu'à choisir le référentiel.

**Dépendances** : aucune (T38 recommandée avant : même `SpaceEnvironmentModule`, fusion plus simple).

## Constat
- `SpaceMotion.score` choisit le référentiel par proximité × regard (D8) : `S_i = P_i · G_i`, bascule si
  `S_c > 1,25 · S_ref` (`SWITCH_MARGIN`) pendant `SWITCH_DELAY` (10) ticks, cooldown `SWITCH_COOLDOWN` (60), fondu
  `BLEND_TICKS` (40), état `SpaceFrameState`.
- `SpaceOrbit` porte sa période : la masse ne change pas les orbites.
- Un seul corps sans parent par système (les compagnes ont pour parent l'étoile principale, doc 03 § 2).
- En gravité brute (`GM / d²`), le Soleil l'emporterait sur la Terre dès ≈ 260 000 km : la sphère d'influence évite ce
  défaut (Terre ≈ 925 000 km, Lune comprise).

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 2–7 ; [`04-mathematiques.md`](../04-mathematiques.md) § 5 point 0 ;
  [`05-synchronisation.md`](../05-synchronisation.md) §§ 2, 5 ; [`decisions.md`](../decisions.md) D7, D8, D29, C83 ;
  [`donnees-systemes.md`](../donnees-systemes.md).
- `model/SpaceBody.java` (record, réseau, NBT tolérant `loadRotation` / `loadTexture`), `model/SpaceLimits.java`,
  `model/SpaceGalaxy.java` (`createDefault`, `addBody`, `updateBody`), `model/SpaceSolRotations.java` (patron d'une
  table par nom pour les anciennes galaxies), `math/SpaceMotion.java` (`score`, `selectReference`, pose par défaut,
  `referenceBodyId`), `math/SpaceFrameState.java`, `math/SpaceCruise.java` (cible : ne pas casser),
  `monde/spaceconfigurator/SpaceConfiguratorWindow.java` (édition d'un corps, action 11).

## Livrables
1. **Modèle** : `SpaceBody.mass` (kg, `double`), bornée dans `SpaceLimits` (finie, `> 0`), NBT `mass`, réseau +8 o par
   corps (bloc froid, système du Configurator). Masse absente (galaxie ancienne) : table par nom pour Sol (comme C83),
   sinon **estimée** à partir du rayon et d'une densité par type (étoile, planète gazeuse / rocheuse selon le rayon,
   lune, objet mineur) ; nouveau corps (`addBody`) : masse estimée. `withMass`.
2. **Données** : masses réelles des 62 corps par défaut dans `donnees-systemes.md` (§ Masses, sources ; exoplanètes :
   masse minimale `M sin i`, le dire) et dans `SpaceGalaxy.createDefault`.
3. **Règle du référentiel** (déterministe, `StrictMath`, sans allocation) :
   - sphère d'influence d'un corps de parent `p` : `r_i = a_i · (m_i / m_p)^(2/5)` (`a_i` = demi-grand axe en km),
     plancher `2 R_i` (petites lunes) ; corps sans parent : sphère infinie ; calculées une fois par système (révision)
     si c'est plus simple ;
   - référentiel = le corps **le plus profond** de la hiérarchie (étoile → planète → lune) dont la sphère contient le
     vaisseau (distance au centre ≤ `r_i`) ; entre deux sœurs qui se recouvrent, la plus forte attraction `m / d²` ;
   - **hystérésis** : on ne quitte la sphère du référentiel qu'au-delà de `1,05 · r` (remplace `SWITCH_MARGIN`) ;
     délai, cooldown et fondu gardés tels quels ; le regard (`aimYaw`, `aimPitch`) n'entre plus dans le choix ;
   - même règle pour la pose par défaut d'un système et un référentiel absent.
4. **Édition** : champ « Masse (kg) » (notation scientifique acceptée) dans l'édition d'un corps du Configurator
   (action 11, validée et bornée par le serveur, révision incrémentée). T42 et T44 reprennent ce champ.
5. Docs 03 §§ 2–7, 04 § 5 point 0 (réécrit), 05 § 5 (budget) ; D8 marquée remplacée par D29 dans `decisions.md` ;
   javadoc de `SpaceMotion` / `SpaceFrameState` à jour.

## Interdits
- Appliquer une gravité au vaisseau ; changer les orbites selon la masse.
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- Banc : rayons de sphère de Sol cohérents (Terre ≈ 0,92 M km, Lune ≈ 66 000 km, Jupiter ≈ 48 M km) ; choix du
  référentiel serveur / client bit à bit ; NBT ancien (sans masse) relu.
- En jeu : posé près de la Lune → Lune ; entre la Terre et la Lune, hors de la sphère de la Lune → Terre ; loin de la
  Terre → Soleil ; tourner le regard ne change rien ; monde du lot 3 chargé avec des masses ; vitesse de croisière
  (T34) toujours juste.

## Compte rendu attendu
Fichiers, format, table des masses et sources, rayons des sphères calculés pour Sol, écarts, scénario en jeu.
