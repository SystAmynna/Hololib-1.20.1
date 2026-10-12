# F01 — `/hololib_inspect` limité à l'exécutant hors force

**Objectif** : sans le niveau op, un joueur ne doit pouvoir modifier que **sa propre** inspection (ce qu'il voit des
autres). Rapport § 7.1-1, recommandation 1.

**Lot** : A   **API** : non (comportement d'une commande)   **Dépendances** : aucune

## Constat (vérifié)
- `command/HoloInspectCommand.java:46` : `requires(source -> source.hasPermission(force ? 2 : 0))`. Sans force, la
  commande est ouverte à tous.
- Forme `<joueurs> <cibles>` (`:58-60`) : les **viewers** sont les joueurs désignés, pas l'exécutant.
- `holo/inspect/HoloInspections.request:56-69` : l'événement `HoloInspectEvent` n'est posté que pour `enable`.
- Conséquences :
  - `/hololib_inspect off Alice Bob`, lancé par un non-op, coupe l'accès d'Alice à Bob, sans aucun contrôle ;
  - `on` fait voir à Alice les affichages de Bob, sauf si un mod annule l'événement.

## À lire avant
- Rapport § 7.1-1 ; [`decisions.md`](../decisions.md) FC3.
- `command/HoloInspectCommand.java`, `holo/inspect/HoloInspections.java`, `holo/inspect/HoloInspectEvent.java`.

## Zone
`command/HoloInspectCommand.java` ; javadoc de `holo/inspect/HoloInspections.java` si besoin.

## Livrables
1. Hors force (`/hololib_inspect`), la forme `<joueurs> <cibles>` n'est acceptée que si `<joueurs>` désigne
   **uniquement l'exécutant**. Sinon : `sendFailure` « Seul un op peut modifier l'inspection d'un autre joueur »
   (sans accents), et rien n'est modifié.
2. Les formes `on|off` et `on|off <joueurs>` (viewer = exécutant) sont inchangées.
3. `/hololib_force_inspect` est inchangée (niveau 2, tous les viewers).
4. Javadoc de la commande mise à jour (qui peut quoi).

## Interdits
- Modifier `HoloInspections.request` (son contrat sert aussi au mode forcé).
- Poster `HoloInspectEvent` pour `off` : ce serait un autre changement de comportement, à proposer en question.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (LAN, 3 joueurs A non-op, B, C) :
  - A lance `/hololib_inspect off B C` → refus, l'inspection de B est inchangée ;
  - A lance `/hololib_inspect on B` → A voit B ;
  - un op lance `/hololib_force_inspect off B C` → l'inspection de B change.

## Compte rendu attendu
Fichiers, messages ajoutés, scénario en jeu à jouer.
