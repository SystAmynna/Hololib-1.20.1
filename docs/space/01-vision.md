# 01 — Vision fonctionnelle

Ce document reformule la demande de l'utilisatrice et y intègre les décisions qu'elle a validées
([`decisions.md`](decisions.md), D1 à D34). Les sections marquées **(lot 2)** décrivent la demande du 2026-10-09
(tâches T14–T27, réalisées, à vérifier en jeu) ; celles marquées **(lot 3)** la demande du 2026-10-10 (tâches
T28–T46, dont les compléments T38–T41 et T43 ; T28–T45 faites, à vérifier en jeu, T46 = intégration), qui **remplace** les sections précédentes là où elles se contredisent ; le reste est le lot 1. Il décrit **ce que voit et fait le joueur** ;
le « comment » est dans les documents 02 à 05.

## Idée d'ensemble

Le monde Minecraft d'une dimension devient un **vaisseau** : il occupe une position et une orientation dans un
système stellaire simulé. Le ciel vanilla est remplacé par la vue de ce système depuis cette pose. Déplacer la pose
déplace « le monde entier » dans le système : les astres défilent, grossissent, tournent autour du monde.

Analogie demandée : en rendu 3D, `vue = caméra⁻¹ × monde`. Ici le **monde Minecraft joue le rôle de la caméra** par
rapport au **repère du système** : le système a sa propre matrice, et la pose du monde (l'« origine ») est la
caméra qui le regarde. Modifier l'origine = déplacer la caméra = tout le ciel bouge.

## Space Environment (`hololib:space_environment`, module de dimension)

- Installé dans la mémoire d'une dimension (`/hololib_module add dimension here hololib:space_environment`).
- Porte **la galaxie de la dimension** (D2) : la liste des systèmes stellaires (position en années-lumière, nom), et
  pour chacun ses corps célestes (étoiles, planètes, lunes) et leurs orbites.
- Désigne le **système courant** : celui dans lequel se trouve le monde (D3 : un seul à la fois ; on en change par le
  **saut**, D14, ou par le Configurator). Les autres systèmes apparaissent comme des **étoiles de fond**, placées selon la carte galactique
  (direction et éclat dépendent de leur distance au système courant).
- Simule les **orbites animées** sur le temps de jeu : les planètes tournent autour de leur étoile, les lunes autour
  de leur planète. Même résultat sur le serveur et chez tous les clients (calcul déterministe).
- Porte l'**origine** : position et orientation du monde dans le système courant, et sa vitesse.
- Dessine le ciel (D1) : fond spatial opaque qui masque le ciel vanilla (dégradé, soleil, lune, étoiles), brouillard et
  horizon noirs, étoiles de fond, corps du système courant vus depuis l'origine (taille apparente réelle, phases
  éclairées par l'étoile). Sans Space Environment installé, la dimension garde son ciel vanilla.
- Galaxie par défaut créée à la première installation pour voir le résultat immédiatement : au lot 1, Sol réduit
  (Soleil, Terre et Lune, Mars, Jupiter) et 11 voisines ; depuis le lot 2, les systèmes réels (voir « Systèmes
  réels »). Une galaxie déjà sauvegardée n'est jamais remplacée.

## Space Controller (`hololib:space_controller`, module de monde / marker)

- Installé sur un marker (`/hololib_marker create poste` puis `/hololib_module add world poste hololib:space_controller`).
  Il agit sur le Space Environment **de la dimension du marker** ; sans Environment, sa fenêtre l'indique et ne fait
  rien.
- Affiche une **fenêtre de contrôle** dans le monde (près du marker) : vitesse, corps le plus proche et sa distance,
  état (libre / piloté par X).
- **Clic sur la fenêtre** → le joueur devient **pilote** (un seul pilote à la fois) : ses contrôles sont interceptés.
  - **Avancer / Reculer** (Z / S par défaut, mais **les touches choisies par le joueur** dans les options) : accélérer /
    décélérer. On gagne ou perd de la vitesse ; on ne se déplace pas d'un pas fixe.
  - **Gauche / Droite** (Q / D par défaut, idem) : roulis de l'origine.
  - **Souris** : yaw (X) et pitch (Y) en **joystick virtuel** (D4). Deux cercles sont affichés dans la vue :
    - le **cercle avant**, fixe dans le monde, dans la direction « avant » du monde (le sens dans lequel il avance) ;
    - le **cercle de visée**, toujours au centre de l'écran ;
    - un **segment** les relie.
    La caméra tourne normalement mais ne peut pas s'éloigner de plus d'un angle maximal du cercle avant. L'écart entre
    les deux cercles donne une **vitesse de rotation** : regarder à droite du cercle avant fait tourner le monde vers la
    droite tant qu'on y regarde ; revenir sur le cercle avant arrête la rotation.
    **(lot 2)** Ces cercles sont dessinés par le module **Space Pilot** du joueur (sans lui, le pilotage est refusé,
    D11) ; le cercle avant est la **zone morte** : aucune rotation tant que le cercle de visée est dedans.
  - **Échap** : rend la main (fin du pilotage).
- **Approche des corps** : plus on s'approche d'un corps céleste, plus la vitesse **vers lui** diminue ; elle tend vers
  zéro à l'approche de la surface, qu'on n'atteint jamais. S'éloigner, ou longer la surface, rend la vitesse normale.
- Les autres joueurs voient le ciel bouger (l'origine est partagée par toute la dimension) ; seuls les cercles du
  joystick sont réservés au pilote.

## Space Configurator (`hololib:space_configurator`, module de monde / marker, op)

- Installé sur un marker ; **réservé aux opérateurs** (niveau 2) : seuls eux voient sa fenêtre et peuvent agir.
- Fait apparaître un **cube** dans le monde, **éditable** avec l'éditeur du mod (déplacer, tourner, mettre à
  l'échelle ; il faut le module `world_editor` et être op).
- **Mode galaxie** : le cube contient la **carte galactique** en 3D, à l'échelle.
  - Les arêtes du cube portent une **échelle graduée en années-lumière**.
  - Chaque système est une **étoile** avec son **nom au-dessus**.
  - Chaque étoile est **déplaçable avec l'outil de translation** de l'éditeur.
  - Une **fenêtre** (à la base du cube au lot 1, détachée et placée devant elle par défaut au lot 2) permet
    d'**ajouter**, **sélectionner**, **renommer**, **retirer** des systèmes, et de choisir le système courant de
    l'Environment.
- **Mode système** (bouton « Éditer » sur un système sélectionné) : le cube ne montre plus la galaxie mais **ce
  système** (étoiles, planètes, lunes, orbites dessinées).
  - La fenêtre liste les corps et permet d'**ajouter** (étoile, planète, lune, objet mineur au lot 2), **modifier**
    (nom, rayon, couleur, période...), **retirer** des corps.
  - L'orbite du corps sélectionné s'édite avec les outils **Rotation** (plan de l'orbite) et **Échelle** (taille et
    forme de l'ellipse) de l'éditeur.
  - Un bouton **Retour** revient au mode galaxie.
- Toute modification est appliquée à la galaxie de l'Environment de la dimension, sauvegardée, et visible aussitôt
  dans le ciel si elle concerne le système courant.

## Saut interstellaire (lot 2, Space Controller + Environment)

- Pendant le pilotage, le joueur **place une étoile** (un autre système, étoile de fond) **dans le réticule** : elle
  devient la cible (repérée par Space Pilot).
- Il **maintient Sneak** (la touche choisie par le joueur dans les options, `key.sneak`) :
  - le monde se **recadre en douceur** pour aligner son axe avant sur l'étoile ; le joystick ne répond plus ;
  - une **roue de chargement** de 3 s se remplit autour de l'étoile de destination ;
  - relâcher Sneak avant la fin annule.
- À la fin de la charge, le monde entre en **distorsion** : le ciel vire au **blanc** avec des **traînées de vitesse**
  dans la direction de l'étoile (effet provisoire du lot 2, **remplacé au lot 3** : section « Distorsion »). Tous les
  joueurs de la dimension le voient.
- La durée du voyage dépend de la **distance** entre le système d'origine et la cible, à une **vitesse de distorsion**
  réglable dans le Space Configurator (défaut **0,5 al/s** : Alpha du Centaure en ≈ 9 s).
- À l'arrivée, le système cible devient le système courant ; le monde est **près de l'étoile cible, sur l'axe du voyage,
  face à l'étoile, vitesse nulle** (D10, C59).

## Space Pilot (lot 2, `hololib:space_pilot`, module joueur)

*(Répartition remplacée au lot 3 : surcouche, noms, Quick Action → Space Knowledge ; voir les sections du lot 3
plus bas.)*

Module d'information sur le pilotage, installé dans la mémoire du joueur
(`/hololib_module add player @s current hololib:space_pilot`) ; **requis pour piloter** (D11). Tout ce qu'il dessine
est **pour le joueur local seulement** (jamais vu par un autre joueur, même en inspection). Tout est actif dans une
dimension munie d'un Space Environment, **sans** besoin d'un Space Controller, sauf le joystick.

- **Surcouche du ciel** :
  - les corps du système courant sont entourés d'un **anneau** (repérables dans le noir) ; quand on les regarde, leur
    **nom** s'affiche ;
  - les **orbites** sont dessinées (ellipses autour de leur parent) ;
  - les **étoiles** valides (autres systèmes de l'Environment) sont entourées d'un anneau **différent** ;
  - **visibilité** (D15) : étoiles et planètes repérées de partout ; une **lune** seulement assez près de son parent ;
    un **objet mineur** (Pluton...) seulement assez près de lui-même.
- **Joystick** (pendant le pilotage seulement) : cercle avant (zone morte) et cercle de visée, roue de charge du saut.
- **Lien** : un segment **en pointillés** relie le cercle avant (direction de l'axe avant, pas le cercle de visée) au
  centre du **corps de référence** (référentiel de la vitesse).
- **HUD de navigation** : vitesse actuelle, corps de référence, distance à ce corps ; **déplaçable** avec le HUD
  Editor.
- **Config** : interrupteurs pour les orbites, le repérage des corps, les étoiles extérieures, les noms au regard
  (corps et étoiles), l'**angle** d'affichage du nom, le HUD de navigation, le lien vers le référentiel.
- **Quick Action** : une seule action qui active / désactive tout le rendu du module (« Space Pilot : OFF » quand il
  est actif, « ON » sinon).

## Persistance (lot 2)

- Quitter puis revenir dans le monde ou sur le serveur : la position dans l'espace (système, pose, référentiel,
  vitesse, saut en cours) est **restaurée**.
- Retirer puis réinstaller le Space Environment d'une dimension : **tout** son état revient (galaxie éditée,
  système courant, pose, réglages, D12). Pour repartir de la galaxie par défaut : retirer le module, quitter le monde,
  supprimer `data/hololib_space.dat` de la dimension, puis réinstaller (pas de commande dédiée).

## Systèmes réels (lot 2)

- Sol contient **tous les corps majeurs** : 8 planètes, leurs lunes majeures, les planètes naines (Cérès, Pluton et
  Charon, Éris, Haumea, Makémaké) ; nouveau type de corps **objet mineur**.
- Les 11 systèmes voisins reçoivent leurs **étoiles compagnes** et leurs **planètes confirmées**, d'après les données
  les plus récentes ([`donnees-systemes.md`](donnees-systemes.md)).

## Space Configurator (lot 2)

- La **carte** (cube) et la **fenêtre** de configuration sont deux objets **séparés**, éditables chacun de son côté ;
  la fenêtre peut être **masquée** avec le `world_editor` (bouton œil) pour ne garder que la carte.
- Mode galaxie : le **système courant** est mis en évidence.
- Mode système : tout est placé **à l'échelle** (tailles exagérées pour rester visibles ; plus de loupe des lunes,
  D13) et le **vaisseau** est une petite **sphère verte** d'où part un **segment** dans la direction de son axe avant.
- L'objet sélectionné porte une **croix 3D** qui le relie aux bords du cube ; sur les bords, ses **coordonnées
  précises** sont écrites.
- Un système (défaut **Sol**) peut être choisi comme **origine des coordonnées** ; les coordonnées affichées s'y
  rapportent.
- Réglage de la **vitesse de distorsion** (défaut 0,5 al/s).

## Astres : textures et rotation propre (lot 3, D21)

- Chaque astre est dessiné avec une **texture** adaptée et **tourne sur lui-même** autour d'un **axe** incliné
  (période, sens et inclinaison réels pour le système solaire ; la Lune montre toujours la même face à la Terre).
- Système solaire : textures réelles trouvées sur internet (Solar System Scope, NASA / USGS). Autres systèmes :
  textures génériques de planètes proches de ce que la science laisse supposer (géante gazeuse chaude, géante de glace,
  rocheuse brûlante ou glacée…) ; étoiles teintées par leur couleur.
- Un astre sans texture choisie reçoit sa texture automatiquement (les galaxies déjà sauvegardées s'affichent aussi
  texturées).

## Fond étoilé (lot 3, D22)

- Le ciel gagne un **fond étoilé générique** (milliers de petites étoiles), généré procéduralement à partir d'une
  graine tenue par le serveur : **tous les joueurs voient le même ciel**.

## Distorsion (lot 3, D23 ; remplace l'effet provisoire du lot 2)

Quand la charge de 3 s est validée, une animation en plusieurs étapes se lance, vue par toute la dimension :
- **départ** : bruit de moteur spatial ; toutes les étoiles du fond **convergent** vers la cible en 1 s ; quand elles
  l'atteignent, **flash** : la distorsion commence ;
- **pendant** : la cible est une **grosse source lumineuse** lointaine avec plusieurs **halos** et **flares** (deux
  étoiles à 4 branches, un peu floues, superposées, qui tournent sur elles-mêmes en sens opposés) ; **particules de
  vitesse blanches** ; le ciel prend un **dégradé arc-en-ciel** qui part de la destination jusqu'à l'arrière (le
  spectre lumineux s'étire autour du vaisseau) ;
- **arrivée** : **flash** (toute la skybox blanche), puis le blanc s'estompe sur la destination.

## Pilotage (lot 3, D24)

- Pendant le pilotage, **Sneak** (saut entre systèmes) est entièrement intercepté : le personnage ne s'accroupit pas.
- **Sprint** maintenu (touche choisie par le joueur) : Avancer / Reculer accélèrent ou décélèrent **bien plus vite** ;
  **Sprint + Saut** (Espace) freine **bien plus vite**.

## Space Knowledge (lot 3, `hololib:space_knowledge`, module joueur, D25)

- Reprend de Space Pilot la **connaissance du ciel** : anneaux autour des astres, tracés d'orbites, étoiles
  extérieures, nom à la vue ; sous le nom, la **distance** à l'objet regardé (activable dans sa Config) : distance à
  la surface d'un astre, en années-lumière pour une étoile extérieure.
- Compatibilités **Config** (ses interrupteurs) et **Quick Action** (héritée de Space Pilot : tout couper / rallumer).
- Pas nécessaire pour piloter ; pour le joueur local seulement.

## Space Pilot (lot 3, D25 ; remplace la section du lot 2 pour la répartition des rendus)

- Garde le **joystick**, la cible et la roue du saut, le lien vers le référentiel et le **HUD de navigation**, qui ne
  s'affichent désormais **que pendant le pilotage** (comme le joystick). Toujours requis pour piloter (D11).
- Le **lien** part exactement du bord du cercle avant et s'arrête exactement au bord de l'anneau du corps de
  référence, que Space Pilot dessine lui-même pendant le pilotage (confondu avec celui de Space Knowledge si les deux
  modules sont là) ; lien et anneau passent devant le terrain.
- Petite **Config** : HUD, lien vers le référentiel, vitesse de croisière ; plus de Quick Action.
- **Calculateur de vitesse de croisière** : le HUD affiche `Vitesse : <valeur> <unité/s> - <↑/↓> <valeur cible>
  <unité/s>`. La valeur cible est la vitesse optimale, calculée en temps réel, pour rejoindre l'astre visé (le plus au
  centre de l'écran) sans le manquer en allant trop vite ; la flèche dit s'il faut accélérer (↑) ou décélérer (↓) ;
  flèche et cible sont **vertes** si la vitesse actuelle en est proche, **rouges** si elle en est loin.

## Sons (lot 3, D26)

- **Accélération / décélération** : propulseurs doux et profonds (ronronnement grave de réacteur et léger sifflement
  de turbine qui monte avec la poussée et la vitesse), montées et descentes glissées, plus marquées en boost ;
  silence à l'arrêt (veille très basse si un pilote est aux commandes).
- **Rotation** : la coque travaille : gémissements métalliques sourds et lointains, rares « tonks » étouffés
  (joystick, roulis, alignement de la charge du saut), plus fréquents quand la rotation est rapide.
- **Saut** : bourdonnement de charge qui monte en tension pendant la charge (coupé si elle est annulée) ; distorsion :
  moteur au départ (convergence des étoiles), souffle au flash, puis grondement, effet de vitesse et sons de coque
  pendant le voyage ; flash d'arrivée.
- Entendus par tous les joueurs de la dimension (le monde est le vaisseau), sans position (comme une ambiance),
  réglés par le volume « Ambiance » ; sons libres de droits (CC0). Fait en T36.

## Space Map et Space Configurator (lot 3, D20 ; remplace la répartition carte / fenêtre des lots 1 et 2)

- Le Configurator est **séparé en deux modules de marker** :
  - **Space Map** (`hololib:space_map`, **tout le monde**) : garde le rendu de la carte (galaxie, systèmes, vaisseau,
    croix, coordonnées) et gagne une **fenêtre de navigation** (éditable séparément). La carte **s'adapte à la taille du
    cube** ; on **zoome vers le centre** ; la **liste des astres** est une **arborescence de la gravité** (la Terre et
    Vénus enfants du Soleil, la Lune enfant de la Terre) et sélectionner un astre **recentre** la carte. La vue est
    partagée par tous les joueurs. **Réalisée en T37** : les astres de la carte sont texturés et tournent sur
    eux-mêmes, la carte suit le temps réel de l'espace (le vaisseau à sa vraie place), toute la carte (étoiles,
    textes, traits) grandit avec le cube, la molette sur le cube ou la fenêtre zoome ; cliquer une lune montre aussi
    sa planète.
  - **Space Configurator** (ops) : garde sa **fenêtre d'édition** et se branche sur la **Space Map du même marker** :
    la carte aide au paramétrage. L'astre sélectionné dans la Map s'édite, y compris sa **rotation** et sa
    **texture** : choisir une texture du mod, ou **importer une image** depuis son ordinateur, envoyée au serveur qui
    l'applique pour tout le monde.

## Compléments (lot 3, D28–D31, demandés le 2026-10-10)

- **Temps** (D28) : le temps de l'espace s'écoule 10 fois moins vite (orbites, rotation des astres) ; un monde
  existant passe au nouveau rythme sans que les astres ne sautent.
- **Référentiel** (D29) : chaque astre a une masse ; l'astre de référence (dont le vaisseau garde la vitesse
  relative) est celui qui a le plus d'influence gravitationnelle sur le vaisseau, au sens des sphères d'influence :
  près de la Lune, la Lune ; entre la Terre et la Lune, la Terre ; loin de toute planète, le Soleil. Regarder ailleurs
  ne change plus le référentiel. La gravité n'agit pas (encore) sur le vaisseau.
- **Lunes** (D30) : Space Knowledge ne repère les lunes d'un astre que de bien plus près, quand elles se distinguent
  de leur planète, au lieu d'un groupe d'anneaux superposés.
- **Space Controller** (D31) : quand un pilote prend les commandes, la fenêtre du poste **qu'il utilise** disparaît
  pour tous les joueurs ; elle revient quand il les quitte. Les fenêtres des autres postes (autres markers) restent
  affichées et inutilisables tant que quelqu'un pilote (comportement actuel).

## Cibler un astre (lot 3, D33, demandé le 2026-10-11)

- Dans la liste des astres de la **Space Map**, la ligne sélectionnée (et elle seule) porte au bout un bouton
  **CIBLER** ; une fois cliqué, il devient **DECIBLER**. Changer la sélection retire la cible automatiquement.
- N'importe quel astre de la liste se cible : étoile, planète, lune, objet mineur, ou un système entier (son étoile),
  y compris dans un autre système que celui du vaisseau.
- La cible est celle **du vaisseau** : une seule pour toute la dimension, gardée quand on relance le monde. Tout
  joueur qui pilote (Space Pilot) la voit.
- **Aux commandes** : l'astre ciblé est entouré d'un anneau **vert** dans le ciel ; une **flèche verte** posée sur le
  bord du cercle de direction (le cercle avant du joystick, sa zone morte) pointe vers lui, pour savoir de quel côté
  tourner ; elle disparaît quand l'astre est dans le cercle. Un astre d'un autre système est montré sur l'étoile de
  ce système, ce qui aide à viser le saut ; après le saut, l'anneau passe sur l'astre lui-même.

## Configurator : RESET et téléportation (lot 3, D34, demandés le 2026-10-11)

- Dans la fenêtre du Configurator, l'astre sélectionné a un bouton **RESET** (remet tout l'astre à ses valeurs par
  défaut) et un bouton **Se teleporter** (le vaisseau apparaît près de l'astre, à l'arrêt, astre en référentiel et
  face à lui, même dans un autre système).

## Hors périmètre (à ne pas implémenter sans nouvelle demande)

- Lien entre la position de l'étoile et le cycle jour / nuit ou l'éclairage du monde Minecraft.
- Collisions, gravité réelle, physique newtonienne du vaisseau.
- Anneaux planétaires (Saturne…), nuages et face de nuit de la Terre, atmosphères dessinées à part (L21).
