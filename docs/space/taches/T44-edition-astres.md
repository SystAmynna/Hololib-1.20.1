# T44 — Configurator : édition de la rotation et de la texture d'un astre

**Objectif** : (D20, D21) quand un astre est sélectionné, la fenêtre du Configurator permet aussi d'éditer sa
**rotation propre** (période, sens, obliquité, nœud, phase) et de choisir sa **texture** parmi les textures intégrées
(ou « auto »). Le champ **Masse** ajouté en T39 est gardé dans la nouvelle disposition.

**Dépendances** : T42, T39 (et T28, T29, T30 déjà faites) ; passe après T43 (ciblage), sans en dépendre.

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 2, 6 (champs ajoutés en T28) ; [`decisions.md`](../decisions.md)
  C36, C69, C71.
- `modules/monde/spaceconfigurator/SpaceConfiguratorWindow.java` (champs numériques en texte, palette, modification
  champ par champ, action 11) ; `SpaceBodyTextures` (T29).

## Livrables
1. Champs « Rotation (h) » (signée, `0` = synchrone), « Obliquite », « Noeud », « Phase rot. » : texte numérique
   comme C36, un champ par action (pas d'écrasement par une copie en retard).
2. Sélecteur de texture : « Auto » + liste des textures intégrées (nom lisible), aperçu éventuel (pastille de couleur
   moyenne ou miniature dessinée par le renderer de la fenêtre si c'est simple) ; place réservée au bouton « Importer »
   (T45).
3. Si la fenêtre manque de place : onglets ou panneau repliable (le dire) ; taille de la fenêtre documentée (C35).
4. Docs 02 § 7, 03 § 6.

## Interdits
- Modifier une classe hors des packages Space ; widget ad hoc si un `HoloWidget*` convient.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : sélectionner la Terre, période 2 h → elle tourne vite dans le ciel et sur la carte ; obliquité 90° → couchée ;
  texture « Mars » sur la Terre → appliquée chez tous les clients ; « Auto » → texture d'origine.

## Compte rendu attendu
Fichiers, disposition de la fenêtre, écarts, scénario.
