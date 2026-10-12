# Décisions

Journal des décisions des correctifs.

| Préfixe | Sens |
|---|---|
| **FD** | validée par l'utilisatrice |
| **FC** | choix par défaut de l'orchestrateur, non bloquant, modifiable sur simple demande |
| **FL** | limite connue acceptée |

Toute décision prise pendant une tâche est ajoutée ici par l'orchestrateur. Les D / C / L de
[`docs/space/decisions.md`](../space/decisions.md) restent valables. Une tâche qui en contredit une le signale, et la
décision Space est annotée.

## Validées par l'utilisatrice

| # | Décision | Conséquence |
|---|---|---|
| — | (aucune pour l'instant) | |

## Choix par défaut (non bloquants)

| # | Choix | Alternative si l'utilisatrice le demande |
|---|---|---|
| FC1 | Tâches préfixées **F**, décisions **FD / FC / FL** | — |
| FC2 | (F02) Contenu d'une action borné à **32 000 octets** ; `source` lu avec `readUtf(64)` ; `HoloModule.sendAction` refuse côté client une action trop grosse (exception claire, rien n'est envoyé) | découper les grosses actions en plusieurs paquets |
| FC3 | (F01) Hors `/hololib_force_inspect`, `/hololib_inspect` ne modifie que l'inspection **de l'exécutant** : la forme `<joueurs> <cibles>` exige que `<joueurs>` soit l'exécutant seul, sinon refus | réserver la forme à deux arguments au niveau 2 |
| FC4 | (F03) `handleMove` refuse tout changement de **famille** d'ancrage (Sky, World, Local, Hud) et soumet le transform à `HoloEditRules.accepts` comme une édition ; `handleOpen` n'accepte que les objets qui s'ouvrent (`HoloRelativeToOpen` ou objet qui déclare `opened`) | garder `handleMove` libre et le restreindre à une liste blanche d'objets |
| FC5 | (F27) Démos derrière une option de configuration **commune** `enableDemos` (`ForgeConfigSpec`, fichier `hololib-common.toml`), **vraie par défaut en développement** (`!FMLLoader.isProduction()`), fausse sinon ; les types restent enregistrés (sauvegardes lisibles), seule leur installation par commande est refusée et leurs renderers ne sont pas enregistrés | retirer les démos du jar de production (source set séparé) |
| FC6 | (F25) Tests en **JUnit 5** (`org.junit.jupiter`), sans dépendance de mock | — |
| FC7 | (F11) Ordre de rendu par **couches nommées** (enum par famille) plutôt que par entier libre | priorité entière |
| FC8 | (F04) Les tables statiques par marker (`SpaceMapModule`, `AreaDisplayerModule`) sont vidées à `ServerStoppedEvent`, et chaque entrée est retirée à la fermeture de son conteneur | tables remplacées par `HoloContainer.find` (F16) |
| FC9 | (F06) `editRange` vérifiée côté serveur avec une **marge de 2 blocs** (latence, interpolation du joueur) | marge nulle |

## Limites connues

| # | Limite |
|---|---|
| FL1 | La synchronisation par **deltas** est hors périmètre. F36 ne fait que partager l'encodage entre destinataires ; les deltas feront l'objet d'une étude dans son compte rendu |
| FL2 | Les numéros de ligne du rapport datent du 12 octobre 2026 ; les cartes citent aussi le nom des méthodes |

## Demandes d'API en attente

Cartes dont le champ **API** vaut `oui`. Chacune n'est lancée qu'après validation (README, « Règle d'API »). La
question à trancher est rappelée ici. Le détail (signatures, alternative) est dans la carte.

| Carte | Question à trancher |
|---|---|
| [F11](taches/F11-ordre-rendu.md) | Couches nommées dans `HoloRenderers.register(renderer, layer)` et `HoloControlCapture.addPassive(captor, priority)` (FC7) ? Ou un seul abonné de setup qui enregistre tout dans l'ordre ? |
| [F12](taches/F12-api-transversales.md) | Déplacer `Configurable` + `ConfigWindow`, `QuickActionProvider` + `QuickAction` + `QuickActionIcon` et `TargetingThroughWalls` dans `holo/config`, `holo/quickaction`, `holo/targeting` ? (Change le package public de ces types.) |
| [F13](taches/F13-style-fenetres.md) | Nouvelle classe publique `holo/window/HoloWindowStyle`, utilisée par toutes les fenêtres de module à la place de `ConfigWindow.ACCENT` / `BACKGROUND_ALPHA` ? |
| [F15](taches/F15-edition-decouplee.md) | Politiques installables (`HoloRoutingPolicy`, `HoloMirrorPolicy`, `HoloPickingOverride`, `HoloInteractionPolicy`) entre le cœur client et `client/edit` ? |
| [F16](taches/F16-recherche-modules.md) | `HoloContainer.find(Class<T>)` / `find(HoloModuleType<T>)` publics et cache client `ClientHoloWorld.localModule(type)` ? |
| [F18](taches/F18-object-state.md) | `ObjectState` public à la place de `HoloObject.restore(...)` ; entier `format` dans le NBT des mémoires ? |
| [F19](taches/F19-accepts-action.md) | `HoloModule.acceptsAction(ServerPlayer, HoloObject)` avec un défaut restrictif (op pour une action non sourcée de dimension ou de position) ? (Change le comportement des modules qui n'ont pas de vérification propre.) |
| [F23](taches/F23-utilitaires-rendu.md) | Nouveau package public `client/render/util` (base, vertex, quad plein écran, rotation céleste) ? |
| [F36](taches/F36-encodage-partage.md) | Seulement si le format réseau change : nouveau protocole (`"10"`) ? |
