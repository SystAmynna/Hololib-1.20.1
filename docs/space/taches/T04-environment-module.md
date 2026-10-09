# T04 — Module Space Environment

**Objectif** : le module de dimension qui porte la galaxie, le système courant et la pose du monde, simule le
mouvement des deux côtés et expose l'API serveur utilisée par le Controller et le Configurator. Pas de rendu (T05).

**Dépendances** : T01, T02.

## À lire avant
- [`02-architecture.md`](../02-architecture.md) §§ 3 à 5 ; [`05-synchronisation.md`](../05-synchronisation.md) en
  entier ; [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 6, 7 ; [`04-mathematiques.md`](../04-mathematiques.md)
  § 5.
- `holo/module/HoloModule.java` (cycle de vie, `sharedWithObservers`, `markDirty`, `handleAction`).
- `modules/dimension/planet1/Planet1Module.java` (module de dimension minimal) ; `modules/HoloLibModules.java`.
- `holo/memory/HoloContainers.java` (`dimension(ServerLevel)`, `position(String)`), `holo/memory/HoloContainer.java`
  (`modules()`, `flush`, `isShared`).
- Patron backend : `modules/camera/config/ConfigWindow.java` (`Backend`, `install`) et
  `client/modules/camera/config/ConfigController.java:46`.
- `client/holo/HoloModes.java` (`find`), `client/holo/ClientHoloWorld.java` (`find(HoloScope)`).

## Livrables
- `modules/dimension/space/SpaceEnvironmentModule.java`.
- `client/modules/dimension/space/SpaceEnvironmentClient.java` (+ `package-info.java`) : installe le backend
  (`clientInstance()` = miroir de la dimension du joueur local).
- `modules/HoloLibModules.java` : enregistrement `space_environment` (`DIMENSION`).

## Spécification
- **État** (doc 02 § 5) ; `load` d'une galaxie vide → `SpaceGalaxy.createDefault()` et pose par défaut.
- **`sharedWithObservers()` → `true`** (le module n'a aucun objet ; sans cela aucun client ne le reçoit).
- `onLoad` : rien à créer (pas d'objet). Garder le module sans objet.
- **`writeSync` / `readSync`** : blocs chaud et froid du doc 05 § 2, bloc froid préfixé de sa longueur et de sa
  révision, sauté par le client si la révision est connue. `forOwner` sans effet (pas de propriétaire).
- **`serverTick(level)`** : un pas `SpaceMotion.step` ; gestion du pilote (libération : joueur absent ou hors de la
  dimension, marker du Controller disparu — `HoloContainers.position(id) == null` —, silence > 60 puis > 200 ticks) ;
  politique d'envoi du doc 05 § 2 ; `T0` = `level.getGameTime()` de l'état envoyé.
- **`clientTick`** : dead reckoning (rejeu depuis `T0`, puis un pas par tick), pose précédente conservée, correction
  douce ; pour le pilote local, prédiction et réconciliation lente (doc 05 § 2).
- **API serveur publique** :
  - `static SpaceEnvironmentModule of(ServerLevel level)` (doc 02 § 4) ;
  - pilotage : `claimPilot(ServerPlayer, String markerId) → boolean`, `releasePilot(UUID)`,
    `pilotInput(ServerPlayer, SpacePilotInput)`, `pilot()` ;
  - lecture : `galaxy()` (lecture seule), `galaxyRevision()`, `currentSystemId()`, `pose()`, `speed()`,
    `nearestBody(...)`, réglages ;
  - édition : une méthode par opération du doc 03 § 6, qui délègue au modèle, puis incrémente la révision et
    `markDirty()` si le bloc froid change ; `setCurrentSystem` et le repli du courant sur `systems().get(0)` après
    `removeSystem` (ou après un `load` qui a perdu le système courant) sont à coder ici (T01 : le modèle les ignore) ;
    `SpaceMotion.step` reçoit le temps de **fin** du pas (`settings.days(gameTime + 1, 0)`), même convention en rejeu
    client ; `galaxy()` public renvoie `galaxy.systems()` (lecture seule), pas la `SpaceGalaxy` ; les `read` réseau du modèle
    lèvent : `readSync` rattrape et logue ; réglages (`setForwardAxis`, `setMaxSpeed`, `setTimeScale`...).
- **API client** : `static SpaceEnvironmentModule client()`, `pose(partialTick, SpacePose dest)`,
  `currentSystem()`, `backgroundStars()`, `bodyPositions(partialTick, Vector3d[] dest)`, `isLocalPilot()`,
  `predictPilotInput(SpacePilotInput)`.
- `save` : galaxie, système courant, pose, vitesse, réglages, révision. Pas le pilote.

## Interdits
- Objets holographiques dans ce module. Rendre public `HoloContainer.find`. Modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : `/hololib_module add dimension here hololib:space_environment` ; quitter / revenir dans le monde :
  pas d'erreur dans le log, l'état est rechargé (galaxie par défaut présente). `/hololib_module remove ...` propre.
- Second client (`runClient2`) connecté : il reçoit le module (vérifiable par un log de débogage temporaire, retiré
  ensuite, ou attendre T05).

## Compte rendu attendu
Fichiers ; signatures publiques (T05, T06, T09, T11 s'appuient dessus) ; format exact de `writeSync` ; écarts.
