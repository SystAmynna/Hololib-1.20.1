# T42 — Space Configurator branché sur la Space Map

**Objectif** : (D20) le Configurator ne garde que sa **fenêtre d'édition** (ops) et ses **poignées** ; il se branche
par compatibilité sur la **Space Map du même marker** : la carte sert d'aide au paramétrage, et l'astre ou le système
sélectionné dans la Map est celui qu'on édite.

**Dépendances** : T37, T39 (champ Masse à reprendre).

## À lire avant
- [`02-architecture.md`](../02-architecture.md) § 7 et la section Space Map (T37) ; [`04-mathematiques.md`](../04-mathematiques.md)
  § 7 ; [`decisions.md`](../decisions.md) C4, C5, C9, C33, C35, C36, C65, C66, D20, C77, C78, L16, L22.
- `modules/monde/spaceconfigurator/` (tout), `modules/monde/spacemap/` (T37) ; conteneur de position d'un marker
  (comment un module trouve un autre module de la même mémoire : parcourir `modules()` du conteneur, comme
  `SpaceEnvironmentModule.of`, sans rendre public `HoloContainer.find`).

## Livrables
1. Recherche de la Map du même marker, deux côtés, sans allocation par tick ; sans Map (C78) : fenêtre affichée avec
   « Space Map requise sur ce marker », boutons d'édition désactivés, aucune poignée.
2. Cube, renderer de carte, copie de la galaxie, sélection, mode, zoom et origine **retirés** du Configurator (pris
   dans la Map) ; la fenêtre affiche et édite la sélection de la Map (système en galaxie, corps en système) ; ajouter /
   retirer / renommer / « Monde ici » / avant du monde / vitesse de distorsion / échelle de temps (C119) / masse d'un
   corps (T39) conservés.
3. Poignées (`SpaceMapHandle`, `SpaceOrbitHandle`) placées et sondées dans le repère du cube **de la Map** et de sa vue
   (centre, zoom) ; recréées quand la sélection ou la vue de la Map change.
4. Actions renumérotées si utile (documenter la table) ; toutes refusées d'abord à un non-op (C9).
4 bis. (T37, C107) La Map suit le temps vivant ; la poignée d'orbite était posée et relue à un temps figé (`t_edit`) :
   ajouter un « temps figé » d'édition (Configurator seulement, pendant l'édition d'une orbite) ou replacer la poignée
   sans trafic par tick ; le dire. Le Configurator cesse d'implémenter `SpaceMapView` (C110 levée).
4 ter. **Fait avant T41** (C119) : ligne « Temps (s/tick) » du mode galaxie, action `15 TIME_SCALE`
   (`SpaceEnvironmentModule.setTimeScale`, époque reposée sans saut). À garder en déplaçant la fenêtre (renuméroter
   l'action si la table change).
5. Anciennes sauvegardes de Configurator relues sans erreur (L22 : vue perdue, à recréer par la Map).
6. Docs 02 § 7, 05 § 4, `.claude/CLAUDE.md` laissé à T46.

## Interdits
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : Map + Configurator sur un marker : un op sélectionne Mars dans la liste de la Map → la fenêtre du
  Configurator édite Mars, la poignée d'orbite apparaît sur la carte et modifie l'orbite ; un non-op voit la Map mais
  pas le Configurator ; Configurator seul : message « Space Map requise ».

## Compte rendu attendu
Fichiers, table des actions, écarts, scénario.
