# T17 — Module Space Pilot : socle, Config, Quick Action

**Objectif** : créer le module joueur `hololib:space_pilot` (D14) : son état (réglages d'affichage), sa fenêtre de
configuration (compatibilité **Config**) et son action de roue (compatibilité **Quick Action** : activer / désactiver
tout le rendu). Aucun rendu dans cette tâche (T18–T20).

**Dépendances** : T16.

## À lire avant
- [`01-vision.md`](../01-vision.md) § Space Pilot ; [`02-architecture.md`](../02-architecture.md) §§ 1, 2, 7 bis ;
  [`05-synchronisation.md`](../05-synchronisation.md) § 4 (Space Pilot) ; [`decisions.md`](../decisions.md) C44, C45,
  C46.
- `.claude/CLAUDE.md` §§ « Configuration en jeu », « Roue d'actions ».
- `modules/camera/config/Configurable.java`, `ConfigWindow.java` ; `demo/DemoConfigurableModule.java` (modèle à suivre).
- `modules/camera/quickaction/QuickActionProvider.java`, `QuickAction.java`, `QuickActionIcon.java` ;
  `demo/DemoQuickActionsModule.java` (modèle) ; `client/modules/camera/quickaction/QuickActionController.java`
  (quand le nom / l'icône d'une action sont relus : chaque frame ? à l'ouverture ?).
- `modules/HoloLibModules.java` (enregistrement) ; `holo/widget/control/` : `HoloWidgetSwitch`, `HoloWidgetValueBar`.

## Livrables
1. `modules/camera/spacepilot/` (+ `package-info.java`) : `SpacePilotModule extends HoloModule implements
   Configurable, QuickActionProvider`, compatibilité `PLAYER`, enregistré `space_pilot` dans `HoloLibModules`.
2. **État** (sauvegardé, synchronisé au propriétaire ; `sharedWithObservers()` faux, C45) : `enabled` (vrai),
   `orbits`, `bodies` (repérage des corps : anneau + nom), `stars` (étoiles extérieures), `names` (nom au regard,
   corps et étoiles), `nameAngle` (5°, 1 à 30°), `hud` (données de navigation), `refLink` (lien vers le
   référentiel) — tous vrais par défaut. Accesseurs publics lus par les renderers (T18–T20). Modifications par
   `sendAction` validées par le serveur (bornes, propriétaire).
3. **Config** : `configName()` = « Space Pilot » ; fenêtre (`ConfigWindow`, créée dans `onLoad` des deux côtés) avec
   un `HoloWidgetSwitch` par booléen ci-dessus sauf `enabled`, et un `HoloWidgetValueBar` pour l'angle (affiche la
   valeur en degrés). Libellés courts sans accents (« Orbites », « Reperage des corps », « Etoiles exterieures »,
   « Nom au regard », « Angle du nom », « Donnees de navigation », « Lien referentiel »).
4. **Quick Action** : une action `toggle`, créée dans `onLoad` ; `name()` = « Space Pilot : OFF » quand `enabled`,
   « Space Pilot : ON » sinon ; icône en quads (`drawIcon`) qui change aussi selon l'état ; `run()` envoie l'action
   de bascule. Si le nom n'est pas relu dynamiquement par la roue, le dire (pas de modification de `quick_action` :
   ce serait une demande d'API).
5. `client/modules/camera/spacepilot/SpacePilotClient` : backend / utilitaire client qui renvoie le `SpacePilotModule`
   **du joueur local** (`HoloModes.find(ClientHoloWorld.find(HoloScope.PLAYER), SpacePilotModule.class)`), sans
   allocation, pour les renderers des tâches suivantes.

## Interdits
- Modifier une classe hors des packages Space (dont `config`, `quick_action`, `HoloWidget*`).
- Créer un widget ad hoc si un `HoloWidget*` convient.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : `/hololib_module add player @s <memoire> hololib:space_pilot` ; le focus (H) → écrou → « Space Pilot » ouvre la
  fenêtre ; chaque interrupteur et l'angle se règlent et survivent à une reconnexion ; la roue Quick Action propose
  « Space Pilot : OFF », un clic bascule et l'action devient « ON ». Un second joueur qui inspecte ne voit ni la
  fenêtre ni l'action (attendu, C45).

## Compte rendu attendu
Fichiers créés / modifiés, actions réseau (octets), comportement de la roue au changement de nom, écarts, scénario.
