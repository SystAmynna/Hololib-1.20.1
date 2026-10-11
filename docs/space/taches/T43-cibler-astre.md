# T43 — Space Map : cibler un astre

**Objectif** : (D33) dans la liste des astres de la Space Map, un bouton **CIBLER** / **DECIBLER** au bout de la
ligne sélectionnée (et d'elle seule) pose ou retire la **cible du vaisseau**. Pour le pilote (Space Pilot, aux
commandes), l'astre ciblé est entouré d'un anneau **vert** et une **flèche verte**, posée sur le bord du cercle avant
du joystick (zone morte), pointe vers lui pour faciliter la rotation.

**Dépendances** : T37, T42 (Map, sélection, Configurator branché) ; T33, T34 (Space Pilot réduit, lien du
référentiel). Toutes faites.

## À lire avant
- [`01-vision.md`](../01-vision.md) § « Cibler un astre » ; [`decisions.md`](../decisions.md) D11, D20, D33, C75,
  C98, C107–C110, C122, C123.
- [`02-architecture.md`](../02-architecture.md) §§ 4, 5, 7 bis, 7 quater ; [`05-synchronisation.md`](../05-synchronisation.md)
  §§ 2 (bloc chaud, politique d'envoi : rien à l'arrêt), 4, 5.
- Map : `modules/monde/spacemap/SpaceMapWindow.java` (arborescence, lignes reconstruites à la synchro),
  `SpaceMapTreeRow.java` (zone du repère `+` / `-` décidée par `clickX` : modèle pour la zone du bouton),
  `SpaceMapModule.java` (actions 0–7 ; `galaxyView`, `selectSystem`, `selectBody`, `mode`, `focusSystem`,
  `selectInView`, `frameOrbit` : **tous** les endroits où la sélection change).
- Environment : `SpaceEnvironmentModule.java` (état, bloc chaud, sauvegarde, API serveur publique, retrait d'un corps
  ou d'un système par l'édition, arrivée du saut), `SpaceArchive.java`.
- Space Pilot : `client/render/modules/camera/spacepilot/SpaceReferenceLinkRenderer.java` (bord du cercle avant
  ramené de la projection `STABLE` du joystick à `WORLD`, anneau du référentiel), `SpaceJumpRenderer.java`
  (direction d'une étoile de fond, conditions d'affichage : pilote local, Space Pilot, distorsion),
  `SpaceJoystickRenderer.java`, `modules/camera/spacepilot/SpaceJoystickForward.java`,
  `client/render/modules/dimension/space/SpaceOverlay.java` (rayons d'anneau, `pixelTan`, `quads()`).

## Livrables
1. **Environment — cible du vaisseau** : état `(systemId, bodyId)`, `0` = aucune cible, `bodyId = 0` = le système
   entier (montré par son étoile principale). API serveur publique (poser, retirer, lire) validée : système et corps
   existants. La cible est **retirée** quand son système ou son corps disparaît (édition du Configurator), **gardée**
   après un saut (y compris vers son propre système). Synchronisée chez tous les clients (lecture par le miroir
   client) et **envoyée au changement même à l'arrêt** (sans casser la politique d'envoi de 05 § 2 : pas de trafic
   par tick) ; sauvegardée dans l'état du module et dans `SpaceArchive` ; lecture tolérante des sauvegardes sans cible.
2. **Map — action `8 TARGET + bool`** (utilisable par tous, comme les autres actions de la Map, accès `EVERYONE`) :
   `true` pose comme cible la **sélection courante de cette Map** (système ou corps), `false` la retire si c'est la
   sélection courante. La Map retient qu'elle a posé la cible ; **tout changement de sélection** de cette Map
   (ligne cliquée, « Galaxie », mode, `focusSystem` / `selectInView` / `frameOrbit` appelés par le Configurator,
   corps ou système disparu) retire la cible du vaisseau **si elle est encore celle qu'elle a posée** (une cible
   posée depuis une autre Map n'est pas touchée).
3. **Ligne sélectionnée** : zone de bouton à son extrémité droite, texte du nom tronqué d'autant
   (`SpaceMapTreeRow.textWidth`) ; libellé « CIBLER » si la sélection n'est pas la cible du vaisseau, « DECIBLER » si
   elle l'est (lu dans le miroir client de l'Environment, lignes reconstruites quand la cible change, jamais par
   frame) ; clic gauche dans la zone = action 8, clic ailleurs inchangé (sélection, dépliage). S'applique aux lignes
   de système et de corps de n'importe quel système ; jamais à « Galaxie ». Réutiliser les traits / `HoloWidget*`
   (sinon le justifier) ; couleurs de la fenêtre (le vert de la cible peut servir au libellé « DECIBLER »).
4. **Anneau vert** (Space Pilot) : un `ViewRenderer` du package `client/render/modules/camera/spacepilot` (nouveau,
   ou ajout à `SpaceJumpRenderer` : choisir et justifier), mêmes conditions que le saut : **pilote local aux
   commandes** (`SpacePilotPad.localPilot()`), module Space Pilot dans la mémoire active, Environment synchronisé,
   rien pendant le voyage et la distorsion (`SpaceWarpTimeline.active`). Cible dans le **système courant** : anneau
   autour du corps (rayons de `SpaceOverlay`, recouvre l'anneau de Space Knowledge et celui du référentiel si c'est le
   même corps) ; cible dans un **autre système** : anneau autour de l'**étoile de fond** de ce système (direction
   calculée comme `SpaceJumpRenderer`). Tailles constantes à l'écran, passe devant le terrain, aucune allocation par
   frame, `SpaceOverlay.quads()`.
5. **Flèche verte** : posée sur le bord extérieur du cercle avant (même conversion `STABLE → WORLD` que le lien du
   référentiel), dans le plan du cercle, tournée vers la cible : angle de la direction de la cible autour de l'axe
   avant du monde (valable aussi pour une cible sur le côté ou derrière). Masquée quand la cible est à l'intérieur du
   cercle avant. Ne masque pas le lien du référentiel (décalage ou ordre de dessin : le dire).
6. Indépendance : la cible du vaisseau **ne change pas** la cible de la vitesse de croisière (T34, au regard, ligne
   « Cible » du HUD) ni celle du saut (étoile au réticule) ; aucun changement du HUD.
7. Docs : 02 (§§ 5, 7 bis, 7 quater : état, action, renderer), 05 (§§ 2, 4, 5 : octets ajoutés au bloc envoyé,
   politique d'envoi), `decisions.md` (choix faits : couleur exacte, taille de la flèche, zone du bouton) ;
   `.claude/CLAUDE.md` laissé à T46.

## Interdits
- Modifier une classe hors des packages Space (sauf l'enregistrement d'un renderer dans `ModuleRenderers` /
  `SpacePilotRenderers`) ; allocation par frame ; état GL modifié à la main ; widget ad hoc si un existant convient ;
  git.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : sélectionner Mars dans la Map → « CIBLER » au bout de sa ligne seulement ; clic → « DECIBLER » ; aux
  commandes : anneau vert autour de Mars, flèche verte sur le cercle avant qui tourne quand on tourne le vaisseau et
  disparaît quand Mars entre dans le cercle ; sans piloter : rien.
- Sélectionner la Lune → la cible tombe (plus d'anneau, « CIBLER » sur la Lune) ; « Galaxie » → idem.
- Cibler Sirius (ligne système) → anneau sur son étoile de fond, la flèche aide à viser ; saut → l'anneau passe sur
  l'étoile principale de Sirius.
- Second joueur LAN aux commandes : même cible ; un joueur qui regarde la Map voit « DECIBLER » sur la ligne ciblée.
- Relancer le monde → cible gardée ; retirer au Configurator le corps ciblé → cible retirée.

## Compte rendu attendu
Fichiers créés / modifiés, format réseau et NBT ajoutés (octets), choix (renderer, flèche, zone du bouton), écarts à
la carte, scénario en jeu.
