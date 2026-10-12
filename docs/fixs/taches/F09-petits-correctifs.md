# F09 — Petits correctifs

**Objectif** : corriger les constats de faible sévérité du rapport (§ 7.3), chacun petit et local.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constats
1. **Y des coins Area non borné à la relecture.** `modules/monde/areadisplayer/AreaDisplayerModule.java:281`
   (`readCorner`) : X et Z sont bornés par `Mth.clamp`, pas Y. `setCorner` borne bien Y à la hauteur de
   construction ; la relecture NBT doit faire de même.
2. **`partialTick` incohérent dans l'édition.** `client/edit/HoloEditTools.pickGizmo` (`:203`), `beginAxis` et
   `client/edit/HoloEditSession.inRange` (`:548`) lisent `mc.getFrameTime()` au lieu du `partialTick` reçu par le
   picking : à l'écran et dans le picking, les gizmos peuvent être décalés d'une fraction de tick.
3. **Caméra de la frame précédente pour les overlays.** `client/render/holo/HoloAttachment.compute` (`:199-202`) est
   appelé au début de la frame (picking) avec `getMainCamera()`, qui n'est pas encore mise à jour. Le placement figé
   d'un overlay peut avoir une frame de retard, ce qui se voit si on bouge vite à la création. Vérifier la réalité
   du décalage avant de corriger. Si la correction demande de déplacer le calcul après `setup` de la caméra, le faire
   sans changer l'API.
4. **`HoloOutlineMask` ne libère pas ses cibles.** `client/render/outline/HoloOutlineMask.java` : une instance par
   renderer, créée au setup, sans libération. Documenter la règle (instance unique, durée de vie du jeu) ou ajouter
   `close()` appelé par le propriétaire. Choisir le plus simple.
5. **Listage de dossier sur le fil serveur.** `modules/dimension/space/texture/SpaceTextureServer.java:110-121`
   (`onBegin`) : `exists`, `count` et `removeOrphans` listent le dossier sur le fil serveur (64 fichiers au plus).
   Mettre en cache le nombre de fichiers (incrémenté à l'écriture, recalculé hors fil au démarrage), ou documenter la
   limite si le gain est négligeable. Choisir et justifier.

## À lire avant
Rapport § 7.3 ; les fichiers cités ; CLAUDE.md « Edition en jeu » (placement figé des overlays).

## Zone
Les fichiers cités, et seulement eux.

## Livrables
Un correctif par constat (ou une décision argumentée de documenter seulement), chacun décrit dans le compte rendu.

## Interdits
- Changer une signature publique.
- Allocation par frame.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu :
  - zone Area relue après redémarrage (coins inchangés) ;
  - gizmos de translation, rotation et échelle stables en bougeant ;
  - boutons d'un objet édité bien placés en ouvrant l'éditeur en mouvement ;
  - radar et ciblage (contours) inchangés ;
  - import de texture inchangé.

## Compte rendu attendu
Pour chaque constat : vérifié ou non, correctif ou documentation, fichiers.
