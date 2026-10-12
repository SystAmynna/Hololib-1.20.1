# Suivi

Tenu par l'orchestrateur. Statuts : `à faire`, `en cours`, `à vérifier en jeu`, `faite`, `bloquée`. Pour une tâche
bloquée, le motif va dans Notes, et la demande dans [`decisions.md`](decisions.md) s'il s'agit d'une question d'API.

**API** : `non` = aucune signature publique du framework ne change ; `oui` = demande d'API à valider avant de
commencer (README, « Règle d'API »).

Créé le 2026-10-12 à partir de [`rapport_mod1.md`](../../rapport_mod1.md).

## Lot A : bugs et robustesse

| Tâche | Titre | Dépend de | API | Statut | Notes / écarts |
|---|---|---|---|---|---|
| [F01](taches/F01-inspect-permission.md) | `/hololib_inspect` limité à l'exécutant hors force | — | non | à faire | |
| [F02](taches/F02-taille-actions.md) | Taille des actions : borne réelle, `source` borné, contrôle à l'envoi | — | non | à faire | |
| [F03](taches/F03-handle-move.md) | `handleMove` / `handleOpen` soumis aux règles | — | non | à faire | |
| [F04](taches/F04-dechargement-niveau.md) | Déchargement de niveau : fermeture des positions, tables vidées | — | non | à faire | |
| [F05](taches/F05-world-renderer-finally.md) | `try/finally` dans `WorldRenderer.renderAll`, erreurs des passes | — | non | à faire | |
| [F06](taches/F06-edit-range-serveur.md) | `editRange` vérifiée côté serveur | — | non | à faire | |
| [F07](taches/F07-facades-serveur-integre.md) | Façades client sûres sur le serveur intégré | — | non | à faire | |
| [F08](taches/F08-contexte-capture.md) | Contexte de capture et captures imbriquées | — | non | à faire | |
| [F09](taches/F09-petits-correctifs.md) | Petits correctifs (Y des coins, partialTick, caméra des overlays, masque) | — | non | à faire | |
| [F10](taches/F10-docs-obsoletes.md) | Documentation obsolète | — | non | à faire | |

## Lot B : structure, cloisonnement, qualité

| Tâche | Titre | Dépend de | API | Statut | Notes / écarts |
|---|---|---|---|---|---|
| [F11](taches/F11-ordre-rendu.md) | Ordre explicite des renderers et des captors | — | oui | à faire | |
| [F12](taches/F12-api-transversales.md) | `Configurable`, `QuickActionProvider`, `TargetingThroughWalls` dans `holo/` | — | oui | à faire | |
| [F13](taches/F13-style-fenetres.md) | `HoloWindowStyle` | — | oui | à faire | après F12 conseillé (mêmes fichiers) |
| [F14](taches/F14-cycle-pilot-controller.md) | Casser le cycle `spacepilot` ↔ `spacecontroller` | — | non | à faire | |
| [F15](taches/F15-edition-decouplee.md) | Édition découplée du cœur client | F26 | oui | à faire | |
| [F16](taches/F16-recherche-modules.md) | `HoloContainer.find(Class)` et cache client | — | oui | à faire | |
| [F17](taches/F17-branchement-client.md) | Un seul style de branchement client | F16 | non | à faire | |
| [F18](taches/F18-object-state.md) | `ObjectState` et version de format NBT | F25 | oui | à faire | |
| [F19](taches/F19-accepts-action.md) | Crochet `acceptsAction` | F02 | oui | à faire | |
| [F20](taches/F20-decoupe-environment.md) | Découper `SpaceEnvironmentModule` | F14, F25 | non | à faire | |
| [F21](taches/F21-decoupe-configurator-map.md) | Découper `SpaceConfiguratorModule` et `SpaceMapModule` | F20 | non | à faire | |
| [F22](taches/F22-decoupe-renderers-space.md) | Découper `SpaceMapCubeRenderer` et `SpaceKnowledgeSkyRenderer` | F23 | non | à faire | |
| [F23](taches/F23-utilitaires-rendu.md) | `client/render/util` partagé | — | oui | à faire | |
| [F24](taches/F24-placement-unique.md) | Placement unique ponts / picking | F23, F26 | non | à faire | |
| [F25](taches/F25-tests-space-math.md) | Tests : JUnit + `space/math` | — | non | à faire | |
| [F26](taches/F26-tests-edition.md) | Tests : édition, texte, Area | F25 | non | à faire | |
| [F27](taches/F27-demos-optionnelles.md) | Démos optionnelles | — | non | à faire | |
| [F28](taches/F28-decoupe-socle.md) | Découper `HoloContainers`, `HoloEditSession`, `HoloEditTools` | F03, F06, F15 | non | à faire | |
| [F29](taches/F29-nettoyage-socle.md) | Nettoyage du socle | F28 | non | à faire | |

## Lot C : performance

| Tâche | Titre | Dépend de | API | Statut | Notes / écarts |
|---|---|---|---|---|---|
| [F30](taches/F30-terrain-area-gpu.md) | Terrain de l'Area Displayer en `VertexBuffer` | — | non | à faire | |
| [F31](taches/F31-etoiles-buffer.md) | Fond étoilé Space en buffer statique | F23 | non | à faire | |
| [F32](taches/F32-cache-positions.md) | Cache des positions par frame, picking borné | F24 | non | à faire | |
| [F33](taches/F33-area-scanner-feed.md) | `AreaScanner` et `AreaFeed` | — | non | à faire | |
| [F34](taches/F34-caches-serveur.md) | Caches serveur (chunks, ids, droits, gardes) | F16 | non | à faire | |
| [F35](taches/F35-caches-client.md) | Caches et boucles côté client | F15, F17 | non | à faire | |
| [F36](taches/F36-encodage-partage.md) | Encodage réseau partagé entre destinataires | F18 | oui si format changé | à faire | |

## Lot final

| Tâche | Titre | Dépend de | API | Statut | Notes / écarts |
|---|---|---|---|---|---|
| [F37](taches/F37-integration.md) | Intégration, documentation, recette | toutes | non | à faire | |

## Correspondance rapport → tâches

| Rapport | Recommandation ou constat | Tâche |
|---|---|---|
| § 8 rec. 1, § 7.1-1 | inspection | F01 |
| § 8 rec. 2, § 7.1-2 | taille des actions | F02 |
| § 8 rec. 3, § 7.1-3 | `handleMove` / `handleOpen` | F03 |
| § 8 rec. 4, § 7.1-4 | déchargement de niveau | F04 |
| § 8 rec. 5, § 7.2 | `WorldRenderer` sans `try/finally` ; politique d'erreur des passes | F05 |
| § 8 rec. 6, § 7.2 | `editRange` serveur | F06 |
| § 7.2 | `HoloTextMetrics` / `HoloThemes` depuis le serveur intégré | F07 |
| § 7.2 | contexte de capture partagé | F08 |
| § 7.3 | Y des coins, `partialTick`, caméra des overlays, `HoloOutlineMask`, listage de `SpaceTextureServer` | F09 |
| § 8 rec. 7, § 5.4 | documentation obsolète | F10 |
| § 8 rec. 8, § 4.5 | ordre des renderers et des captors | F11 |
| § 8 rec. 9, § 4.2 | API transversales hébergées dans des modules | F12, F13 |
| § 8 rec. 10, § 4.3 | cycle `spacepilot` ↔ `spacecontroller` | F14 |
| § 8 rec. 11, § 4.4 | édition tissée dans le cœur client | F15 |
| § 8 rec. 12, § 4.6 | recherche des modules ; deux styles de branchement | F16, F17 |
| § 8 rec. 13, § 5.3, § 7.2 | `HoloObject.restore` ; pas de version NBT | F18 |
| § 3.4, § 7.2 | actions non sourcées de dimension ; vérifications redondantes | F19 |
| § 8 rec. 14, § 5.2 | god classes Space | F20, F21, F22 |
| § 8 rec. 15, § 5.3 | utilitaires de rendu ; placement dupliqué | F23, F24 |
| § 8 rec. 16, § 5.6 | tests | F25, F26 |
| § 8 rec. 17, § 4.6 | démos livrées | F27 |
| § 5.2 | `HoloContainers`, `HoloEditSession`, `HoloEditTools` | F28 |
| § 5.3, § 5.5 | duplications et nommage du socle ; code mort | F29 |
| § 8 rec. 18, § 6.1 | terrain Area ré-émis chaque frame | F30 |
| § 8 rec. 19, § 6.1 | étoiles ré-émises chaque frame | F31 |
| § 8 rec. 20, § 6.1 | positions `ResolvedHolo`, inversion avant portée | F32 |
| § 8 rec. 21, § 6.3 | `AreaScanner`, `AreaFeed` | F33 |
| § 8 rec. 22, § 6.3 | `onChunkWatch`, `HoloModuleType.id()`, `updateAccess`, AttackGuard | F34 |
| § 8 rec. 23, § 6.1, § 6.2 | itérateurs, `mayEdit`, `HoloEditPanel`, en-têtes de la Map, `client()` Space, `drag`, découpe des orbites | F35 |
| § 8 rec. 24, § 6.3 | encodage de la synchronisation | F36 |
| § 7.2 | registre `disableSync`, protocole fixe | F37 (documenté comme limite, décision à prendre) |
| — | intégration, CLAUDE.md, recette | F37 |

## Journal

| Date | Tâche | Événement |
|---|---|---|
| 2026-10-12 | — | Dossier créé (37 tâches) |
