# F32 — Cache des positions par frame, picking borné

**Objectif** : la position et l'orientation d'un objet routé sont calculées une fois par frame, sans allocation. Le
picking des objets monde teste la distance avant de construire et d'inverser une matrice. Rapport § 6.1,
recommandation 20.

**Lot** : C   **API** : non   **Dépendances** : F24

## Constat
- `client/render/holo/ResolvedHolo.java:100-147` :
  - `position` crée 2 à 3 `Vec3` par objet monde (`getPosition` + `.add`, `getEyePosition().add`, `view.add`) ;
  - `renderBounds` crée un `AABB`.
- Ce calcul est fait **trois fois** par frame :
  - `HoloPicking.worldMatrix:430` ;
  - `WorldRenderer.renderAll:64` ;
  - `HoloFocusBridge:59`.
- `HoloPicking.intersect:549-550` construit et inverse une matrice pour **chaque** objet monde routé (jusqu'à 96
  blocs) avant de tester la portée de 16 blocs (`WORLD_REACH`).
- `HoloPicking.pickWorld` (`:336-339`) crée deux `Vec3` et un `ClipContext` à chaque frame où un candidat est touché.

## À lire avant
- Rapport § 6.1 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.6.
- `client/render/holo/ResolvedHolo.java`, `HoloPicking.java`, `HoloFocusBridge.java`, `client/render/world/*`, et
  `HoloPlacements` (créé par F24).

## Zone
`client/render/holo/**`, `client/render/world/WorldRenderer.java`.

## Livrables
1. `ResolvedHolo` garde sa position et son orientation dans des champs mutables, recalculés au plus une fois par
   couple (frame, partialTick). L'invalidation se fait par un compteur de frame lu dans `RenderDispatcher`, ou
   équivalent. Plus de `Vec3` alloué dans ce chemin.
2. `HoloPicking` rejette d'abord un objet monde par sa distance (centre et rayon englobant) avant toute matrice.
3. Le `ClipContext` et les `Vec3` du test d'occultation sont réduits au minimum (vanilla impose `ClipContext` :
   l'allouer seulement quand un candidat est touché, comme aujourd'hui, est acceptable ; le noter).
4. **Mesure avant / après** : 50 objets monde (démo) dans un rayon de 64 blocs, FPS et allocations (profileur, ou
   `-Xlog:gc` sur 60 s).

## Interdits
- Changer le picking, le placement ou l'ordre de rendu.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu : la visée est identique (scénario de F24) ; mesure jointe.

## Compte rendu attendu
Mécanisme d'invalidation, fichiers, mesures.
