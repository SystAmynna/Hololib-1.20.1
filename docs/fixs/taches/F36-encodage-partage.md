# F36 — Encodage réseau partagé entre destinataires

**Objectif** : l'état d'un module est sérialisé **une fois** par flush, puis le même contenu est envoyé à tous ses
destinataires (propriétaire, observateurs, viewers), au lieu d'être réencodé pour chacun. Les deltas restent hors
périmètre, mais sont étudiés. Rapport § 6.3, recommandation 24.

**Lot** : C   **API** : non, sauf si le format réseau change (alors **oui : à valider**, nouveau protocole)
**Dépendances** : F18

## Constat
- `HoloContainer.flush` (`holo/memory/HoloContainer.java:263`) renvoie l'état **complet** (`writeSync`) à chaque
  `markDirty`.
- `ModuleData.of` (`network/HoloSyncData.java:600`) alloue un buffer `Unpooled` et copie un `byte[]` à chaque fois.
- L'encodage est fait deux fois (propriétaire, puis observateurs), et une fois par viewer pour une mémoire de
  joueur (`sendToObservers`).
- Un paquet est envoyé par objet sale, sans limite de débit.
- Les mémoires de dimension utilisent `PacketDistributor.DIMENSION`, donc un seul encodage : c'est le modèle à
  suivre.

## À lire avant
- Rapport § 3.3, § 6.3 ; [`decisions.md`](../decisions.md) FL1.
- `holo/memory/HoloContainer.java` (`flush`, `sendToObservers`), `network/HoloSyncData.java`,
  `network/HoloModulePacket.java`, `network/HoloObjectPacket.java`, `network/HoloNetwork.java` ; la carte F18
  (`ObjectState`).

## Zone
`holo/memory/*`, `network/*`.

## Livrables
1. Encodage unique : le contenu de `writeSync` est produit une fois par flush, puis partagé (même `byte[]`, ou même
   paquet pré-encodé) entre le propriétaire et les observateurs, lorsque leurs vues sont identiques. Si la vue
   diffère (modules partagés seulement pour l'observateur), un encodage par vue distincte, pas par joueur.
2. Regroupement des `HoloObjectPacket` d'un même module et d'un même tick en un seul paquet, **si** cela ne change
   pas le format. Sinon, le proposer comme demande d'API (protocole `"10"`) et s'arrêter.
3. Étude des deltas (FL1), dans le compte rendu : modules qui en profiteraient (taille moyenne de `writeSync` par
   module, fréquence), coût de mise en œuvre, proposition.
4. **Mesure avant / après** : octets par seconde et temps serveur avec 4 joueurs qui s'inspectent mutuellement, et
   des modules qui changent chaque tick (Space Pilot aux commandes, démo de fenêtre animée).

## Interdits
- Changer le format réseau sans validation (protocole incrémenté).
- Changer ce que chaque joueur reçoit (vues, droits).

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu (LAN, 2 clients) : inspection croisée, ajout et retrait de modules, objets publics et privés, édition ;
  mesure jointe.

## Compte rendu attendu
Mécanisme, fichiers, mesure, étude des deltas.
