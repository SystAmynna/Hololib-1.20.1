# T41 — Fenêtre du Space Controller masquée pendant le pilotage

**Objectif** : (D31) quand un pilote prend les commandes, la fenêtre du Space Controller **qu'il utilise** (marker du
pilote) **disparaît pour tous les joueurs** ; elle réapparaît quand le pilote quitte le pilotage. Les fenêtres des
**autres postes** (autres markers de la dimension) restent affichées et inutilisables comme aujourd'hui (« Pilote par
X », « Poste occupe », clic refusé).

**Dépendances** : aucune.

## Constat (piège)
La capture du pilotage est **liée à la fenêtre** : le trait `HoloPointerInput` du pad démarre une capture liée à
l'objet, et `HoloInteractions.valid` la termine dès que l'objet n'est plus routé (`HoloRouting.isRouted`). Masquer la
fenêtre par `shown()` couperait donc le pilotage à l'instant où il commence.

## À lire avant
- [`02-architecture.md`](../02-architecture.md) § 6 ; [`05-synchronisation.md`](../05-synchronisation.md) § 3 ;
  [`decisions.md`](../decisions.md) C24, C25, C27, D11, D31.
- `modules/monde/spacecontroller/` (tout : `SpaceControllerModule.pilot()`, `SpaceControllerWindow`, `SpacePilotPad`),
  `holo/widget/control/HoloPointerInput.java`, `holo/capture/HoloCaptures.java`, `client/interact/HoloInteractions.java`
  (`dropInvalid`, `valid`), `holo/object/HoloObject.shown()`.

## Livrables
1. `SpaceControllerWindow.shown()` faux seulement quand le pilote est **à ce poste** (`pilot != null && pilotHere`,
   état du module déjà synchronisé chez tous) ; les autres postes restent affichés, inchangés. En session d'édition
   la fenêtre reste révélée (framework).
2. Pilotage indépendant de la visibilité : le pad démarre sa capture **sans la lier à la fenêtre**
   (`HoloCaptures.begin` sur un captor propre, au clic) au lieu de la capture liée du trait ; il la termine lui-même
   dans tous les cas où la capture liée le faisait : Échap / Entrée, autre pilote, délai C25, Environment absent,
   accès perdu (interrupteur « ops »), Space Pilot retiré (D11), **module déchargé ou marker retiré** (`onUnload`),
   joueur déconnecté / changement de dimension. Aucun changement du trait ni du framework (sinon : s'arrêter et
   décrire le besoin).
3. Sur le poste utilisé, les autres joueurs ne voient plus « Pilote par X » (fenêtre masquée) ; sur les autres postes,
   « Pilote par X » / « Poste occupe » restent affichés et le clic reste refusé. HUD de Space Pilot inchangé.
4. Docs 02 § 6, 05 § 3 ; `.claude/CLAUDE.md` (section Space Controller) laissé à T46.

## Interdits
- Modifier une classe hors des packages Space.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : clic sur la zone → la fenêtre disparaît chez le pilote et chez un second joueur LAN ; un second poste
  (autre marker) reste visible, affiche le pilote et refuse le clic ; le pilotage complet
  marche (gaz, roulis, joystick, frein, saut) sans coupure ; Échap → la fenêtre réapparaît partout ; marker retiré
  (ou déchargé) pendant le pilotage → capture rendue, souris libre ; un pilote qui se déconnecte libère le poste et la
  fenêtre revient.

## Compte rendu attendu
Fichiers, cas de fin de capture couverts, écarts, scénario en jeu.
