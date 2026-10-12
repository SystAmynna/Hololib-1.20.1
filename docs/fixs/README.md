# Dossier de correctifs — examen n° 1

Ce dossier pilote les correctifs issus de l'examen du code du 12 octobre 2026
([`rapport_mod1.md`](../../rapport_mod1.md), à la racine du projet). Le rapport contient 24 recommandations
(section 8), auxquelles s'ajoutent les constats de sévérité moyenne et faible de la section 7. Ce dossier les découpe
en **37 tâches** (`taches/F01` … `F37`).

| Lot | Tâches | Contenu |
|---|---|---|
| **A** : bugs et robustesse | F01–F10 | failles de validation serveur, fuite au déchargement, état GL, capture, documentation obsolète |
| **B** : structure, cloisonnement, qualité | F11–F29 | ordre de rendu, API remontées dans le framework, cycles, découpe des god classes, dédoublonnage, tests |
| **C** : performance | F30–F36 | buffers GPU, caches par frame, budgets serveur, encodage réseau |
| **Final** | F37 | intégration, documentation, recette groupée |

Les tâches s'exécutent une par une, par des sous-agents, sous la supervision de l'orchestrateur (la session
principale), comme pour [`docs/space/`](../space/README.md). Les documents de référence font foi : une carte de
tâche ne répète pas ce qu'ils disent, elle y renvoie.

Préfixes : **F** pour les tâches, **FD / FC / FL** pour les décisions. Ils évitent toute confusion avec les T / D /
C / L de Space.

## Ordre de lecture

1. [`../../rapport_mod1.md`](../../rapport_mod1.md) : constats, schémas de la structure, recommandations. Les cartes
   citent ses sections (par exemple « rapport § 7.1-3 »).
2. [`01-architecture-cible.md`](01-architecture-cible.md) : l'état visé après les correctifs (packages ajoutés,
   règles, découpes).
3. [`decisions.md`](decisions.md) : décisions validées, choix par défaut, limites, **demandes d'API en attente**.
4. [`suivi.md`](suivi.md) : avancement des tâches et correspondance rapport → tâches.
5. `.claude/CLAUDE.md` : fonctionnement général du mod (à jour). `rapport.md` : description détaillée (6 octobre).

En cas de doute, **le code fait foi**. Pour Minecraft et Forge, lire les sources mappées (chemin dans CLAUDE.md)
plutôt que deviner. Les numéros de ligne cités par le rapport datent du 12 octobre et peuvent avoir glissé : chercher
par nom de méthode.

## Graphe des tâches

```
Lot A (indépendantes entre elles)
  F01  F02 ──────────────────────────────> F19 acceptsAction
  F03 ─┐  F04  F05  F06 ─┐  F07  F08  F09  F10
       └─────────────────┴──> F28 découpe HoloContainers / édition

Filet de tests
  F25 tests space/math ─┬─> F26 tests édition / texte / Area ─┬─> F15 édition découplée ─┬─> F28 ─> F29 nettoyage
                        │                                     │                          └─> F35 (avec F17)
                        │                                     └─> F24 placement unique ─> F32 cache positions
                        ├─> F18 ObjectState + version NBT ─> F36 encodage partagé
                        └─> F20 découpe SpaceEnvironment (avec F14) ─> F21 découpe Configurator / Map

Structure
  F11 ordre de rendu      F12 API remontées      F13 HoloWindowStyle      F14 cycle pilot/controller ─> F20
  F16 find(Class) ─┬─> F17 branchement client unique ─> F35
                   └─> F34 caches serveur
  F23 utilitaires de rendu ─┬─> F22 découpe renderers Space
                            ├─> F24
                            └─> F31 étoiles en buffer statique
  F27 démos optionnelles

Performance indépendante
  F30 terrain Area en VertexBuffer      F33 AreaScanner / AreaFeed

                                                            toutes ──> F37 intégration
```

**Ordre par défaut**, séquentiel :
1. lot A (F01 → F10) ;
2. F25, F26 : les tests servent de filet de sécurité avant les refactorisations ;
3. lot B dans l'ordre des numéros, en respectant les dépendances ;
4. lot C ;
5. F37.

Les cartes `API : oui` (voir [`decisions.md`](decisions.md), « Demandes d'API ») ne sont lancées qu'après validation.
Si une demande attend, l'orchestrateur passe à la tâche suivante.

**Fichiers communs** à fusionner à la main si des tâches avancent en parallèle (worktrees) :

| Fichier | Tâches |
|---|---|
| `holo/memory/HoloContainers.java` | F03, F04, F06, F19, F28, F34 |
| `client/render/holo/HoloPicking.java` | F09, F15, F24, F32 |
| `modules/dimension/space/SpaceEnvironmentModule.java` | F14, F20, F35 |
| `client/render/holo/HoloBridges.java`, `client/render/modules/ModuleRenderers.java` | F11, F27 |
| `client/edit/HoloEditSession.java`, `HoloEditTools.java` | F09, F15, F28, F29, F35 |
| `modules/camera/config/*`, `modules/camera/quickaction/*` | F12, F13 |

## Protocole de l'orchestrateur

Pour chaque tâche :

1. Choisir dans [`suivi.md`](suivi.md) la première tâche « à faire » dont les dépendances sont « faites » ou « à
   vérifier en jeu ».
2. **Demander à l'utilisatrice** s'il faut la commencer ou mettre le processus en pause (une question à chaque
   tâche).
3. **Si la carte porte `API : oui`**, présenter la demande d'API : signature proposée, alternative et conséquences,
   reprises de la carte et de [`decisions.md`](decisions.md). Attendre la validation, puis la consigner en **FD**
   dans `decisions.md`. Sans validation, la tâche reste « bloquée ».
4. Passer la tâche « en cours » et lancer **un** sous-agent `general-purpose` avec le prompt type ci-dessous.
5. À son retour :
   - lire le compte rendu et relire les fichiers qu'il liste, **sans aucune commande git**, même en lecture
     (l'utilisatrice gère git) ;
   - vérifier les critères d'acceptation de la carte : au minimum la compilation, et les tests à partir de F25 ;
   - le scénario en jeu est fait par l'utilisatrice, ou décrit pour elle.
6. Écarts mineurs : les corriger, ou relancer le même agent (`SendMessage`). Tout besoin hors de la zone de la carte
   devient une demande dans `decisions.md`.
7. Mettre à jour `suivi.md` (statut, écarts, fichiers, une ligne de journal). Ne **pas** committer sans demande de
   l'utilisatrice.
8. Si une décision a été prise en cours de route, l'ajouter à `decisions.md`. Si elle change
   `01-architecture-cible.md`, corriger ce document.

### Prompt type d'un sous-agent

```
Tu implementes la tache <Fxx> du dossier docs/fixs/ du mod Holo Lib (Forge 1.20.1, Java 17).
1. Lis .claude/CLAUDE.md, docs/fixs/README.md, docs/fixs/01-architecture-cible.md, docs/fixs/decisions.md, puis
   docs/fixs/taches/<Fxx-...>.md, les sections de rapport_mod1.md qu'elle cite et tous les fichiers de sa section
   "A lire avant".
2. Implemente exactement les livrables de la carte, en respectant les conventions du projet.
3. Interdit : modifier un fichier hors de la section "Zone" de la carte, ou changer une signature publique non prevue
   par la carte. Si c'est indispensable, ARRETE-TOI et decris le besoin (quoi, pourquoi, alternative tentee) dans
   ton compte rendu.
4. Compile : JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava (doit reussir). Si la tache F25 est faite :
   JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew test (doit reussir).
5. N'utilise aucune commande git (meme en lecture). Rends le compte rendu demande par la carte (fichiers
   crees/modifies, ecarts a la carte et pourquoi, points a verifier en jeu, questions).
```

## Règle d'API

Ces correctifs touchent le framework : la règle du projet (« aucune modification de l'API ou de l'architecture sans
accord explicite ») s'applique carte par carte.

- **`API : non`** : la carte ne change aucune signature publique du framework (`holo/`, `network/`, `client/` hors
  modules). Elle ne modifie que les fichiers de sa section « Zone ». Elle peut ajouter des membres privés ou
  package-private, et corriger un comportement fautif décrit dans la carte.
- **`API : oui`** : la carte décrit dans ses livrables la nouvelle API (types, signatures, packages déplacés) et
  l'alternative. **Seule une validation de l'utilisatrice**, consignée en FD, autorise la tâche. L'agent implémente
  l'API validée, et rien de plus.
- Dans les deux cas, un besoin imprévu de changer une signature publique, d'ajouter un paquet réseau ou de toucher un
  module hors zone oblige l'agent à **s'arrêter** et à le décrire. L'orchestrateur consigne la demande dans
  `decisions.md` et la soumet à l'utilisatrice.
- Le protocole réseau (`HoloNetwork`, `"9"`) ne change que si une carte le prévoit et que l'utilisatrice l'a validé.
  Tout changement de format d'un paquet du framework l'incrémente.
- Le format de sauvegarde reste lisible. Une carte qui change un format NBT doit relire l'ancien format (migration),
  et la relecture est testée.

## Conventions rappelées

- Celles de `.claude/CLAUDE.md`. Commentaires et javadoc en **français sans accents** ; accolades sur une nouvelle
  ligne ; une `package-info.java` par package non trivial (les documents de ce dossier, eux, sont accentués).
- Code commun : aucune classe client. Le client passe par un `Backend` installé au setup client.
- Rendu : `RenderType` / `MultiBufferSource` / `RenderSystem` uniquement, aucune allocation par frame, état GL
  restauré (y compris sur exception), renderers enregistrés par `HoloRenderers` / `HoloObjectRenderers`.
- **Refactorisation** (découpes, dédoublonnage) : aucun changement de comportement. Le compte rendu liste les
  méthodes déplacées et leur nouvel emplacement. Les tests existants passent sans modification de leurs assertions.
- **Performance** : la carte fournit une mesure **avant / après**, avec le scénario, la méthode et les chiffres :
  FPS moyen et minimum, temps par tick (`/debug start` / `stop`, ou F3 + profileur), octets envoyés.
- Build : `JAVA_HOME=~/.jdks/temurin-17.0.17 ./gradlew compileJava`. Jeu : `... ./gradlew runClient` (`runClient2`
  pour un second joueur, monde ouvert en LAN).
