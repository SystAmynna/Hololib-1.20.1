# T07f — Approche plus près des corps, taille apparente réelle, LOD des sphères

**Objectif** : demande de l'utilisatrice (D9, 2026-10-09) : « pouvoir m'approcher plus près des planètes
(dépendamment de leur taille), pour vraiment sentir à quel point on est minuscule », et des sphères plus nettes de
près (plus de polygones).

**Dépendances** : T07e.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 4 (projection, α), § 5 points 3 et 4 (freinage, garde-fou,
  plancher : c'est la spécification).
- `modules/dimension/space/math/SpaceMotion.java` (constantes `APPROACH_FACTOR`, garde-fou), `SpaceSkyFrame.java`
  (`angularRadius`, plafond 80°).
- `client/render/modules/dimension/space/SpaceSkyRenderer.java` (maillage polaire 24 × 6, calottes, halo, taille
  minimale, rayons de dessin).
- `modules/dimension/space/SpaceEnvironmentModule.java` (seuils d'affichage qui dépendent de l'altitude, ex.
  `tooFar`), `modules/monde/spacecontroller/` (affichage de l'altitude).

## Livrables
1. **Mouvement** (`SpaceMotion`) : `K` = 2 ; plancher `h_min = 0,01 · R` (constante nommée `MIN_ALTITUDE_FACTOR`) dans
   le freinage d'approche et le garde-fou (doc 04 § 5). Le reste du pas inchangé (déterminisme, référentiel).
2. **Taille apparente** : plus de plafond à 80° (`SpaceSkyFrame.angularRadius` → jusqu'à 90°, `asin(min(1, R/d))`).
   Vérifier le rendu à α → 90° : la calotte devient une demi-sphère qui remplit la moitié du ciel avec un horizon ;
   rien ne doit dépasser le fond ni le plan lointain ; phases correctes ; halo d'étoile (plafond 100° à revoir s'il
   n'a plus de sens).
3. **LOD des sphères** (renderer) : plusieurs maillages polaires précalculés (statiques), choisis selon α (et
   éventuellement la taille à l'écran) : ex. 24 × 6 (petit), 48 × 12, 96 × 24, 192 × 48 (α proche de 90°) ;
   anneaux resserrés vers le bord (silhouette ronde) et assez d'anneaux au centre pour un terminateur (limite
   jour / nuit) net. Pas de saut visible au changement de niveau (choisir des seuils avec marge, ou garder le niveau
   tant que α ne s'en éloigne pas nettement). **Aucune allocation par frame**, coût raisonnable (un seul gros corps
   à la fois en pratique).
4. Revoir les seuils qui supposaient une altitude ≥ quelques rayons (ex. correction / saut de l'affichage, défaut de
   l'altitude affichée) et la javadoc.

## Interdits
- Modifier le framework ou une classe hors des packages Space.
- Changer les signes, la règle des gaz / du frein, le référentiel (D7, D8), le lissage client (T07e) au-delà des
  seuils liés à l'altitude.

## Critères d'acceptation
- `compileJava` réussit.
- Vérification numérique jetable : approche frontale de la Terre et de la Lune à `V_max` puis à vitesse modérée →
  altitude finale ≥ plancher, jamais dessous, et atteinte en un temps raisonnable (donner les durées) ; marche
  arrière idem ; Soleil idem ; référentiel Soleil loin de tout : trajectoire inchangée tant qu'on n'entre pas dans
  la nouvelle zone de freinage.
- En jeu : descendre à ≈ 64 km de la Terre → elle remplit la moitié du ciel, horizon net et rond, sphère lisse ;
  idem Lune à ≈ 17 km ; aucun saut au changement de LOD ; FPS sans chute notable.

## Compte rendu attendu
Fichiers modifiés, constantes, résultats numériques (altitudes et durées), niveaux de LOD et seuils, coût en sommets,
écarts, scénario en jeu.
