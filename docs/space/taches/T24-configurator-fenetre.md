# T24 — Space Configurator : fenêtre détachée de la carte, vitesse de distorsion

**Objectif** : (D14) séparer le rendu de la **carte** (cube) et de la **fenêtre de configuration** : deux objets
éditables séparément ; la fenêtre peut être **masquée** avec le `world_editor` pour ne garder que la carte. Ajouter le
réglage de la **vitesse de distorsion** (défaut 0,5 al/s).

**Dépendances** : T16 (T15 pour le réglage).

## À lire avant
- [`02-architecture.md`](../02-architecture.md) § 7 ; [`decisions.md`](../decisions.md) C9, C33, C35.
- `modules/monde/spaceconfigurator/SpaceConfiguratorModule.java` (`placeWindow`, appels au sondage du cube, accès de
  la fenêtre, NBT), `SpaceConfiguratorWindow.java` (mode galaxie : lignes de réglages), `SpaceMapCube.java`
  (interfaces d'édition, `opRequired`).
- `holo/edit/HoloEditVisibility.java`, `HoloDisplay` (bouton œil OFF / FOCUS / ON), `demo/DemoWorldPosModule.java`
  (objet `World` éditable d'un marker) ; `.claude/CLAUDE.md` § Edition en jeu (`world_editor`, `opRequired`).
- `SpaceEnvironmentModule.setWarpSpeed` (T15).

## Livrables
1. `SpaceConfiguratorWindow` : `HoloEditTranslation`, `HoloEditRotation`, `HoloEditVisibility`, `opRequired()` vrai,
   `ownRequired()` faux (comme le cube). Le module **ne la replace plus** quand le cube bouge : placement initial
   (devant la base du cube, comme avant) à la création seulement ; ensuite son transform est le sien (sauvegardé et
   synchronisé par le framework). Anciennes sauvegardes : placement initial recalculé une fois.
2. Masquage : avec le `world_editor`, l'œil de la fenêtre la passe à OFF → seul le cube reste (C9 inchangée : rien pour
   les non-ops) ; l'œil du cube, s'il existe, reste indépendant. Vérifier qu'une fenêtre masquée ne bloque pas les
   poignées.
3. Le cube garde ses outils (translation, rotation, échelle) ; recadrage, sélection, modes inchangés.
4. Mode galaxie : ligne « Distorsion (al/s) » (champ texte numérique comme les champs du mode système, C36) qui
   appelle `setWarpSpeed` via une action (op vérifié en premier) ; valeur lue dans l'Environment.
5. Docs 02 § 7 et décisions (C33 / C35 annotées) à jour.

## Interdits
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (op, `world_editor`) : déplacer / tourner le cube → la fenêtre ne bouge pas ; déplacer la fenêtre seule ;
  œil de la fenêtre → OFF : la fenêtre disparaît hors session d'édition, le cube reste éditable ; reconnexion → tout
  à sa place ; « Distorsion » = 2 → le saut Sol → Alpha du Centaure dure ≈ 2,2 s (si T21 / T22 faites).

## Compte rendu attendu
Fichiers, gestion des anciennes sauvegardes, nouvelle action (numéro), écarts, scénario.
