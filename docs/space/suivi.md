# Suivi

Tenu par l'orchestrateur. Statuts : `à faire`, `en cours`, `à vérifier en jeu`, `faite`, `bloquée` (motif dans
Notes, et demande dans [`decisions.md`](decisions.md) si c'est une question d'API).

| Tâche | Titre | Dépend de | Statut | Notes / écarts |
|---|---|---|---|---|
| [T01](taches/T01-modele.md) | Modèle de données | — | faite | `save()` sans argument (doc 03 corrigé) ; `setCurrentSystem` reporté en T04 ; C12–C15. Fichiers : `modules/dimension/space/{package-info, model/*}` |
| [T02](taches/T02-mathematiques.md) | Mathématiques pures | T01 | faite | Garde-fou sur le segment, Kepler de Danby, `defaultPose` prend les réglages (doc 04 corrigé, C16–C18, L6) ; signes de rotation à vérifier en jeu en T07. Fichiers : `modules/dimension/space/math/*` (+ `SpaceRotations`, `SpaceMotionSettings`) |
| [T03](taches/T03-extension-brouillard.md) | Extension API : couleur du brouillard | — | à vérifier en jeu | Couleur réécrite seulement si changée, restaurée après exception (doc 06 respecté). Fichiers : `SkyRenderer`, `RenderDispatcher`, `client/render/package-info`, `.claude/CLAUDE.md`, `rapport.md` § 6.1. En jeu : ciel / brouillard inchangés sans module Space (jour, nuit, pluie, eau, lave, Nether) |
| [T04](taches/T04-environment-module.md) | Module Space Environment | T01, T02 | à vérifier en jeu | Envois limités (doc 05 corrigé), un tick de retard à l'affichage, `Backend` à 3 méthodes (doc 02 corrigé), C19–C22 ; garde `current == null` ajoutée par l'orchestrateur dans `readSync`. Fichiers : `modules/dimension/space/SpaceEnvironmentModule`, `client/modules/dimension/space/*`, `HoloLibModules` (+1 enregistrement). En jeu : add / quitter-revenir / remove sans erreur, second client en LAN |
| [T05](taches/T05-rendu-ciel.md) | Rendu du ciel spatial | T03, T04 | à vérifier en jeu | Lancée avant le test en jeu de T03 / T04 (choix de l'utilisatrice). Calottes au lieu de disques plats (doc 04 corrigé), C23, L7. Fichiers : `client/render/modules/dimension/space/*`, `ModuleRenderers` (+1 appel). Champ d'étoiles décoratif : à juger après le test en jeu |
| [T06](taches/T06-controller-module.md) | Module Space Controller | T04 | à vérifier en jeu | Lancée avant le test en jeu de T04 (choix de l'utilisatrice). Battement dès T06 (sinon libéré après 10 s), C24–C26 ; points d'extension pour T07 notés dans sa carte. Fichiers : `modules/monde/{package-info, spacecontroller/*}`, `HoloLibModules` (+1 enregistrement) |
| [T07](taches/T07-pilotage.md) | Captor de pilotage | T06 | à vérifier en jeu | Lancée avant le test en jeu de T06. Seul `SpacePilotPad` (+ package-info) modifié ; signes non touchés (à vérifier en jeu, C28) ; clics capturés (C27), L8 |
| [T08](taches/T08-joystick.md) | Mini-interface joystick | T07 | à vérifier en jeu | + interrupteur « tous / ops » (D5, sans `HoloAccess`) ; fenêtre 176 × 108 ; C29. Fichiers : `modules/monde/spacecontroller/{SpaceJoystickForward, SpaceJoystickAim}` + modifs du module / fenêtre / pad, `client/modules/monde/*`, `client/render/modules/monde/*`, `ModuleRenderers` (+1 appel) |
| [T07b](taches/T07b-frein.md) | Vitesse signée et frein (Espace) | T02, T04, T06–T08 | à vérifier en jeu | Demande de l'utilisatrice (D6). `SpacePilotInput` sur 5 octets (doc 05 corrigé) ; vérifications numériques OK (arrêt exact à 0, marche arrière jamais dans un corps) |
| [T07c](taches/T07c-referentiel.md) | Vitesse relative au corps de référence | T02, T04, T06, T07b | à vérifier en jeu | D7. `step` prend `refId` + temps de début et de fin ; bloc chaud + `varint ref` ; docs 04 / 05 corrigés ; C30, L9, L10. Vérifs numériques : distance à la Terre constante à 3e-13 sur 2000 ticks, 0 bascule à mi-distance, identique bit à bit à l'ancien calcul quand le Soleil est la référence |
| [T07d](taches/T07d-ciblage.md) | Ciblage distance × regard, affichage sans saccade | T07c | à vérifier en jeu | D8. `SpaceFrameState` (nouveau), entrée 7 octets (visée), bloc chaud + 5 varints ; docs 04 / 05 réécrits ; C31, L11, L12. Vérifs numériques : suit la visée en 10 ticks, cooldown ≥ 60, fondu continu, affichage exact à 4e-12 près de la Lune (au lieu de 13 km), déterminisme bit à bit, régression identique avec le Soleil comme référence |
| [T07e](taches/T07e-saccades.md) | Saccades à l'approche : diagnostic + lissage client | T07d | à vérifier en jeu | Cause principale mesurée : recalage du temps vanilla (saut de 146–163° par frame stationné près de la Terre / Lune) → horloge d'affichage propre + historique 32 ticks + retard observateur ; après : 0° stationné, saut max = idéal en approche. Doc 05 réécrit ; C20, C22 revus ; C32, L13, L14 ; L12 levée. Seul `SpaceEnvironmentModule` (client) modifié |
| [T07f](taches/T07f-approche.md) | Approche plus près, taille apparente réelle, LOD | T07e | à vérifier en jeu | D9. Plancher 1 % R jamais franchi (banc), plafond 80° levé, LOD 4 niveaux avec hystérésis, normale exacte en perspective (doc 04 § 4 corrigé), seuil `tooFar` selon l'altitude (doc 05, C32) ; loin de tout identique bit à bit |
| [T09](taches/T09-configurator-module.md) | Module Space Configurator | T04 | à vérifier en jeu | C33, L15. Fichiers : `modules/monde/spaceconfigurator/*`, `client/modules/monde/spaceconfigurator/*`, `client/render/modules/monde/spaceconfigurator/*` (renderer provisoire), `HoloLibModules` et `ModuleRenderers` (+1 chacun). À vérifier : correction d'échelle reçue par le client qui vient de relâcher le cube |
| [T10](taches/T10-rendu-cube.md) | Rendu du cube galaxie | T09 | à vérifier en jeu | Renderer provisoire complété (seul `SpaceMapCubeRenderer` + package-info) ; `RenderType` local (doc 02 § 8), règles depuis le coin `(-1,1,1)` (doc 04 § 7) ; C34. Coût ≈ 74 quads / étoile (≈ 19 000 à 256 systèmes, non mesuré en jeu) |
| [T11](taches/T11-edition-galaxie.md) | Édition de la galaxie | T10 | à vérifier en jeu | Actions 3 à 7 (ajouter, retirer, renommer, monde ici, avant du monde), poignée `SpaceMapHandle` + renderer, fenêtre 240 × 136 ; C35 ; doc 02 § 7 et carte T12 annotés |
| [T12](taches/T12-mode-systeme.md) | Mode système | T11 | à vérifier en jeu | Actions 8 à 11, `SpaceOrbitHandle`, `SpaceSystemScale`, `SpaceOrbitHandleRenderer`, `t_edit` synchronisé ; loupe arrondie, décomposition robuste (aller-retour ≤ 0,1° sur 100 000 cas) ; docs 02 / 04 / 05 annotés ; C36, L16 |
| [T13](taches/T13-integration.md) | Intégration et documentation | toutes | à vérifier en jeu | Revue statique : 2 défauts corrigés (pose recalée au chargement, clé `time` ; vertex sans allocation dans 4 renderers) ; `.claude/CLAUDE.md` (arborescence + section « Modules Space »), `rapport.md` (§ 8.12, § 9) ; C37, C38, L17 ; docs 02 / 03 / 05 alignés. **Recette groupée à faire** (scénario dans le compte rendu de l'orchestrateur) |

## Journal

*(une ligne par événement : date, tâche, ce qui s'est passé)*
- 2026-10-09 — T01 : lancée (sous-agent general-purpose).
- 2026-10-09 — T01 : faite. `compileJava` OK, relecture (aller-retour NBT / réseau symétriques, invariants dans les constructeurs). Notes reportées dans T02 et T04.
- 2026-10-09 — T02 : lancée (sous-agent general-purpose).
- 2026-10-09 — T02 : faite. `compileJava` OK ; vérifications numériques de l'agent (garde-fou, déterminisme, orbites) ; départ de Kepler remplacé par l'orchestrateur (résidu max 5e-15 vérifié).
- 2026-10-09 — T02 : faite (voir tableau). T03 : lancée (sous-agent general-purpose).
- 2026-10-09 — T03 : implémentée, `compileJava` OK, diff relu (5 fichiers autorisés seulement). Reste le test en jeu par l'utilisatrice.
- 2026-10-09 — T04 : lancée (sous-agent general-purpose).
- 2026-10-09 — T04 : implémentée, `compileJava` OK, diff relu (seul `HoloLibModules` touché hors Space). Écarts consignés (C19–C22). Reste le test en jeu.
- 2026-10-09 — T05 : lancée avant le test en jeu de T03 / T04, à la demande de l'utilisatrice.
- 2026-10-09 — T05 : implémentée, `compileJava` OK, diff relu (seul `ModuleRenderers` touché hors Space, aucune allocation par frame relevée). Test en jeu groupé T03 + T04 + T05 à faire.
- 2026-10-09 — T06 : lancée avant le test en jeu de T04, à la demande de l'utilisatrice. Champ d'étoiles décoratif reporté après le test.
- 2026-10-09 — T06 : implémentée, `compileJava` OK, diff relu (actions validées côté serveur : source, marker, pilote). Test en jeu à faire.
- 2026-10-09 — D5 validée (interrupteur d'accès en T08). T07 : lancée.
- 2026-10-09 — T07 : implémentée, `compileJava` OK, diff relu, cohérence des signes avec T02 contrôlée sur papier. Test en jeu (sens) à faire.
- 2026-10-09 — T08 : lancée.
- 2026-10-09 — T08 : implémentée, `compileJava` OK, diff relu (vérifications op côté serveur, aucune allocation par frame). Lot pilotage T06–T08 complet, à tester en jeu.
- 2026-10-09 — D6 validée (vitesse signée, frein sur Espace) ; T07b créée et lancée.
- 2026-10-09 — T07b : implémentée, `compileJava` OK, `SpaceMotion.step` relu contre le doc 04 § 5. Test en jeu à faire.
- 2026-10-09 — Essai en jeu de l'utilisatrice : en coupant les gaz près d'une planète, elle s'éloigne. D7 validée (référentiel = corps le plus proche, hystérésis) ; T07c créée et lancée.
- 2026-10-09 — T07c : implémentée, `compileJava` OK, docs 04 / 05 alignés sur le code. Test en jeu à faire.
- 2026-10-09 — D8 validée (ciblage distance × regard, délai, cooldown, fondu ; saccade L10) ; T07d créée et lancée.
- 2026-10-09 — T07d : implémentée, `compileJava` OK. Doc 04 § 5 nettoyé (doublons dus à l'orchestrateur) et aligné ; doc 05 à jour. Réglage du poids du regard (L11) à décider.
- 2026-10-09 — θ₀ du ciblage porté à 20° (choix de l'utilisatrice, fait par l'orchestrateur). Saccades à l'approche signalées en jeu : T07e créée et lancée.
- 2026-10-09 — T07e : causes mesurées sur banc (horloge client, état en avance, rejeu observateur), corrigées côté client ; `compileJava` OK. Limite pilote en réseau (L14) à soumettre.
- 2026-10-09 — D9 validée (plancher 1 % du rayon, freinage 2 rayons, plafond 80° levé, LOD) ; T07f créée et lancée. Horodatage des entrées (L14) non retenu pour l'instant.
- 2026-10-09 — T07f : implémentée, `compileJava` OK, docs 04 / 05 et décisions alignés. Test en jeu à faire.
- 2026-10-09 — T09 : lancée.
- 2026-10-09 — T09 : implémentée, `compileJava` OK, diff relu (op vérifié en premier dans `handleAction`). Test en jeu à faire.
- 2026-10-09 — T10 : lancée.
- 2026-10-09 — T10 : implémentée, `compileJava` OK, docs 02 / 04 annotés. Test en jeu à faire.
- 2026-10-09 — T11 : lancée.
- 2026-10-09 — T11 : sous-agent arrêté par l'orchestrateur (limite d'usage atteinte), travail possiblement partiel.
- 2026-10-09 — T11 : aucune modification laissée par le sous-agent interrompu (fichiers inchangés depuis T10) ; relancée.
- 2026-10-09 — T11 : implémentée, `compileJava` OK, diff relu (op vérifié en premier, framework intact). Test en jeu à faire.
- 2026-10-09 — Échap valide le nom (convention du mod gardée). T12 : lancée.
- 2026-10-09 — T12 : implémentée, `compileJava` OK, diff relu (op vérifié en premier, framework intact). Lot édition complet. Test en jeu à faire.
- 2026-10-09 — T12 : période recalculée par Kepler quand `a` change à la poignée (orchestrateur, validé par l'utilisatrice). T13 : lancée.
- 2026-10-09 — T13 : implémentée, `compileJava` OK, revue statique faite. Toutes les tâches sont implémentées ; reste la recette groupée en jeu.
