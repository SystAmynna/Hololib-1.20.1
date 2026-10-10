# T15 — Étoiles de fond identifiées et vitesse de distorsion

**Objectif** : préparer le lot 2 dans l'Environment : chaque étoile de fond connaît son **système** (id, nom) —
nécessaire à la surcouche (T18 : nom au regard) et à la cible du saut (T21 / T22) — et l'Environment porte le réglage
**vitesse de distorsion** (al/s, défaut 0,5 ; édité par le Configurator en T24).

**Dépendances** : T14.

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 3 (borne de la vitesse de distorsion), 4, 5 (`BackgroundStar`) ;
  [`05-synchronisation.md`](../05-synchronisation.md) §§ 2, 5 (bloc chaud, budget).
- `modules/dimension/space/model/SpaceGalaxy.java` (`BackgroundStar`, `backgroundStars`),
  `modules/dimension/space/math/SpaceMotionSettings.java` (record, `withXxx`, `write/read`, `save/load`),
  `modules/dimension/space/SpaceEnvironmentModule.java` (réglages : setters serveur, envoi limité C19).
- `client/render/modules/dimension/space/SpaceSkyRenderer.java` (lecture des étoiles de fond : ne doit rien changer à
  l'affichage).

## Livrables
1. `BackgroundStar` : + `id` (int, varint réseau) + `name` (String, `writeUtf` borné à `MAX_NAME_LENGTH`). Écriture /
   lecture symétriques, javadoc.
2. `SpaceMotionSettings` : + `warpSpeed` (double, al/s, défaut 0,5, borné 0,01 à 1 000 dans `SpaceLimits`) avec
   `withWarpSpeed` ; NBT clé `warp` (absente = défaut) ; réseau dans le bloc chaud.
3. `SpaceEnvironmentModule` : setter serveur public `setWarpSpeed(double)` (valide, assainit, renvoie
   `SpaceEditResult`, marque l'envoi comme les autres réglages) ; accesseur client / serveur `warpSpeed()`. Accesseur
   client pour retrouver une étoile de fond par id (`backgroundStar(int id)` ou équivalent sans allocation).
4. Budget du doc 05 § 5 recalculé (taille d'une `BackgroundStar` avec nom moyen) ; docs 03 / 05 alignés.

## Interdits
- Modifier une classe hors des packages Space.
- Changer l'affichage du ciel.

## Critères d'acceptation
- `compileJava` réussit.
- Vérification jetable : aller-retour réseau et NBT des réglages et des étoiles (avec et sans `warp` dans le tag).
- En jeu : ciel inchangé ; ancienne sauvegarde rechargée sans erreur (vitesse de distorsion 0,5).

## Compte rendu attendu
Fichiers modifiés, nouveaux formats (octets), budget, écarts.
