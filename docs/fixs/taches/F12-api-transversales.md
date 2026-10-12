# F12 — `Configurable`, `QuickActionProvider` et `TargetingThroughWalls` remontés dans le framework

**Objectif** : les points d'extension utilisés par plusieurs modules vivent dans `holo/`. Les modules Config, Quick
Action et Ciblage deviennent des **consommateurs** de ces API au lieu d'en être les propriétaires. Rapport § 4.2,
recommandation 9.

**Lot** : B   **API** : **oui : à valider** (packages publics déplacés)   **Dépendances** : aucune (F13 touche les
mêmes fichiers : la faire après)

## Constat (vérifié)
- `modules/camera/config/Configurable` (`configName()`, `configWindow()` qui rend un `ConfigWindow`) est
  implémentée par 9 modules : EditorPanel, QuickAction, SpaceKnowledge, SpacePilot, Radar, KeysEditor, AttackGuard,
  ThemeConfiguratorBase, démo.
- `ConfigWindow` n'importe que `holo/` : il est déplaçable tel quel, avec son `Backend` (`storeOffset`).
- `modules/camera/quickaction/QuickActionProvider` (`quickActions()` → `List<QuickAction>`) : implémentée par
  QuickAction (démo), SpaceKnowledge, Radar. `QuickAction` n'importe que `HoloCanvas` ; `QuickActionIcon` est à
  vérifier.
- `modules/camera/targeting/TargetingThroughWalls` (`throughWallTargets()`) : implémentée par Radar, lue par
  `TargetingClient`.
- Résultat : 12 modules dépendent du package d'un autre module pour exister.

## À lire avant
- Rapport § 4.2 et graphe § 2.4 ; [`01-architecture-cible.md`](../01-architecture-cible.md) §§ 2, 5.
- `modules/camera/config/*`, `modules/camera/quickaction/*`, `modules/camera/targeting/*`, leurs contrôleurs client
  (`client/modules/camera/config|quickaction|targeting`), tous les implémenteurs (grep).

## Demande d'API (à valider avant de commencer)

| Type | Package proposé |
|---|---|
| `Configurable`, `ConfigWindow` (+ `Backend`) | `holo/config/` |
| `QuickActionProvider`, `QuickAction`, `QuickActionIcon` | `holo/quickaction/` |
| `TargetingThroughWalls` | `holo/targeting/`, ou `holo/interact/` si l'utilisatrice préfère un package existant |

- Les modules (`ConfigModule`, listes, écrou, épingle ; `QuickActionModule`, roue, secteurs, lignes ;
  `TargetingModule`) restent où ils sont.
- Le fonctionnement (découverte chaque tick, clés de sauvegarde `<type>/<id>`) est inchangé.
- **Conséquence** : le package public de ces types change. Un mod tiers qui les utilise doit changer ses imports.
  Aucun ne l'utilise à ce jour à notre connaissance.

**Alternative** : laisser les types en place et documenter `modules/camera/config|quickaction|targeting` comme des
« modules d'API ». Aucun code ne change, mais le graphe de dépendances reste tel quel.

## Zone
Les types déplacés ; leurs `package-info.java` (nouveaux et anciens) ; les imports de tous les fichiers qui les
utilisent (`modules/**`, `client/**`, `demo/**`).

## Livrables
1. Déplacement des types (code inchangé, sauf le package et les imports), avec un `package-info.java` par nouveau
   package : rôle, règle d'implémentation reprise de CLAUDE.md.
2. Les clés de sauvegarde, les ids de types de module et le réseau ne changent pas : vérifier qu'aucun nom de classe
   n'est sérialisé.
3. Le graphe des imports entre modules (méthode du rapport § 4.1) n'a plus d'arêtes vers `camera/config`,
   `camera/quickaction` ou `camera/targeting` pour ces types. Joindre la sortie de la mesure.
4. CLAUDE.md : arborescence et sections « Configuration en jeu », « Roue d'actions », « Ciblage » mises à jour.

## Interdits
- Changer une signature des types déplacés.
- Déplacer les modules eux-mêmes.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : la liste Config affiche les mêmes modules ; les fenêtres s'ouvrent, s'épinglent et se replacent ; la roue
  montre les mêmes actions dans le même ordre ; le radar cible à travers les murs ; une sauvegarde d'avant la tâche se
  relit à l'identique (épingles, ordre des actions).

## Compte rendu attendu
Table ancien → nouveau package, fichiers modifiés (nombre), mesure du graphe, scénarios.
