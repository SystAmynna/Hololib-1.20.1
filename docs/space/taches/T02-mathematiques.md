# T02 — Mathématiques pures

**Objectif** : écrire les calculs déterministes communs : unités, orbites de Kepler, pose du monde, pas de
mouvement, entrée du pilote, transformation système → ciel.

**Dépendances** : T01.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 1 à 6 (spécification).
- [`05-synchronisation.md`](../05-synchronisation.md) § 2 (« dead reckoning » : la même fonction tourne des deux côtés).
- `holo/edit/HoloEditMath.java` (conventions de rotation, style sans allocation, `rotation`, `setRotation`).
- `holo/object/HoloTransform.java` (`setLookingAtOrigin` : formule de direction).
- Le modèle écrit en T01 (`modules/dimension/space/model/`).

## Livrables
`modules/dimension/space/math/` : `package-info.java`, `SpaceUnits`, `SpaceOrbits`, `SpacePose`, `SpacePilotInput`,
`SpaceMotion` (+ ses réglages : record `SpaceMotionSettings` ou champs de l'Environment, au choix, documenté),
`SpaceSkyFrame`.

## Spécification
- `SpaceUnits` : constantes (`AU_KM` = 149 597 870,7, identique à la constante privée de `SpaceGalaxy` ; gérer un
  vecteur nul dans `galacticDirection` : deux systèmes peuvent se superposer), secondes par jour..., conversion temps de jeu → jours simulés avec `frac` pour
  la précision (doc 04 § 3).
- `SpaceOrbits.positions(system, tDays, Vector3d[] dest)` : positions absolues (km, repère système) de tous les corps,
  parent avant enfant, sans allocation (tableau `dest` fourni, de capacité `SpaceLimits.MAX_BODIES`).
  Newton borné (6 itérations) ; garantir un résultat fini pour `e ≤ 0,95`.
- `SpacePose` : `Vector3d position`, `Quaterniond orientation`, `set`, `copy`, `lerp/slerp(a, b, t, dest)` pour
  l'interpolation, `write/read` réseau, `save/load` NBT.
- `SpacePilotInput` : record `(byte throttle, byte roll, byte yaw, byte pitch)` quantifié (`/127` pour les taux),
  `ZERO`, `sanitize`, `write/read`.
- `SpaceMotion.step(...)` : un pas de tick exactement comme le doc 04 § 5 (gaz, rotation composée à droite,
  freinage d'approche `K = 10`, garde-fou de la demi-altitude). Pure : mêmes entrées → mêmes sorties, bit à bit
  (pas de `Math.random`, pas d'horloge, pas d'ordre dépendant d'un `HashMap`).
- `SpaceMotion.defaultPose(system, tDays, dest)` et `nearestBody(...)` (doc 04 § 5).
- `SpaceSkyFrame` : construit depuis une pose (`set(pose)`), puis `direction(pointKm, Vector3f dest) → double distance`
  et `galacticDirection(from, to, dest)` (doc 04 § 2) ; aucune allocation.
- Constantes des réglages par défaut (`V_max`, `turn`, `rollSpeed`, échelle de temps, axe avant) au même endroit.

## Interdits
- Classes client, accès au monde. Allocation dans `step`, `positions`, `direction` (appelées par tick ou par frame).

## Critères d'acceptation
- `compileJava` réussit.
- Vérification par le raisonnement documentée dans le compte rendu : orbite circulaire (`e = 0`) → distance constante
  `a` ; `e = 0,5` à `M = 0` → périapse `a(1−e)` sur `+X` ; une approche frontale à `V_max` d'une planète n'atteint
  jamais `d ≤ R` (l'altitude est au moins divisée par deux par tick).
- Optionnel (non bloquant) : une commande de débogage n'est **pas** demandée ; ne pas ajouter de dépendance de test
  au build.

## Compte rendu attendu
Fichiers ; signatures publiques ; conventions de signe retenues pour yaw / pitch / roll (à vérifier en jeu en T07) ;
écarts au doc 04.
