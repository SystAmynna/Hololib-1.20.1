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
  le même `writeSync` : chaque envoi doit être **autonome** (pas de différentiel).
- Actions client → serveur : `sendAction(source, writer)`, 32 767 octets max ; le serveur vérifie portée et accès
  avant `handleAction`.

## 2. Space Environment : froid / chaud

`writeSync` écrit, dans cet ordre :

| Bloc | Contenu | Taille typique |
|---|---|---|
| **Chaud** | tick de référence `T0` (`long`), pose `s` (3 `double`), `Q` (4 `double`), `V` signée (`double`), état du référentiel (`SpaceFrameState` : 5 `varint` — ref, précédent, ticks depuis bascule, candidat, ticks de candidature), entrée courante du pilote (7 octets : gaz, roulis, lacet, tangage, drapeaux dont frein, visée yaw / pitch), présence + UUID du pilote, réglages (axe avant, `V_max`, `turn`, `rollSpeed`, échelle de temps) | ≈ 120 o |
| **Froid** | longueur (varint), révision (`int`), système courant complet, étoiles de fond (`BackgroundStar`) | 1 à 7 Ko |

Le client lit la révision ; si c'est celle qu'il connaît, il **saute** le reste du bloc (`skipBytes(longueur − 4)`)
sans rien reconstruire. Les octets circulent quand même : c'est le prix de l'absence d'envoi partiel (budget § 5).

### Politique d'envoi (serveur, `serverTick`)

| Événement | Envoi |
|---|---|
| Édition de la galaxie touchant le système courant ou la liste des étoiles de fond, changement de système courant, réglage | `markDirty()` au tick suivant, au plus une fois tous les 4 ticks (glisser une poignée enverrait sinon ~7 Ko par tick) |
| Nouvelle entrée du pilote **différente** de la précédente | `markDirty()`, au plus une fois tous les 4 ticks (drapeau « en attente » sinon) |
| Monde en mouvement (`V > 0` ou entrée de rotation non nulle) | correction toutes les 20 ticks |
| Passage de « en mouvement » à « immobile » | un envoi (pose finale chez les clients et dans la sauvegarde) |
| Monde immobile, pas de pilote | rien (ni envoi ni sauvegarde : la pose suit pourtant son référentiel ; elle est recalée au chargement grâce à `time`, C37) |

Les éditions de systèmes **non courants** qui ne changent ni leur position ni leur éclat sont quand même envoyées, au
plus une fois toutes les 20 ticks : le framework ne marque la sauvegarde que par `markDirty()`, qui synchronise aussi ;
sans envoi l'édition serait perdue à l'arrêt (T04).

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

## 3. Pilotage (Controller → Environment)

| Action (`sendAction(window, ...)`, premier octet) | Envoyée | Traitement serveur |
|---|---|---|
| `BEGIN` | au début de la capture | `claimPilot(joueur, marker)` : accepté si personne ne pilote ou si c'est le même joueur ; sinon refus (message), le client rend la main en voyant un autre pilote dans l'état |
| `INPUT` | quand l'entrée quantifiée change (visée comprise, au pas de quantification près : regarder autour envoie aussi dans la zone morte, pour un rejeu identique), au plus toutes les 2 ticks ; sinon battement toutes les 20 ticks | ignorée si l'émetteur n'est pas le pilote ; valeurs bornées (octets signés `/127`) |
| `END` | à la fin de la capture | `releasePilot` ; entrée remise à zéro (le monde garde sa vitesse nominale) |

Libération automatique du pilote : déconnexion, changement de dimension, plus de 60 ticks sans `INPUT` (entrée remise
à zéro) puis 200 ticks (pilote libéré), marker du Controller déchargé ou retiré.

## 4. Controller et Configurator

- **Controller** : état minuscule (nom du pilote, vitesse relative, référentiel et altitude au-dessus de lui, Environment présent).
  `markDirty()` au plus 4 fois par seconde, seulement si une valeur affichée change (vitesse et distance arrondies à
  3 chiffres significatifs).
- **Configurator** : mode, sélection (système, corps), `t_edit` (`double`, T12), cadrage, révision vue, avant du
  monde, en-têtes de tous les systèmes, corps du système édité.
  `markDirty()` sur changement de révision de l'Environment (sondé dans `serverTick`) ou d'état d'interface. Jamais
  par tick.
- Objets (fenêtres, cube, poignées) : synchronisés par le framework (`HoloObjectPacket` par objet modifié), petits.

## 5. Budget

| Cas | Envoi | Débit par joueur de la dimension |
|---|---|---|
| Ciel immobile | aucun | 0 |
| Monde en croisière, pas de pilote | 1 état / s (≈ 2 à 7 Ko) | ≤ 7 Ko/s |
| Pilote en manœuvre | ≤ 5 états / s | ≤ 35 Ko/s (pire cas 256 systèmes, 32 corps) |

Acceptable (un chunk vanilla pèse 10 à 20 Ko). Si un jour cela devient gênant, la piste est un envoi partiel dans le
framework : **c'est une modification d'API**, non demandée, à ne pas faire sans validation.
