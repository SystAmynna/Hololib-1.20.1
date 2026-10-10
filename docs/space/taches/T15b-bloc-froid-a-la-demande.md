# T15b — Bloc froid envoyé seulement au besoin

**Objectif** : demande de l'utilisatrice (D16) : les données lourdes (galaxie, système courant, étoiles de fond et
leurs noms) ne sont envoyées à un client **qu'une fois**, à l'initialisation du module chez lui, puis seulement quand
elles changent (édition par le Space Configurator, changement de système par un saut). Tout le reste est calculé côté
client (dead reckoning, orbites, LOD). Aujourd'hui chaque envoi de l'Environment (C19, au plus tous les 4 ticks en
manœuvre) répète le bloc froid, que le client saute s'il connaît la révision : c'est ce gaspillage qu'on supprime.

**Dépendances** : T15.

## À lire avant
- [`05-synchronisation.md`](../05-synchronisation.md) §§ 2, 5 ; [`decisions.md`](../decisions.md) C19, D16.
- `modules/dimension/space/SpaceEnvironmentModule.java` : `writeSync` / `readSync`, `rebuildCold`, `coldBytes`,
  `coldRevision`, `revision`, politique d'envoi de `serverTick`, `restore` (T14).
- Framework (lecture seule) : `holo/module/HoloModule.java` (`writeSync(buf, forOwner)`, `markDirty`),
  `network/HoloSyncData.java` et les appels d'envoi des conteneurs (`HoloContainers`, `DimensionHoloContainer`) :
  **quand** `writeSync` est appelé (instantané à l'arrivée d'un joueur dans la dimension / reconnexion, envoi après
  `markDirty`), et si un même tampon est partagé entre destinataires.

## Livrables
1. **Diagnostic** (compte rendu) : tous les chemins par lesquels un client reçoit l'Environment (connexion, changement
   de dimension, installation du module, `markDirty`), et ce que `writeSync` peut savoir du destinataire.
2. **Format** : le bloc froid devient **optionnel** dans `writeSync` (drapeau). Le serveur ne l'inclut que :
   - dans les envois qui suivent un changement de révision (édition, restauration T14, changement de système) ;
   - quand un joueur **nouveau** pour cet Environment a pu recevoir un état sans bloc froid : le serveur suit
     l'ensemble des joueurs de la dimension (`level.players()`, au tick) et, à l'arrivée d'un joueur, inclut le bloc
     froid dans le prochain envoi (au plus 4 ticks, C19) ;
   - (piste à évaluer) toujours dans l'instantané initial si le diagnostic montre qu'on peut le reconnaître sans
     toucher au framework.
3. **Client** : un état chaud reçu sans bloc froid connu (révision inconnue ou jamais reçue) est gardé en attente ;
   ciel non dessiné (ou fond seul) tant que le bloc froid de la révision annoncée n'est pas arrivé ; aucune erreur.
4. Budget du doc 05 § 5 recalculé (bloc chaud seul en régime courant) ; doc 05 § 2 et C19 alignés.

## Interdits
- Modifier le framework (`holo/`, `network/`, `HoloContainers`...). Si le bloc froid ne peut pas être garanti à un
  nouveau client sans cela (ex. besoin de savoir qu'un `writeSync` est un instantané initial, ou d'un envoi ciblé à
  un joueur), **s'arrêter** et faire une demande d'API (besoin, alternative tentée, modification proposée).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : solo et LAN (client 2 qui se connecte, change de dimension, se reconnecte) : ciel correct en moins d'une
  seconde ; édition au Configurator visible chez tous ; en manœuvre, plus de bloc froid répété (log de taille
  temporaire, retiré ensuite).

## Compte rendu attendu
Diagnostic, nouveau format (octets), règles d'inclusion du bloc froid, budget, fichiers modifiés, écarts, scénario.
