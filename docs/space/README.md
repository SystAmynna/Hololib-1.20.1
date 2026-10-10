# Dossier de construction — modules Space

Ce dossier pilote la construction de quatre modules liés de Holo Lib :

| Module | Id | Compatibilité | Rôle |
|---|---|---|---|
| Space Environment | `hololib:space_environment` | `DIMENSION` | simule la galaxie et le système courant, dessine le ciel, porte la pose du monde (le « vaisseau ») et le saut |
| Space Controller | `hololib:space_controller` | `POSITION` (marker) | poste de pilotage : gaz, roulis, joystick au regard, saut vers une étoile |
| Space Configurator | `hololib:space_configurator` | `POSITION` (marker), op | carte galactique (cube) et fenêtre d'édition, séparées |
| Space Pilot | `hololib:space_pilot` | `PLAYER` | affichage personnel du pilote : surcouche du ciel, joystick, HUD de navigation (lot 2) |

**Lot 1** (T01–T13, T07b–T07f) : fait, commit `21f21b1` ; ses cartes de tâche sont dans l'historique git.
**Lot 2** (T14–T27) : saut interstellaire, module Space Pilot, persistance, systèmes réels, Configurator revu. C'est
ce que décrivent `taches/` et [`suivi.md`](suivi.md).

Le développement est découpé en **tâches** (`taches/`) exécutées une par une par des sous-agents, sous la
supervision d'un agent orchestrateur. Les documents numérotés sont la **référence** : une carte de tâche ne répète
pas ce qu'ils disent, elle y renvoie.

## Ordre de lecture

1. [`01-vision.md`](01-vision.md) — ce que l'utilisatrice veut, en termes de jeu (lots 1 et 2).
2. [`02-architecture.md`](02-architecture.md) — packages, classes, flux, API du mod à réutiliser.
3. [`03-modele-donnees.md`](03-modele-donnees.md) — galaxie, systèmes, corps, orbites, unités, bornes, formats.
4. [`04-mathematiques.md`](04-mathematiques.md) — repères, « le monde est la caméra », orbites, projection, pilotage,
   saut, surcouche.
5. [`05-synchronisation.md`](05-synchronisation.md) — état froid / chaud, dead reckoning, budget réseau, pilote, saut.
6. [`decisions.md`](decisions.md) — décisions validées, choix par défaut, limites, demandes d'API en attente.
7. [`donnees-systemes.md`](donnees-systemes.md) — valeurs réelles des systèmes par défaut (T16).
8. [`suivi.md`](suivi.md) — avancement des tâches.

Le fonctionnement général du mod est décrit dans `.claude/CLAUDE.md` (à jour) et `rapport.md` (détaillé, parfois en
retard). En cas de doute, **le code fait foi** ; pour Minecraft / Forge, lire les sources mappées (chemin dans
CLAUDE.md) plutôt que deviner.

## Graphe des tâches (lot 2)

```
T14 persistance ─> T15 etoiles identifiees + distorsion ─> T16 systemes reels ─┬─> T17 Space Pilot socle ─┬─> T18 surcouche du ciel
                                                                               │                          ├─> T19 joystick + lien ──┐
                                                                               │                          └─> T20 HUD                │
                                                                               ├─> T21 saut : modele ─────┬─────────────────────────┴─> T22 saut : pilotage
                                                                               │                          └─> T23 rendu distorsion
                                                                               └─> T24 Configurator fenetre ─> T25 carte ─> T26 croix + origine
                                                                                                                  toutes ──> T27 integration
```

Ordre par défaut : **séquentiel**, T14 → T27. Après T16, les lots Pilot (T17–T20), Saut (T21–T23) et Configurator
(T24–T26) touchent surtout des packages disjoints : ils peuvent avancer en parallèle **chacun dans son worktree**
(`isolation: "worktree"`), les fichiers communs (`HoloLibModules`, `ModuleRenderers`, `SpaceEnvironmentModule`)
étant fusionnés à la main. T22 attend T19 **et** T21.

## Protocole de l'orchestrateur

L'orchestrateur est la session principale (celle qui parle avec l'utilisatrice). Pour chaque tâche :

1. Choisir dans [`suivi.md`](suivi.md) la première tâche « à faire » dont les dépendances sont « faites » ou « à
   vérifier en jeu ».
2. **Demander à l'utilisatrice** s'il faut la commencer ou mettre le processus en pause (une question à chaque tâche).
3. La passer « en cours » et lancer **un** sous-agent `general-purpose` avec le prompt type ci-dessous.
4. À son retour : lire le compte rendu, relire le diff (`git diff`, ou `/code-review` sur la branche), vérifier les
   critères d'acceptation de la carte (au minimum la compilation ; le scénario en jeu est fait par l'utilisatrice ou
   décrit pour elle).
5. Écarts mineurs : les corriger ou relancer le même agent (`SendMessage`). Demande d'API : voir plus bas.
6. Mettre à jour `suivi.md` (statut, écarts, fichiers, une ligne de journal). Ne **pas** committer sans demande de
   l'utilisatrice.
7. Si une décision de conception a été prise en cours de route, l'ajouter à `decisions.md` et, si elle change un
   document de référence, corriger ce document (les documents restent la vérité pour les tâches suivantes).

### Prompt type d'un sous-agent

```
Tu implementes la tache <Txx> du dossier docs/space/ du mod Holo Lib (Forge 1.20.1, Java 17).
1. Lis .claude/CLAUDE.md, docs/space/README.md, docs/space/02-architecture.md, docs/space/decisions.md, puis
   docs/space/taches/<Txx-...>.md et tous les documents et fichiers listes dans sa section "A lire avant".
2. Implemente exactement les livrables de la carte, en respectant les conventions du projet.
3. Interdit : modifier une classe hors des packages Space, sauf ce que la carte autorise explicitement. Si c'est
   indispensable, ARRETE-TOI et decris le besoin (quoi, pourquoi, alternative tentee) dans ton compte rendu.
4. Compile : JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava (doit reussir).
5. Ne committe pas. Rends le compte rendu demande par la carte (fichiers crees/modifies, ecarts a la carte et
   pourquoi, points a verifier en jeu, questions).
```

## Règle d'API (non négociable)

L'API et l'architecture actuelles du mod ne changent **pas**. Sont autorisés, et seulement eux :

- créer et modifier des classes dans les **packages Space** :
  `modules/dimension/space/**`, `modules/monde/spacecontroller/**`, `modules/monde/spaceconfigurator/**`,
  `modules/camera/spacepilot/**` (nouveau), et leurs miroirs `client/modules/...` et `client/render/modules/...` ;
- ajouter les enregistrements dans `modules/HoloLibModules.java` et `client/render/modules/ModuleRenderers.java` ;
- installer des backends client (patron `XxxModule.install(...)` au setup client) depuis les classes client Space ;
- **utiliser** l'API existante, y compris les couches de compatibilité : implémenter `Configurable`
  (`modules/camera/config`), `QuickActionProvider` (`modules/camera/quickaction`), les interfaces d'édition
  (`holo/edit`) sur un objet `Hud` ou `World`, lire un conteneur par `HoloContainers.player(...)` /
  `ClientHoloWorld.find(...)`, utiliser `SavedData` vanilla. Ce n'est pas une modification d'API.

Tout autre besoin (rendre publique une méthode privée, ajouter un callback au framework, un paquet réseau, toucher un
module non Space comme `config`, `quick_action` ou les éditeurs...) est une **demande d'API** : le sous-agent s'arrête
et la décrit, l'orchestrateur la consigne dans `decisions.md` (section « Demandes en attente ») et la soumet à
l'utilisatrice. Rien n'est implémenté avant sa validation. Copier une primitive privée du mod (ex. `ring` de
`HoloGizmoRenderer`) dans un renderer Space n'est **pas** une modification d'API : c'est autorisé.

## Conventions rappelées

- Commentaires et javadoc en **français sans accents** ; accolades sur une nouvelle ligne ; une `package-info.java`
  par package non trivial (les documents de ce dossier, eux, sont accentués). Messages et noms affichés sans accents
  (C15).
- Code commun (`modules/...`) : aucune classe client (`Minecraft`, `RenderSystem`...). Le client passe par un
  `Backend` installé au setup client.
- Rendu : `RenderType` / `MultiBufferSource` / `RenderSystem` uniquement, aucune allocation par frame, état GL
  restauré, renderers enregistrés via `HoloRenderers` ou `HoloObjectRenderers`, jamais abonnés aux événements Forge.
- Widgets : traits et `HoloWidget*` existants (`holo/widget/control`) ; pas de widget ad hoc si un existant convient.
- Mouvement : tout ce qui change la pose passe par une fonction **déterministe** partagée serveur / clients
  (`StrictMath`, pas de `Math` ni de JOML `float`), comme `SpaceMotion.step`.
- Build : `JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava` ; jeu : `... ./gradlew runClient` (`runClient2`
  pour un second joueur, monde ouvert en LAN).
