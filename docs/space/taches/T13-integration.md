# T13 — Intégration et documentation

**Objectif** : vérifier l'ensemble en conditions réelles, corriger les défauts d'intégration, et mettre la
documentation du mod à jour.

**Dépendances** : T01 à T12.

## À lire avant
- Tout le dossier `docs/space/`, en particulier [`suivi.md`](../suivi.md) (écarts notés) et
  [`decisions.md`](../decisions.md).
- `.claude/CLAUDE.md`, `rapport.md` (sections 9 « Commandes » et 10 « Étendre le mod »).

## Livrables
- Corrections d'intégration (dans les packages Space seulement ; sinon demande d'API).
- `.claude/CLAUDE.md` : arborescence (`modules/dimension/space`, `modules/monde/...`, renderers), une section courte
  « Modules Space » (rôles, liens entre modules, règles : Environment seule autorité, poignées sondées, sync
  froid / chaud) — au style du fichier (sans accents).
- `rapport.md` : modules Space dans la liste des modules et un scénario pas à pas (pilotage).
- `docs/space/suivi.md` à jour ; `decisions.md` complété des choix faits pendant la construction.

## Scénario de recette (avec l'utilisatrice)
1. Monde neuf, op : `/hololib_module add dimension here hololib:space_environment` → ciel spatial.
2. `/hololib_marker create poste` + `space_controller` ; `/hololib_marker create carte` + `space_configurator`.
3. Configurator : ajouter un système, le déplacer, l'éditer (ajouter une planète + lune, incliner une orbite),
   « Monde ici » sur ce système.
4. Controller : piloter vers la nouvelle planète ; vérifier le freinage d'approche ; Échap.
5. `runClient2` (non-op) : ciel identique et fluide pendant le pilotage, pas de cube ni de fenêtre du Configurator,
   peut piloter quand c'est libre.
6. Redémarrer : tout est conservé (galaxie, pose, cube, cadrage).
7. Retirer les trois modules : ciel vanilla, aucune erreur.
8. Performances : FPS avec la galaxie par défaut et avec 256 systèmes (commande ou ajout répété) ; débit réseau
   raisonnable pendant le pilotage.

## Critères d'acceptation
- `compileJava` réussit ; scénario passé ou écarts consignés dans `suivi.md` avec une proposition.

## Recette groupée (préparée en T13, à suivre en un passage)
Préparation : monde neuf en créatif, `runClient` (op) ouvert en LAN + `runClient2` non-op.
0. Sans module Space (T03) : ciel / brouillard inchangés (jour, nuit, pluie, eau, lave, Nether).
1. Environment (T04, T05) : ciel noir, astres avec phases, brouillard noir hors de l'eau ; client 2 identique ; avec
   `planet_1` : liseré bleuté ? (L7) ; champ d'étoiles décoratif utile ?
2. Controller (T06, T08, D5) : fenêtre (vitesse, référentiel, altitude) ; clic → pilote, cercles chez le pilote seul
   (F5 compris) ; client 2 « Poste occupé » ; interrupteur « ops » refusé / pilote non-op libéré.
3. Pilotage (T07–T07f) : touches rebindées ; gaz, marche arrière, frein Espace ; signes du lacet / tangage / roulis
   (C28) ; arrêt près de la Terre sans saccade, référentiel stable ; loin de tout, viser un corps (L11) ; descente à
   ≈ 64 km (Terre) / 17 km (Lune), horizon net, LOD sans saut ; observateur fluide (L13) ; Échap ; taille des
   cercles (C29).
4. Configurator (T09–T12) : non visible du client 2 ; cube édité (fenêtre suit, échelle uniformisée — correction
   reçue au relâchement ?) ; galaxie : ajouter, renommer, poignée, recadrer, avant du monde, retirer ; système :
   loupe, + planète / lune, poignée d'orbite (inclinaison, `a`, `e`, période qui suit), champs, Animer, retirer
   avec descendants, Retour ; RESET de la poignée d'orbite (L16).
5. « Monde ici » sur le nouveau système (deux clients) ; piloter vers la nouvelle planète.
6. Persistance (C37) : se garer à `V = 0` près d'une planète, attendre > 5 min (sauvegarde auto), quitter / revenir :
   planète au même endroit ; galaxie, pose, cube, cadrage, sélection, mode conservés.
7. Retraits : Controller pendant le pilotage ; Environment (ciel vanilla, messages « Aucun Space Environment »),
   réinstallation ; les trois modules sans erreur.
8. Performances : FPS galaxie par défaut puis beaucoup de systèmes ; débit réseau pendant le pilotage.
