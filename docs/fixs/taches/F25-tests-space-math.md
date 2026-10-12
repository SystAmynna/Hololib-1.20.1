# F25 — Tests : infrastructure JUnit et `space/math`

**Objectif** : mettre en place les tests unitaires du projet (aucun aujourd'hui) et couvrir d'abord le code le plus
sensible et le plus pur : les mathématiques Space, déterministes et partagées par le serveur et les clients. Ils
servent de filet aux refactorisations (F18, F20…). Rapport § 5.6, recommandation 16.

**Lot** : B   **API** : non (`build.gradle` et `src/test` seulement)   **Dépendances** : aucune

## Constat
- `src/test` est vide.
- `modules/dimension/space/math/` est pur (`StrictMath`, `double`) : `SpaceMotion`, `SpaceOrbits`, `SpaceJump`,
  `SpaceSpin`, `SpaceCruise`, `SpaceRotations`, `SpaceUnits`, `SpacePose`, `SpacePilotInput`.
- Les bancs jetables des lots Space (déterminisme bit à bit, Kepler, croisière) existaient seulement dans les
  comptes rendus : `docs/space/suivi.md`.

## À lire avant
- Rapport § 5.6 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 6 ; [`decisions.md`](../decisions.md)
  FC6.
- `build.gradle` (ForgeGradle 6) ; `docs/space/04-mathematiques.md` ; `docs/space/suivi.md` (bancs décrits par T02,
  T28, T31, T32, T34, T38, T39) ; `modules/dimension/space/math/*`, `model/SpaceGalaxy`, `model/SpaceStarField`.

## Zone
`build.gradle` (bloc `dependencies` et `test`), `src/test/java/com/amynna/hololib/**`.

## Livrables
1. JUnit 5 (`org.junit.jupiter:junit-jupiter`, version fixée) et `test { useJUnitPlatform() }`.
   - Vérifier que les classes Minecraft utilisées par les tests se chargent (les tests ne lancent pas le jeu).
   - Si une classe vanilla ne se charge pas hors du jeu, la contourner, ou limiter le test, et le noter.
2. Tests :
   - **déterminisme** : `SpaceMotion.step` sur N pas avec des entrées fixes donne une pose de référence (valeurs
     consignées dans le test ; bit à bit) ;
   - **Kepler** : position sur orbite à e = 0, 0,5, 0,95 (résidu < 1e-12), période ;
   - **sphère d'influence** : référentiel attendu près de la Lune, entre la Terre et la Lune, loin (D29) ;
   - **saut** : `travelTicks` (minimum 40), arrivée à 10 rayons ;
   - **croisière** : `v*` positive et décroissante à l'approche, aucun arrêt sous la surface (banc 1D de T34
     réduit) ;
   - **spin** : orientation à t = 0 et après une période ;
   - **horloge** : époque reposée sans saut des astres (D28) ;
   - **fond étoilé** : même graine → mêmes étoiles.
3. `./gradlew test` documenté dans `.claude/CLAUDE.md` (section Build) et dans `docs/fixs/README.md` (déjà prévu
   dans le prompt type).

## Interdits
- Modifier le code de production pour le rendre testable, sauf rendre package-private ce qui est privé, et
  seulement si c'est indispensable (le noter).

## Critères d'acceptation
- `./gradlew test` réussit ; `compileJava` réussit ; `runClient` démarre toujours.
- Les valeurs de référence sont justifiées (calcul à la main, ou valeur actuelle consignée comme référence de
  non-régression : le dire).

## Compte rendu attendu
Dépendances ajoutées, tests (nom et ce qu'ils vérifient), temps d'exécution, difficultés de chargement.
