# T09 — Module Space Configurator (socle)

**Objectif** : le module de marker réservé aux ops : cube éditable, fenêtre à sa base qui le suit, contrôle op,
mode galaxie / système, copie synchronisée des données de l'Environment. Le rendu du contenu du cube est T10,
l'édition T11 et T12.

**Dépendances** : T04.

## À lire avant
- [`01-vision.md`](../01-vision.md) § Space Configurator ; [`02-architecture.md`](../02-architecture.md) §§ 3, 4, 7,
  9 ; [`04-mathematiques.md`](../04-mathematiques.md) § 7 (repère du cube, échelle uniforme) ;
  [`05-synchronisation.md`](../05-synchronisation.md) § 4 ; [`decisions.md`](../decisions.md) C4, C9, C11.
- `holo/edit/HoloEditable.java`, `HoloEditRules.java` (`mayEdit`, `accepts`), `demo/DemoWorldPosModule.java` +
  `demo/DemoCube.java` (objet `World` éditable, `opRequired`).
- `holo/memory/HoloContainers.java` (`handleEdit` : application directe du transform, pas de callback).
- `holo/object/HoloObject.java` (`shown()`, `setAccess`, `editRange`), `HoloAccess.java` (`players(...)`).
- `modules/camera/config/ConfigListWindow.java` (lignes reconstruites par `sync(...)`, appelé chaque tick client par
  `ConfigController`, seulement si l'ensemble a changé ; sinon recoloration), `ConfigWindow.java` (style).

## Livrables
- `modules/monde/spaceconfigurator/` : `package-info.java`, `SpaceConfiguratorModule`, `SpaceMapCube`,
  `SpaceConfiguratorWindow` (squelette : titre, mode, nom du système sélectionné, boutons désactivés en attendant
  T11) ; backend « joueur local op ».
- `client/modules/monde/spaceconfigurator/SpaceConfiguratorClient.java` (+ `package-info.java`) : installe le backend
  (`Minecraft.getInstance().player.hasPermissions(2)`).
- Renderer **provisoire** du cube (arêtes seulement) si T10 n'est pas faite dans la foulée : sinon l'objet n'est pas
  dessiné (avertissement « pas de renderer »).
- `modules/HoloLibModules.java` : `space_configurator` (`POSITION`).

## Spécification
- `SpaceMapCube` : `World()`, transform par défaut `(0, 2, 0)` échelle 1,5 (cube de 3 blocs), `PUBLIC`,
  `HoloEditTranslation + HoloEditRotation + HoloEditScale`, `opRequired() = true`, `ownRequired() = false`,
  `boundingRadius = √3`. `shown()` = backend op.
- `SpaceConfiguratorWindow` : `World()`, posée devant la base du cube et tournée comme lui (yaw seulement),
  **non éditable** ; replacée par le module quand le cube bouge (sondage du doc 02 § 7 / doc 04 § 7) ; `shown()` = op.
  Accès = `HoloAccess.players(<ops connectés>)`, recalculé dans `serverTick` et réaffecté seulement s'il change.
- Échelle uniforme imposée (C11) après une édition.
- État : mode (`GALAXY` / `SYSTEM`), système sélectionné, corps sélectionné, cadrage galaxie `(c, H)`, révision de
  l'Environment déjà copiée, copie des en-têtes de tous les systèmes, copie du système édité. Sauvegardé : mode,
  sélections, cadrage (la copie est recalculée).
- `serverTick` : `SpaceEnvironmentModule.of(level)` ; si sa révision a changé → recopie et `markDirty()` ; sinon rien.
- `handleAction` : **toujours** vérifier `player.hasPermissions(2)` en premier (refus silencieux sinon), puis
  `source` attendu. Actions de ce socle : sélectionner un système, changer de mode, « Recadrer ».

## Interdits
- Stocker la galaxie de référence dans le Configurator (seulement une copie). Modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (op) : marker + `space_configurator` → cube et fenêtre visibles ; le cube se déplace / tourne / se met à
  l'échelle avec l'éditeur (G, module `world_editor`) et la fenêtre le suit ; un étirement non uniforme est corrigé.
- `runClient2` non-op : ne voit ni cube ni fenêtre ; une action forgée serait refusée (relecture du code).

## Compte rendu attendu
Fichiers ; format de l'état synchronisé ; méthode de sondage du cube ; écarts.
