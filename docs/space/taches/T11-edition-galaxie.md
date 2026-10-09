# T11 — Édition de la galaxie

**Objectif** : rendre la fenêtre du Configurator fonctionnelle en mode galaxie (ajouter, sélectionner, renommer,
retirer des systèmes, choisir le système du monde, régler l'axe avant) et déplacer l'étoile sélectionnée avec l'outil
de translation via une poignée.

**Dépendances** : T10.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 7 (conversion poignée ↔ al) ; [`03-modele-donnees.md`](../03-modele-donnees.md)
  § 6 (opérations) ; [`decisions.md`](../decisions.md) C1, C4, C10.
- `holo/widget/control/HoloWidgetList.java`, `HoloWidgetButton.java`, `HoloWidgetTextBox.java`,
  `modules/camera/config/ConfigListWindow.java` (lignes reconstruites, sélection par couleurs).
- `holo/module/HoloModule.java` (`addObject` / `removeObject` à chaud, deux côtés) ; `client/holo/ClientHoloContainer.java`
  (`add` : `readSync` → `onLoad` → objets ; `update` : `readSync` → `onSync` → objets ; paquet d'objet inconnu ignoré).
- `client/edit/HoloEditSession.java` (`holds` : placement serveur ignoré pendant la manipulation),
  `client/edit/HoloEditTools.java` (translation sur axes monde, taille des gizmos).

## Livrables
- `SpaceConfiguratorWindow` (mode galaxie complet), `SpaceMapHandle`, et les actions correspondantes dans
  `SpaceConfiguratorModule` ; renderer de la poignée (petit marqueur) dans le package client des renderers.

## Spécification
- Fenêtre : liste des systèmes (`HoloWidgetList` de `HoloWidgetButton`, sélection en couleur), champ nom
  (`HoloWidgetTextBox`, validation à Entrée), boutons « Ajouter », « Retirer » (confirmation par second clic),
  « Éditer » (→ mode système, T12), « Monde ici » (système courant), « Recadrer », ligne « Avant du monde » (N / S /
  E / O / haut / bas : `HoloWidgetMultiButton`, clic gauche = choix suivant, clic droit = précédent ; ou six
  `HoloWidgetButton`). Chaque action : `sendAction(window, ...)`, serveur :
  op vérifié, appel de l'opération de l'Environment, message d'erreur au joueur si refus.
- `SpaceMapHandle` : `World()`, `HoloEditTranslation`, `opRequired = true`, `ownRequired = false`, `PUBLIC`,
  `shown()` = op ; **existe seulement** si un système est sélectionné en mode galaxie : créée dans `onLoad` (deux
  côtés) si l'état le demande, puis ajoutée / retirée au changement d'état (serveur au changement, client dans
  `onSync`), seulement si absente / présente (`addObject` lève sur une clé déjà utilisée), clé `handle`.
- Sondage (doc 04 § 7) : le module retient la dernière position qu'il a écrite ; un écart > 1e-4 dans `serverTick`
  = édition du joueur → conversion, bornage au cube, `moveSystem`. Le module réécrit la poignée quand la sélection,
  le cube, le cadrage ou la position du système changent.
- Le cadrage n'est **pas** recalculé automatiquement quand une étoile sort : la poignée est bornée au cube ;
  « Recadrer » l'agrandit.

## Interdits
- Un objet éditable par étoile (C4). Modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (op, `world_editor`) : ajouter un système l'affiche dans le cube et dans le ciel comme étoile de fond ;
  le renommer met à jour l'étiquette ; le sélectionner fait apparaître la poignée sur son étoile ; la déplacer avec
  l'outil translation (G) déplace l'étoile dans le cube et sa direction dans le ciel ; « Monde ici » change le ciel ;
  retirer fonctionne et ne casse pas la sélection ; tout est conservé après redémarrage.

## Compte rendu attendu
Fichiers ; actions et leur format ; comportements limites (poignée hors cube, système courant retiré...).
