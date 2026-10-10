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
| **Chaud** | tick de référence `T0` (`long`), pose `s` (3 `double`), `Q` (4 `double`), `V` signée (`double`), état du référentiel (`SpaceFrameState` : 5 `varint` — ref, précédent, ticks depuis bascule, candidat, ticks de candidature), entrée courante du pilote (7 octets : gaz, roulis, lacet, tangage, drapeaux — bit 1 frein, bit 2 saut —, visée yaw / pitch ; + id de la cible en varint si le drapeau du saut est levé, T21), présence + UUID du pilote, réglages (7 `double` : axe avant yaw / pitch, `V_max`, `turn`, `rollSpeed`, échelle de temps, **vitesse de distorsion** — T15, 8 o), **saut** (T21, `SpaceJump.write` : phase en octet — 0 aucun, 1 charge, 2 voyage ; si ≠ `NONE` : cible varint, pas faits dans la phase varint, durée du voyage varint, `Q₀` 4 `double`, origine galactique 3 `double`, cible galactique 3 `double` : 81 o + 3 varints, 84 à 89 o ; 86 o pour Sol → Alpha du Centaure) | 142 o sans pilote, 158 o avec (141 / 157 avant T21 + 1 o de phase) ; **227 o** pendant un saut sans pilote (mesuré en T21), 244 o avec le pilote qui tient le saut |
| **En-tête** | révision (varint : celle du système courant et des étoiles de fond de cet état), longueur `L` du bloc froid (varint, **0 = absent**) | 2 o (révision < 128 ; 3 o jusqu'à 16 383) |
| **Froid** (si `L > 0`) | système courant complet, étoiles de fond (`BackgroundStar`, avec id et nom depuis T15 : ≈ 31 o par étoile au lieu de 20, [`03`](03-modele-donnees.md) § 5) | 0,7 Ko (galaxie du lot 1, mesuré) ; **3,1 Ko** (galaxie de T16, Sol courant : 34 corps + 11 voisines, 3 101 o mesurés en T15b) ; ≈ 19 Ko au pire (§ 5) |

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
| `INPUT` (lot 2, T21 : + drapeau `FLAG_JUMP` (bit 2 de l'octet de drapeaux) et, s'il est levé, id de cible en varint ; `SpacePilotInput` : 7 octets sans saut, 8 avec une cible < 128, 9 jusqu'à 16 383, 12 au plus — `size()`, `MAX_BYTES`) | quand l'entrée quantifiée change (visée comprise, au pas de quantification près : regarder autour envoie aussi dans la zone morte, pour un rejeu identique), au plus toutes les 2 ticks ; sinon battement toutes les 20 ticks | ignorée si l'émetteur n'est pas le pilote ; valeurs bornées (octets signés `/127`) |
| `END` | à la fin de la capture | `releasePilot` ; entrée remise à zéro (le monde garde sa vitesse nominale) |
| `OPS_ONLY` (+ booléen) | clic sur l'interrupteur « Pilotage : tous / ops » | op seulement (sinon message) ; un pilote non-op est libéré au tick suivant |

Libération automatique du pilote : déconnexion, changement de dimension, plus de 60 ticks sans `INPUT` (entrée remise
à zéro) puis 200 ticks (pilote libéré), marker du Controller déchargé ou retiré.

## 4. Controller, Configurator, Space Pilot

- **Controller** : état minuscule (nom du pilote, vitesse relative, référentiel et altitude au-dessus de lui, Environment présent).
  `markDirty()` au plus 4 fois par seconde, seulement si une valeur affichée change (vitesse et distance arrondies à
  3 chiffres significatifs).
- **Configurator** : mode, sélection (système, corps), `t_edit` (`double`, T12), cadrage, révision vue, avant du
  monde, en-têtes de tous les systèmes, corps du système édité.
  `markDirty()` sur changement de révision de l'Environment (sondé dans `serverTick`) ou d'état d'interface. Jamais
  par tick.
- **Configurator, lot 2** : + vitesse de distorsion de l'Environment (`double` après l'avant du monde, relue comme
  lui au changement de l'instance des réglages, T24) ; + corps zoomé (varint après le corps sélectionné, T25 ; le
  vaisseau est calculé par chaque client depuis le miroir de l'Environment, rien de plus sur le réseau) ; + origine des coordonnées (varint, T26). La fenêtre détachée
  (T24) n'ajoute rien d'autre à l'état du module : son transform et son affichage sont synchronisés par le
  framework.
- **Space Pilot** : état de **2 o** (7 interrupteurs en bits, angle du nom en degrés) envoyé **au propriétaire seulement** (`sharedWithObservers()`
  faux, objets `PRIVATE`, C45) ; `markDirty()` à chaque réglage changé. Les rendus lisent le miroir de
  l'Environment, rien n'est envoyé pour eux.
- Objets (fenêtres, cube, poignées) : synchronisés par le framework (`HoloObjectPacket` par objet modifié), petits.

## 5. Budget

Depuis T15b (D16), le bloc froid ne circule plus en régime courant : chiffres recalculés (état du module seul, hors
enveloppe du framework : type, UUID, ≈ 40 o par paquet).

| Cas | Envoi | Débit par joueur de la dimension |
|---|---|---|
| Ciel immobile | aucun | 0 |
| Monde en croisière, pas de pilote | 1 état / s, bloc chaud + en-tête (**144 o** depuis T21) | ≈ 0,14 Ko/s (avant T15b : 3,25 Ko/s ; ≤ 19 Ko/s au pire) |
| Pilote en manœuvre | ≤ 5 états / s (160 o avec le pilote) | ≈ 0,8 Ko/s (avant : ≈ 16 Ko/s ; ≤ 95 Ko/s au pire) |
| Voyage par saut | 1 état / s (229 o) + 1 bloc froid à l'arrivée | ≈ 0,15 Ko/s + 1 bloc froid (≈ 3 Ko ; ≤ 19 Ko au pire) une fois |
| Arrivée d'un joueur (connexion, changement de dimension, réapparition) | 1 instantané complet, à lui seul | ≈ 3,25 Ko une fois (Sol courant) ; ≤ 19 Ko au pire |
| Édition au Configurator (poignée glissée sur le système courant ou une étoile de fond) | ≤ 5 états / s, chacun avec le bloc froid (la révision change à chaque pas) | ≈ 16 Ko/s pendant le geste ; ≤ 95 Ko/s au pire (inchangé : c'est une édition) |
| Édition d'un système non courant (sans effet sur les étoiles de fond) | ≤ 1 état / s avec le bloc froid | ≈ 3,25 Ko/s pendant l'édition |
| Demande de bloc froid d'un client (anomalie seulement) | 1 bloc froid à toute la dimension, ≤ 1 toutes les 20 ticks | ≤ 3,25 Ko/s (≤ 19 Ko/s au pire) tant que l'anomalie dure |

Tailles mesurées en T15, T16 et T15b (banc jetable, `FriendlyByteBuf` réel) :

| Élément | Taille |
|---|---|
| Bloc chaud | 142 o (158 avec un pilote ; 141 / 157 avant T21) ; réglages 56 o dont 8 pour la vitesse de distorsion ; saut 1 o (84 à 89 o pendant un saut : 227 o de bloc chaud) |
| En-tête (T15b) | 2 o (révision et longueur en varint ; `L = 0` sans bloc froid) |
| État courant, sans bloc froid (T15b) | **144 o** depuis T21 (160 o avec un pilote ; 229 o pendant un saut sans pilote) |
| `BackgroundStar` | 20 o + id + nom : 26 à 39 o pour les voisins par défaut (moyenne **31 o**, nom moyen 9 caractères) ; 55 o avec un nom ASCII de 32 caractères ; 120 o au pire théorique (32 caractères de 3 octets UTF-8, id ≥ 16 384) |
| Corps sur orbite, nom de 32 caractères ASCII | 108 o |
| Bloc froid, galaxie du lot 1 (Sol 5 corps + 11 voisines) | 729 o (dont 342 o d'étoiles de fond) |
| Bloc froid, galaxie par défaut de T16, Sol courant (34 corps + 11 voisines) | **3 107 o** avant T15b (révision `int` et longueur comprises), **3 101 o** depuis (révision dans l'en-tête) : Sol 2 759 o (≈ 81 o par corps sur orbite), étoiles de fond 342 o ; c'est aussi le plus gros bloc froid de cette galaxie (Alpha du Centaure courant : ≈ 0,8 Ko) |
| État complet (bloc chaud + en-tête + froid), Sol courant : instantané, envoi après édition | **3 245 o** (141 + 3 + 3 101 ; la révision n'est plus dans le bloc froid) ; Alpha du Centaure courant : 906 o |
| Bloc froid, pire cas ASCII (255 étoiles + système de 48 corps, noms de 32 caractères) | ≈ 19 Ko (étoiles 13,9 Ko au lieu de 5,1 Ko avant T15) ; ≈ 40 Ko au pire théorique (noms multi-octets) |

Les noms ont environ doublé le pire cas (≈ 10 → 19 Ko par bloc froid). Depuis T15b ce coût n'est payé qu'à
l'arrivée d'un joueur et à chaque changement (édition, saut), plus jamais en croisière ni en manœuvre. Reste la
diffusion à **toute** la dimension d'un bloc froid demandé ou forcé pour un seul joueur : un envoi ciblé serait une
modification d'API, non demandée (les deux cas sont des anomalies).
