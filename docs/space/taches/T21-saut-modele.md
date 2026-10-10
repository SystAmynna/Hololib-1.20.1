# T21 — Saut interstellaire : modèle et serveur (Environment)

**Objectif** : (D14, D10) l'Environment sait faire un **saut** vers un autre système : charge de 3 s avec
alignement en douceur de l'axe avant sur l'étoile cible, voyage en distorsion dont la durée dépend de la distance et de
la vitesse de distorsion, arrivée au bord du système cible face à l'étoile. Déterministe, rejoué par les clients,
sauvegardé. Ni rendu ni contrôle du pad ici (T22, T23).

**Dépendances** : T15 (T16 conseillée pour tester avec les vrais systèmes).

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 2, 4, 5, **8** (spécification du saut) ;
  [`05-synchronisation.md`](../05-synchronisation.md) §§ 2, 3 (bloc chaud, `INPUT`, saut chez le client) ;
  [`03-modele-donnees.md`](../03-modele-donnees.md) § 4 (clé `jump`) ; [`decisions.md`](../decisions.md) D10, C22,
  C39–C43.
- `modules/dimension/space/math/` : `SpaceMotion` (`step`, frein), `SpacePilotInput` (format, drapeaux),
  `SpaceFrameState`, `SpacePose`, `SpaceRotations` (slerp / rotations en `double` existants ?).
- `modules/dimension/space/SpaceEnvironmentModule.java` : `serverTick`, `pilotInput`, `setCurrentSystem` (révision,
  pose par défaut), `writeSync` / `readSync` (bloc chaud), rejeu client et historique (T07e), `save` / `load`.

## Livrables
1. `math/SpaceJump` : état immuable ou mutable documenté (phase `NONE` / `CHARGE` / `TRAVEL`, id cible, ticks
   écoulés, durée du voyage, `Q₀` au début de la charge, positions galactiques d'origine et de cible) ; `write/read`,
   `save/load` ; fonctions pures (`StrictMath`) : orientation de la charge au tick `n` (§ 8), durée
   `N = max(20, ceil(20 · D / v_w))`, pose d'arrivée (D10, C42).
2. `SpacePilotInput` : drapeau `FLAG_JUMP` + id de cible (varint, présent si le drapeau est levé) ; `BYTES` remplacé
   par une taille variable documentée ; `of(...)` et égalité à jour (deux entrées avec cibles différentes diffèrent).
3. Intégration au pas (`SpaceMotion.step` ou étape voisine appelée au même endroit côté serveur **et** client) :
   - `NONE` + drapeau saut + cible valide (système existant ≠ courant) → `CHARGE` ;
   - `CHARGE` : alignement 40 ticks, frein, gaz / roulis / joystick ignorés ; drapeau perdu → `NONE` ; 60 ticks →
     `TRAVEL` (le pilote peut relâcher Sneak à partir de là) ;
   - `TRAVEL` : pose figée ; au bout de `N` ticks, **serveur seulement** : système courant ← cible (révision
     incrémentée, bloc froid), pose d'arrivée, `V = 0`, référentiel = étoile principale, saut → `NONE`.
   - un pilote libéré pendant `CHARGE` annule ; pendant `TRAVEL` ne change rien (C40).
4. Sync : saut dans le bloc chaud (§ 2 du doc 05) ; politique d'envoi (début, annulation, voyage, arrivée). Client :
   rejeu de la charge identique au serveur ; en `TRAVEL` le compte s'arrête à `N` en attendant le bloc froid ; la
   prédiction du pilote (T07e) applique aussi le saut.
5. Sauvegarde : clé `jump` (voyage en cours repris, C43) ; recalage C37 inactif pendant un saut.
6. API publique en lecture pour les renderers et le Controller : `jump()` (phase, cible, progression `0..1` de la
   charge et du voyage avec `partialTick`, temps restant en s), `warpSpeed()`.
7. Docs 03 / 04 § 8 / 05 alignés sur le code (formats exacts, octets).

## Interdits
- Modifier une classe hors des packages Space.
- Casser le déterminisme : aucun `Math.*` trigonométrique, aucun `float` dans le pas, même suite d'opérations serveur
  et client.

## Critères d'acceptation
- `compileJava` réussit.
- Banc jetable (sans jeu) : charge depuis une orientation à 170° de la cible → aligné à 1e-9 au tick 40, identique bit
  à bit serveur / rejeu ; annulation au tick 30 → `NONE`, orientation gardée ; voyage Sol → Alpha du Centaure à
  0,5 al/s = 175 ticks (4,37 / 0,5 × 20) ; arrivée : distance à l'étoile = `r_a`, axe avant vers l'étoile à 1e-9,
  `V = 0`, référentiel = étoile principale ; aller-retour NBT et réseau d'un saut en cours.
- En jeu (sans T22 : simuler par une commande de test jetable non committée, ou attendre T22) : rien.

## Compte rendu attendu
Fichiers, formats (octets), résultats du banc, points d'attention pour T22 / T23 (API de lecture), écarts.
