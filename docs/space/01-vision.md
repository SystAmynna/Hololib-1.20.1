# 01 — Vision fonctionnelle

Ce document reformule la demande de l'utilisatrice et y intègre les décisions qu'elle a validées
([`decisions.md`](decisions.md), D1 à D19). Les sections marquées **(lot 2)** décrivent la demande du 2026-10-09
(tâches T14–T27, réalisées, à vérifier en jeu) ; le reste est le lot 1. Il décrit **ce que voit et fait le joueur** ;
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
  devient la cible (repérée par la surcouche de Space Pilot).
- Il **maintient Sneak** (la touche choisie par le joueur dans les options, `key.sneak`) :
  - le monde se **recadre en douceur** pour aligner son axe avant sur l'étoile ; le joystick ne répond plus ;
  - une **roue de chargement** de 3 s se remplit autour de l'étoile de destination ;
  - relâcher Sneak avant la fin annule.
- À la fin de la charge, le monde entre en **distorsion** : le ciel vire au **blanc** avec des **traînées de vitesse**
  dans la direction de l'étoile (effet **provisoire**, appelé à être remplacé : il est isolé dans le code). Tous les
  joueurs de la dimension le voient.
- La durée du voyage dépend de la **distance** entre le système d'origine et la cible, à une **vitesse de distorsion**
  réglable dans le Space Configurator (défaut **0,5 al/s** : Alpha du Centaure en ≈ 9 s).
- À l'arrivée, le système cible devient le système courant ; le monde est **près de l'étoile cible, sur l'axe du voyage,
  face à l'étoile, vitesse nulle** (D10, C59).

## Space Pilot (lot 2, `hololib:space_pilot`, module joueur)

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

## Hors périmètre (à ne pas implémenter sans nouvelle demande)

- Effet de distorsion définitif (celui du lot 2 est provisoire et sera redessiné).
- Lien entre la position de l'étoile et le cycle jour / nuit ou l'éclairage du monde Minecraft.
- Collisions, gravité réelle, physique newtonienne du vaisseau.
- Textures d'astres (les astres sont dessinés en quadrilatères colorés, comme `planet_1`).
