# Dossier de construction — modules Space

Ce dossier pilote la construction de trois modules liés de Holo Lib :

| Module | Id | Compatibilité | Rôle |
|---|---|---|---|
| Space Environment | `hololib:space_environment` | `DIMENSION` | simule un système stellaire et le dessine à la place du ciel ; porte la galaxie de la dimension |
| Space Controller | `hololib:space_controller` | `POSITION` (marker) | pilote la pose du monde dans le système (gaz, roulis, joystick au regard) |
| Space Configurator | `hololib:space_configurator` | `POSITION` (marker), op | cube éditable : carte galactique, puis édition d'un système (corps, orbites) |

Le développement est découpé en **tâches** (`taches/`) exécutées une par une par des sous-agents, sous la
supervision d'un agent orchestrateur. Les documents numérotés sont la **référence** : une carte de tâche ne répète
pas ce qu'ils disent, elle y renvoie.

## Ordre de lecture

1. [`01-vision.md`](01-vision.md) — ce que l'utilisatrice veut, en termes de jeu.
2. [`02-architecture.md`](02-architecture.md) — packages, classes, flux, API du mod à réutiliser.
3. [`03-modele-donnees.md`](03-modele-donnees.md) — galaxie, systèmes, corps, orbites, unités, bornes, formats.
4. [`04-mathematiques.md`](04-mathematiques.md) — repères, « le monde est la caméra », orbites, projection, pilotage.
5. [`05-synchronisation.md`](05-synchronisation.md) — état froid / chaud, dead reckoning, budget réseau, pilote.
6. [`06-extension-api.md`](06-extension-api.md) — la **seule** modification d'API autorisée (couleur du brouillard).
7. [`decisions.md`](decisions.md) — décisions validées, choix par défaut, demandes d'API en attente.
8. [`suivi.md`](suivi.md) — avancement des tâches.

Le fonctionnement général du mod est décrit dans `.claude/CLAUDE.md` (à jour) et `rapport.md` (détaillé, parfois en
retard : protocole réseau, widgets récents). En cas de doute, **le code fait foi** ; pour Minecraft / Forge, lire les
sources mappées (chemin dans CLAUDE.md) plutôt que deviner.

## Graphe des tâches

```
T01 modele ──> T02 maths ──┐
                           ├─> T04 environment ──┬─> T05 rendu du ciel  (+ T03)
T03 extension API ─────────┘                     ├─> T06 controller ─> T07 pilotage ─> T08 joystick
                                                 └─> T09 configurator ─> T10 rendu cube ─> T11 edition galaxie ─> T12 mode systeme
                                                                                             toutes ──> T13 integration
```

`T03` ne dépend de rien et peut être faite en premier. Après `T04`, les lots Controller (`T06`→`T08`) et Configurator
(`T09`→`T12`) touchent des packages disjoints : ils peuvent avancer en parallèle **chacun dans son worktree**
(`isolation: "worktree"`), les fichiers communs (`HoloLibModules`, `ModuleRenderers`) étant fusionnés à la main. Par
défaut : séquentiel.

## Protocole de l'orchestrateur

L'orchestrateur est la session principale (celle qui parle avec l'utilisatrice). Pour chaque tâche :

1. Choisir dans [`suivi.md`](suivi.md) la première tâche « à faire » dont les dépendances sont « faites ».
2. La passer « en cours » et lancer **un** sous-agent `general-purpose` avec le prompt type ci-dessous.
3. À son retour : lire le compte rendu, relire le diff (`git diff`, ou `/code-review` sur la branche), vérifier les
   critères d'acceptation de la carte (au minimum la compilation ; le scénario en jeu est fait par l'utilisatrice ou
   décrit pour elle).
4. Écarts mineurs : les corriger ou relancer le même agent (`SendMessage`). Demande d'API : voir plus bas.
5. Mettre à jour `suivi.md` (statut, écarts, fichiers). Ne **pas** committer sans demande de l'utilisatrice.
6. Si une décision de conception a été prise en cours de route, l'ajouter à `decisions.md` et, si elle change un
   document de référence, corriger ce document (les documents restent la vérité pour les tâches suivantes).

### Prompt type d'un sous-agent

```
Tu implementes la tache <Txx> du dossier docs/space/ du mod Holo Lib (Forge 1.20.1, Java 17).
1. Lis .claude/CLAUDE.md, docs/space/README.md, docs/space/02-architecture.md, puis docs/space/taches/<Txx-...>.md
   et tous les documents et fichiers listes dans sa section "A lire avant".
2. Implemente exactement les livrables de la carte, en respectant les conventions du projet.
3. Interdit : modifier une classe hors des packages Space, sauf ce que la carte autorise explicitement. Si c'est
   indispensable, ARRETE-TOI et decris le besoin (quoi, pourquoi, alternative tentee) dans ton compte rendu.
4. Compile : JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava (doit reussir).
5. Ne committe pas. Rends le compte rendu demande par la carte (fichiers crees/modifies, ecarts a la carte et
   pourquoi, points a verifier en jeu, questions).
```

## Règle d'API (non négociable)

L'API et l'architecture actuelles du mod ne changent **pas**. Sont autorisés, et seulement eux :

- créer des classes dans les packages Space (voir `02-architecture.md`, § Packages) ;
- ajouter les enregistrements dans `modules/HoloLibModules.java` et `client/render/modules/ModuleRenderers.java` ;
- installer des backends client (patron `XxxModule.install(...)` au setup client) depuis les classes client Space ;
- l'extension validée de [`06-extension-api.md`](06-extension-api.md) (tâche `T03`, et elle seule).

Tout autre besoin (rendre publique une méthode privée, ajouter un callback au framework, un paquet réseau...) est une
**demande d'API** : le sous-agent s'arrête et la décrit, l'orchestrateur la consigne dans `decisions.md` (section
« Demandes en attente ») et la soumet à l'utilisatrice. Rien n'est implémenté avant sa validation. Copier une
primitive privée du mod (ex. `ring` de `HoloGizmoRenderer`) dans un renderer Space n'est **pas** une modification
d'API : c'est autorisé.

## Conventions rappelées

- Commentaires et javadoc en **français sans accents** ; accolades sur une nouvelle ligne ; une `package-info.java`
  par package non trivial (les documents de ce dossier, eux, sont accentués).
- Code commun (`modules/...`) : aucune classe client (`Minecraft`, `RenderSystem`...). Le client passe par un
  `Backend` installé au setup client.
- Rendu : `RenderType` / `MultiBufferSource` / `RenderSystem` uniquement, aucune allocation par frame, état GL
  restauré, renderers enregistrés via `HoloRenderers` ou `HoloObjectRenderers`, jamais abonnés aux événements Forge.
- Widgets : traits et `HoloWidget*` existants (`holo/widget/control`) ; pas de widget ad hoc si un existant convient.
- Build : `JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava` ; jeu : `... ./gradlew runClient` (`runClient2`
  pour un second joueur, monde ouvert en LAN).
