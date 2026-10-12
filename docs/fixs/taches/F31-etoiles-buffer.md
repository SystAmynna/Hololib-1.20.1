# F31 — Fond étoilé et sphère de fond Space dans un buffer statique

**Objectif** : les 4 000 étoiles procédurales (16 000 sommets) et la sphère de fond sont construites **une fois par
graine** dans un buffer GPU. À chaque frame, elles sont seulement dessinées avec une matrice. Rapport § 6.1,
recommandation 19.

**Lot** : C   **API** : non   **Dépendances** : F23

## Constat
- `client/render/modules/dimension/space/SpaceSkyRenderer.java:425-447` (`drawStarPattern`) : quads précalculés,
  mais **ré-émis** à chaque frame.
- La sphère de fond 16 × 8 est ré-émise elle aussi.
- Exception : pendant la convergence de la distorsion (T35), les étoiles sont recalculées pendant environ 1 s. Ce
  cas doit rester géré.

## À lire avant
- Rapport § 6.1 ; `docs/space/02-architecture.md` § 5 ; `docs/space/decisions.md` C93–C95, D23.
- `SpaceSkyRenderer`, `warp/*` (convergence), `modules/dimension/space/model/SpaceStarField.java`, la carte F30
  (même technique), `client/render/sky/*` (cible hors écran avec shaderpack).

## Zone
`client/render/modules/dimension/space/**`.

## Livrables
1. Buffer statique du fond étoilé, reconstruit au changement de graine, libéré au retrait de l'Environment ou à la
   déconnexion. Dessin par matrice (orientation du ciel).
2. Même traitement pour la sphère de fond si le gain le justifie (mesurer).
3. Pendant la convergence de la distorsion : émission immédiate, comme aujourd'hui.
4. Avec shaderpack : vérifier le rendu dans la cible hors écran du ciel. Repli si nécessaire.
5. **Mesure avant / après** (FPS, ciel Space, regard fixe, avec et sans Oculus).

## Interdits
- Changer l'apparence (taille, couleur, éclat, bande et renflement de la Voie lactée).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu :
  - ciel identique ;
  - mêmes étoiles en LAN ;
  - saut avec convergence intacte ;
  - retrait et réinstallation de l'Environment sans fuite ;
  - mesure jointe.

## Compte rendu attendu
Conception, fichiers, mesures, comportement avec shaderpack.
