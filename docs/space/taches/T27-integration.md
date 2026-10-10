# T27 — Intégration et documentation du lot 2

**Objectif** : vérifier que le lot 2 tient ensemble, corriger les défauts trouvés à la relecture, et mettre la
documentation du mod à jour.

**Dépendances** : T14 à T26.

## À lire avant
- Tout le dossier `docs/space/` ; `suivi.md` (écarts notés par tâche) ; `git diff main...HEAD -- src/` (ou depuis le
  commit `21f21b1`).
- `.claude/CLAUDE.md` (arborescence, section « Modules Space »), `rapport.md` (§ 8.12, § 9 commandes).

## Livrables
1. **Revue statique** du lot 2 : déterminisme (aucun `Math` trigonométrique ni `float` dans les pas partagés),
   allocations par frame dans les renderers Space et Space Pilot, état GL restauré, `package-info.java` présents,
   vérifications serveur (op en premier, propriétaire de Space Pilot, D11), anciennes sauvegardes (lot 1) relues.
   Corriger les défauts **dans les packages Space** ; signaler les autres.
2. **`.claude/CLAUDE.md`** : arborescence (`modules/camera/spacepilot`, renderers Space Pilot, `SpaceArchive`,
   `SpaceJump`, `SpaceWarpEffect`), section « Modules Space » complétée (Space Pilot, saut, persistance, systèmes réels,
   Configurator du lot 2), protocole réseau si changé.
3. **`rapport.md`** : § 8.12 (saut, Space Pilot), § 9 (commande `/hololib_module add player @s <memoire> hololib:space_pilot`).
4. Docs `01`–`05` alignés sur le code final ; `decisions.md` à jour (C et L nouveaux) ; `suivi.md`.
5. **Scénario de recette groupée** (dans le compte rendu) couvrant D10–D15, en solo puis avec un second client LAN.

## Interdits
- Modifier le framework ; ajouter des fonctionnalités.

## Critères d'acceptation
- `compileJava` réussit ; revue faite ; documentation à jour ; scénario fourni.

## Compte rendu attendu
Défauts trouvés et corrigés (fichier:ligne), défauts signalés hors périmètre, fichiers de doc modifiés, scénario.
