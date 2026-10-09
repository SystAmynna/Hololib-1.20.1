# 01 — Vision fonctionnelle

Ce document reformule la demande de l'utilisatrice et y intègre les décisions qu'elle a validées
([`decisions.md`](decisions.md), D1 à D4). Il décrit **ce que voit et fait le joueur** ; le « comment » est dans les
documents 02 à 06.

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
- Désigne le **système courant** : celui dans lequel se trouve le monde (D3 : un seul à la fois, pas de voyage
  interstellaire). Les autres systèmes apparaissent comme des **étoiles de fond**, placées selon la carte galactique
  (direction et éclat dépendent de leur distance au système courant).
- Simule les **orbites animées** sur le temps de jeu : les planètes tournent autour de leur étoile, les lunes autour
  de leur planète. Même résultat sur le serveur et chez tous les clients (calcul déterministe).
- Porte l'**origine** : position et orientation du monde dans le système courant, et sa vitesse.
- Dessine le ciel (D1) : fond spatial opaque qui masque le ciel vanilla (dégradé, soleil, lune, étoiles), brouillard et
  horizon noirs, étoiles de fond, corps du système courant vus depuis l'origine (taille apparente réelle, phases
  éclairées par l'étoile). Sans Space Environment installé, la dimension garde son ciel vanilla.
- Système d'exemple créé à l'installation (une étoile, quelques planètes, une lune) pour voir le résultat
  immédiatement.

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
  - Une **fenêtre à la base du cube** permet d'**ajouter**, **sélectionner**, **renommer**, **retirer** des systèmes, et
    de choisir le système courant de l'Environment.
- **Mode système** (bouton « Éditer » sur un système sélectionné) : le cube ne montre plus la galaxie mais **ce
  système** (étoiles, planètes, lunes, orbites dessinées).
  - La fenêtre liste les corps et permet d'**ajouter** (étoile, planète, lune), **modifier** (nom, rayon, couleur,
    période...), **retirer** des corps.
  - L'orbite du corps sélectionné s'édite avec les outils **Rotation** (plan de l'orbite) et **Échelle** (taille et
    forme de l'ellipse) de l'éditeur.
  - Un bouton **Retour** revient au mode galaxie.
- Toute modification est appliquée à la galaxie de l'Environment de la dimension, sauvegardée, et visible aussitôt
  dans le ciel si elle concerne le système courant.

## Hors périmètre (à ne pas implémenter sans nouvelle demande)

- Voyage d'un système à un autre en pilotant (D3).
- Lien entre la position de l'étoile et le cycle jour / nuit ou l'éclairage du monde Minecraft.
- Collisions, gravité réelle, physique newtonienne du vaisseau.
- Textures d'astres (les astres sont dessinés en quadrilatères colorés, comme `planet_1`).
