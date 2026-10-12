# F24 — Placement unique entre les ponts et le picking

**Objectif** : la transformation qui place un objet (HUD, vue, ciel, monde, overlay) est calculée par **une seule**
fonction. Les ponts la dessinent, `HoloPicking` l'inverse : le dessin et la visée ne peuvent plus diverger. Rapport
§ 5.3, recommandation 15.

**Lot** : B   **API** : non (interne à `client/render/holo`)   **Dépendances** : F23, F26

## Constat
Le même placement est écrit deux fois, avec `PoseStack` dans les ponts et avec `Matrix4f` dans `HoloPicking` :

| Famille | Ponts | `HoloPicking` | Autre |
|---|---|---|---|
| HUD | `HoloHudBridge:38-41` | `hudMatrix:406-408` | `HoloHudPreview:49-52` |
| Vue | `HoloViewBridge:55-62` | `viewMatrix:412-426` | |
| Ciel | `HoloSkyBridge:47-49` | `skyMatrix:443-456` | |
| Overlay | `eyeOverlay` | `overlayMatrix:462-469` | |

- `HoloPlacement` n'en factorise qu'une partie.
- La transformation pixel des fenêtres est aussi recopiée : `HoloWindowRenderer:26-27` et `EditorWindowRenderer`.
- L'aiguillage par famille est écrit deux fois dans `HoloRouting` (`:126-131`, `:170-175`).

## À lire avant
- Rapport § 5.3, § 6.1 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.6.
- `client/render/holo/*` (ponts, `HoloPicking`, `HoloPlacement`, `ResolvedHolo`, `HoloRouting`, `HoloHudPreview`,
  `HoloWindowRenderer`), `client/render/modules/camera/editor/EditorWindowRenderer.java`.

## Zone
`client/render/holo/**`, `client/render/modules/camera/editor/EditorWindowRenderer.java`.

## Livrables
1. Une classe interne (par exemple `HoloPlacements`) qui calcule, par famille, la matrice de placement d'un objet
   résolu dans un `Matrix4f` fourni.
   - Les ponts l'appliquent (`pose.last().pose().mul(...)`, normale comprise : vérifier la matrice des normales).
   - `HoloPicking` l'inverse ; `HoloHudPreview` la réutilise.
2. Transformation pixel des fenêtres mise en commun.
3. Aiguillage par famille de `HoloRouting` écrit une seule fois.
4. Aucune allocation par frame (matrices préallouées).

## Interdits
- Changer le résultat : mêmes positions à l'écran, même picking.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu :
  - viser un widget dans chaque famille (HUD, vue relative et follow, ciel, monde, overlays d'édition, fenêtre
    d'outils) : le survol correspond exactement au dessin, y compris en bord de fenêtre et en F5 pour le `Local`
    d'un autre joueur ;
  - aperçu HUD de l'éditeur ;
  - avec et sans bobbing.

## Compte rendu attendu
Fonctions mises en commun, fichiers, scénario de visée par famille.
