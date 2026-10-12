# F02 — Taille des actions : borne réelle, `source` borné, contrôle à l'envoi

**Objectif** : une action de module ne doit jamais déconnecter son émetteur. La borne annoncée doit être la vraie,
la chaîne `source` doit être bornée, et une action trop grosse doit être refusée **avant** l'envoi. Rapport § 7.1-2,
recommandation 2.

**Lot** : A   **API** : non (`MAX_PAYLOAD` change de valeur, pas de signature)   **Dépendances** : aucune

## Constat (vérifié)
- `network/HoloModuleActionPacket.java:20` : `MAX_PAYLOAD = 32767` pour le **contenu** seul.
- La limite vanilla `ServerboundCustomPayloadPacket` vaut 32 767 octets pour le paquet **entier** : index du
  message, scope, UUID, `source`, contenu. Vérifié dans les sources mappées
  (`net/minecraft/network/protocol/game/ServerboundCustomPayloadPacket.java`).
- `source` est lu par `readUtf()` sans borne (`:32`), alors que les autres paquets utilisent `MAX_KEY_LENGTH = 64`.
- `HoloModule.send` / `sendAction` (`holo/module/HoloModule.java:108-126`) ne contrôlent pas la taille.
- Conséquence : un contenu proche de 32 767 octets fait échouer le décodage côté serveur, et le joueur est
  déconnecté.

## À lire avant
- Rapport § 7.1-2 ; [`decisions.md`](../decisions.md) FC2.
- `network/HoloModuleActionPacket.java`, `network/HoloNetwork.java`, `holo/module/HoloModule.java` (`sendAction`,
  `send`), les autres paquets qui définissent `MAX_KEY_LENGTH`.
- Côté modules : chercher les actions potentiellement grosses (`SpaceConfiguratorModule` : noms, champs ;
  `KeysEditorModule` ; `ThemeConfiguratorBase`). Aucune ne devrait dépasser quelques centaines d'octets : le vérifier
  et le noter.

## Zone
`network/HoloModuleActionPacket.java`, `holo/module/HoloModule.java` (corps de `send`).

## Livrables
1. `MAX_PAYLOAD = 32000`, documenté : marge pour l'en-tête, limite vanilla rappelée.
2. `source` écrit et lu avec une borne de 64 caractères, la même constante que les autres paquets. Pas de nouvelle
   constante partagée ici : c'est F29.
3. `HoloModule.send` mesure le contenu avant de construire le paquet. Au-delà de `MAX_PAYLOAD`, il lève
   `IllegalStateException` avec le type du module et la taille, et rien n'est envoyé. Documenter ce comportement
   dans la javadoc de `sendAction`.
4. Le décodage serveur reste protégé par `readByteArray(MAX_PAYLOAD)`. Un paquet malformé lève une exception de
   décodage (comportement réseau standard de Forge, non modifié).
5. Le protocole reste `"9"` : le format binaire ne change pas, seules les bornes changent. Le justifier dans le
   compte rendu.

## Interdits
- Découper les actions en plusieurs paquets (alternative de FC2, non retenue).
- Toucher les canaux dédiés (`space_textures`, `area_terrain`), qui ont leurs propres bornes.

## Critères d'acceptation
- `compileJava` réussit.
- Banc jetable (non livré) ou démonstration : une action de 31 900 octets passe ; une de 32 001 lève l'exception côté
  client sans rien envoyer.
- Le compte rendu liste la taille maximale observée des actions des modules du mod.

## Compte rendu attendu
Fichiers, taille maximale des actions existantes, résultat du banc, justification du protocole inchangé.
