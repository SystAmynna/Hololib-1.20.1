# F26 — Tests : édition, texte, Area

**Objectif** : couvrir par des tests les autres classes pures et sensibles : droits et mathématiques d'édition,
tampon de texte, grille et maillage de l'Area Displayer, formats affichés. Rapport § 5.6.

**Lot** : B   **API** : non   **Dépendances** : F25

## Constat
Classes pures sans test :
- `holo/edit/HoloEditRules` (droits, `accepts` partagé client / serveur) ;
- `holo/edit/HoloEditMath` (rotations, `rebaseLocal` / `rebaseDirection`) ;
- `holo/widget/control/HoloTextBuffer` ;
- `modules/monde/areadisplayer/AreaGrid`, `client/modules/monde/areadisplayer/AreaMesher` ;
- `modules/dimension/space/SpaceFormat`.

## À lire avant
- Rapport § 5.6 ; la carte F25 (infrastructure).
- CLAUDE.md « Edition en jeu », « Modules Area » ; les classes citées.

## Zone
`src/test/java/com/amynna/hololib/**`.

## Livrables
1. `HoloEditRules` :
   - `accepts` (seules les composantes permises changent, par outil et par ancrage) ;
   - capacités selon les éditeurs équipés ;
   - `ownRequired` / `opRequired`.
2. `HoloEditMath` :
   - aller-retour `rebaseLocal` (relatif ↔ follow), `rebaseDirection` ;
   - cas aux pôles (pitch ±90°).
3. `HoloTextBuffer` : insertion, suppression, sélection, curseur, bornes. `HoloTextMetrics` n'a pas de backend dans
   les tests : vérifier le comportement inerte (largeur estimée).
4. `AreaGrid` :
   - pas automatique sous `128³` voxels ;
   - paliers relevés (`raised`) ;
   - sections de `16³`.
5. `AreaMesher` : un cube plein donne 6 faces fusionnées ; deux cubes adjacents n'ont pas de face interne.
6. `SpaceFormat` : distances, vitesses, al, unités aux bornes.

## Interdits
- Modifier le code de production, sauf la visibilité package-private si c'est indispensable (le noter).

## Critères d'acceptation
- `./gradlew test` réussit.

## Compte rendu attendu
Tests ajoutés, comportements surprenants découverts (à signaler comme bugs potentiels, sans les corriger).
