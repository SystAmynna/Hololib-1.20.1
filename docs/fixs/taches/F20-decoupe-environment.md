# F20 — Découper `SpaceEnvironmentModule`

**Objectif** : réduire `SpaceEnvironmentModule` (2198 lignes, plus de 60 champs) en classes à responsabilité
unique, **sans changer de comportement**. Rapport § 5.2, recommandation 14.

**Lot** : B   **API** : non (packages Space)   **Dépendances** : F14, F25 (tests de `space/math` comme filet)

## Constat
Responsabilités mêlées (numéros de ligne du 12 octobre) :

| | Responsabilité | Lignes |
|---|---|---|
| (a) | API d'édition de la galaxie | `:1625-2030` |
| (b) | codec chaud / froid (`writeSync` / `readSync` / `rebuildCold`) | `:601-745` |
| (c) | miroir client : prédiction, réconciliation, historique 32 ticks, horloge d'affichage | `:745-1060`, `:1330-1460`, `:2065-2140` |
| (d) | simulation serveur et pilote | `:1069-1300` |
| (e) | archive | `:1168-1200` |

Point fragile : `writeSync` a des effets de bord (`flushExpected` / `snapshotWrites`, `:620-628`) qui supposent un
appel exact par `flush` du framework.

## À lire avant
- Rapport § 5.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 4.
- `docs/space/02-architecture.md`, `docs/space/05-synchronisation.md`, `docs/space/decisions.md` (D16, D28, D29,
  D33, D34, C19, C20, C37).
- `SpaceEnvironmentModule` en entier ; ses utilisateurs (Controller, Map, Configurator, Pilot, Knowledge, sons,
  renderers).

## Zone
`modules/dimension/space/**`, `client/modules/dimension/space/**` (si le miroir client y trouve mieux sa place :
voir le livrable 1).

## Livrables
1. Découpe proposée par le rapport, ajustée après lecture :
   - `SpaceGalaxyEditor` (a) : API d'édition serveur, validation, incrément de révision ;
   - `SpaceSyncCodec` (b) : écriture et lecture des blocs chaud et froid ;
   - `SpaceClientMirror` (c) : uniquement l'état client. Il reste en code commun s'il n'utilise aucune classe client
     (règle du projet), et le backend reste la frontière ;
   - le module garde (d) et (e), et délègue.
   
   API publique de `SpaceEnvironmentModule` (utilisée par Controller, Map, Configurator) inchangée : délégation.
2. Les effets de bord de `writeSync` sont soit supprimés, soit isolés et documentés avec l'hypothèse sur le
   framework.
3. Aucune classe de plus de 700 lignes. Joindre la table des tailles.
4. `docs/space/02-architecture.md` mis à jour (classes, responsabilités).

## Interdits
- Changer le format réseau ou NBT, la simulation (`SpaceMotion.step`), le timing des envois.
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` et `test` réussissent. Tests de F25 inchangés.
- Banc de non-régression (non livré, ou test JUnit si possible) : octets de `writeSync` (chaud et froid) identiques
  avant / après sur un état de référence.
- En jeu (LAN) :
  - pilotage, saut, téléportation, cible ;
  - édition au Configurator ;
  - Space Map ;
  - reconnexion d'un observateur (bloc froid) ;
  - redémarrage du serveur (archive) ;
  - sons.

## Compte rendu attendu
Table méthode → nouvelle classe, tailles, banc d'identité des octets, fichiers, scénario.
