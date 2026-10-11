# Annexe — données réelles des systèmes par défaut

Source des valeurs de la galaxie par défaut (`SpaceGalaxy.createDefault`, tâche T16 ; masses : T39, section
« Masses »).
Recherche faite le **2026-10-09** par l'orchestrateur. Le sous-agent **recopie** ces valeurs ; il n'en invente pas et
n'en corrige pas sans le signaler dans son compte rendu.

## Conventions de recopie

- **Unités du modèle** ([`03`](03-modele-donnees.md) § 1) : rayon en km, `a` en UA, période en jours, luminosité
  relative au Soleil, angles en degrés. `R☉ = 695 700 km`, `R⊕ = 6 371 km`, `1 UA = 149 597 870,7 km`,
  `1 an = 365,25 j`.
- **Orientation de l'orbite** (`SpaceOrbit(a, e, yaw, pitch, roll, période, phase)`) : approximation déjà utilisée par
  le lot 1 — `yaw` = longitude du périhélie `ϖ` (inconnue : 0), `pitch` = inclinaison, `roll` = 0. Le nœud ascendant
  n'est pas modélisé.
  - Planètes et objets mineurs de Sol : `pitch` = inclinaison sur l'écliptique.
  - **Lunes** : `pitch` = **obliquité du parent + inclinaison sur l'équateur du parent** (colonne « pitch » déjà
    calculée), ramené à `[−180, 180[` par `SpaceLimits`. Effet voulu : les lunes d'Uranus tournent presque à angle
    droit de l'écliptique, Triton est rétrograde.
  - Étoiles compagnes et exoplanètes : `pitch` = inclinaison sur le plan du ciel (seule valeur publiée) ; repère sans
    rapport avec celui du système, mais donne des orbites variées.
- **Phase** (anomalie moyenne à `t = 0`) : Sol = anomalie moyenne J2000 approchée (planètes, Pluton) ; tout le reste
  **arbitraire** (valeurs étalées, notées « arb. »). Aucune n'est censée être exacte.
- **Rayon estimé** (« est. ») : exoplanète sans transit, rayon tiré par l'archive NASA d'une relation masse-rayon à
  partir de la masse minimale. À garder tel quel, la javadoc le dit.
- **Couleurs** : proposées par l'orchestrateur (ARGB opaque), ajustables sans demande.
- **Noms** : recopiés **sans accents** dans le code (convention du mod, C15 : Vénus → `Venus`, Ganymède →
  `Ganymede`, Éris → `Eris`, Makémaké → `Makemake`, Déimos → `Deimos`, Téthys → `Tethys`, Dioné → `Dione`,
  Rhéa → `Rhea`, Obéron → `Oberon`, Cérès → `Ceres`, Altaïr → `Altair`, Véga → `Vega`) ; ce document garde
  l'orthographe française.
- **Ordre des corps** : celui des tableaux (ids croissants dans cet ordre) ; l'étoile principale en premier, id 1.

## Bornes à modifier (`SpaceLimits`, dans la tâche T16)

| Borne | Actuelle | Nouvelle | Motif |
|---|---|---|---|
| `MAX_BODIES` | 32 | **48** | Sol : 34 corps |
| `MIN_SEMI_MAJOR_AXIS` | 1e-4 UA | **1e-5 UA** | Phobos : 6,27e-5 UA |
| `MAX_PERIOD` | 1e7 j | **1e9 j** | Proxima autour d'Alpha Cen A : 2,0e8 j |

Le budget réseau ([`05`](05-synchronisation.md) § 5) est recalculé par T16 avec 34 corps dans le système courant.

## Sol (position galactique 0, 0, 0) — 34 corps

### Soleil et planètes

Sources : NASA Planetary Fact Sheets (<https://nssdc.gsfc.nasa.gov/planetary/factsheet/>), JPL SSD, éléments
moyens J2000 (<https://ssd.jpl.nasa.gov/planets/approx_pos.html>).

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | yaw ϖ | pitch i | Période (j) | Phase |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Soleil | STAR | — | 695 700 | `0xFFFFE07A` | 1 | — | — | — | — | — | — |
| 2 | Mercure | PLANET | 1 | 2 439,7 | `0xFF9A9A9A` | 1 | 0,3871 | 0,2056 | 77,46 | 7,00 | 87,969 | 174,8 |
| 3 | Vénus | PLANET | 1 | 6 051,8 | `0xFFE6C88A` | 1 | 0,7233 | 0,0068 | 131,53 | 3,39 | 224,701 | 50,1 |
| 4 | Terre | PLANET | 1 | 6 371,0 | `0xFF3A6FD8` | 1 | 1,0000 | 0,0167 | 102,94 | 0,00 | 365,256 | 357,5 |
| 5 | Mars | PLANET | 1 | 3 389,5 | `0xFFC1440E` | 1 | 1,5237 | 0,0934 | 336,04 | 1,85 | 686,980 | 19,4 |
| 6 | Jupiter | PLANET | 1 | 69 911 | `0xFFD8A06A` | 1 | 5,2029 | 0,0489 | 14,75 | 1,30 | 4 332,59 | 20,0 |
| 7 | Saturne | PLANET | 1 | 58 232 | `0xFFE3CB8F` | 1 | 9,5367 | 0,0565 | 92,43 | 2,49 | 10 759,22 | 317,0 |
| 8 | Uranus | PLANET | 1 | 25 362 | `0xFF9FD8E0` | 1 | 19,189 | 0,0457 | 170,96 | 0,77 | 30 688,5 | 142,2 |
| 9 | Neptune | PLANET | 1 | 24 622 | `0xFF3E66D6` | 1 | 30,070 | 0,0113 | 44,97 | 1,77 | 60 182 | 256,2 |

Le Soleil garde le rayon du lot 1 arrondi à la valeur IAU (695 700 au lieu de 696 000).

### Objets mineurs (`MINOR`, parent = Soleil)

Sources : NASA Fact Sheets (Pluton, Cérès), JPL Small-Body Database (<https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html>)
pour Éris, Haumea, Makémaké. `ϖ = Ω + ω`.

| # | Nom | Rayon (km) | Couleur | a (UA) | e | yaw ϖ | pitch i | Période (j) | Phase |
|---|---|---|---|---|---|---|---|---|---|
| 10 | Cérès | 469,7 | `0xFF8C8C88` | 2,767 | 0,0758 | 153,9 | 10,59 | 1 680 | 95 (arb.) |
| 11 | Pluton | 1 188,3 | `0xFFD9B58F` | 39,48 | 0,2488 | 224,07 | 17,16 | 90 560 | 14,5 |
| 12 | Éris | 1 163 | `0xFFE8E8E8` | 67,86 | 0,4407 | 187,6 | 44,04 | 203 830 | 205 (arb.) |
| 13 | Haumea | 780 (moyen, corps très allongé) | `0xFFDADADA` | 43,22 | 0,191 | 0,9 | 28,2 | 103 774 | 218 (arb.) |
| 14 | Makémaké | 715 | `0xFFC98E6B` | 45,43 | 0,159 | 16,6 | 28,98 | 111 845 | 165 (arb.) |

### Lunes (`MOON`)

Sources : NASA Fact Sheets des planètes et JPL « Planetary Satellite Mean Elements »
(<https://ssd.jpl.nasa.gov/sats/elem/>). Obliquités des parents : Terre 23,44 (non utilisée : l'orbite de la Lune est
donnée par rapport à l'écliptique), Mars 25,19, Jupiter 3,13, Saturne 26,73, Uranus 97,77, Neptune 28,32,
Pluton 119,6. `a` en UA = `a` (km) / 149 597 870,7.

| # | Nom | Parent | Rayon (km) | Couleur | a (km) | a (UA) | e | i équateur | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 15 | Lune | Terre (4) | 1 737,4 | `0xFFB4B4B4` | 384 400 | 0,0025696 | 0,0549 | 5,145 (écliptique) | 5,145 | 27,322 | 220 |
| 16 | Phobos | Mars (5) | 11,27 | `0xFF7A6A5A` | 9 376 | 0,00006268 | 0,0151 | 1,08 | 26,27 | 0,3189 | 10 |
| 17 | Déimos | Mars (5) | 6,2 | `0xFF8A7A6A` | 23 463 | 0,00015684 | 0,0003 | 1,79 | 26,98 | 1,2624 | 190 |
| 18 | Io | Jupiter (6) | 1 821,6 | `0xFFE8D85A` | 421 700 | 0,0028189 | 0,0041 | 0,05 | 3,18 | 1,7691 | 30 |
| 19 | Europe | Jupiter (6) | 1 560,8 | `0xFFD9CBB0` | 671 034 | 0,0044856 | 0,009 | 0,47 | 3,60 | 3,5512 | 120 |
| 20 | Ganymède | Jupiter (6) | 2 634,1 | `0xFF9C8F80` | 1 070 412 | 0,0071553 | 0,0013 | 0,20 | 3,33 | 7,1546 | 250 |
| 21 | Callisto | Jupiter (6) | 2 410,3 | `0xFF6E6358` | 1 882 709 | 0,0125851 | 0,0074 | 0,19 | 3,32 | 16,689 | 330 |
| 22 | Encelade | Saturne (7) | 252,1 | `0xFFF4F8FF` | 237 948 | 0,0015906 | 0,0047 | 0,01 | 26,74 | 1,3702 | 60 |
| 23 | Téthys | Saturne (7) | 531,1 | `0xFFDCDCDC` | 294 619 | 0,0019694 | 0,0001 | 1,12 | 27,85 | 1,8878 | 150 |
| 24 | Dioné | Saturne (7) | 561,4 | `0xFFC8C8C8` | 377 396 | 0,0025227 | 0,0022 | 0,03 | 26,76 | 2,7369 | 240 |
| 25 | Rhéa | Saturne (7) | 763,8 | `0xFFBDBDBD` | 527 108 | 0,0035235 | 0,0013 | 0,35 | 27,08 | 4,5182 | 300 |
| 26 | Titan | Saturne (7) | 2 574,7 | `0xFFD9A74A` | 1 221 870 | 0,0081677 | 0,0288 | 0,35 | 27,08 | 15,945 | 20 |
| 27 | Japet | Saturne (7) | 734,5 | `0xFF8F8270` | 3 560 820 | 0,0238026 | 0,0286 | 15,47 | 42,20 | 79,321 | 110 |
| 28 | Miranda | Uranus (8) | 235,8 | `0xFFB0B0B0` | 129 390 | 0,0008649 | 0,0013 | 4,23 | 102,00 | 1,4135 | 200 |
| 29 | Ariel | Uranus (8) | 578,9 | `0xFFBEBEBE` | 191 020 | 0,0012769 | 0,0012 | 0,26 | 98,03 | 2,5204 | 280 |
| 30 | Umbriel | Uranus (8) | 584,7 | `0xFF707070` | 266 300 | 0,0017801 | 0,0039 | 0,13 | 97,90 | 4,1442 | 40 |
| 31 | Titania | Uranus (8) | 788,4 | `0xFFB4A89C` | 435 910 | 0,0029139 | 0,0011 | 0,34 | 98,11 | 8,7059 | 160 |
| 32 | Obéron | Uranus (8) | 761,4 | `0xFF9E9086` | 583 520 | 0,0039006 | 0,0014 | 0,06 | 97,83 | 13,463 | 260 |
| 33 | Triton | Neptune (9) | 1 353,4 | `0xFFE0C8C0` | 354 759 | 0,0023714 | 0,000016 | 156,885 (rétrograde) | −174,80 | 5,8769 | 70 |
| 34 | Charon | Pluton (11) | 606 | `0xFF9A9090` | 19 591 | 0,00013096 | 0,0002 | 0,08 | 119,68 | 6,3872 | 0 |

Charon a pour parent un `MINOR` : c'est la règle de parenté ajoutée par T16 (`MOON` : parent `PLANET` **ou**
`MINOR`).

## Systèmes voisins

Positions (ascension droite h, déclinaison °, distance al) **inchangées** par rapport au lot 1
(`SpaceGalaxy.addNeighbor`). Le rayon et la luminosité de l'étoile principale sont repris ou corrigés comme indiqué.
Les orbites d'étoiles compagnes ont pour parent l'étoile principale (règle `STAR` → `STAR`).

Sources communes : NASA Exoplanet Archive, table `pscomppars` interrogée le 2026-10-09
(<https://exoplanetarchive.ipac.caltech.edu/>, requête `sy_dist < 8` pc) ; pages Wikipédia (anglaises) des étoiles
pour les paramètres stellaires et orbites des binaires (elles citent les publications d'origine).

### Alpha du Centaure (4,37 al) — 5 corps

Sources : <https://en.wikipedia.org/wiki/Alpha_Centauri>, <https://en.wikipedia.org/wiki/Proxima_Centauri>, archive
NASA (Proxima b, d). Proxima d confirmée par NIRPS en 2025.

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Alpha du Centaure A | STAR | — | 851 100 | `0xFFFFF1C8` | 1,52 | — | — | — | — | — |
| 2 | Alpha du Centaure B | STAR | 1 | 600 500 | `0xFFFFD8A0` | 0,50 | 23,3 | 0,5195 | 79,2 | 29 133 (79,76 ans) | 0 |
| 3 | Proxima du Centaure | STAR | 1 | 107 300 | `0xFFFF6A3C` | 0,0016 | 8 700 | 0,50 | 107,6 | 199 790 000 (547 000 ans) | 180 |
| 4 | Proxima b | PLANET | 3 | 6 500 (est., 1,02 R⊕) | `0xFF9C7A5A` | 1 | 0,04848 | 0 | 0 | 11,18465 | 40 |
| 5 | Proxima d | PLANET | 3 | 4 410 (est., 0,692 R⊕) | `0xFF8A7060` | 1 | 0,02881 | 0 | 0 | 5,12338 | 200 |

Note : le corps du lot 1 s'appelait « Alpha du Centaure » ; renommé en « Alpha du Centaure A » (le système garde son
nom).

### Barnard (5,96 al) — 5 corps

Sources : archive NASA (b : 2024, ESPRESSO ; c, d, e : Basant et al. 2025, ApJL) ;
<https://www.astronomy.com/science/three-new-planets-found-around-barnards-star>.

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Barnard | STAR | — | 130 100 (0,187 R☉) | `0xFFFF8A5C` | 0,0035 | — | — | — | — |
| 2 | Barnard d | PLANET | 1 | 4 420 (est., 0,694 R⊕) | `0xFF8C6E5A` | 1 | 0,0188 | 0,04 | 2,3402 | 0 |
| 3 | Barnard b | PLANET | 1 | 4 590 (est., 0,720 R⊕) | `0xFF9A7A62` | 1 | 0,0229 | 0,03 | 3,1542 | 90 |
| 4 | Barnard c | PLANET | 1 | 4 730 (est., 0,743 R⊕) | `0xFF86705E` | 1 | 0,0274 | 0,08 | 4,1244 | 180 |
| 5 | Barnard e | PLANET | 1 | 4 060 (est., 0,637 R⊕) | `0xFF7E6C60` | 1 | 0,0381 | 0,04 | 6,7392 | 270 |

`pitch` = 0 pour les quatre (inclinaison inconnue).

### Wolf 359 (7,86 al) — 1 corps

Aucune planète confirmée (voir « Non retenus »). Étoile : rayon 100 200 km (0,144 R☉, au lieu de 112 000),
luminosité 0,0011, couleur `0xFFFF7448`. Source : <https://en.wikipedia.org/wiki/Wolf_359>.

### Lalande 21185 (8,31 al) — 3 corps

Sources : archive NASA (GJ 411 b, 2019 ; HD 95735 c, 2021) ; <https://en.wikipedia.org/wiki/Lalande_21185>.

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Lalande 21185 | STAR | — | 272 700 (0,392 R☉) | `0xFFFF9C66` | 0,021 | — | — | — | — |
| 2 | Lalande 21185 b | PLANET | 1 | 9 240 (est., 1,45 R⊕) | `0xFFA08A70` | 1 | 0,07879 | 0,063 | 12,9394 | 60 |
| 3 | Lalande 21185 c | PLANET | 1 | 23 950 (est., 3,76 R⊕) | `0xFF7FA6C8` | 1 | 2,94 | 0,132 | 2 946 | 230 |

### Sirius (8,60 al) — 2 corps

Source : <https://en.wikipedia.org/wiki/Sirius>, <https://en.wikipedia.org/wiki/Sirius_B>.

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Sirius A | STAR | — | 1 190 000 (1,711 R☉) | `0xFFCAD8FF` | 25,4 | — | — | — | — | — |
| 2 | Sirius B | STAR | 1 | 5 634 (naine blanche) | `0xFFE8EEFF` | 0,056 | 19,8 | 0,5914 | 136,3 | 18 309 (50,13 ans) | 120 |

### Epsilon Eridani (10,47 al) — 2 corps

Sources : archive NASA (eps Eri b : a, e, i, période ; rayon est.) ;
<https://en.wikipedia.org/wiki/Epsilon_Eridani_b> (orbite révisée 2025).

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Epsilon Eridani | STAR | — | 511 300 (0,735 R☉) | `0xFFFFC27A` | 0,34 | — | — | — | — | — |
| 2 | Epsilon Eridani b | PLANET | 1 | 87 900 (est., 13,8 R⊕) | `0xFFC8A47A` | 1 | 3,53 | 0,06 | 40 | 2 680 | 300 |

### 61 Cygni (11,40 al) — 2 corps

Source : <https://en.wikipedia.org/wiki/61_Cygni> (orbite de la binaire, ajustement le plus récent du tableau).

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 61 Cygni A | STAR | — | 462 600 (0,665 R☉) | `0xFFFFB46A` | 0,153 | — | — | — | — | — |
| 2 | 61 Cygni B | STAR | 1 | 413 900 (0,595 R☉) | `0xFFFFA860` | 0,085 | 86,8 | 0,442 | 53 | 258 260 (707 ans) | 45 |

### Procyon (11,46 al) — 2 corps

Sources : Bond et al. 2015 (ApJ 813, 106 : période, masses), <https://en.wikipedia.org/wiki/Procyon> (a, e, rayon de B).

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | pitch | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Procyon A | STAR | — | 1 424 800 (2,048 R☉) | `0xFFFFF4E8` | 6,93 | — | — | — | — | — |
| 2 | Procyon B | STAR | 1 | 8 585 (naine blanche) | `0xFFF0F0FF` | 0,00049 | 15,14 | 0,3979 | 31,1 | 14 917 (40,84 ans) | 210 |

### Tau Ceti (11,91 al) — 4 corps

Source : archive NASA (`pscomppars` : tau Cet g, h, f ; Feng et al. 2017). Tau Ceti e n'y figure plus (voir « Non
retenus »).

| # | Nom | Type | Parent | Rayon (km) | Couleur | Lum. | a (UA) | e | Période (j) | Phase (arb.) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Tau Ceti | STAR | — | 551 700 (0,793 R☉) | `0xFFFFE9B0` | 0,52 | — | — | — | — |
| 2 | Tau Ceti g | PLANET | 1 | 7 520 (est., 1,18 R⊕) | `0xFFB08A6A` | 1 | 0,133 | 0,06 | 20,00 | 15 |
| 3 | Tau Ceti h | PLANET | 1 | 7 580 (est., 1,19 R⊕) | `0xFF9C8C78` | 1 | 0,243 | 0,23 | 49,41 | 130 |
| 4 | Tau Ceti f | PLANET | 1 | 11 530 (est., 1,81 R⊕) | `0xFF8AA0B8` | 1 | 1,334 | 0,16 | 636,13 | 250 |

### Altaïr (16,73 al) — 1 corps, Véga (25,04 al) — 1 corps

Aucune planète confirmée. Étoiles inchangées (Altaïr 1 260 000 km, lum. 10,6, `0xFFF0F4FF` ; Véga 1 640 000 km,
lum. 40,1, `0xFFD6E2FF`). Noms du lot 1 gardés : « Altair », « Vega ».

## Rotation propre (lot 3, T28)

Recherche faite le **2026-10-10** par le sous-agent de T28 ; valeurs recopiées dans le code : Sol dans
`model/SpaceSolRotations` (table nom → rotation, seule source, utilisée par `SpaceGalaxy.createDefault` **et** par la
lecture d'une galaxie sauvegardée avant T28 : rotation « auto » par nom exact), voisins dans `createDefault` (appels
`spin` / `sync` par nom). Modèle et calcul :
[`03`](03-modele-donnees.md) § 2, [`04`](04-mathematiques.md) § 10, [`decisions.md`](decisions.md) C71.

### Conventions de recopie

- **Période** sidérale en heures ; **signe** = sens autour du pôle nord IAU (`< 0` rétrograde) ; `0` = **synchrone**
  (même face vers le parent). Les fiches NASA indiquent un sens rétrograde à la fois par une période négative **et**
  une obliquité > 90° : le modèle ne garde qu'une des deux marques. Recopie : pôle **nord IAU** (côté nord du plan
  invariable, obliquité ≤ 90°) et rétrograde par le signe, soit `obliquité modèle = 180 − obliquité NASA` pour Vénus,
  Uranus et Pluton.
- **Obliquité** : par rapport au plan de l'orbite (fiches NASA « Obliquity to orbit ») ; Soleil : par rapport à
  l'écliptique. Valeurs ramenées à la précision d'un `float` par le modèle (écart < 1e-5 relatif).
- **Nœud** `Ω` (degrés) : direction de la ligne des nœuds de l'équateur dans le plan de l'orbite du modèle, comptée
  depuis le périapse (`+X` du repère de l'orbite) : `Ω = λ_pôle − 90 − ϖ`, avec `λ_pôle` la longitude écliptique du
  pôle nord (calculée depuis l'ascension droite / déclinaison IAU J2000, `ε = 23,4392911°`) et `ϖ` le `yaw` de l'orbite
  (tableau des planètes). Pour le Soleil (sans parent) : `Ω = λ_pôle − 90`. L'inclinaison de l'orbite et le nœud
  ascendant (non modélisés) sont négligés : la direction du pôle est approchée à quelques degrés près.
- **Phase** : 0 partout (méridien origine non calé : le repère du modèle est simplifié, une phase IAU `W₀` n'y aurait
  pas de sens exact).
- Statut : **réel** (publié, source citée), **estimé** (déduit d'une mesure publiée par un calcul simple),
  **supposé** (choix physiquement plausible sans mesure : rotation synchrone d'une planète très proche de son
  étoile, axe aligné sur l'orbite), **inventé** (aucune donnée : valeur plausible choisie).

### Sol

Sources : NASA Planetary Fact Sheets (<https://nssdc.gsfc.nasa.gov/planetary/factsheet/> et fiches par corps :
`sunfact`, `mercuryfact`, `venusfact`, `marsfact` (Terre comprise), `jupiterfact`, `saturnfact`, `uranusfact`,
`neptunefact`, `plutofact`, `moonfact`, consultées le 2026-10-10) pour périodes, obliquités et pôles ; pôles IAU WGCCRE
(Archinal et al., rapports 2009 / 2015, tels que repris par les fiches NASA et
<https://en.wikipedia.org/wiki/Poles_of_astronomical_bodies>) ; Cérès : IAU 2015 (mission Dawn).

| Corps | Période NASA (h) | Obliquité NASA (°) | Pôle nord IAU α, δ (°) | λ_pôle (°) | **Période modèle (h)** | **Obliquité modèle (°)** | **Nœud Ω (°)** | Statut |
|---|---|---|---|---|---|---|---|---|
| Soleil | 609,12 | 7,25 (écliptique) | 286,13, 63,87 | 345,77 | 609,12 | 7,25 | −104,23 | réel |
| Mercure | 1 407,6 | 0,034 | 281,010, 61,414 | 318,23 | 1 407,6 | 0,034 | 150,77 | réel |
| Vénus | −5 832,6 | 177,36 | 272,76, 67,16 | 30,19 | −5 832,6 | 2,64 | 168,66 | réel (rétrograde) |
| Terre | 23,9345 | 23,44 | 0, 90 | 90,00 | 23,9345 | 23,44 | −102,94 | réel |
| Mars | 24,6229 | 25,19 | 317,681, 52,887 | 352,91 | 24,6229 | 25,19 | −73,13 | réel |
| Jupiter | 9,9250 | 3,13 | 268,057, 64,495 | 247,82 | 9,9250 | 3,13 | 143,07 | réel |
| Saturne | 10,656 | 26,73 | 40,589, 83,537 | 79,53 | 10,656 | 26,73 | −102,90 | réel |
| Uranus | −17,24 | 97,77 | 257,311, −15,175 | 257,65 | −17,24 | 82,23 | −3,31 | réel (rétrograde) |
| Neptune | 16,11 | 28,32 | 299,36, 43,46 | 319,24 | 16,11 | 28,32 | −175,73 | réel |

Objets mineurs :

| Corps | Période (h) | Obliquité modèle (°) | Nœud Ω (°) | Statut, source |
|---|---|---|---|---|
| Cérès | 9,07417 | 4,0 | 127,31 | période réelle (IAU 2015, Dawn) ; obliquité ≈ 4° (valeur usuelle de Dawn, **approchée**) ; pôle 291,418, 66,764 |
| Pluton | −153,2928 | 60,49 (= 180 − 119,51) | 3,28 | réel (fiche NASA : pôle positif 132,99, −6,16 ; pôle nord = antipode) |
| Éris | 378,86 (15,7859 j) | 0 | 0 | période réelle : rotation synchrone avec Dysnomia (Szakáts et al. 2023 ; Bernstein et al. 2023, PSJ : 15,771 ± 0,008 j, orbite de Dysnomia 15,78590 j) ; Dysnomia n'est pas modélisée, d'où une période explicite et non `0` ; pôle **inconnu** (0) |
| Haumea | 3,9155 | 0 | 0 | période réelle (3,9154 ± 0,0001 h, A&A 2013) ; pôle **inconnu** (0) |
| Makémaké | 22,8266 | 0 | 0 | période réelle (Hromakina et al. 2019, A&A 625, A46 : double pic 22,8266 h) ; pôle **inconnu** (0) |

Lunes : toutes **synchrones** (période `0`, phase 0 = méridien origine tourné vers la planète, comme la convention IAU
des satellites synchrones), axe sur la normale de leur orbite (obliquité 0, **approchée** : les obliquités réelles,
états de Cassini, sont de l'ordre du dixième de degré), sauf la **Lune** : obliquité 6,68° sur son orbite (fiche NASA)
et nœud 180° (pôle penché vers le pôle de l'écliptique : état de Cassini, l'équateur lunaire à 1,54° de l'écliptique).
Triton (orbite rétrograde) et Charon sont synchrones comme les autres (fiche NASA de Pluton : période de Charon =
période orbitale, 6,3872 j).

### Systèmes voisins

Sources (consultées le 2026-10-10) : pages Wikipédia anglaises des étoiles (infobox, qui citent les publications) ;
Proxima : Collins et al. 2017 (82,6 ± 0,1 j, profils Hα ; <https://uhra.herts.ac.uk/id/eprint/5659/>) ; Barnard :
Toledo-Padrón et al. 2019 (145 ± 15 j ; <https://arxiv.org/abs/1812.06712>). Pôles des étoiles et des planètes : aucune
donnée exploitable dans le repère du modèle (on ne connaît au mieux que l'inclinaison sur la ligne de visée) :
obliquité 0, nœud 0 (**supposés** : axe sur le haut du système, ou sur la normale de l'orbite pour une compagne).

| Corps | Période modèle (h) | Valeur publiée | Statut |
|---|---|---|---|
| Alpha du Centaure A | 679,2 | 28,3 ± 0,5 j | réel |
| Alpha du Centaure B | 880,8 | 36,7 ± 0,3 j | réel |
| Proxima du Centaure | 1 982,4 | 82,6 ± 0,1 j | réel |
| Proxima b, Proxima d | 0 (synchrone) | — | **supposé** (0,049 et 0,029 UA d'une naine rouge) |
| Barnard | 3 480 | 145 ± 15 j | réel |
| Barnard b, c, d, e | 0 (synchrone) | — | **supposé** (0,019 à 0,038 UA) |
| Wolf 359 | 64,896 | 2,704 ± 0,003 j | réel |
| Lalande 21185 | 1 279,92 | 53,33 ± 0,19 j (Kemmer et al. 2025) | réel |
| Lalande 21185 b | 0 (synchrone) | — | **supposé** (0,079 UA) |
| Lalande 21185 c | 16 | — | **inventé** (mini-Neptune à 2,94 UA : période de type Neptune) |
| Sirius A | 127,4 | v sin i = 16,3 km/s | **estimé** : `2π R / v` avec R = 1 190 000 km, axe supposé perpendiculaire à la visée |
| Sirius B | 24 | — | **inventé** (naine blanche ; rotation non mesurée) |
| Epsilon Eridani | 278,4 | 11,60 ± 0,16 j | réel |
| Epsilon Eridani b | 10 | — | **inventé** (géante gazeuse à 3,5 UA : période de type Jupiter) |
| 61 Cygni A | 852,96 | 35,54 ± 0,47 j | réel |
| 61 Cygni B | 829,2 | 34,55 ± 0,57 j | réel (le texte de la page donne ≈ 38 j en moyenne ; valeur de l'infobox gardée) |
| Procyon A | 552 | 23 j | réel (approché) |
| Procyon B | 24 | — | **inventé** (naine blanche) |
| Tau Ceti | 1 104 | 46 ± 4 j (étude 2023) | réel |
| Tau Ceti g, Tau Ceti h | 0 (synchrone) | — | **supposé** (0,133 et 0,243 UA ; h est la moins sûre) |
| Tau Ceti f | 24 | — | **inventé** (1,33 UA, non verrouillée) |
| Altaïr | 7,77 | 7,77 h (vitesse équatoriale ≈ 286 km/s) | réel |
| Véga | 16,3 | 16,3 h (≈ 236 km/s à l'équateur) | réel |

Rotation par défaut d'un **type** (corps ajouté au Configurator, ou corps d'une galaxie sauvegardée avant T28 dont le
nom n'est pas celui d'un corps de Sol) : étoile 609,12 h
(Soleil), planète 24 h, lune synchrone, objet mineur 9,07417 h (Cérès) ; obliquité, nœud et phase 0.

## Textures (lot 3, T29)

Recherche, téléchargement et conversion faits le **2026-10-10** par le sous-agent de T29. Fichiers :
`src/main/resources/assets/hololib/textures/space/sol/` (33 corps de Sol) et `generic/` (étoile + 19 génériques),
**53 PNG, 12,5 Mo** ; crédits complets (source, auteur, licence, modifications de chaque fichier) dans
`assets/hololib/textures/space/CREDITS.md`. Code : `SpaceBodyTextures` (package `modules/dimension/space`), seule
source de la liste des ids, de la table nom → id et de la règle « auto » ([`decisions.md`](decisions.md) C69, C70).

### Conventions

- Équirectangulaires 2:1, PNG RGB 8 bits sans alpha ; 1024 × 512 (étoile, planètes), 512 × 256 (lunes, objets
  mineurs) — C70. Anneaux, nuages, face de nuit : absents (L21).
- **Nord en haut, est à droite, longitude 0 au centre de l'image** : `u = 0,5 + longitude_est / 360`, `v = 0,5 −
  latitude / 180`. C'est la longitude 0 sur `+X` de [`04`](04-mathematiques.md) (rotation propre) : la face
  `u = 0,5` d'un corps synchrone regarde son parent (Lune : face visible vers la Terre). Les cartes sources centrées
  sur 180° (USGS) ont été décalées d'une demi-largeur.
- **Étoiles** : une seule image, `generic/star.png` (luminance de la photosphère du Soleil, étirée), **teintée** par
  la couleur du corps au rendu (`Builtin.tinted()`).
- **Zones jamais observées** (Pluton, Charon, Triton, lunes d'Uranus, pôles de Callisto, Europe, Ganymède) : noires
  dans les sources, remplies par un reflet très flouté de la zone observée voisine ramené vers la teinte moyenne
  (pas de relief inventé). Part remplie : lunes d'Uranus 57 à 63 %, Triton 40 %, Charon 36 %, Pluton 34 %, autres ≤ 5 %.

### Règle « auto » (`SpaceBodyTextures.auto`)

1. **Nom** : un corps dont le nom est exactement celui d'un corps de Sol (sans accents) **et** du même type reçoit la
   texture de ce corps, quel que soit son système (une étoile reçoit toujours la texture d'étoile).
2. Sinon **catégorie générique** :
   - `STAR` → étoile teintée ;
   - `MOON`, `MINOR` → **lune glacée** si `T_eq < 170 K` (ligne des glaces), sinon **lune rocheuse** ;
   - `PLANET`, selon le rayon `R` (en `R⊕ = 6 371 km`) : **rocheuse** si `R ≤ 1,6 R⊕` (au-delà, les planètes ont
     presque toutes une enveloppe de gaz : vallée de rayon, Fulton et al. 2017 ; Rogers 2015) — **brûlante** si
     `T_eq ≥ 360 K`, **glacée** si `T_eq < 170 K`, **tempérée** entre les deux ; **géante de glace** (ou
     mini-Neptune) si `1,6 < R < 6 R⊕` ; **géante gazeuse** au-delà — **chaude** si `T_eq ≥ 700 K` (classes IV–V de
     Sudarsky : sans nuages ou nuages de silicates, sombre), **tempérée** si `T_eq ≥ 170 K` (classe II : nuages d'eau,
     claire), **froide** sinon (classe I : ammoniac, bandes de type Jupiter / Saturne).
3. **Variante** : tirée d'un mélange entier des ids du système et du corps (déterministe, mêmes textures chez tous
   les clients ; deux corps voisins n'ont pas forcément la même).

**Température d'équilibre** : `T_eq = 278,6 K · L^(1/4) / √a` (albédo nul, redistribution complète ; Terre 279 K),
`L` = luminosité (Soleil = 1) de l'étoile la plus proche dans la parenté du corps, `a` (UA) = demi-grand axe du corps
de la chaîne qui orbite directement cette étoile (la planète pour une lune). Les autres étoiles d'un système multiple
sont ignorées. Les seuils sont des choix de rendu, pas des frontières physiques nettes.

Résolution d'une référence ([`03`](03-modele-donnees.md) § 2) : `builtin:<id>` connue → ce fichier ; `builtin`
inconnue ou `auto` → règle « auto » ; `custom:<sha1>` → image importée (T45) si le client l'a, sinon repli sur la
règle « auto » (`SpaceBodyTextures.builtinFor` rend toujours une texture intégrée, `customHash` l'empreinte).

### Sol

| Corps | Fichier | Source | Licence | Remarque |
|---|---|---|---|---|
| Soleil | `generic/star.png` | Solar System Scope, `2k_sun` | CC BY 4.0 | teintée par `0xFFFFE07A` |
| Mercure | `sol/mercure.png` | Solar System Scope | CC BY 4.0 | |
| Vénus | `sol/venus.png` | Solar System Scope, atmosphère | CC BY 4.0 | vue de l'espace (nuages) |
| Terre | `sol/terre.png` | Solar System Scope, jour | CC BY 4.0 | sans nuages (L21) |
| Mars | `sol/mars.png` | Solar System Scope | CC BY 4.0 | |
| Jupiter | `sol/jupiter.png` | Solar System Scope | CC BY 4.0 | |
| Saturne | `sol/saturne.png` | Solar System Scope | CC BY 4.0 | sans anneaux (L21) |
| Uranus | `sol/uranus.png` | Solar System Scope | CC BY 4.0 | presque uniforme (réel) |
| Neptune | `sol/neptune.png` | Solar System Scope | CC BY 4.0 | |
| Cérès | `sol/ceres.png` | Solar System Scope | CC BY 4.0 | **fictive** (carte du site) |
| Pluton | `sol/pluton.png` | USGS / New Horizons | domaine public (NASA) | sud remplissage ; gris colorés brun → beige |
| Éris | `sol/eris.png` | Solar System Scope | CC BY 4.0 | **fictive** |
| Haumea | `sol/haumea.png` | Solar System Scope | CC BY 4.0 | **fictive** ; sphère (corps allongé non modélisé) |
| Makémaké | `sol/makemake.png` | Solar System Scope | CC BY 4.0 | **fictive** |
| Lune | `sol/lune.png` | Solar System Scope | CC BY 4.0 | |
| Phobos | `sol/phobos.png` | USGS / Viking (cartes de P. Stooke) | domaine public | |
| Déimos | `sol/deimos.png` | JPL Solar System Simulator / Viking | politique d'usage JPL | |
| Io | `sol/io.png` | USGS / Galileo, couleur | domaine public | |
| Europe | `sol/europe.png` | USGS / Voyager + Galileo | domaine public | niveaux de gris |
| Ganymède | `sol/ganymede.png` | USGS / Voyager + Galileo, couleur | domaine public | |
| Callisto | `sol/callisto.png` | USGS / Voyager + Galileo | domaine public | niveaux de gris |
| Encelade | `sol/encelade.png` | USGS / Cassini | domaine public | niveaux de gris |
| Téthys | `sol/tethys.png` | USGS / Cassini | domaine public | niveaux de gris |
| Dioné | `sol/dione.png` | USGS / Cassini + Voyager | domaine public | niveaux de gris |
| Rhéa | `sol/rhea.png` | USGS / Cassini + Voyager | domaine public | niveaux de gris |
| Titan | `sol/titan.png` | USGS / Cassini ISS (PIA19658) | domaine public (NASA) | **brume** orangée modulée à ±10 % par la surface |
| Japet | `sol/japet.png` | USGS / Cassini + Voyager | domaine public | deux faces (claire / sombre) |
| Miranda, Ariel, Umbriel, Titania, Obéron | `sol/<nom>.png` | JPL Solar System Simulator / Voyager 2 | politique d'usage JPL | hémisphère nord jamais vu : remplissage |
| Triton | `sol/triton.png` | USGS / Voyager 2, couleur | domaine public | nord remplissage |
| Charon | `sol/charon.png` | USGS / New Horizons | domaine public (NASA) | sud remplissage |

### Systèmes voisins (règle générique)

`T_eq` calculée par `SpaceBodyTextures.equilibriumTemperature` sur la galaxie par défaut (banc T29) ; variante tirée
des ids.

| Corps | Type | Rayon (R⊕) | `T_eq` (K) | Catégorie | Fichier | Justification |
|---|---|---|---|---|---|---|
| Toutes les étoiles (17) | STAR | — | — | étoile | `generic/star.png` | teintée par la couleur du corps (naines blanches comprises) |
| Proxima b | PLANET | 1,02 | 253 | rocheuse tempérée | `rocky_temperate_3` | taille terrestre en zone habitable de Proxima |
| Proxima d | PLANET | 0,69 | 328 | rocheuse tempérée | `rocky_temperate_1` | sous la masse de la Terre, côté chaud de la zone tempérée (comme Vénus, 328 K) |
| Barnard d | PLANET | 0,69 | 494 | rocheuse brûlante | `rocky_hot_2` | 0,019 UA d'une naine rouge |
| Barnard b | PLANET | 0,72 | 448 | rocheuse brûlante | `rocky_hot_1` | comme Mercure (448 K) |
| Barnard c | PLANET | 0,74 | 409 | rocheuse brûlante | `rocky_hot_2` | |
| Barnard e | PLANET | 0,64 | 347 | rocheuse tempérée | `rocky_temperate_2` | juste sous le seuil de 360 K |
| Lalande 21185 b | PLANET | 1,45 | 378 | rocheuse brûlante | `rocky_hot_1` | super-Terre rocheuse (≤ 1,6 R⊕) à 0,079 UA |
| Lalande 21185 c | PLANET | 3,76 | 62 | géante de glace | `ice_giant_2` | mini-Neptune froide (2,94 UA) |
| Epsilon Eridani b | PLANET | 13,8 | 113 | géante gazeuse froide | `gas_cold_1` | géante de type Jupiter (122 K) à 3,5 UA |
| Tau Ceti g | PLANET | 1,18 | 649 | rocheuse brûlante | `rocky_hot_1` | 0,133 UA d'une étoile de 0,52 L☉ |
| Tau Ceti h | PLANET | 1,19 | 480 | rocheuse brûlante | `rocky_hot_1` | |
| Tau Ceti f | PLANET | 1,81 | 205 | géante de glace | `ice_giant_1` | au-dessus de 1,6 R⊕ : probablement une enveloppe de gaz (mini-Neptune) |

Rayons des exoplanètes : estimés (annexe, « est. ») ; la catégorie hérite de cette incertitude.

## Masses (lot 3, T39)

Masses en kg, recopiées dans la table `model/SpaceKnownMasses` (**seule source** : `SpaceGalaxy.createDefault` et
lecture d'un corps sauvegardé avant T39, par nom exact). Recherche faite le **2026-10-11** par le sous-agent de T39.
Elles ne servent qu'au choix du référentiel (sphères d'influence, D29) : ni gravité, ni effet sur les orbites.

### Conventions

- `M☉ = 1,9884·10³⁰ kg` (NASA Sun Fact Sheet), `M⊕ = 5,9722·10²⁴ kg` (NASA Earth Fact Sheet) ; étoiles voisines
  recopiées en masses solaires, exoplanètes en masses terrestres, converties par le code (`sun(…)`, `earth(…)`).
- Lunes de Sol : `m = GM / G`, `G = 6,6743·10⁻¹¹ m³ kg⁻¹ s⁻²`, `GM` de JPL SSD, *Planetary Satellite Physical
  Parameters* (<https://ssd.jpl.nasa.gov/sats/phys_par/>), arrondies à 5 chiffres.
- **Exoplanètes : masse minimale `M sin i`** (vitesses radiales, inclinaison inconnue), valeurs « par défaut » de
  l'archive NASA (table `pscomppars` / `ps`, interrogée le 2026-10-11, <https://exoplanetarchive.ipac.caltech.edu/>),
  **sauf Epsilon Eridani b** : masse vraie (Thompson et al. 2025, orbite inclinée de 40° : c'est le `pitch` de
  l'annexe).
- Étoiles : masses des pages Wikipédia (anglaises) des étoiles, qui citent les publications d'origine (Alpha du
  Centaure A / B : Akeson et al. 2021 ; Procyon : Bond et al. 2015 ; 61 Cygni : Kervella et al. 2008) ; Barnard,
  Proxima, Lalande 21185, Epsilon Eridani et Tau Ceti : `st_mass` de l'archive NASA.

### Sol

Sources : NASA Planetary Fact Sheets (<https://nssdc.gsfc.nasa.gov/planetary/factsheet/>, Soleil 1 988 400·10²⁴ kg,
Jupiter 1 898,13·10²⁴ kg…) ; JPL SSD pour les lunes et Cérès (`GM` 62,6284 km³/s², Dawn) ; Éris : Holler et al. 2021 ;
Haumea : Ragozzine & Brown 2009 ; Makémaké : estimation par sa lune MK2 (≈ 3,1·10²¹ kg, incertaine).

| Corps | Masse (kg) | | Corps | Masse (kg) | | Corps | Masse (kg) |
|---|---|---|---|---|---|---|---|
| Soleil | 1,9884e30 | | Pluton | 1,303e22 | | Encelade | 1,0803e20 |
| Mercure | 3,3010e23 | | Éris | 1,6466e22 | | Téthys | 6,1750e20 |
| Vénus | 4,8673e24 | | Haumea | 4,006e21 | | Dioné | 1,0955e21 |
| Terre | 5,9722e24 | | Makémaké | 3,1e21 | | Rhéa | 2,3065e21 |
| Mars | 6,4169e23 | | Lune | 7,3458e22 | | Titan | 1,3452e23 |
| Jupiter | 1,89813e27 | | Phobos | 1,0618e16 | | Japet | 1,8057e21 |
| Saturne | 5,6832e26 | | Déimos | 1,4413e15 | | Miranda | 6,4426e19 |
| Uranus | 8,6811e25 | | Io | 8,9296e22 | | Ariel | 1,2511e21 |
| Neptune | 1,02409e26 | | Europe | 4,7986e22 | | Umbriel | 1,2750e21 |
| Cérès | 9,3835e20 | | Ganymède | 1,4815e23 | | Titania | 3,3996e21 |
| | | | Callisto | 1,0757e23 | | Obéron | 3,0760e21 |
| | | | | | | Triton | 2,1403e22 |
| | | | | | | Charon | 1,5897e21 |

### Systèmes voisins

| Corps | Masse | kg | Source |
|---|---|---|---|
| Alpha du Centaure A | 1,0788 M☉ | 2,1451e30 | Akeson et al. 2021 |
| Alpha du Centaure B | 0,9092 M☉ | 1,8079e30 | Akeson et al. 2021 |
| Proxima du Centaure | 0,1221 M☉ | 2,4278e29 | archive NASA |
| Proxima b | 1,055 M⊕ (`M sin i`) | 6,3007e24 | Suárez Mascareño et al. 2025 (archive) |
| Proxima d | 0,26 M⊕ (`M sin i`) | 1,5528e24 | Suárez Mascareño et al. 2025 (archive) |
| Barnard | 0,162 M☉ | 3,2212e29 | archive NASA |
| Barnard d | 0,263 M⊕ (`M sin i`) | 1,5707e24 | Basant et al. 2025 |
| Barnard b | 0,299 M⊕ (`M sin i`) | 1,7857e24 | Basant et al. 2025 (archive) |
| Barnard c | 0,335 M⊕ (`M sin i`) | 2,0007e24 | Basant et al. 2025 |
| Barnard e | 0,193 M⊕ (`M sin i`) | 1,1526e24 | Basant et al. 2025 |
| Wolf 359 | 0,110 M☉ | 2,1872e29 | Wikipédia |
| Lalande 21185 | 0,3899 M☉ | 7,7528e29 | archive NASA |
| Lalande 21185 b | 2,69 M⊕ (`M sin i`) | 1,6065e25 | Hurt et al. 2022 (archive, GJ 411 b) |
| Lalande 21185 c | 13,6 M⊕ (`M sin i`) | 8,1222e25 | Hurt et al. 2022 (archive, HD 95735 c) |
| Sirius A | 2,063 M☉ | 4,1021e30 | Wikipédia |
| Sirius B | 1,018 M☉ | 2,0242e30 | Wikipédia |
| Epsilon Eridani | 0,82 M☉ | 1,6305e30 | archive NASA |
| Epsilon Eridani b | 1,00 M_J (masse **vraie**) | 1,8981e27 | Thompson et al. 2025 (archive) |
| 61 Cygni A | 0,70 M☉ | 1,3919e30 | Kervella et al. 2008 |
| 61 Cygni B | 0,63 M☉ | 1,2527e30 | Kervella et al. 2008 |
| Procyon A | 1,478 M☉ | 2,9389e30 | Bond et al. 2015 |
| Procyon B | 0,592 M☉ | 1,1771e30 | Bond et al. 2015 |
| Tau Ceti | 0,783 M☉ | 1,5569e30 | archive NASA |
| Tau Ceti g | 1,75 M⊕ (`M sin i`) | 1,0451e25 | Feng et al. 2017 |
| Tau Ceti h | 1,83 M⊕ (`M sin i`) | 1,0929e25 | Feng et al. 2017 |
| Tau Ceti f | 3,93 M⊕ (`M sin i`) | 2,3471e25 | Feng et al. 2017 |
| Altaïr | 1,86 M☉ | 3,6984e30 | Wikipédia |
| Véga | 2,135 M☉ | 4,2452e30 | Wikipédia |

Les rayons des exoplanètes sans transit restent ceux de l'annexe (estimés depuis la masse minimale) ; ils n'ont pas été
recalculés depuis ces masses.

### Sphères d'influence obtenues (banc T39)

Rayon de **base** `r = a (m / m_parent)^0,4`, au moins **10 rayons** (plancher porté de 2 à 10 après T39), puis rayon
de **jeu** (seul utilisé pour le référentiel) : × 4 pour une lune, × 1,5 pour une planète ou un objet mineur, plafonné
à `0,5 a` ; une étoile compagne garde son rayon de base. Base → jeu :

| Corps | Base (km) | Jeu (km) | | Corps | Base (km) | Jeu (km) |
|---|---|---|---|---|---|---|
| Terre | 924 650 | 1 386 975 | | Lune | 66 184 | 192 203 (plafond) |
| Mars | 577 240 | 865 850 | | Phobos | 112,7 | 450,8 |
| Vénus | 616 250 | 924 380 | | Déimos | 62 | 248 |
| Mercure | 112 410 | 168 620 | | Io | 18 216 | 72 864 |
| Jupiter | 48,21 M | 72,31 M | | Europe | 15 608 | 62 432 |
| Saturne | 54,55 M | 81,82 M | | Ganymède | 26 341 | 105 364 |
| Uranus | 51,76 M | 77,64 M | | Callisto | 37 681 | 150 725 |
| Neptune | 86,66 M | 129,98 M | | Rhéa | 7 638 | 30 552 |
| Cérès | 77 000 | 115 500 | | Titan | 43 321 | 173 285 |
| Pluton | 3,147 M | 4,720 M | | Japet | 22 510 | 90 039 |
| | | | | Triton | 13 534 | 54 136 |
| | | | | Charon | 8 445 | 9 796 (plafond) |

Au plancher de 10 rayons (base) : Phobos, Déimos, Io, Europe, Ganymède, Rhéa, Encelade, Téthys, Dioné, Miranda, Ariel,
Umbriel, Titania, Triton. Voisins (jeu) : étoiles compagnes au rayon de base (Alpha du Centaure B 21,8 UA,
Proxima 3 640 UA, Sirius B 14,9 UA, Procyon B 10,5 UA, 61 Cygni B 83,2 UA) ; Proxima b 159 300 km, Proxima d 66 150 km,
Barnard b, c, d, e 60 900 à 70 950 km, Epsilon Eridani b 53,1 M km,
Lalande 21185 c 16,9 M km, Tau Ceti f 3,53 M km.

## Non retenus (motif)

| Objet | Statut au 2026-10-09 |
|---|---|
| Proxima c | candidate (2020) ; signal non retrouvé par NIRPS (2025) |
| Alpha du Centaure A « S1 » | candidate imagée par JWST (2024), non revue en 2025, non confirmée |
| Barnard « f » (≈ 2,78 j) | proposée en 2025 (résultat préliminaire) ; l'archive garde b, c, d, e |
| Wolf 359 b | candidate (≈ 2 900 j), ni confirmée ni réfutée |
| Wolf 359 c | réfutée (Lafarga et al. 2021 : rotation de l'étoile) |
| Lalande 21185 candidate à 0,514 UA | candidate (2021) |
| Tau Ceti e | absente de l'archive composite NASA ; b, c, d réfutées |
| 61 Cygni b | candidate astrométrique (Kervella et al. 2022), étoile hôte inconnue |
| Véga b | candidate (Hurt et al. 2021), non confirmée |
| Altaïr, Sirius, Procyon | aucune planète connue |
| Ceintures (astéroïdes, Kuiper, disques de débris d'Epsilon Eridani, Véga...) | hors modèle (pas de corps « ceinture ») |

## Décompte

| Système | Corps | | Système | Corps |
|---|---|---|---|---|
| Sol | 34 | | Epsilon Eridani | 2 |
| Alpha du Centaure | 5 | | 61 Cygni | 2 |
| Barnard | 5 | | Procyon | 2 |
| Wolf 359 | 1 | | Tau Ceti | 4 |
| Lalande 21185 | 3 | | Altaïr | 1 |
| Sirius | 2 | | Véga | 1 |

Total : 62 corps, 12 systèmes. Toutes les valeurs restent dans les nouvelles bornes.
