# T12 — Mode système du Configurator

**Objectif** : éditer un système dans le cube : afficher ses corps et orbites, ajouter / modifier / retirer étoiles,
planètes et lunes, éditer les orbites avec les outils Rotation et Échelle via une poignée d'orbite.

**Dépendances** : T11.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) §§ 3, 7 (Kepler, échelle système, loupe des lunes, poignée
  d'orbite) ; [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 2, 3, 6 ; [`decisions.md`](../decisions.md) C5, C8.
- Tout ce qui a été lu pour T11 ; `holo/edit/HoloEditMath.java` (`rotation`, `setRotation`) ;
  `holo/widget/control/HoloWidgetValueBar.java`, `HoloWidgetSwitch.java`.

## Livrables
- `SpaceConfiguratorWindow` (contenu du mode système), `SpaceOrbitHandle`, actions dans `SpaceConfiguratorModule` ;
  rendu du mode système dans `SpaceMapCubeRenderer` ; renderer de la poignée d'orbite (l'ellipse).

## Spécification
- Entrée en mode système (« Éditer ») : `t_edit` figé ; le cube montre le système sélectionné (corps aux positions de
  Kepler à `t_edit`, orbites en ellipses, échelle et loupe du doc 04 § 7, rayons affichés bornés), le nom des corps ;
  « Animer » (interrupteur) fait avancer le temps d'affichage localement (client), sans rien envoyer.
- Fenêtre : liste des corps (indentée par parenté), « + Étoile », « + Planète », « + Lune » (parent = sélection
  compatible), « Retirer » (descendants compris, confirmation), champs du corps sélectionné : nom, rayon (km), couleur
  (palette ou barre de teinte), luminosité (étoiles), période (jours), phase (`HoloWidgetValueBar` : valeur 0..1 convertie en 0–360° par la fenêtre ; la barre affiche un pourcentage,
  pas de 1 % = 3,6° ; un `HoloLabel` voisin affiche les degrés) ; valeurs
  d'orbite lues (a en UA, e) ; « Retour » → mode galaxie.
- `SpaceOrbitHandle` : `World()`, `HoloEditRotation + HoloEditScale`, op, `PUBLIC`, `shown()` = op ; existe
  seulement si un corps **à orbite** est sélectionné en mode système (clé `orbit`) ; posée sur le parent affiché, sa
  rotation = `R_cube · R_orbite`, son échelle = `(a_aff·σ, a_aff·σ, b_aff·σ)` ; sondage et conversion inverse du doc
  04 § 7 → `updateBody` (orbite). `scaleY` réécrit.
- Toute modification passe par `updateBody` / `addBody` / `removeBody` de l'Environment ; si le système édité est le
  système courant, le ciel se met à jour (révision).

## Interdits
- Modifier `kind` ou `parentId` d'un corps existant (retirer puis ajouter). Modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : « Éditer » sur le système courant montre étoile, planètes, lune (visible grâce à la loupe) et leurs
  orbites ; ajouter une planète l'affiche dans le cube et dans le ciel ; tourner la poignée d'orbite incline l'orbite
  (vérifiable dans le cube et dans le ciel) ; l'échelle de la poignée change `a` et `e` ; retirer une planète retire
  ses lunes ; « Retour » rend la galaxie ; tout est conservé après redémarrage.

## Compte rendu attendu
Fichiers ; ergonomie retenue pour les champs numériques ; écarts au doc 04 § 7.

## État laissé par T11
`SpaceConfiguratorWindow` contient un panneau système **provisoire** (`systemPanel`, « à venir (T12) ») avec un bouton
« Retour » : le remplacer par le panneau du mode système en gardant « Retour ». Actions existantes du module : codes 0 à 7
(sélection, mode, recadrer, ajouter, retirer, renommer, monde ici, avant du monde) ; continuer la numérotation.
