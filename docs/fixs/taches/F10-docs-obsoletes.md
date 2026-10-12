# F10 — Documentation obsolète

**Objectif** : aligner la documentation sur le code. Rapport § 5.4, recommandation 7.

**Lot** : A   **API** : non (documentation seule)   **Dépendances** : aucune

## Constats (vérifiés)
1. `network/package-info.java:7` annonce le protocole `"6"` ; le code utilise `"9"` (`HoloNetwork.java:23`).
2. La commande est enregistrée sous `hololib_memory` (`command/HoloMemoryCommand.java:54`). La javadoc,
   `holo/module/HoloModule.java:149`, `.claude/CLAUDE.md` et `docs/space/` disent `/holo_memory`.
   - **Le code fait foi** : corriger la documentation, pas le nom de la commande.
   - Exception : si l'utilisatrice préfère renommer la commande, c'est une question à poser avant de commencer.
3. `holo/object/HoloVisibility.java:8` : `PUBLIC` documenté « Tous les joueurs de la dimension ». Pour une mémoire de
   joueur, seuls les joueurs qui suivent **et** inspectent le propriétaire le voient.
4. `holo/module/HoloModule.java:56` : « un joueur peut avoir plusieurs instances d'un type », alors que
   `HoloContainer.install` en interdit plus d'une par mémoire. Préciser le rôle d'`instanceId` (clé réseau, stable
   entre sauvegardes).
5. Javadoc « restaure l'état GL » de `HoloOutlineShader.compose` (`:67-77`), `SkyCancelFill` (`:57-64`) et
   `SkyRenderPass.composite` (`:184-193`) : en réalité, ces méthodes remettent les valeurs **par défaut** de vanilla
   (blend coupé, cull actif, profondeur `LEQUAL`). Corriger la javadoc ; ne pas changer le code.

## À lire avant
Rapport § 5.4 ; les fichiers cités ; grep de `holo_memory` dans tout le dépôt (`src/`, `.claude/CLAUDE.md`,
`docs/`, `rapport.md`).

## Zone
Javadoc et `package-info` des fichiers cités ; `.claude/CLAUDE.md` ; `docs/space/*.md` ; `rapport.md`. Uniquement
le texte qui mentionne ces points.

## Livrables
Les cinq corrections. Une liste des occurrences de `holo_memory` corrigées (fichier et ligne).

## Interdits
- Toute modification de code exécutable.

## Critères d'acceptation
- `compileJava` réussit (javadoc seulement).
- Plus aucune occurrence de `/holo_memory` hors du journal historique de `docs/space/suivi.md` (à laisser tel
  quel) ; aucune mention du protocole `"6"`.

## Compte rendu attendu
Liste des fichiers et occurrences modifiés.
