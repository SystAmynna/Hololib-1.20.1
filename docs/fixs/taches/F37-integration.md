# F37 — Intégration, documentation et recette

**Objectif** : clore le dossier. La documentation décrit le code final, la recette groupée en jeu est décrite pour
l'utilisatrice, et l'état de chaque point du rapport est consigné.

**Lot** : final   **API** : non   **Dépendances** : toutes les tâches faites ou abandonnées (statut consigné dans
[`suivi.md`](../suivi.md))

## À lire avant
- [`suivi.md`](../suivi.md) (comptes rendus, écarts) ; [`decisions.md`](../decisions.md) ;
  [`01-architecture-cible.md`](../01-architecture-cible.md).
- `.claude/CLAUDE.md`, `rapport.md`, `rapport_mod1.md`, `docs/space/02-architecture.md`.

## Zone
`.claude/CLAUDE.md`, `rapport.md`, `rapport_mod1.md` (annexe de suivi seulement), `docs/fixs/*`, `docs/space/*.md`
(passages touchés), `package-info.java` des packages créés ou modifiés.

## Livrables
1. `.claude/CLAUDE.md` à jour :
   - arborescence (packages ajoutés : `holo/config`, `holo/quickaction`, `client/render/util`… selon les FD) ;
   - règles (ordre des renderers, recherche des modules, `acceptsAction`, `ObjectState`, démos, tests) ;
   - build (`./gradlew test`).
2. `01-architecture-cible.md` réécrit en « architecture obtenue » : propositions refusées retirées, écarts notés.
3. `rapport_mod1.md` : section finale **« Suivi des recommandations »** (recommandation → tâche → statut). Le reste du
   rapport est laissé tel quel, comme photographie du 12 octobre.
4. `rapport.md` : passages devenus faux corrigés (ou mention en tête qu'il date d'avant les correctifs, si la
   correction est trop large : demander).
5. Registre `disableSync` et protocole fixe (rapport § 7.2) : point non traité par une tâche. Le consigner en FL,
   ou proposer une décision : synchroniser le registre, ou prévenir le joueur des types inconnus.
6. Recette groupée en jeu, dans `suivi.md` : tous les scénarios « à vérifier en jeu » des cartes, regroupés et
   dédoublonnés, en solo puis en LAN, avec et sans Oculus.
7. Mesure des imports de nouveau (méthode du rapport § 4.1) : plus de cycle, aucun import client dans `holo/`,
   aucun import de module dans le framework.

## Interdits
- Modifier du code exécutable, sauf une erreur de documentation dans du javadoc.

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- La recette est rédigée.
- Le suivi des recommandations est complet (24 sur 24, avec leur statut).

## Compte rendu attendu
Fichiers mis à jour, mesure des imports, recette, points restés ouverts.
