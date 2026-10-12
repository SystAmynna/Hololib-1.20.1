# F13 — `HoloWindowStyle` : style commun des fenêtres de module

**Objectif** : les fenêtres de marker (et toute fenêtre de module) prennent leur style d'une classe du framework,
et non plus des constantes de la fenêtre Config. Le code recopié (motif, bordure, barre de titre) disparaît.
Rapport § 4.2.

**Lot** : B   **API** : **oui : à valider** (nouvelle classe publique)   **Dépendances** : aucune ; après F12
conseillé (mêmes fichiers)

## Constat (vérifié)
- `ConfigWindow.ACCENT` / `BACKGROUND_ALPHA` sont utilisés par :
  - les fenêtres de marker `SpaceMapWindow`, `SpaceControllerWindow`, `SpaceConfiguratorWindow`,
    `AreaConfiguratorWindow` ;
  - `QuickActionModule` et `SpacePilotHud`.
- Chaque fenêtre de marker recopie le même `root().setPattern(...).setBorder(...)` et la même barre de titre (par
  exemple `SpaceMapWindow.java:156-168`).

## À lire avant
- Rapport § 4.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 2.
- `ConfigWindow` (constantes, construction du cadre et du titre), les six fichiers cités, `holo/window/HoloWindow`,
  `holo/widget/HoloPanel`, `holo/theme/HoloColor`.

## Demande d'API (à valider avant de commencer)
Nouvelle classe finale `holo/window/HoloWindowStyle` :
- constantes `HoloColor` (accent, fond avec alpha) ;
- méthode statique `apply(HoloWindow window, Component title)` (ou `decorate`) qui pose le motif, la bordure et la
  barre de titre comme aujourd'hui ;
- hauteur de la barre de titre exposée, pour la mise en page.

`ConfigWindow` l'utilise elle aussi. Ses constantes `ACCENT` / `BACKGROUND_ALPHA` deviennent des alias dépréciés,
puis disparaissent en F37 si personne ne les utilise.

**Alternative** : `ConfigWindow` garde ses constantes, et seules les fenêtres de marker passent par une classe utilitaire
**package-private** dans chaque module. Ni API, ni dédoublonnage entre modules.

## Zone
`holo/window/HoloWindowStyle.java` (nouveau), `ConfigWindow`, les six fichiers utilisateurs.

## Livrables
1. `HoloWindowStyle`, documentée.
2. Les six utilisateurs migrés ; le code recopié est supprimé.
3. Rendu **identique au pixel près** : mêmes couleurs, mêmes alphas, mêmes tailles, même barre de titre.

## Interdits
- Changer l'apparence (même légèrement) ou les tailles des fenêtres.
- Couleur en dur (règle des thèmes).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : captures avant / après des fenêtres Space Map, Controller, Configurator, Area Configurator, d'une fenêtre
  Config, de la roue et du HUD Space Pilot, avec deux thèmes (défaut, staff). Aucune différence visible.

## Compte rendu attendu
API créée, fichiers migrés, lignes supprimées, captures ou description de la comparaison.
