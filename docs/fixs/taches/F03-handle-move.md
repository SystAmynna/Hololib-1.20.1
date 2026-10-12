# F03 — `handleMove` et `handleOpen` soumis aux règles

**Objectif** : un client modifié ne doit pas pouvoir déplacer, agrandir ou changer de famille un objet de sa mémoire
en dehors des règles d'édition, ni ouvrir un objet qui ne s'ouvre pas. Rapport § 7.1-3, recommandation 3.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat (vérifié)
- `holo/memory/HoloContainers.java:187-202` (`handleMove`) :
  - seul le propriétaire du scope joueur peut déplacer (correct) ;
  - mais **n'importe quel objet** de sa mémoire, éditable ou non, sans `HoloEditRules.mayEdit` / `accepts` ;
  - seul garde-fou : `HoloAnchor.acceptable(transform)`. Il accepte une échelle jusqu'à `HoloTransform.MAX_SCALE`
    (4096) et un changement d'ancrage quelconque (World → Hud…).
- Conséquence : un objet `PUBLIC` agrandi couvre l'écran des joueurs qui inspectent le propriétaire.
- `handleOpen` (`:205`) ne vérifie pas que l'objet est ouvrable.
- Utilisateurs légitimes de `handleMove` (à confirmer en lisant le code) :
  - `requestTransform` des fenêtres Config déplacées par leur cadre ;
  - placement des `HoloRelativeToOpen` par le client du propriétaire ;
  - aperçu du Theme Configurator ;
  - roue Quick Action.
  
  Ils ne doivent pas être cassés.

## À lire avant
- Rapport § 7.1-3 ; [`decisions.md`](../decisions.md) FC4.
- `holo/memory/HoloContainers.java` (`handleMove`, `handleOpen`, `handleEdit` pour le modèle de vérification),
  `holo/edit/HoloEditRules.java`, `holo/object/HoloAnchor.java` (`acceptable`), `holo/object/HoloObject.java`
  (`requestTransform`, `requestOpened`, `opened`).
- Tous les appels de `requestTransform` / `HoloMoveObjectPacket` et de `requestOpened` / `HoloOpenObjectPacket`
  (grep) : leur liste va dans le compte rendu.

## Zone
`holo/memory/HoloContainers.java` (`handleMove`, `handleOpen`), méthodes privées ajoutées à côté ;
`holo/edit/HoloEditRules.java` (méthode package-private ou publique **seulement** si elle existe déjà sous une autre
forme ; sinon demander).

## Livrables
1. `handleMove` refuse un changement de **famille** d'ancrage (`Sky`, `World`, `Local`, `Hud`). À l'intérieur de
   `Local`, le passage relatif ↔ follow reste permis s'il l'est aujourd'hui (outil `HoloEditFixOrDynamic`) : le
   vérifier.
2. `handleMove` applique au transform les mêmes bornes de composantes que `HoloEditRules.accepts` pour les outils de
   l'objet. Un objet non `HoloEditable` ne peut être déplacé que par les chemins légitimes listés. Si ces chemins ne
   se distinguent pas des autres, s'arrêter et décrire le besoin (par exemple un marqueur « déplaçable par son
   propriétaire » sur l'objet = API).
3. En cas de refus, renvoi correctif du placement serveur, comme aujourd'hui.
4. `handleOpen` n'accepte que les objets qui s'ouvrent : `HoloRelativeToOpen`, ou objets dont `shown()` dépend de
   `opened()`. Le critère exact est décidé après lecture et noté dans le compte rendu.

## Interdits
- Modifier `handleEdit` (modèle déjà correct) ou la forme des paquets.
- Casser un chemin légitime : fenêtres Config, roue, aperçu de thème, fenêtres `HoloRelativeToOpen` replacées à
  l'ouverture du focus.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : déplacer une fenêtre Config par son cadre fonctionne et reste placée après reconnexion ; ouvrir et fermer
  le focus replace les fenêtres ; la roue Quick Action s'affiche au regard ; l'aperçu du Theme Configurator suit la
  configuration.
- Banc jetable ou relecture argumentée : un `HoloMoveObjectPacket` qui change World → Hud ou met l'échelle à 4096 est
  refusé et le placement est renvoyé.

## Compte rendu attendu
Liste des chemins légitimes trouvés, règle retenue pour chacun, critère d'ouvrabilité, fichiers, scénario en jeu.
