# T26 — Space Configurator : croix 3D, coordonnées, origine

**Objectif** : (D14) l'objet sélectionné sur la carte porte une **croix 3D** qui le relie aux bords du cube ; sur les
bords, là où arrive la croix, ses **coordonnées précises** sont affichées. Un système (défaut **Sol**) peut être
choisi comme **origine des coordonnées** ; les coordonnées s'y rapportent.

**Dépendances** : T25.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 7 (croix et coordonnées) ; [`03-modele-donnees.md`](../03-modele-donnees.md)
  § 4 (clé `origin`) ; [`decisions.md`](../decisions.md) C50.
- `SpaceMapCubeRenderer` (règles graduées, texte à l'échelle 0,01, C34), `SpaceConfiguratorModule` (sélection, NBT,
  sync, actions), `SpaceConfiguratorWindow` (mode galaxie).

## Livrables
1. **État** : `origin` (id de système, sauvegardé et synchronisé) ; défaut = système nommé `Sol`, sinon le premier ;
   origine supprimée → retour au défaut. Action « Origine » (op vérifié en premier) : la ligne du système sélectionné
   dans la fenêtre propose « Definir comme origine » ; l'origine est indiquée dans la liste.
2. **Croix** : pour la sélection (système en mode galaxie, corps en mode système), trois segments parallèles aux axes
   du cube passant par l'objet, jusqu'aux six faces ; couleur de la sélection, fins.
3. **Coordonnées** : aux extrémités, étiquette de la coordonnée de l'axe (3 décimales) : mode galaxie `X / Y / Z` en
   al relatives à l'origine ; mode système en UA relatives à l'étoile principale. Étiquettes face caméra ou dans le
   plan de la face (choisir lisible), échelle de texte comme les graduations.
4. Les règles graduées (C34) restent relatives à leur coin ; la ligne de la sélection de la fenêtre affiche les mêmes
   coordonnées relatives à l'origine.

## Interdits
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : sélectionner Sirius → croix jusqu'aux faces, coordonnées ≈ distance de Sirius (8,6 al au total) depuis Sol ;
  définir Alpha du Centaure comme origine → Alpha du Centaure à `0,000` et Sol à ≈ `4,37` al ; recharger → origine
  gardée ; mode système, sélectionner Mars → coordonnées en UA autour du Soleil.

## Compte rendu attendu
Fichiers, nouvelle action, format des étiquettes, écarts, scénario.
