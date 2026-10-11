# Dossier de construction — modules Space

Ce dossier pilote la construction de six modules liés de Holo Lib (deux nouveaux au lot 3) :

| Module | Id | Compatibilité | Rôle |
|---|---|---|---|
| Space Environment | `hololib:space_environment` | `DIMENSION` | simule la galaxie et le système courant, dessine le ciel, porte la pose du monde (le « vaisseau ») et le saut |
| Space Controller | `hololib:space_controller` | `POSITION` (marker) | poste de pilotage : gaz, roulis, joystick au regard, saut vers une étoile |
| Space Map | `hololib:space_map` | `POSITION` (marker), tous | **lot 3** : carte (cube : galaxie, systèmes, vaisseau, croix) et fenêtre de navigation (arborescence, recentrage, zoom) |
| Space Configurator | `hololib:space_configurator` | `POSITION` (marker), op | fenêtre d'édition et poignées ; **lot 3** : branché sur la Space Map du même marker (avant : carte comprise) |
| Space Pilot | `hololib:space_pilot` | `PLAYER` | aides au pilotage : joystick, lien, saut, HUD de navigation et vitesse de croisière (**lot 3** : HUD seulement aux commandes, surcouche cédée à Space Knowledge) |
| Space Knowledge | `hololib:space_knowledge` | `PLAYER` | **lot 3** : connaissance du ciel : anneaux, orbites, nom et distance au regard (Config, Quick Action) |

**Lot 1** (T01–T13, T07b–T07f) : fait, commit `21f21b1` ; ses cartes de tâche sont dans l'historique git.
**Lot 2** (T14–T27) : saut interstellaire, module Space Pilot, persistance, systèmes réels, Configurator revu ; fait,
commit `dd32493`, à vérifier en jeu ; ses cartes de tâche sont dans l'historique git.
**Lot 3** (T28–T46, demandé le 2026-10-10) : rotation propre et textures des astres, fond étoilé procédural,
distorsion redessinée, contrôles (Sneak, Sprint), Space Knowledge et vitesse de croisière, sons, Space Map séparée du
Configurator, import de textures ; compléments ajoutés le même jour (T38–T41, D28–D31) : temps ÷ 10, masse des
astres et référentiel par sphère d'influence, lunes repérées de près, fenêtre du Controller masquée pendant le
pilotage ; ajout du 2026-10-11 (T43, D33) : cibler un astre depuis la Space Map (anneau vert et flèche pour le
pilote), insérée avant l'édition des astres (T43–T45 renumérotées T44–T46). T28–T45 faites, à vérifier en jeu
(notes dans [`suivi.md`](suivi.md) ; cartes T28–T34 retirées) ; T46 (intégration et documentation) clôt le lot.

Le développement est découpé en **tâches** (`taches/`) exécutées une par une par des sous-agents, sous la
supervision d'un agent orchestrateur. Les documents numérotés sont la **référence** : une carte de tâche ne répète
pas ce qu'ils disent, elle y renvoie.

## Ordre de lecture

1. [`01-vision.md`](01-vision.md) — ce que l'utilisatrice veut, en termes de jeu (lots 1 à 3).
2. [`02-architecture.md`](02-architecture.md) — packages, classes, flux, API du mod à réutiliser.
3. [`03-modele-donnees.md`](03-modele-donnees.md) — galaxie, systèmes, corps, orbites, unités, bornes, formats.
4. [`04-mathematiques.md`](04-mathematiques.md) — repères, « le monde est la caméra », orbites, projection, pilotage,
   saut, surcouche.
5. [`05-synchronisation.md`](05-synchronisation.md) — état froid / chaud, dead reckoning, budget réseau, pilote, saut.
6. [`decisions.md`](decisions.md) — décisions validées, choix par défaut, limites, demandes d'API en attente.
7. [`donnees-systemes.md`](donnees-systemes.md) — valeurs réelles des systèmes par défaut (T16 ; rotation et
   textures : T28, T29).
8. [`suivi.md`](suivi.md) — avancement des tâches.

Le fonctionnement général du mod est décrit dans `.claude/CLAUDE.md` (à jour) et `rapport.md` (détaillé, parfois en
retard). En cas de doute, **le code fait foi** ; pour Minecraft / Forge, lire les sources mappées (chemin dans
CLAUDE.md) plutôt que deviner.

## Graphe des tâches (lot 3, reste à faire)

```
T35 distorsion ─> T36 sons
T37 Space Map ─────────────────────────────> T42 Configurator branche ─> T43 cibler un astre
T38 temps / 10 ─> T39 masse + sphere d'influence ─┘ (champ Masse repris par T42 / T44)
T42 ─> T44 edition astres ─> T45 import texture   (T44 apres T43 dans l'ordre, sans en dependre)
T40 lunes de pres
T41 fenetre du Controller masquee
                                                                       toutes ──> T46 integration
```

(T28–T34, faites : T28 → T29 → T30 → T37 ; T28 → T31 → T35 ; T32 → T36 ; T32 + T33 → T34.)

Ordre par défaut : **séquentiel**, T35 → T46 (T42, T44, T45, Configurator, reportées après les compléments à la
demande de l'utilisatrice ; T43, ciblage, placée avant l'édition des astres à sa demande).
T38–T41 sont indépendantes entre elles ; T38 avant T39 est conseillé (même `SpaceEnvironmentModule`).
Groupes qui touchent surtout des packages disjoints : Distorsion et sons (T35–T36), Carte (T37, T42–T45), compléments
(T38–T41). Fichiers communs à fusionner à la main si des groupes avancent en parallèle (worktrees) :
`SpaceEnvironmentModule` (T35, T36, T38, T39, T43), `SpaceSkyRenderer` (T35), `SpaceMotion` / `SpaceBody` (T39),
`SpaceKnowledgeSkyRenderer` (T35, T40), `SpacePilotPad` / `SpaceControllerWindow` (T41), Configurator (T39,
T42, T44), `SpaceMapModule` / `SpaceMapWindow` / `SpaceMapTreeRow` (T37, T42, T43), renderers de Space Pilot (T43),
`HoloLibModules`, `ModuleRenderers`.

## Protocole de l'orchestrateur

L'orchestrateur est la session principale (celle qui parle avec l'utilisatrice). Pour chaque tâche :

1. Choisir dans [`suivi.md`](suivi.md) la première tâche « à faire » dont les dépendances sont « faites » ou « à
   vérifier en jeu ».
2. **Demander à l'utilisatrice** s'il faut la commencer ou mettre le processus en pause (une question à chaque tâche).
3. La passer « en cours » et lancer **un** sous-agent `general-purpose` avec le prompt type ci-dessous.
4. À son retour : lire le compte rendu, relire les fichiers qu'il liste (**aucune commande git**, même en lecture :
   l'utilisatrice gère git), vérifier les
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
5. N'utilise aucune commande git (meme en lecture). Rends le compte rendu demande par la carte (fichiers
   crees/modifies, ecarts a la carte et pourquoi, points a verifier en jeu, questions).
```

## Règle d'API (non négociable)

L'API et l'architecture actuelles du mod ne changent **pas**. Sont autorisés, et seulement eux :

- créer et modifier des classes dans les **packages Space** :
  `modules/dimension/space/**`, `modules/monde/spacecontroller/**`, `modules/monde/spaceconfigurator/**`,
  `modules/camera/spacepilot/**`, et au lot 3 `modules/monde/spacemap/**`, `modules/camera/spaceknowledge/**`, et
  leurs miroirs `client/modules/...` et `client/render/modules/...` ;
- (lot 3) ajouter des **ressources** sous `src/main/resources/assets/hololib/textures/space/**`,
  `assets/hololib/sounds/space/**`, `assets/hololib/sounds.json`, avec leurs fichiers de crédits (`CREDITS.md`) ;
- (lot 3, **T45 seulement**, ex-T44, D27 validée) déclarer un canal réseau dédié aux textures Space (`SimpleChannel` propre,
  dans un package Space, enregistré par un abonné `FMLCommonSetupEvent` Space) ; `HoloNetwork` et son protocole ne
  changent pas ;
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
