# F15 — Découpler l'édition du cœur client

**Objectif** : le routage, le picking, l'interaction et les miroirs client n'importent plus `client/edit`. L'édition
installe au setup des **politiques** que le cœur consulte. Le cœur devient lisible et testable sans l'édition.
Rapport § 4.4, recommandation 11.

**Lot** : B   **API** : **oui : à valider** (interfaces de politique)   **Dépendances** : F26 (tests de
`HoloEditRules` / `HoloEditMath` comme filet)

## Constat (vérifié par imports)

| Dépendance | Sens inverse |
|---|---|
| `HoloRouting` → `HoloEditSession.reveals` / `hudSuspended` (`HoloRouting.java:105,148-150`) | `HoloEditSession` → `HoloRouting`, `ResolvedHolo` |
| `HoloPicking` → `HoloEditTools` (`:112,310,477,495`) | `HoloEditTools` → `HoloPicking` |
| `ClientHoloContainer.apply` → `HoloEditSession.holds` (`:118`) | `HoloEditSession` → `ClientHoloWorld` |
| `HoloInteractions.valid` → `HoloEditTools.editing` (`:242`) | — |
| `ClientHoloWorld` → `HoloRouting.refresh` (couche miroir → rendu) | — |

## À lire avant
- Rapport § 4.4 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.5.
- CLAUDE.md « Edition en jeu », « Interaction au regard ».
- `client/holo/*`, `client/render/holo/HoloRouting.java`, `HoloPicking.java`, `client/interact/HoloInteractions.java`,
  `client/edit/*`.

## Demande d'API (à valider avant de commencer)
Interfaces, avec un défaut inerte (cœur sans édition). Le package exact est à proposer : `client/holo/policy`, par
exemple.

| Interface | Méthodes |
|---|---|
| `HoloRoutingPolicy` | `boolean reveals(HoloObject)`, `boolean hudSuspended()` |
| `HoloMirrorPolicy` | `boolean holds(HoloObject)` (placement reçu ignoré) |
| `HoloPickingOverride` | gizmos et poignées testés avant les objets ; signature à définir après lecture de `HoloPicking` |
| `HoloInteractionPolicy` | `boolean stillValid(HoloObject)` |

- `client/edit` installe ses implémentations au setup client.
- `ClientHoloWorld` publie la fin de mise à jour des miroirs, et le routage s'y abonne. Plus d'appel direct
  `HoloRouting.refresh`.

**Alternative** : garder les appels directs, mais regrouper toutes les requêtes du cœur vers l'édition dans une seule
façade `HoloEditHooks` (statique, `client/edit`). Le couplage reste, mais il est concentré et documenté.

## Zone
`client/holo/*`, `client/render/holo/HoloRouting.java`, `HoloPicking.java`, `client/interact/HoloInteractions.java`,
`client/edit/*`, nouveau package de politiques.

## Livrables
1. L'option validée. Après la tâche, `client/holo`, `client/render/holo` et `client/interact` n'importent plus
   `client/edit`. Joindre la mesure.
2. Comportement identique en session d'édition et hors session.
3. `package-info` des packages touchés mis à jour (qui installe quoi).

## Interdits
- Changer le comportement de l'édition (outils, poignées, objets tenus, révélation, HUD suspendu).
- Allocation par frame.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu, recette de l'éditeur :
  - own fix, own dyn, HUD, monde, dimension, other ;
  - objets `OFF` révélés ;
  - objets tenus reconvertis à la fermeture ;
  - placement reçu ignoré pendant la manipulation ;
  - gizmos visés avant les objets ;
  - fenêtre d'outils visée au-dessus de tout ;
  - focus et éditeur jamais ensemble.

## Compte rendu attendu
Option appliquée, interfaces, mesure des imports avant / après, fichiers, recette rejouée.
