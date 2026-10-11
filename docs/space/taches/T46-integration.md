# T46 — Intégration et documentation du lot 3

**Objectif** : vérifier que le lot 3 tient ensemble, corriger les défauts trouvés à la relecture, mettre la
documentation du mod à jour.

**Dépendances** : T28 à T45 (dont T38–T41, ajoutées le 2026-10-10 : temps ÷ 10, masse et sphère d'influence, lunes
repérées de près, fenêtre du Controller masquée pendant le pilotage ; et T43, ajoutée le 2026-10-11 : astre ciblé
depuis la Space Map, D33).

## À lire avant
- Tout le dossier `docs/space/` ; `suivi.md` (écarts notés par tâche, fichiers listés par chaque compte rendu : **pas
  de git**, lire les fichiers eux-mêmes).
- `.claude/CLAUDE.md` (arborescence, section « Modules Space »), `rapport.md`.

## Livrables
1. **Revue statique** du lot 3 : déterminisme (rotation propre, boost, croisière, saut, époque du temps simulé,
   sphères d'influence : `StrictMath`, `double`),
   allocations par frame (ciel texturé, fond étoilé, distorsion, surcouche Knowledge, carte), état GL restauré, sons
   arrêtés proprement, vérifications serveur (op en premier, canal des textures, quota), anciennes sauvegardes du lot 2
   relues (Environment avec migration de l'échelle de temps et masses absentes, Configurator, Space Pilot) ; capture
   du pilotage non liée à la fenêtre (T41) bien rendue dans tous les cas ; cible du vaisseau (T43) : validée et
   effacée par le serveur (corps retiré, sélection changée), sauvegardée, anneau vert et flèche sans allocation. Corriger dans les packages Space ; signaler
   le reste.
2. **`.claude/CLAUDE.md`** : arborescence (`spacemap`, `spaceknowledge`, cache de textures, `warp/`, sons, canal des
   textures), section « Modules Space » (Map / Configurator, Knowledge / Pilot, textures et rotation, fond étoilé,
   distorsion, contrôles, sons, échelle de temps, masse et référentiel par sphère d'influence, repérage des lunes,
   fenêtre du Controller masquée pendant le pilotage, cible du vaisseau posée depuis la Map), ressources (`textures/space`, `sounds/space`, crédits).
3. **`rapport.md`** : commandes (`space_map`, `space_knowledge`), crédits des ressources.
4. Docs `01`–`05` alignés sur le code final ; `decisions.md` (C et L nouveaux) ; `suivi.md`.
5. **Scénario de recette groupée** (compte rendu) couvrant D20–D33, en solo puis avec un second client LAN.

## Interdits
- Modifier le framework ; ajouter des fonctionnalités ; utiliser git.

## Critères d'acceptation
- `compileJava` réussit ; revue faite ; documentation à jour ; scénario fourni.

## Compte rendu attendu
Défauts trouvés et corrigés (fichier:ligne), défauts signalés hors périmètre, fichiers de doc modifiés, scénario.
