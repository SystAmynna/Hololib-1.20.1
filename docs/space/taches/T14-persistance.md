# T14 — Persistance de la position et de l'état de l'Environment

**Objectif** : demande de l'utilisatrice (D12, D14) : « quand on déconnecte et reconnecte du monde / serveur ou qu'on
désactive / réactive le module Space Environment, la position dans l'espace est sauvegardée et restaurée ». Après
retrait puis réinstallation de l'Environment, **tout** son état revient (galaxie, système courant, pose, référentiel,
réglages). Lève L17.

**Dépendances** : aucune (lot 1 fait).

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) § 4 (NBT, archive), [`05-synchronisation.md`](../05-synchronisation.md)
  § 2 (politique d'envoi, « monde immobile : rien », persistance), [`decisions.md`](../decisions.md) C19, C37, L17.
- `modules/dimension/space/SpaceEnvironmentModule.java` : `onLoad`, `onUnload` (s'il existe), `save`, `load`, recalage
  C37 au premier tick, politique d'envoi dans `serverTick`.
- `holo/memory/dimension/DimensionHoloContainer.java` et `holo/memory/HoloContainer.java` : quand `save` d'un module est
  appelé, ce que fait `markPersistDirty`, ce qui se passe au retrait d'un module (`/hololib_module remove`) et à
  l'arrêt du serveur. `holo/module/HoloModule.java` : cycle de vie.
- `net/minecraft/world/level/saveddata/SavedData.java`, `DimensionDataStorage.java` (sources mappées) : `computeIfAbsent`,
  `setDirty`, moment de l'écriture.

## Livrables
1. **Diagnostic d'abord** (compte rendu) : reproduire par lecture du code (et banc jetable si utile) les cas
   « quitter / revenir en solo », « serveur LAN : client qui se déconnecte / reconnecte », « arrêt du serveur avec
   monde immobile » et « avec monde en mouvement ou piloté » ; dire pour chacun si la pose rechargée est juste, et
   sinon **pourquoi** (dernier `markDirty` trop ancien, recalage C37 faux, pilote non libéré...). Ne corriger qu'après.
2. **Corrections** de la reconnexion selon le diagnostic, dans l'Environment seulement (ex. `markDirty` à la libération
   du pilote ou à l'arrêt du mouvement s'il manque, sauvegarde de l'état au `onUnload`).
3. **Archive** `modules/dimension/space/SpaceArchive extends SavedData` (clé `hololib_space` dans le
   `DimensionDataStorage` du `ServerLevel` de la dimension) : contient la copie du tag `save` de l'Environment. Écrite
   (et `setDirty`) chaque fois que l'Environment est sauvegardé ou retiré. À l'installation d'un Environment **sans
   état** (installation par commande, `onLoad` serveur sans `load`), si l'archive existe, l'Environment s'y recharge
   (`load(tag)`) au lieu de créer la galaxie par défaut ; recalage C37 compris.
4. Le Configurator (C33) garde sa sélection : après restauration, les ids sont les mêmes (L17 levée).
5. Docs : 03 § 4 et 05 § 2 alignés sur ce qui est fait ; `decisions.md` L17 barrée « levée par T14 ».

## Interdits
- Modifier le framework (`holo/`, `HoloContainers`...) : si le retrait d'un module ne laisse aucune occasion d'écrire
  l'archive, s'arrêter et faire une **demande d'API**.
- Changer le format NBT existant de l'Environment (seulement ajouter des clés).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (scénario pour l'utilisatrice) : piloter loin de la Terre, s'arrêter, quitter, revenir → même ciel. Idem en
  mouvement (approximation C37 acceptée). Idem serveur LAN (client 2 qui se reconnecte). `/hololib_module remove
  dimension here hololib:space_environment` puis `add` → galaxie éditée, système, pose et réglages revenus ; le
  Configurator garde sa sélection.
- Une dimension sans archive se comporte comme avant (galaxie par défaut).

## Compte rendu attendu
Diagnostic (cas par cas, cause), corrections, fichiers créés / modifiés, clés NBT ajoutées, écarts, scénario en jeu.
