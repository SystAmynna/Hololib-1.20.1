# 05 — Synchronisation

## 1. Contrainte du framework

- `markDirty()` fait renvoyer **tout** `writeSync` en fin du tick serveur courant (`HoloContainers.onServerTick` :
  `tick()` puis `flush()` ; `network/HoloSyncData.java` `ModuleData.of`), à **tous** les observateurs de la mémoire. Pour une mémoire de dimension : tous les joueurs de la
  dimension. Il n'existe pas d'envoi partiel.
- Un module **sans objet `PUBLIC`** n'est envoyé aux observateurs que si `sharedWithObservers()` renvoie vrai.
  **L'Environment n'a aucun objet : il doit surcharger `sharedWithObservers()` → `true`**, sinon aucun client ne le
  reçoit.
- À l'arrivée d'un module (snapshot, `ADD`), le client applique `readSync` puis `onLoad` (**pas** `onSync`) ; à chaque
  mise à jour, `readSync` puis `onSync` (`client/holo/ClientHoloContainer.java`, `add` / `update`). Les deux utilisent
  le même `writeSync` : chaque envoi doit être **autonome** (pas de différentiel), sauf ce que le module sait que tous
  les destinataires ont déjà (bloc froid de l'Environment, § 2, T15b).
- **Qui appelle `writeSync`** (diagnostic T15b) : `writeSync(buf, forOwner)` ne connaît pas le destinataire
  (`forOwner` est toujours faux pour une mémoire de dimension) ; il est appelé (`HoloSyncData.ModuleData.of`) :
  1. par l'**envoi de fin de tick** (`HoloContainers.onServerTick`, phase `END` : `container.tick()` — donc
     `serverTick` — puis aussitôt `container.flush()`) quand `markDirty()` a été appelé : **un seul** tampon, envoyé
     à tous les joueurs de la dimension (`sendToDimension`) ;
  2. par un **instantané** pour un seul joueur (`observerSnapshot`, `sendDimension`) : connexion
     (`PlayerLoggedInEvent`), changement de dimension (`PlayerChangedDimensionEvent`), réapparition
     (`PlayerRespawnEvent`) ; ces événements sont postés pendant `tickChildren` (tick des niveaux ou des
     connexions), donc **avant** l'envoi de fin de tick du même tick, joueur déjà ajouté au niveau ;
  3. à l'**installation** du module (`HoloContainer.install`, commande) : `ADD` à tous les joueurs de la dimension.

  Un module qui ne marque son état sale que dans son propre `serverTick` sait donc reconnaître l'appel 1 (le premier
  `writeSync` qui suit son `markDirty`) ; tout autre appel est un instantané ou une installation.
- Actions client → serveur : `sendAction(source, writer)`, 32 767 octets max ; le serveur vérifie portée et accès
  avant `handleAction`.

## 2. Space Environment : froid / chaud

`writeSync` écrit, dans cet ordre (format de T15b, D16) : bloc chaud, en-tête, bloc froid **optionnel** :

| Bloc | Contenu | Taille typique |
|---|---|---|
| **Chaud** | tick de référence `T0` (`long`), pose `s` (3 `double`), `Q` (4 `double`), `V` signée (`double`), état du référentiel (`SpaceFrameState` : 5 `varint` — ref, précédent, ticks depuis bascule, candidat, ticks de candidature), entrée courante du pilote (7 octets : gaz, roulis, lacet, tangage, drapeaux — bit 1 frein, bit 2 saut, bit 3 boost (T32) —, visée yaw / pitch ; + id de la cible en varint si le drapeau du saut est levé, T21), présence + UUID du pilote, réglages (7 `double` : axe avant yaw / pitch, `V_max`, `turn`, `rollSpeed`, échelle de temps, **vitesse de distorsion** — T15, 8 o ; puis **époque de l'horloge** — T38, `long` + `double`, 16 o : 72 o), **saut** (T21, `SpaceJump.write` : phase en octet — 0 aucun, 1 charge, 2 voyage ; si ≠ `NONE` : cible varint, pas faits dans la phase varint, durée du voyage varint, `Q₀` 4 `double`, origine galactique 3 `double`, cible galactique 3 `double` : 81 o + 3 varints, 84 à 89 o ; 86 o pour Sol → Alpha du Centaure), **cible du vaisseau** (T43 : varint système, `0` = aucune ; + varint corps si système ≠ 0, `0` = le système entier : 1 o sans cible, 3 o avec des ids < 128, 10 o au pire théorique), **compteur de téléportations** (D34 : 1 octet, modulo 256) | **160 o** depuis D34 (+1 o) ; **159 o** sans pilote ni cible, **175 o** avec le pilote (158 / 174 mesurés en T38, + 1 o d'octet de cible ; + 2 o avec une cible) ; **244 o** pendant un saut sans pilote (243 o avant T43), 261 o avec le pilote qui tient le saut |
| **En-tête** | révision (varint : celle du système courant et des étoiles de fond de cet état), longueur `L` du bloc froid (varint, **0 = absent**) | 2 o (révision < 128 ; 3 o jusqu'à 16 383) |
| **Froid** (si `L > 0`) | système courant complet, étoiles de fond (`BackgroundStar`, avec id et nom depuis T15 : ≈ 31 o par étoile au lieu de 20, [`03`](03-modele-donnees.md) § 5), **graine du fond étoilé** (T31, `long`, 8 o ; le client ne la lit que si le bloc la contient) | 0,7 Ko (galaxie du lot 1, mesuré) ; 3,1 Ko (galaxie de T16, Sol courant : 34 corps + 11 voisines, 3 101 o mesurés en T15b) ; **3,7 Ko** depuis T28 (rotation propre et texture : +17 o par corps, 3 679 o mesurés) ; 3 687 o depuis T31 (+8 o de graine) ; **3 959 o** depuis T39 (masse : +8 o par corps, Sol +272 o, mesurés) ; ≈ 21,8 Ko au pire (§ 5) |

**Inclusion du bloc froid** (serveur, T15b, D16) :
- **Instantané ou installation** (tout `writeSync` qui n'est pas l'envoi de fin de tick, § 1) : **toujours**. Un
  joueur qui arrive (connexion, changement de dimension, réapparition, reconnexion) reçoit donc la galaxie dans le
  même paquet que son premier état.
- **Envoi de fin de tick** (le `writeSync` qui suit le `markDirty` de `serverTick`, drapeau `flushExpected`) :
  seulement si la révision a changé depuis le dernier bloc froid diffusé (édition au Configurator, restauration T14,
  changement de système courant, arrivée d'un saut T21), ou s'il est **forcé** :
  - arrivée d'un joueur **sans instantané reconnu** : le serveur suit les joueurs du niveau (`level.players()`, à
    chaque tick) ; plus d'arrivants que d'instantanés écrits depuis le tick précédent → bloc froid forcé dans le
    prochain envoi (au plus 4 ticks). Filet de sécurité : en pratique chaque arrivée a son instantané ;
  - **demande d'un client** qui a reçu un état sans le bloc froid de sa révision (action `ACTION_REQUEST_COLD`,
    au plus une fois toutes les 20 ticks des deux côtés ; l'envoi part à toute la dimension, il n'y a pas d'envoi
    ciblé).
- Personne dans la dimension : la révision courante est réputée connue de tous (le prochain arrivant la recevra dans
  son instantané) ; rien n'est diffusé pour une restauration ou une édition faite sans joueur.

**Fond étoilé (T31, D22, C72)** : seule la graine circule, dans le bloc froid ; chaque client génère le même champ
(`SpaceStarField.generate(seed)`, SplitMix64 et `StrictMath` : mêmes étoiles sur toute JVM, vérifié par empreinte
sous Java 17 et 26), une fois par graine, à la première lecture (`SpaceEnvironmentModule.starField()`). La graine ne
change qu'avec la galaxie (création, restauration d'archive) : toujours avec un changement de révision, donc jamais
sans son bloc froid.

Toute édition qui change la révision diffuse donc le bloc froid **une fois** (au plus une fois tous les 4 ticks pour
le système courant et les étoiles de fond, toutes les 20 ticks pour les autres éditions), puis plus rien tant qu'il ne
change pas. En régime courant (croisière, manœuvre), seuls le bloc chaud et l'en-tête circulent.

**Client** : si la révision annoncée est celle du bloc froid connu, un bloc froid joint (diffusion forcée) est
**sauté** (`L` octets). Sinon, un bloc froid joint est lu ; s'il n'y en a pas (ou s'il est invalide), l'état est
**ignoré** : le miroir n'est plus `synced()`, le ciel est réduit au fond (`SpaceSkyRenderer`), le Controller et le pad
attendent, et le client redemande le bloc froid ; le premier état complet le resynchronise (saut). Aucun lecteur client
ne plante sans bloc froid : le ciel teste `synced()` et `currentSystem()`, le Controller et le pad testent `synced()`,
le Configurator ne lit que `settings()` (sa copie de la galaxie est la sienne, rafraîchie par le serveur).

### Politique d'envoi (serveur, `serverTick`)

| Événement | Envoi |
|---|---|
| Édition de la galaxie touchant le système courant ou la liste des étoiles de fond, changement de système courant, réglage | `markDirty()` au tick suivant, au plus une fois tous les 4 ticks (glisser une poignée enverrait sinon ~7 Ko par tick) ; bloc froid joint si la révision a changé (pas pour un réglage seul) |
| Arrivée d'un joueur dans la dimension | instantané pour lui seul, bloc froid compris (framework) ; rien pour les autres |
| Demande de bloc froid d'un client (anomalie) | bloc froid forcé dans le prochain envoi, au plus une fois toutes les 20 ticks |
| Nouvelle entrée du pilote **différente** de la précédente | `markDirty()`, au plus une fois tous les 4 ticks (drapeau « en attente » sinon) |
| Cible du vaisseau posée, changée ou retirée (Space Map, astre retiré par l'édition ; T43) | `markDirty()` au tick suivant, au plus une fois tous les 4 ticks, **même à l'arrêt** (bloc chaud seul, sauf changement de révision) ; rien ensuite tant qu'elle ne change pas |
| Téléportation (Configurator, D34) | `markDirty()` **immédiat** (fin du tick, sans l'attente de 4 ticks) ; compteur de téléportations du bloc chaud incrémenté : chaque client **saute** à la nouvelle pose (affichage et prédiction du pilote, comme un changement de système), sans lissage ni réconciliation ; autre système : révision changée, bloc froid joint |
| Monde en mouvement (`V > 0` ou entrée de rotation non nulle) | correction toutes les 20 ticks |
| Début / annulation de charge, début de voyage, arrivée (T21) | `markDirty()` immédiat (au plus une fois tous les 4 ticks) ; l'arrivée change le système courant (révision) : bloc froid joint, une fois |
| Charge ou voyage en cours | correction toutes les 20 ticks (comme en mouvement : un saut actif compte comme mouvement) |
| Passage de « en mouvement » à « immobile » | un envoi (pose finale chez les clients et dans la sauvegarde) |
| Monde immobile, pas de pilote | rien (ni envoi ni `markDirty` : la pose suit pourtant son référentiel ; l'archive en recopie l'état vivant à chaque sauvegarde du niveau, T14, et le recalage C37 couvre le reste) |

Les éditions de systèmes **non courants** qui ne changent ni leur position ni leur éclat sont quand même envoyées, au
plus une fois toutes les 20 ticks : le framework ne marque la sauvegarde que par `markDirty()`, qui synchronise aussi ;
sans envoi l'édition serait perdue à l'arrêt (T04). Elles changent la révision : le bloc froid est joint à cet envoi
(contenu identique, mais la révision annoncée doit rester celle que les clients connaissent).

Affichage client (T07e) : **horloge d'affichage propre au client**, découplée du `gameTime` du niveau (que vanilla
recale toutes les 20 ticks : un recalage faisait sauter les corps d'un tick de mouvement, ≈ 107 000 km pour la Terre).
Elle avance d'un tick par tick client et rattrape sa consigne (niveau − 1, et pour un observateur, moins son retard)
en douceur : zone morte 1 tick, gain 0,1 par tick d'écart, vitesse bornée à ±25 %, saut seulement au-delà de
20 ticks. `pose(partialTick)` et `displayDays(partialTick)` partagent ce temps. Un **historique de 32 ticks** garde,
pour chaque tick simulé, la pose d'affichage et l'état du référentiel ; l'interpolation se fait entre les deux
entrées qui encadrent le temps affiché, **dans le repère du référentiel** (interpoler `s − P_frame` puis rajouter
`P_frame` au temps d'affichage, fondu compris). Les renderers utilisent `bodyPositions` / `displayDays`, jamais le
`gameTime` de Minecraft.

### Dead reckoning (client)

Le mouvement est déterministe ([`04`](04-mathematiques.md) § 5). À réception d'un état `(T0, s, Q, V, référentiel,
entrée)` :
- un état « en avance » (`T0` au-delà du dernier tick simulé, cas normal en solo) : l'ancienne trajectoire est
  d'abord prolongée jusqu'à `T0`, pour comparer au même tick ;
- le client rejoue de `T0` jusqu'au dernier tick de l'historique, puis fait un pas par `clientTick` avec la dernière
  entrée reçue.

**Observateur** (pas le pilote local) : affichage en **retard** = retard constaté des états reçus + 4 ticks (oubli de
0,05 tick par état, plafond 16 ; ≈ 2 à 4 ticks en solo, ≈ 8 en réseau). Ce retard laisse corriger des ticks pas
encore affichés : si l'ancienne simulation donnait déjà l'état reçu, rien à faire ; sinon `findChange` retrouve le
tick exact du changement d'entrée (pas déterministe bit à bit) et réécrit la trajectoire ; à défaut, glissement
linéaire dans l'historique ; un état arrivé trop tard → **correction douce** sur 5 ticks ; **saut** seulement au-delà
de 10° d'orientation, ou d'un écart de position supérieur à `min(tan 10° · d / max(1, tan α), h / 2)` vis-à-vis du
corps le plus proche (`tan α = R / √(h(2R + h))` : près d'un corps l'horizon est très sensible à l'altitude ; le
plafond `h/2` évite qu'une correction fasse passer l'affichage sous la surface ; T07f).

**Changement d'horloge** (T38, [`04`](04-mathematiques.md) § 3) : l'échelle et l'époque arrivent avec les réglages,
dans le bloc chaud ; l'époque `E` est le tick de l'état serveur au changement, donc `T0 ≥ E` et le rejeu depuis
l'état reçu est exact (banc T38 : client = serveur bit à bit à chaque tick, changement compris). L'historique de
32 ticks traverse le changement ainsi : les ticks **antérieurs** à `E` gardent l'ancienne horloge (`previousClock`,
connue si le miroir était synchronisé) pour l'affichage et les rejeux (`days(t)` choisit l'horloge selon `t`) : le
temps affiché reste continu et les poses déjà stockées restent cohérentes avec les corps ; les poses simulées
**après** `E` avec l'ancienne horloge (client en avance sur l'état reçu) sont déplacées du déplacement de leur corps
de référence entre les deux horloges (`changeClock`, fondu d'une bascule ignoré), ainsi que la pose affichée et la
prédiction du pilote. **Accroc accepté** : un observateur affiche avec au moins 4 ticks de retard, donc des ticks
antérieurs à `E` à la réception : aucun saut (banc : pas du monde par rapport à la Terre inchangé, 500 km par image à
2 000 km/tick). Le pilote local, sans retard, a déjà affiché un peu après `E` avec l'ancienne échelle : à la réception,
le temps affiché recule de cette avance (banc, latence 2 ticks : 0,65 tick à 360 s/tick, soit ≈ 1° de rotation de la
Terre, recul imperceptible des orbites) ; sa position par rapport au référentiel ne saute pas (sans le recalage :
≈ 19 500 km près de la Terre). Deux changements d'échelle à moins de 32 ticks d'intervalle : seul le dernier garde
l'horloge précédente (accroc d'un tick au plus). La migration d'un monde ancien (premier tick serveur) se fait avant
tout état synchronisé : un instantané écrit avant ce tick porte encore l'ancienne horloge, l'état suivant la nouvelle.

### Pilote local (prédiction)

Le client du pilote applique **son** entrée immédiatement (`predictPilotInput`), sans attendre l'écho du serveur, et
ne fait qu'une **réconciliation lente** vers l'état serveur rejoué (10 % de l'écart par tick) tant qu'il pilote. À la
fin du pilotage, correction douce normale. L'écart résiduel vient de la latence entre l'appui et sa prise en
compte serveur (le serveur applique l'entrée à réception) : mesuré 5,5 à 7,5° en solo, ≈ 19° en réseau, sans saut
mais avec une légère dérive après un relâché (L14).

**Saut chez le client** (T21) : la charge et le voyage sont rejoués par le même pas déterministe
(`SpaceMotion.step` avec `SpaceJump`) dans toutes les suites de pas du client (rejeu, prédiction, recherche du tick de
changement, historiques : l'état du saut fait partie de l'état simulé comparé bit à bit) ; le client ne passe au
système cible qu'à réception du bloc froid de la nouvelle révision (l'arrivée est décidée par le serveur ; jusque-là
il reste en `TRAVEL` à la fin du compte, distorsion affichée). L'envoi de l'arrivée porte la nouvelle révision **et**
son bloc froid dans le même paquet (§ 2) : pas d'attente (changement de système = saut d'affichage).
Les positions galactiques d'origine et de cible sont figées dans l'état du saut et transmises (48 o) : le client
n'a pas la galaxie, et la cible pourrait bouger (Configurator) pendant le saut. Au début de la charge, les deux côtés
lisent la même valeur : coordonnées du système cible arrondies en `float` (celles de son étoile de fond) côté
serveur, étoile de fond reçue côté client ; origine = système courant (`double`, les deux côtés).
**Pilote** : la prédiction démarre la charge dès l'appui (avant l'écho du serveur) ; son saut est décalé de la latence
par rapport au saut rejoué depuis l'état serveur. S'ils diffèrent (phase ou cible) plus de 40 ticks d'affilée (saut
refusé ou annulé par le serveur), la prédiction est ramenée sur l'état rejoué (`reconcileJump`) : sans cela, un voyage
prédit que le serveur n'a pas fait resterait figé au bout de son compte.

**Persistance** (T14) : la mémoire de dimension n'est réécrite à une sauvegarde du niveau (auto-sauvegarde toutes
les 6 000 ticks, pause en solo, `/save-all`, arrêt) que si un module a appelé `markDirty()` depuis la précédente ;
l'Environment ne le fait pas quand le monde est immobile (règle ci-dessus), ni pendant les 20 ticks qui suivent un
envoi. L'**archive** `SpaceArchive` ([`03`](03-modele-donnees.md) § 4) comble ce trou sans rien envoyer : tant qu'un
Environment y est branché (dès son premier tick serveur), elle est toujours « à écrire » et recopie l'état **vivant**
(pose, vitesse, référentiel, galaxie, réglages, et le saut à partir de T21) à **chaque** sauvegarde du niveau. Au
chargement, l'Environment prend l'archive si elle est plus récente (`saved` > `time` de la mémoire), puis recale la
pose (C37) depuis ce tick : en pratique l'écart est nul (même sauvegarde), le recalage ne sert plus qu'après un
retrait / réinstallation ou une archive plus ancienne. Pilote et entrée ne sont pas sauvegardés : au retour, le monde
garde sa vitesse, sans rotation (comme à la libération du pilote). Un client qui se reconnecte (LAN, serveur) reçoit
l'état vivant du serveur dans l'instantané de la dimension : rien à restaurer.

## 3. Pilotage (Controller → Environment)

| Action (`sendAction(window, ...)`, premier octet) | Envoyée | Traitement serveur |
|---|---|---|
| `BEGIN` | au début de la capture (lot 2 : seulement si le client voit son Space Pilot) | refus (message) d'un non-op en mode « ops » (D5), puis sans `SpacePilotModule` dans la mémoire active (D11, lot 2) ; puis `claimPilot(joueur, marker)` : accepté si personne ne pilote ou si c'est le même joueur ; sinon refus (message), le client rend la main en voyant un autre pilote dans l'état |
| `INPUT` (lot 2, T21 : + drapeau `FLAG_JUMP` (bit 2 de l'octet de drapeaux) et, s'il est levé, id de cible en varint ; `SpacePilotInput` : 7 octets sans saut, 8 avec une cible < 128, 9 jusqu'à 16 383, 12 au plus — `size()`, `MAX_BYTES` ; lot 3, T32 : + drapeau `FLAG_BOOST` (bit 3 de l'octet de drapeaux, Sprint maintenu), **taille inchangée** ; un bit inconnu est toujours refusé à la lecture) | quand l'entrée quantifiée change (visée comprise, au pas de quantification près : regarder autour envoie aussi dans la zone morte, pour un rejeu identique ; boost compris : appui et relâchement de Sprint envoient l'entrée, même seuls), au plus toutes les 2 ticks ; sinon battement toutes les 20 ticks | ignorée si l'émetteur n'est pas le pilote ; valeurs bornées (octets signés `/127`) |
| `END` | à la fin de la capture | `releasePilot` ; entrée remise à zéro (le monde garde sa vitesse nominale) |
| `OPS_ONLY` (+ booléen) | clic sur l'interrupteur « Pilotage : tous / ops » | op seulement (sinon message) ; un pilote non-op est libéré au tick suivant |

Libération automatique du pilote : déconnexion, changement de dimension, plus de 60 ticks sans `INPUT` (entrée remise
à zéro) puis 200 ticks (pilote libéré), marker du Controller déchargé ou retiré.

**Fenêtre masquée (T41, D31)** : rien de plus sur le réseau. Chaque client masque la fenêtre du poste dont l'état
synchronisé désigne un pilote passant par lui (`pilot != null && pilotHere`, déjà dans l'état du Controller) ; elle
réapparaît à l'état suivant la libération (`END`, délai, déconnexion : ≤ 5 ticks, `SYNC_INTERVAL`). La capture du
pilote n'étant plus liée à la fenêtre, le client envoie `END` lui-même dans tous les cas de fin (§ 6 du doc 02) ;
quand le module est déchargé côté client (marker retiré, chunk plus suivi, changement de dimension, déconnexion),
`END` part encore mais peut être ignoré : le serveur libère alors le pilote par ses propres règles ci-dessus.

## 4. Controller, Space Map, Configurator, Space Pilot, Space Knowledge

- **Controller** : état minuscule (nom du pilote, vitesse relative, référentiel et altitude au-dessus de lui, Environment présent).
  `markDirty()` au plus 4 fois par seconde, seulement si une valeur affichée change (vitesse et distance arrondies à
  3 chiffres significatifs).
- **Configurator** (T42, branché sur la Space Map du même marker) : `t_edit` de la poignée d'orbite (`double`),
  drapeau Environment, puis avant du monde (octet), vitesse de distorsion et échelle de temps (2 `double`) :
  **26 o** au plus (9 o sans Environment). Plus de copie de la galaxie ni de vue : la sélection, les en-têtes et le
  système montré sont ceux de la Map (ses propres paquets). `markDirty()` quand une valeur copiée des réglages change
  (instance des réglages remplacée) ou quand `t_edit` est repris (changement de contexte de la vue de la Map :
  sélection, zoom, centre, révision copiée) ; jamais par tick. Les poignées (objets) sont envoyées par le framework
  quand le serveur les replace : au changement de sélection, de vue, de cube ou d'orbite, pas au fil du temps (temps
  figé).
- **Configurator, lots 1 et 2** (avant T42, pour mémoire) : mode, sélection, `t_edit`, cadrage, révision, avant du
  monde, vitesse de distorsion, corps zoomé, origine, en-têtes de tous les systèmes et corps du système édité ; repris
  par la Space Map en T37 / T42.
- **Space Map** (T37) : mode, système affiché, système et corps sélectionnés, corps au centre (varints), demi-étendue
  de la vue système, centre et demi-étendue de la carte galactique (5 `double`), origine (varint), drapeau
  Environment ; puis révision (`int`), système courant, en-têtes de tous les systèmes, système affiché (optionnel :
  vue système, ou système déplié dans l'arborescence). ≈ 50 o + en-têtes + système affiché (Sol ≈ 3,3 Ko depuis
  T28, **3 609 o** depuis T39 : masse, +8 o par corps ; carte galactique sans système déplié : en-têtes seulement). `markDirty()` aussitôt au changement de révision de
  l'Environment ; après une action de navigation, au plus un envoi toutes les **4 ticks** (molette tournée : ≤ 5 états
  / s, ≈ 17 Ko/s au pire avec Sol déplié, pendant le geste seulement). Rien par tick ; le temps vivant et le vaisseau
  sont lus par chaque client dans le miroir de l'Environment.
- **Space Pilot** : état de **1 o** depuis T33 (3 interrupteurs en bits : `hud`, `refLink`, `cruise` ; 2 o au lot 2)
  envoyé **au propriétaire seulement** (`sharedWithObservers()` faux, objets `PRIVATE`, C45) ; `markDirty()` à chaque
  réglage changé. Les rendus lisent le miroir de l'Environment, rien n'est envoyé pour eux.
- **Space Knowledge** (T33) : état de **2 o** (6 interrupteurs en bits : `enabled`, `orbits`, `bodies`, `stars`,
  `names`, `distance` ; angle du nom en degrés), même règle (propriétaire seulement, `markDirty()` au changement) ;
  actions `FLAG` (3 o) et `ANGLE` (2 o).
- **Space Map, cible du vaisseau** (T43) : rien de plus dans l'état synchronisé de la Map ; la cible circule dans le
  bloc chaud de l'Environment et la fenêtre la lit dans son miroir. La cible **posée** par la Map (deux ids) est un
  état serveur sauvegardé, non synchronisé : la poser ou l'oublier demande un `markDirty()` (seul moyen de faire
  sauvegarder la mémoire du marker), donc un envoi de l'état de la Map (≈ 50 o + en-têtes + système affiché, jusqu'à
  ≈ 3,6 Ko avec Sol), au plus une fois / 4 ticks, à chaque clic sur « CIBLER » ou quand la sélection change (déjà
  envoyé dans ce cas). Action `8 TARGET` : 2 o (type + booléen).
- Objets (fenêtres, cube, poignées) : synchronisés par le framework (`HoloObjectPacket` par objet modifié), petits.

## 5. Budget

Depuis T15b (D16), le bloc froid ne circule plus en régime courant : chiffres recalculés (état du module seul, hors
enveloppe du framework : type, UUID, ≈ 40 o par paquet).

| Cas | Envoi | Débit par joueur de la dimension |
|---|---|---|
| Ciel immobile | aucun | 0 |
| Monde en croisière, pas de pilote | 1 état / s, bloc chaud + en-tête (**161 o** depuis T43, 163 o avec une cible ; 160 o depuis T38 ; 144 o depuis T21) | ≈ 0,16 Ko/s (avant T15b : 3,25 Ko/s ; ≤ 19 Ko/s au pire) |
| Cible du vaisseau posée ou retirée (T43) | 1 état de l'Environment (bloc chaud + en-tête, ≈ 163 o) + 1 état de la Map (≤ 3,6 Ko avec Sol affiché), une fois par clic | négligeable |
| Pilote en manœuvre | ≤ 5 états / s (176 o avec le pilote depuis T38 ; 160 o avant) | ≈ 0,9 Ko/s (avant : ≈ 16 Ko/s ; ≤ 95 Ko/s au pire) |
| Voyage par saut | 1 état / s (245 o depuis T38 ; 229 o avant) + 1 bloc froid à l'arrivée | ≈ 0,16 Ko/s + 1 bloc froid (≈ 3 Ko ; ≤ 19 Ko au pire) une fois |
| Arrivée d'un joueur (connexion, changement de dimension, réapparition) | 1 instantané complet, à lui seul | ≈ 3,25 Ko une fois (Sol courant ; ≈ 3,8 Ko depuis T28 ; **≈ 4,1 Ko** depuis T39) ; ≤ 19 Ko au pire (≈ 21 Ko depuis T28, ≈ 21,8 Ko depuis T39) |
| Édition au Configurator (poignée glissée sur le système courant ou une étoile de fond) | ≤ 5 états / s, chacun avec le bloc froid (la révision change à chaque pas) | ≈ 16 Ko/s pendant le geste (≈ 20 Ko/s depuis T39) ; ≤ 95 Ko/s au pire (inchangé : c'est une édition) |
| Édition d'un système non courant (sans effet sur les étoiles de fond) | ≤ 1 état / s avec le bloc froid | ≈ 3,25 Ko/s pendant l'édition |
| Demande de bloc froid d'un client (anomalie seulement) | 1 bloc froid à toute la dimension, ≤ 1 toutes les 20 ticks | ≤ 3,25 Ko/s (≤ 19 Ko/s au pire) tant que l'anomalie dure |
| Import d'une texture (T45, canal `hololib:space_textures`) | client de l'op → serveur : `Begin` (≈ 25 o), puis le PNG en `Chunk` de 30 000 o, **4 par tick**, puis `Result` (≈ 25 o) ; rien si l'empreinte est déjà stockée (`DONE` aussitôt) ; puis l'action 7 (≈ 60 o) → 1 état complet de l'Environment (révision : bloc froid, ≈ 4 Ko avec Sol) à toute la dimension et 1 état de la Map | montant : ≤ 2 Mio (PNG RGB 1024 × 512 : typiquement 0,3 à 1,5 Mo) en ≤ 18 ticks (≈ 2,4 Mo/s), une fois, de l'op seul |
| Texture importée vue par un client (après un import, ou à l'arrivée d'un joueur) | `Request` (≈ 21 o) la première fois que le client dessine un corps `custom:<sha1>` sans image (ciel du système courant, système montré par une Map), puis la texture **à lui seul** en `Data` de 30 000 o, 4 par tick ; `Missing` (≈ 21 o) si absente (nouvelle demande après 60 s) | ≤ 2 Mio par texture, une fois par texture et par session (cache client gardé jusqu'à la déconnexion, changements de dimension compris), ≈ 2,4 Mo/s au plus par joueur (une texture à la fois) ; au pire 64 textures par dimension (quota) |

Tailles mesurées en T15, T16, T15b et T38 (banc jetable, `FriendlyByteBuf` réel) :

| Élément | Taille |
|---|---|
| Bloc chaud | **159 o** depuis T43 (+1 o d'octet de cible ; 161 o avec une cible d'ids < 128 ; 175 avec un pilote) ; 158 o en T38 (174 avec un pilote ; 142 / 158 avant T38 ; 141 / 157 avant T21) ; réglages **72 o** dont 8 pour la vitesse de distorsion et 16 pour l'époque de l'horloge (T38 : +16 o) ; saut 1 o (84 à 89 o pendant un saut : 243 o de bloc chaud, 227 avant T38) |
| En-tête (T15b) | 2 o (révision et longueur en varint ; `L = 0` sans bloc froid) |
| État courant, sans bloc froid (T15b) | **161 o** depuis T43 (calculé : +1 o sans cible, +3 o avec ; 163 o avec une cible) ; 160 o depuis T38, mesurés (176 o avec un pilote ; 245 o pendant un saut sans pilote) ; 144 / 160 / 229 o de T21 à T37 |
| `BackgroundStar` | 20 o + id + nom : 26 à 39 o pour les voisins par défaut (moyenne **31 o**, nom moyen 9 caractères) ; 55 o avec un nom ASCII de 32 caractères ; 120 o au pire théorique (32 caractères de 3 octets UTF-8, id ≥ 16 384) |
| Corps sur orbite, nom de 32 caractères ASCII | 108 o (**125 o** depuis T28 avec une texture `auto`) |
| Rotation propre + texture d'un corps (T28) | **+17 o** (4 `float` + 1 o pour `auto`) ; +36 o pour une texture `custom:<sha1>`, +34 à +50 o pour `builtin:<id>` (1 à 32 caractères) |
| Bloc froid, galaxie du lot 1 (Sol 5 corps + 11 voisines) | 729 o (dont 342 o d'étoiles de fond) |
| Bloc froid, galaxie par défaut de T16, Sol courant (34 corps + 11 voisines) | **3 107 o** avant T15b (révision `int` et longueur comprises), **3 101 o** depuis (révision dans l'en-tête) : Sol 2 759 o (≈ 81 o par corps sur orbite), étoiles de fond 342 o ; c'est aussi le plus gros bloc froid de cette galaxie (Alpha du Centaure courant : ≈ 0,8 Ko) |
| État complet (bloc chaud + en-tête + froid), Sol courant : instantané, envoi après édition | 3 245 o (141 + 3 + 3 101 ; la révision n'est plus dans le bloc froid) ; Alpha du Centaure courant : 906 o ; **3 823 o** depuis T28 (+578 o), Alpha du Centaure ≈ 990 o ; **3 848 o** mesurés en T38 (graine T31 et époque T38 comprises) |
| Bloc froid, galaxie par défaut, Sol courant, **depuis T28** | **3 679 o** mesurés (banc T28 : Sol 3 337 o = 2 759 + 34 × 17, étoiles de fond 342 o inchangées) ; Alpha du Centaure courant 857 o (+85 o) |
| Masse d'un corps (T39) | **+8 o** par corps (`double`, après la luminosité) : Sol 3 337 → **3 609 o** (+272 o), bloc froid Sol **3 959 o** (mesuré, graine comprise), Alpha du Centaure **905 o** (+40 o) ; état complet Sol ≈ **4 120 o** (158 + 3 + 3 959) ; pire cas + 48 × 8 = 384 o (≈ 21,8 Ko, ≈ 42,4 Ko au pire théorique). Aussi dans les copies du Configurator et de la Space Map |
| Graine du fond étoilé (T31) | **+8 o** par bloc froid (`long`) : Sol courant **3 687 o**, état complet 3 831 o ; les ≈ 4 000 étoiles générées ne circulent jamais (en clair : ≈ 80 Ko) |
| Bloc froid, pire cas ASCII (255 étoiles + système de 48 corps, noms de 32 caractères) | ≈ 19 Ko (étoiles 13,9 Ko au lieu de 5,1 Ko avant T15) ; ≈ 40 Ko au pire théorique (noms multi-octets) ; T28 : + 48 × 50 o au pire (rotation + texture `builtin` de 32 caractères), soit ≈ 21,4 Ko (≈ 42 Ko au pire théorique) |

Textures importées (T45) : elles ne passent **jamais** par la synchro des modules (seule la référence
`custom:<sha1>`, 21 o, est dans le bloc froid) ; le canal dédié ne sert que le joueur qui importe ou qui demande, à
son rythme (4 morceaux de 30 000 o par tick, une transmission à la fois par joueur dans chaque sens). Un joueur qui
arrive ne paie que les textures des corps qu'il dessine (système courant et système montré par une Map en vue), une
fois par session ; il voit la texture `auto` du corps pendant le transfert (≈ 0,1 à 1 s par texture).

Les noms ont environ doublé le pire cas (≈ 10 → 19 Ko par bloc froid). La rotation propre et la texture (T28)
ajoutent 17 o par corps en pratique (Sol : +578 o, +19 %), envoyés seulement avec le bloc froid ; la masse (T39)
8 o de plus par corps (Sol : +272 o, +7 %), même règle ; les valeurs de
rotation sont ramenées à la précision d'un `float` dans le modèle, ce qui divise leur coût par deux (32 → 16 o) sans
perte d'exactitude entre serveur et clients. La copie du système édité par le Configurator grandit d'autant. Depuis T15b ce coût n'est payé qu'à
l'arrivée d'un joueur et à chaque changement (édition, saut), plus jamais en croisière ni en manœuvre. Reste la
diffusion à **toute** la dimension d'un bloc froid demandé ou forcé pour un seul joueur : un envoi ciblé serait une
modification d'API, non demandée (les deux cas sont des anomalies).
