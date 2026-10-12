# F08 — Contexte de capture et captures imbriquées

**Objectif** : un captor qui démarre une capture pendant un de ses callbacks ne doit pas faire agir le contexte du
captor extérieur sur la nouvelle capture. Rapport § 7.2.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat
- `client/capture/HoloControlCapture.java:84` : un `CONTEXT` statique unique, réaffecté à chaque appel
  (`:546-550`, `CONTEXT.captor = captor`).
- Scénario : un callback `onPress` démarre une capture imbriquée (`HoloInteractions.Context.capture` → `begin`).
  Cela réaffecte le contexte ; le `capture.release()` (`:591`) ou le `setLook` du captor extérieur, fait ensuite,
  agit alors sur le **nouveau** captor.
- `client/interact/HoloInteractions.java:56,272-278` : même motif pour le contexte d'interaction.
- Pas de scénario de jeu connu qui le déclenche aujourd'hui : risque latent pour un module tiers.

## À lire avant
- Rapport § 7.2 ; CLAUDE.md « Capture de contrôles ».
- `client/capture/HoloControlCapture.java` (distribution, `context`, `release`), `holo/capture/HoloCaptor.java`,
  `holo/capture/HoloCaptures.java`, `client/interact/HoloInteractions.java` (`Context`, `capture`).

## Zone
`client/capture/HoloControlCapture.java`, `client/interact/HoloInteractions.java`.

## Livrables
1. Le contexte remis à un captor reste lié à **ce** captor pendant tout le callback. Deux options, sans allocation
   par événement :
   - une petite pile de contextes préalloués, avec une profondeur maximale (4) et une erreur claire au-delà ;
   - ou sauvegarde et restauration des champs du contexte autour de chaque appel de callback.
   
   Choisir et justifier.
2. Même traitement pour `HoloInteractions.CONTEXT`.
3. Javadoc : un captor ne doit pas conserver la référence du contexte hors du callback (règle à écrire si elle n'y
   est pas).

## Interdits
- Changer les interfaces `HoloCaptor` / contexte publics.

## Critères d'acceptation
- `compileJava` réussit.
- Banc jetable (non livré) : une démo dont `onPress` démarre une capture explicite ; le `release` du premier captor
  ne termine pas la seconde capture.
- En jeu : pilotage Space (capture explicite), champs de texte (`KEYBOARD`), éditeur (glisser) et Quick Action
  fonctionnent comme avant.

## Compte rendu attendu
Option retenue, fichiers, résultat du banc, scénarios rejoués.
