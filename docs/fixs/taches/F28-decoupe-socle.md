# F28 — Découper `HoloContainers`, `HoloEditSession` et `HoloEditTools`

**Objectif** : séparer les responsabilités des trois plus grosses classes du socle, sans changer de comportement.
Rapport § 5.2.

**Lot** : B   **API** : non (classes internes ; signatures publiques conservées par délégation)   **Dépendances** :
F03, F06, F15

## Constat

| Classe | Lignes | Responsabilités mêlées |
|---|---|---|
| `holo/memory/HoloContainers` | 473 | index statique ; cycle de vie des markers ; API des mémoires ; 4 handlers réseau avec toutes les permissions (`handleMove`, `handleOpen`, `handleAction`, `handleEdit`) ; abonnés Forge |
| `client/edit/HoloEditSession` | 647 | cycle de la session ; repère des objets tenus (`Held`, `toReal` / `toDisplayed`, `release`) ; envois réseau |
| `client/edit/HoloEditTools` | 646 | choix de l'outil ; glisser de la caméra ; mathématiques des gizmos (`raySegment`, `rayRing`, `lineParam`, `ringAngle`) ; classes de poignées |

## À lire avant
- Rapport § 5.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 4.
- Les trois classes après F03, F06 et F15 ; les cartes de ces tâches et leurs comptes rendus dans
  [`suivi.md`](../suivi.md).

## Zone
`holo/memory/*`, `client/edit/*`.

## Livrables
1. `HoloServerHandlers` (package `holo/memory`) : les quatre handlers et leurs permissions. Les paquets l'appellent
   directement, ou `HoloContainers` délègue.
2. `HoloEditHeld` (package `client/edit`) : objets tenus et conversions.
3. `HoloGizmoMath` (package `client/edit`, ou `client/render/util` si F23 l'a prévu) : mathématiques des gizmos.
   Testées par F26 si elles y sont : adapter les tests sans changer leurs assertions.
4. Aucune classe de plus de 450 lignes dans ces packages.

## Interdits
- Changer un comportement, une signature publique ou un format.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu : recette de l'éditeur (comme F15) ; déplacement de fenêtres Config ; actions des modules ; inspection.

## Compte rendu attendu
Table méthode → classe, tailles, fichiers, scénario.
