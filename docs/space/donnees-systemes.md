# Annexe — données réelles des systèmes par défaut

Source des valeurs de la galaxie par défaut (`SpaceGalaxy.createDefault`, tâche [`T16`](taches/T16-systemes-reels.md)).
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
