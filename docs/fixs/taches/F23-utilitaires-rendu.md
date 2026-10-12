# F23 — Utilitaires de rendu partagés (`client/render/util`)

**Objectif** : mettre en commun les petites primitives de rendu recopiées dans le framework et dans les renderers
de modules. Rapport § 5.3, recommandation 15.

**Lot** : B   **API** : **oui : à valider** (nouveau package public)   **Dépendances** : aucune

## Constat

| Primitive | Copies | Où |
|---|---|---|
| `basis(Vector3f)` (repère orthonormé autour d'une direction) | 6 | renderers Space |
| `vertex(...)` sans allocation (position + couleur, + uv ou lumière) | 7 | renderers de modules |
| `red` / `green` / `blue` d'une couleur ARGB | 3 | renderers de modules |
| quad plein écran en identité | 3 | `SkyRenderPass.composite`, `SkyCancelFill`, `HoloOutlineShader` |
| rotation céleste | 3 | `SkyRenderContext.applyCelestialRotation`, `HoloPlacement.sky:500`, `HoloEditTools.celestial:443-447` |
| `placeBillboard` / `disc` | 2 | deux renderers de cube |

## À lire avant
- Rapport § 5.3 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 2.
- Toutes les copies (grep de `basis(`, `private static void vertex(`, `red(`, `applyCelestialRotation`,
  `celestial(`) ; `client/render/package-info.java`.

## Demande d'API (à valider avant de commencer)
Package public `client/render/util`, classes finales statiques et sans état :

| Classe | Contenu |
|---|---|
| `HoloRenderMath` | `basis`, rotation céleste, conversions de couleur ARGB ; sorties dans des tampons fournis par l'appelant |
| `HoloVertices` | variantes de `vertex` |
| `FullscreenQuad` | `draw(shader)` en identité, état GL documenté |

**Alternative** : des utilitaires `final` package-private, un par famille d'appelants. Moins de dédoublonnage
(framework et modules restent séparés).

## Zone
`client/render/util/**` (nouveau) ; les fichiers qui contiennent les copies **du framework** (`client/render/sky`,
`client/render/cancel/SkyCancelFill`, `client/render/outline/HoloOutlineShader`, `client/render/holo/HoloPlacement`,
`client/edit/HoloEditTools`) ; les renderers de modules **sauf** `SpaceMapCubeRenderer` et
`SpaceKnowledgeSkyRenderer` (traités par F22).

## Livrables
1. Les utilitaires validés, documentés, **sans allocation** (tampons fournis par l'appelant ou `ThreadLocal`
   interdit : le rendu est mono-fil, utiliser des paramètres de sortie).
2. Remplacement des copies dans la zone, avec un résultat numérique identique (mêmes formules, même ordre des
   opérations en `float`).
3. Table des copies supprimées.

## Interdits
- Changer une formule (même « équivalente »).
- Toucher `SpaceMapCubeRenderer` et `SpaceKnowledgeSkyRenderer` (F22).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu, captures avant / après identiques :
  - ciel Space (fond, étoiles, corps) ;
  - objets `Sky` ;
  - radar et ciblage (contours) ;
  - ciel annulé avec shaderpack ;
  - gizmos de rotation d'un objet `Sky`.

## Compte rendu attendu
API, table des copies supprimées, fichiers, comparaison.
