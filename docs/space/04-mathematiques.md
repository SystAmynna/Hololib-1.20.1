# 04 — Mathématiques

Package `modules/dimension/space/math/` (T02) pour les §§ 1 à 6 ; le § 7 est implémenté par le Configurator (T11,
T12) ; le § 10 (rotation propre, T28) par `math/SpaceSpin` ; le § 11 (vitesse de croisière, T34) par
`math/SpaceCruise`. Calculs en `double` (JOML `Vector3d`, `Quaterniond`, `Matrix3d` — JOML est fourni par Minecraft), conversion en
`float` seulement au moment de dessiner. Aucune allocation dans les fonctions appelées par frame : paramètres
`dest` réutilisés, comme `HoloEditMath`.

Conventions Minecraft rappelées : `+X` est, `+Y` haut, `+Z` sud ; yaw 0 = sud, positif vers la droite (sens horaire
vu du dessus) ; pitch positif vers le bas. Direction d'un regard `(yaw, pitch)` :
`(-sin yaw · cos pitch, -sin pitch, cos yaw · cos pitch)` (même formule que `HoloTransform.setLookingAtOrigin`).

## 1. Repères

| Repère | Unité | Définition |
|---|---|---|
| **Monde** `W` | bloc | axes de Minecraft ; c'est le repère du `PoseStack` reçu par un `SkyRenderer` (centré sur l'œil, sans translation) |
| **Système** `S` | km | centré sur le corps sans parent du système courant ; axes **parallèles à ceux de la galaxie** |
| **Galaxie** `G` | al | origine arbitraire ; mêmes axes que `S` |
| **Orbite** `O` | UA | plan `XZ`, foyer (le parent) à l'origine, périapse sur `+X` |
| **Cube** `C` | sans unité | repère local de `SpaceMapCube`, cube `[-1, 1]³` |

## 2. La pose du monde : « le monde est la caméra »

La **pose** (`SpacePose`) est la position `s` (km, repère `S`) et l'orientation `Q` (quaternion unitaire) du monde
dans le système. `Q` envoie une direction exprimée dans les axes du monde vers les axes du système :
`v_S = Q · v_W`.

Comme en rendu 3D (`vue = caméra⁻¹`), la **matrice du système vue depuis le monde** est l'inverse de la pose :

```
M_SW = Q⁻¹ · T(-s)          p_W = Q⁻¹ · (p_S − s)
```

C'est la « matrice propre » du système demandée : le système entier est exprimé dans `S`, et la seule chose qui
change quand on pilote est la pose `(s, Q)`, donc `M_SW`. Déplacer ou tourner l'origine déplace ou tourne tout le ciel
d'un coup, sans toucher aux données du système. `SpaceSkyFrame` (T02) encapsule ce calcul : il est construit une fois
par frame à partir de la pose interpolée, puis transforme chaque point du système en **direction** (`Vector3f`
unitaire, axes du monde) et **distance** (km).

Direction d'un autre système (étoile de fond) : `w = normalize(Q⁻¹ · (g_i − g_courant))`, la position du monde dans
son système étant négligeable à l'échelle galactique.

L'**axe avant** du monde `F_W` est un réglage de l'Environment (défaut : nord, `(0, 0, −1)`, yaw 180°, pitch 0).
L'avant du monde dans le système est `Q · F_W`. Axe haut `U_W = (0, 1, 0)`, axe droit `R_W = F_W × U_W` (est pour un
avant au nord).

## 3. Orbites (Kepler)

Temps simulé en jours (T38, D28) : `t = t_e + (gameTime − T_e + partialTick) × échelle / 86 400`, avec `échelle` =
secondes simulées par tick (réglage de l'Environment, défaut **360** depuis T38 : un jour simulé = 240 ticks = 12 s, un
mois lunaire ≈ 5,5 min, une année terrestre ≈ 73 min ; 3 600 avant T38) et **époque** `(T_e, t_e)` = tick de jeu et
temps simulé où l'échelle a été posée (`SpaceMotionSettings.epochTick` / `epochDays`, réglages synchronisés et
sauvegardés). Seul calcul : `SpaceMotionSettings.days(gameTime, partialTick)` (via `SpaceEnvironmentModule.days` dans
le module : serveur, rejeu client, prédiction, affichage ; le Configurator lit `days()` pour `t_edit` et
`elapsedDays` pour son animation). `gameTime` est `level.getGameTime()` des deux côtés (synchronisé par vanilla) : le
calcul est **déterministe**, rien n'est envoyé pour animer les orbites ; `gameTime − T_e` est calculé en `long`.

**Changement d'échelle** (`setTimeScale`, ou tout `setSettings` qui change l'échelle) : l'époque est reposée au tick de
l'état `T` (`t_e = t_ancienne(T)`, `T_e = T`) : `t` est continu (égal bit à bit en `T`), seule sa pente change ; aucun
astre ne saute, le vaisseau reste à sa place par rapport à son référentiel. Une échelle nulle fige les orbites au
temps courant (avant T38 : au temps 0). L'époque n'est jamais réglée à la main. Époque `(0, 0)` = formule d'avant T38,
bit à bit. Migration d'un monde ancien : [`03`](03-modele-donnees.md) § 4 ; côté client : [`05`](05-synchronisation.md)
§ 2.

Pour un corps d'orbite `(a, e, orientation R_o, période P, phase φ₀)` :

```
M = 2π · frac(t / P + φ₀ / 360)              anomalie moyenne dans [0, 2π[ (frac evite la perte de precision)
E − e·sin E = M                               Newton, depart de Danby E = M + 0,85·e·signe(sin M), 6 iterations max
x_O = a (cos E − e)      z_O = a √(1 − e²) sin E      y_O = 0
p_local = R_o · (x_O, 0, z_O) × UA_KM         (R_o = Quaterniond.rotationYXZ(-yaw, pitch, roll), radians)
p = p_parent + p_local
```

`R_o` suit la même convention que `HoloEditMath.rotation` (qui, elle, prend un `HoloTransform` et rend un
`Quaternionf` : la recopier en `double`, ne pas l'appeler ici).

Les corps sont ordonnés parent avant enfant (garanti par le modèle) : une passe suffit. `SpaceOrbits.positions(system,
t, dest[])` remplit un tableau réutilisé.

## 4. Projection dans le ciel (T05)

Pour chaque corps `i`, avec la pose interpolée :

```
rel = p_i − s ;  d = |rel| ;  w = Q⁻¹ · rel / d          (direction, axes du monde)
α = asin(min(1, R_i / d))                                 rayon angulaire apparent, jusqu'a 90° (D9 : plus de plafond a 80°)
```

- **Billboard** : chaque corps est une **calotte** de la sphère de rayon `r_i`, centrée sur `w`, de demi-angle `α`
  (un disque plat de grand `α` dépasserait le fond et le plan lointain ; T05). Les calottes sont placées à des rayons
  de dessin **distincts et ordonnés** : tri par `d` décroissant, `r_i` réparti **géométriquement** de 105 (le plus
  lointain) à 50 (le plus proche) blocs. Un disque plus proche de l'œil passe devant : l'ordre d'occultation
  est exact quelle que soit la taille, avec la profondeur normale de la passe du ciel.
- **Phases** : le disque est un maillage polaire à **4 niveaux de détail** précalculés (24×6, 48×12, 96×24,
  192×48 ; anneaux `ρ_k = sin(kπ/2N)`), choisi selon la taille à l'écran `tan(min(α, 89°)) · m11` (projection
  courante : FOV et longue-vue comptent), seuils de montée 0,1 / 0,4 / 1,4, descente sous 0,8 × le seuil (niveau
  mémorisé par corps) — T07f. Chaque sommet reçoit la **normale exacte en perspective** de la sphère : avec
  `s = sin α` et β l'angle du rayon au centre, le rayon touche la sphère à `t = cos β − √(s² − sin²β)` ; normale
  `t·sin β / s` en radial et `(1 − t·cos β) / s` vers l'œil (la formule orthographique
  `u·B_u + v·B_v + √(1−u²−v²)·(−w)` assombrissait à tort l'horizon d'une planète proche ; loin, les deux coïncident) ; éclairage `0,06 + max(0, n·l)` avec `l` = direction corps → étoile (repère monde). Les
  étoiles ne sont pas éclairées : couleur pleine + halo translucide en couronne autour du disque (jusqu'à 2,5 α, plafonné à 100°, alpha décroissant).
- **Textures et rotation propre** (T30, D21) : le disque garde son maillage polaire tourné vers l'œil (résolution là
  où l'on regarde, de près comme de loin : un maillage UV de la sphère n'aurait que quelques mailles sur la calotte
  visible au plancher d'altitude). Chaque sommet reçoit les coordonnées de texture du point de la sphère qu'il vise —
  la même normale exacte `n_W` que l'éclairage — ramené dans les axes du corps au temps d'affichage :
  `n_B = q_B⁻¹ · Q · n_W` (`q_B` : § 10, `Q` : pose, § 2), puis `λ = atan2(−n_B.z, n_B.x)`,
  `φ = atan2(n_B.y, √(n_B.x² + n_B.z²))`, `u = 0,5 + λ / 2π`, `v = 0,5 − φ / π` (C85 : longitude 0 au centre, nord
  en haut). En pratique, la base du disque `(b_u, b_v, w)` est tournée une fois par corps et par frame, et chaque
  sommet combine ses trois images. **Couture** : dans un quad dont les `u` s'étalent sur plus d'un demi-tour, les
  `u < 0,5` reçoivent `+1` (texture répétée en `u`, bord figé en `v`) ; un quad qui contient un pôle (au plus 15° au
  niveau 0, 1,9° au niveau 3) reste approximatif, adouci par le mipmap. Couleur des sommets : planètes, texture en
  couleurs × ombrage (`0,06 + max(0, n·l)`) ; étoiles, texture en luminance × couleur du corps × `k`, sans ombrage, avec
  `k = min(1 / luminance moyenne, 1 / max(r, g, b))` (éclat moyen ramené vers celui de la couleur unie) et l'opacité du
  halo multipliée par l'éclat moyen obtenu `min(1, luminance × k)` (son bord intérieur jamais plus lumineux que le
  disque). Texture pas encore chargée (ou illisible) : disque en couleur unie comme avant. Points (`α < 0,12°`) :
  jamais texturés ; couleur moyenne de la texture si elle est chargée (× couleur du corps × `k` pour une texture
  teintée), sinon couleur du corps : pas de saut de couleur au seuil. Sommets émis par corps (quads × 4) : 576, 2 304, 9 216, 36 864 selon le niveau
  (sommets distincts de la grille : 175, 637, 2 425, 9 457).
- **Taille minimale** : si `α < 0,12°`, dessiner un point de taille `0,12°` dont l'éclat décroît avec `α` (une
  planète lointaine reste visible comme un point).
- **Étoiles de fond** : quads de taille angulaire fixe (≈ 0,15° à 0,4° selon l'éclat) à 110 blocs ; éclat
  `∝ luminosité / distance²` (al), en échelle logarithmique bornée.
- **Fond** : sphère (ou cube) opaque noire à 120 blocs, dessinée en premier (sous le plan lointain, ≥ 128).

## 5. Mouvement du monde (`SpaceMotion`, un pas par tick)

État : pose `(s, Q)`, vitesse nominale `V` (km/tick). Entrée du pilote `SpacePilotInput` : `throttle ∈ {−1, 0, 1}`,
`roll ∈ {−1, 0, 1}`, `yawRate, pitchRate ∈ [−1, 1]`, `brake` (booléen, touche Saut), `boost` (booléen, touche Sprint,
lot 3, T32), visée `aimYaw, aimPitch` (écart du regard au cercle avant, octets signés, 1 = 30/127°,
`aimPitch > 0` = au-dessus ; sans effet sur le pas depuis T39). Masses des corps (T39) : seulement pour le
référentiel (point 0). Réglages : `V_max` (défaut 10⁷ km/tick), `turn` (défaut
1,5 °/tick), `rollSpeed` (défaut 2 °/tick).

0. **Référentiel** (D7, règle de choix **D29**, T39 ; remplace le score proximité × regard de D8) : la vitesse `V`
   est relative à un **corps de référence** `ref` (état de l'Environment, synchronisé et sauvegardé, id de corps
   stable). Choix **une seule fois, au début du pas**, positions au temps `t` (état `SpaceFrameState` mis à jour en
   place). Le référentiel est l'astre qui a **le plus d'influence gravitationnelle** sur le vaisseau, au sens des
   **sphères d'influence** (comme Kerbal Space Program). La masse ne sert qu'à ce choix : **aucune gravité n'est
   appliquée au vaisseau** et les orbites gardent leur période (`SpaceOrbit`).
   - **Sphère d'influence** d'un corps `i` de parent `p` : **rayon de base** (`SpaceMotion.baseInfluenceRadius`)
     `r_i = a_i · (m_i / m_p)^(2/5)` (`a_i` = demi-grand axe en km, `StrictMath.pow`), au moins **`10 R_i`**
     (`INFLUENCE_FLOOR`, porté de 2 à 10 à la demande de l'utilisatrice) ; puis **rayon de jeu**
     (`SpaceMotion.influenceRadius`, seul utilisé pour le référentiel ; retouche après essai en jeu : « extrêmement
     compliqué d'atteindre une lune sans la louper ») : rayon de base × **4** pour une lune (`MOON`, y compris autour
     d'un objet mineur), × **1,5** pour une planète ou un objet mineur
     (`MOON_INFLUENCE_FACTOR`, `PLANET_INFLUENCE_FACTOR`), **plafonné à `0,5 a_i`**
     (`INFLUENCE_MAX_ORBIT_FRACTION` : la sphère d'une lune ne contient jamais le centre de son parent) ; une **étoile
     compagne** garde son rayon de base (ni facteur, ni plafond). Corps sans
     parent : sphère **infinie**. Calculées une fois par système (instance immuable : une édition en donne une autre).
     Sol (banc T39, base → jeu) : Terre 924 650 → **1 387 000 km**, Mars 577 000 → 866 000 km, Jupiter 48,2 → **72,3 M
     km**, Vénus 924 000, Mercure 169 000, Saturne 81,8 M, Neptune 130 M, Cérès 115 500, Pluton 4,72 M km ; Lune
     66 184 → **192 200 km** (plafond `0,5 a`), Io 18 216 → 72 864 km, Europe 15 608 → 62 432 km, Ganymède 26 341 →
     105 364 km, Callisto 37 681 → 150 725 km, Rhéa 7 638 → 30 552 km, Titan 43 321 → 173 285 km, Phobos 112,7 →
     451 km, Déimos 62 → 248 km, Charon 8 445 → 9 796 km (plafond `0,5 a`). Voisins : étoiles compagnes inchangées
     (Alpha du Centaure B 21,8 UA, Proxima 3 640 UA, Sirius B 14,9 UA, Procyon B 10,5 UA, 61 Cygni B 83,2 UA) ;
     planètes : Proxima b 159 000 km, Proxima d et Barnard b à e ≈ 61 000 à 71 000 km.
   - **Corps visé** `c` (`SpaceMotion.influenceTarget`) : on part des corps sans parent (le plus attirant s'il y en a
     plusieurs), puis on **descend** tant qu'un enfant du corps retenu contient le vaisseau (distance au centre
     `d ≤ r`) : étoile → planète → lune. Entre deux sœurs qui le contiennent toutes deux (sphères qui se recouvrent,
     ex. Alpha du Centaure B et Proxima), la plus forte attraction `m / d²` (`d²` au moins 1 km²) ; à égalité, la
     première dans l'ordre du système. En gravité brute (`G m / d²` seule), le Soleil l'emporterait sur la Terre dès
     ≈ 260 000 km : la sphère d'influence évite ce défaut.
   - **Hystérésis** : la sphère du référentiel courant **et de ses ancêtres** est agrandie de **5 %**
     (`INFLUENCE_HYSTERESIS` = 1,05) : on ne la quitte qu'au-delà de `1,05 · r` (remplace la marge de score 1,25).
   - **Bascule** : si `c ≠ ref`, `c` est candidat ; après **10 ticks consécutifs** de candidature (délai, 0,5 s), et si
     **60 ticks** (cooldown, 3 s) se sont écoulés depuis la bascule précédente → `ref = c`. `ref` absent (supprimé,
     système changé, aucun) → `c` immédiatement, sans délai ni fondu (même règle, sans hystérésis).
   - **Fondu** : à la bascule de `A` vers `B`, le mouvement d'entraînement passe de `A` à `B` en **40 ticks** (2 s) :
     `Δp_frame = (1 − σ) · Δp_A + σ · Δp_B`, `σ = smoothstep(ticks depuis la bascule / 40)` (`σ = 0` au pas de la bascule). Le changement ne
     modifie ni `s` ni `V`. Pendant le fondu, le freinage d'approche du référentiel utilise aussi la règle `min`.
   - Le **regard** (visée du pilote, `aimYaw`, `aimPitch` de `SpacePilotInput`) n'entre plus dans le choix : il reste
     transmis (format inchangé) mais le pas ne le lit plus.
   - Constantes dans `SpaceMotion` (`INFLUENCE_EXPONENT` 0,4, `INFLUENCE_FLOOR` 10, `MOON_INFLUENCE_FACTOR` 4, `PLANET_INFLUENCE_FACTOR` 1,5,
     `INFLUENCE_MAX_ORBIT_FRACTION` 0,5, `INFLUENCE_HYSTERESIS` 1,05,
     `SWITCH_DELAY` 10, `SWITCH_COOLDOWN` 60, `BLEND_TICKS` 40). État à synchroniser et sauver : `ref`, `ref`
     précédent, ticks depuis la bascule, candidat et ticks de candidature.
   - Exemples (banc T39, rayons de jeu) : à 20 000 km de la Lune → Lune ; entre la Terre et la Lune, hors de la
     sphère de la Lune (150 000 km de la Terre, 238 000 de la Lune) → Terre ; à 1,5 M km de la Terre → Soleil ; Lune à
     180 000 km → Lune, à 200 000 km → Terre ; référentiel Lune, monde à `1,03 r` → reste Lune, à `1,06 r` → Terre
     après le délai.
   - **Lunes** (banc T39, croisière) : une lune n'est captée que si le vaisseau reste 10 ticks (délai, inchangé) dans
     sa sphère. Plancher de 2 rayons : Io, Europe, Rhéa captées seulement en arrivant lentement, Phobos et Déimos jamais ;
     plancher de 10 rayons : Europe atteinte, Rhéa captée par moments, Io non ; **rayon de jeu** (× 4, plafond
     `0,5 a`) : depuis leur planète ou une lune voisine, le pilote nominal capte et atteint la Lune, Io, Europe,
     Ganymède, Callisto, Rhéa, Titan et Charon (arrêt à ≈ 1,97 R en 15 à 40 s) ; Déimos est captée mais pas atteinte
     par le pilote nominal (le pilote brutal s'y pose au plancher) ; **Phobos** n'est toujours pas captée (la poursuite
     du banc passe à 496 km au plus près, sphère de 451 km ; 770 km/tick autour de Mars).
1. **Gaz** (vitesse **signée**, `V ∈ [−V_max, V_max]` ; `V < 0` = le monde part en arrière ; D6) :
   - `brake` (Espace maintenu) **prime** sur `throttle` : `V = V · 0,92`, et `V = 0` quand `|V| < 1` km/tick ;
   - sinon `throttle = s ≠ 0` : si `V` est de signe opposé à `s`, `V = V · 0,92` (et `0` sous 1 km/tick) ; sinon
     `V = s · min(V_max, max(1, |V| · 1,05))` (accélère dans le sens `s`, en partant de 1 km/tick) ;
   - pas de touche : `V` inchangée (le monde garde son élan). `|V|` est bornée à `V_max` à chaque pas.
   - **Boost** (lot 3, T32, D24, C74 ; Sprint maintenu) : les mêmes règles avec des pentes **triplées en échelle
     logarithmique** : facteur de décélération `k_b = 0,92³ = 0,778688` (frein et gaz opposé) au lieu de `k = 0,92`,
     facteur d'accélération `1,05³ = 1,157625` au lieu de `1,05` (`SpaceMotion.BOOST_DECELERATION`,
     `BOOST_ACCELERATION`). Le boost seul ne change rien ; il est ignoré pendant la charge du saut (frein forcé à
     0,92, § 8). Vérifié au banc (`V_max` = 10⁷ km/tick) :

     | | sans boost | avec boost |
     |---|---|---|
     | de 1 km/tick à `V_max` (gaz) | 331 ticks (16,6 s) | 111 ticks (5,6 s) |
     | de `V_max` à l'arrêt (frein, ou gaz opposé jusqu'à 0) | 194 ticks (9,7 s) | 65 ticks (3,3 s) |
     | distance d'arrêt depuis `V_max` | 1,150 · 10⁸ km (0,77 UA) | 3,519 · 10⁷ km (0,24 UA) |

   - **Distance d'arrêt** (relative au référentiel ; utilisée par la vitesse de croisière, T34, § 11) : le frein multiplie
     `V` par `k` puis le monde avance de la nouvelle vitesse, jusqu'au pas où `|V| < 1` (arrêt net, déplacement nul) :
     `d(V) = Σ_{n=1..N} |V| · kⁿ`, `N` = plus grand `n` tel que `|V| · kⁿ ≥ 1`, soit
     `d(V) = k · |V| · (1 − k^N) / (1 − k)`, majorée par `k · |V| / (1 − k)` — **11,5 · |V|** sans boost,
     **3,5185 · |V|** avec (la borne dépasse `d` de 3,5 à 4,5 km avec boost, de 11,5 à 12,5 km sans : négligeable). Calcul exact :
     `SpaceMotion.stoppingDistance(V, boost)` (même suite de multiplications que le pas). Le freinage d'approche (point
     3) ne fait que ralentir davantage près d'un corps : la distance réelle est plus courte, la formule est prudente.
2. **Rotation**, dans les axes du monde (le monde est le vaisseau) puis composée à droite :
   `Q ← Q · Rot(U_W, −yawRate·turn) · Rot(R_W, pitchRate·turn) · Rot(F_W, roll·rollSpeed)`, puis normalisation.
   Sens voulus (à vérifier en jeu) : regarder à droite du cercle avant fait pivoter l'avant du monde vers la droite
   (les astres glissent vers la gauche de l'écran) ; regarder au-dessus fait monter l'avant ; Droite (D) fait rouler
   vers la droite.
3. **Vitesse effective** : `v = V · (Q · F_W)`, relative à `ref` ; déplacement dans le repère du système
   `D = v + Δp_frame` (`Δp_frame = p_ref(t+1) − p_ref(t)` hors fondu ; le monde est entraîné par le mouvement
   orbital de `ref` : à `V = 0` il reste immobile par rapport à lui). **Chaque corps `i` est traité dans son propre repère** : le monde part de
   `o_i = s − p_i(t)` (`h_i = |o_i| − R_i`, `n_i` = direction monde → corps) et se déplace de `D − Δp_i`
   (`Δp_i = p_i(t+1) − p_i(t)`). Freinage d'approche : `approche = min(v · n_i, (D − Δp_i) · n_i)` (pour `ref` :
   `v · n_i`) ; si `approche > 0`, `f_i = clamp((h_i − h_min,i) / (K · R_i), 0, 1)` (`K = 2`, `h_min,i = 0,01 · R_i` : D9) et `v ← v − (1 − f_i) · approche ·
   n_i`. Le `min` ne retire que de la vitesse **commandée** : le freinage ne pousse jamais le monde, qui reste lié à
   son référentiel à `V = 0` (sinon la courbure de l'orbite d'une lune l'en arracherait). Longer la surface ou
   s'éloigner garde toute la vitesse, et `V` (nominale) n'est jamais modifiée, d'où le « rétablissement ».
4. **Garde-fou** : `s' = s + D`. Pour chaque corps, si le **segment relatif** `[o_i, o_i + D − Δp_i]` passe à moins de
   `R_i + h_i / 2` du centre (avec `h_i` d'avant le pas), le mouvement s'arrête à l'entrée de cette sphère, puis le
   reste du pas, privé de sa composante radiale, la longe (tester le segment absolu `[s, s']` contre les corps à `t+1`
   stoppait à tort un monde placé devant une planète rapide). **Plancher** (D9) : la sphère de garde a
   pour rayon `R_i + max(h_i / 2, h_min,i)`, `h_min,i = 0,01 · R_i` (Terre 64 km, Lune 17 km, Soleil 6 960 km) :
   l'altitude au plus se **divise par deux** par tick jusqu'au plancher, jamais en dessous, même à `V_max`. Un monde
   sous le plancher au début du pas (édition, arrondi) est ramené radialement à `R_i + h_min,i`, puis le pas continue
   de là (il s'éloigne librement ou longe la sphère). Pour un corps immobile, c'est exactement le calcul d'avant D7.
5. `s = s'`. La fonction est déterministe :
   `SpaceMotion.step(pose, V, frame, input, système, tDébut, tFin, réglages, destPose) → V`, `frame`
   (`SpaceFrameState`) mis à jour en place (méthode d'instance qui porte son espace de travail, une instance par fil ; `tDébut` /
   `tFin` = `days(T, 0)` / `days(T + 1, 0)`, horloge du § 3 avec son époque : passer les deux évite une dérive d'arrondi ; `V`
   bornée à `V_max` à chaque pas). Le serveur et les clients l'appellent avec les mêmes entrées
   ([`05`](05-synchronisation.md)).

Pose par défaut d'un système (`setCurrentSystem`, réinitialisation ; `SpaceMotion.defaultPose(système, t, réglages,
dest)`) : sur la droite parent → planète de départ, **au-delà de la planète, à `min(0,02 a, 0,8 r)` d'elle** (`r` =
rayon de **base** de sa sphère d'influence, sans facteur de jeu, T39, C115 ; avant T39 : à `1,02 a` du parent, hors de la sphère de la Terre) — sans
planète : 1 UA sur `+X` —, hors de tout corps (altitude ≥ 20 rayons), avant du monde tourné vers l'étoile principale,
`V = 0` ; référentiel = corps visé par la règle des sphères (sans hystérésis). Sol (banc T39, `t = 0`) : 0,988 UA du
Soleil, **739 720 km de la Terre**, Lune à 982 000 km, référentiel = **Terre**. Dans les voisins, une planète proche de
son étoile a une sphère plus petite que la garde de 20 rayons : le référentiel y est l'étoile (Barnard, Tau Ceti g / h)
ou une planète plus lointaine (Lalande 21185 c, Tau Ceti f, Epsilon Eridani b) ; la pose utilise le rayon de **base**
(`baseInfluenceRadius`, sans le facteur de jeu) : elle est inchangée par la retouche des rayons de jeu. Seul le
référentiel d'Alpha du Centaure change : la pose (à 136 500 km de Proxima b, garde de 20 rayons) est hors de la sphère
de base de Proxima b (106 000 km) mais dans sa sphère de jeu (× 1,5 = 159 300 km) : Proxima b au lieu de Proxima. **Planète de départ** (T16) : la `PLANET` dont le
demi-grand axe `a` est le plus proche de 1 UA en échelle logarithmique (`|ln a|` minimal, `StrictMath`, la première en
cas d'égalité) — la Terre dans Sol, indépendamment de son rang et de son nom ; dans un système voisin, la planète la
plus « tempérée » (Proxima b, Barnard e, Lalande 21185 c, Epsilon Eridani b, Tau Ceti f) ; objets mineurs et lunes
jamais retenus.

### 5 bis. Téléportation (D34, T44 suite)

`SpaceEnvironmentModule.teleportTo(système, corps)` (Configurator, ops) pose le vaisseau « comme en orbite » par
`SpaceMotion.orbitPose(système, i, t, réglages, approche, dest)` :
- distance au centre du corps `d = max(1,5 R, min(10 R, 0,5 r))`, `r` = rayon **de jeu** de sa sphère d'influence
  (`influenceRadius`, infini sans parent) : 10 rayons comme l'arrivée d'un saut (C59), mais toujours dans la sphère.
  Terre 63 710 km, Lune 17 370 km, Phobos 111 km, Soleil 6,96 M km ;
- côté : vers l'étoile la plus proche dans sa parenté (face éclairée) ; pour une étoile, vers d'où vient le vaisseau
  (même système : sa position ; autre système : direction galactique du système quitté, comme l'axe d'un saut ;
  sinon vers son parent, sinon `+X`) ;
- le point doit avoir le corps pour cible de la règle des sphères (`influenceTarget`, hystérésis du référentiel) et
  être au-dessus du plancher d'altitude de chaque corps ; sinon les axes `±X, ±Y, ±Z` sont essayés dans l'ordre ;
  aucun ne convient : premier point gardé ;
- orientation : avant du monde tourné vers le corps, haut au plus près de `+Y` (même construction que la pose par
  défaut, `lookAt`) ; `V = 0`, référentiel = le corps sans fondu, saut annulé ; autre système : il devient courant
  (révision incrémentée).

Affichage (`SpaceMotion.interpolate`) : interpoler `s − P_frame` entre les deux derniers ticks puis rajouter
`P_frame` au temps d'affichage (fondu compris) : aucun écart avec le mouvement du référentiel entre deux ticks.

`nearestBody(pose, système, t)` : corps de plus petite altitude `h_i`. Le Controller affiche le **référentiel** `ref`
(« Référentiel : Terre ») et l'altitude au-dessus de lui ; la vitesse affichée est relative à lui.

## 6. Joystick virtuel (T07, T08)

Données : regard de la caméra `(ψ, θ)`, direction de l'axe avant `(ψ_F, θ_F)` (dérivée de `F_W`, fixe dans le monde).

```
Δψ = wrapDegrees(ψ − ψ_F) ;  Δθ = θ − θ_F ;  m = √(Δψ² + Δθ²)
si m > A : (Δψ, Δθ) ← (Δψ, Δθ) · A / m            borne du cone, A = 30°
camera ← (ψ_F + Δψ, θ_F + Δθ)                       HoloCapture.setLook
k = clamp((m − D) / (A − D), 0, 1) / m              zone morte D = 2° (k = 0 si m = 0)
yawRate = Δψ · k ;  pitchRate = −Δθ · k             (regarder plus haut = pitch MC negatif = nez qui monte)
```

**Lot 2 (T19, C48)** : la zone morte `D` n'est plus une constante de 2° mais le rayon angulaire du cercle avant
moins celui du cercle de visée, `D = atan(R_F / d) − atan(R_A / d)` (`d` = distance des cercles, 2 blocs) : aucune
rotation tant que le cercle de visée est entièrement dans le cercle avant ; la formule de `k` reste continue au bord.
Avec les rayons du lot 1 (`R_F = 0,06`, `R_A = 0,035`), `D` valait 0,72° (< 1,5°) : le cercle avant est agrandi à
`R_F = 0,105` bloc, d'où `D = 3,005° − 1,003° ≈ 2,0°` (la zone morte du lot 1, C27). `D` est calculé par
`SpacePilotPad.DEAD_ZONE` (`StrictMath`) depuis les constantes publiques `SpaceJoystickForward.RADIUS` /
`SpaceJoystickAim.RADIUS` / `SpaceJoystickForward.DISTANCE`.
Pendant un saut (§ 8), le joystick ne commande rien (taux nuls, caméra toujours bornée au cône).

L'écart est mesuré dans l'espace des angles (suffisant dans un cône de 30° autour d'un avant horizontal). À la prise
du pilotage, la caméra est ramenée **en douceur** dans le cône (interpolation sur quelques frames), pas d'un coup.

Dessin (T08, migré dans Space Pilot en T19 ; seul le rayon du cercle avant change, 0,06 → 0,105) : objets `PRIVATE`
de `SpacePilotModule`, placés par son `clientTick`, dessinés seulement pendant que le joueur local pilote (capture du pad
en cours et état du poste qui le désigne, `SpacePilotPad.localPilot()`). Le cercle avant est un objet `Local(false)` en `(x, y, z) = (ψ_F, θ_F, 2)` ; le cercle de visée un
`Local(true)` en `(0, 0, 2)`. Le **segment** est dessiné par le renderer du cercle avant : à `ViewRenderPass`, la pile
part de l'identité dans l'espace vue, donc `pose.last().pose()` est la matrice repère local → vue ; son inverse
(calculé dans une `Matrix4f` réutilisée) envoie le centre de l'écran `(0, 0, −2)` dans le repère local du cercle, où
l'on trace le segment entre l'origine et ce point. Le renderer ne dessine le segment que si
`ctx.family() == VIEW`.

## 7. Cube de la carte (T11, T12 ; Space Map T37, Configurator branché T42)

> Depuis T42, le cube, la vue (mode, cadrage, zoom, sélection, origine) et le renderer appartiennent à la **Space Map** ;
> le Configurator n'a plus que sa fenêtre et ses poignées, placées et relues dans le repère du cube et de la vue de la
> Map (voir « Configurator branché » plus bas). Les paragraphes historiques ci-dessous (« Animer », action 13, zoom du
> Configurator) décrivent le lot 2 ; les formules (repère, échelles, poignées) restent valables.

**Repère du cube** : le transform `T` (translation `t`, rotation `R` = `HoloEditMath.rotation`, échelle `σ`) place le
point local `q` en `P = t + R · (σ ⊙ q)`, `P` dans le repère du marker (axes du monde). Inverse :
`q = (R⁻¹ · (P − t)) ⊘ σ`. Le Configurator impose une **échelle uniforme** (`σx = σy = σz`) : s'il détecte une échelle
non uniforme après une édition, il la ramène à la moyenne.

**Mode galaxie** : cadrage `(centre c, demi-étendue H)` en al, état du Configurator. `q = (g − c) / H`.
« Recadrer » : `c` = centre de la boîte englobante, `H = nice(1,1 × demi-étendue max)`, au moins 1 al ;
`nice` = valeur 1-2-5 supérieure. Graduations des arêtes : pas `nice(H / 4)`, étiquettes en al sur trois arêtes.
Poignée de translation : à chaque tick, si `P_poignée` diffère (`> 1e-4`) de la dernière valeur écrite par le module,
`q` est calculé, borné à `[−1, 1]³`, puis `g = c + q · H` → `moveSystem`. Le module réécrit la poignée quand la
sélection, le cube ou la position du système changent (et retient cette valeur).

**Mode système** : temps **figé** à l'entrée dans le mode (`t_edit`), bouton « Animer » optionnel. Échelle linéaire
`q = p / H_sys` avec `H_sys = nice(1,1 × portée max)` en UA (portée d'un corps = somme des apoapses de sa chaîne de
parents). La loupe des lunes est **supprimée (D13, T25)** : toutes les positions sont à l'échelle
(`SpaceSystemScale.positions` = positions de Kepler absolues × `1 / (UA_KM · H)`), seules les tailles sont exagérées ;
dans Sol, la Lune est collée à la Terre, et tout le système intérieur (jusqu'à ≈ 9 UA) tient dans le disque du Soleil,
car Éris porte `H_sys` à 200 UA.

**Zoom sur un corps (T25, D17)** : état du Configurator `zoom` (id du corps, 0 = système entier ; sauvegardé et
synchronisé, car la poignée d'orbite est placée et relue par le serveur à l'échelle de la vue), réglé par l'action 13
depuis la fenêtre (« Zoom » sur le corps sélectionné, « Dezoom »). Vue centrée sur le corps `c` au temps affiché :
`q = (p − p_c) / H_zoom`, `H_zoom = nice(1,1 × max(portée des descendants mesurée depuis c, 5 R_c))` (Mars : Deimos →
`2e-4` UA, Phobos à 0,31 et Deimos à 0,78 unité du cube). Sont dessinés : les corps dans le cube (marge 5 %), les
orbites des descendants de `c` (seules à l'échelle de la vue), et un **arc local** de l'orbite de `c` (32 segments en
anomalie excentrique sur `E₀ ± min(π, 2 H / (a (1 − e)))`, recalculé chaque frame, segments hors du cube omis). La
poignée d'orbite n'existe que pour un corps dont le parent est `c` ou un descendant de `c`, et, dans toute vue,
seulement si le demi-petit axe affiché de l'orbite atteint `1e-5` unité du cube (sinon son échelle serait bornée et
sa relecture fausse : Phobos dans la vue de Sol entier s'édite dans le zoom sur Mars). Remis à 0 au changement
de mode ou de système et si le corps disparaît. Un changement de zoom reconstruit le cache de mise en page.

**Rayon affiché (T25)** : `max(clamp(0,015 + 0,01 · log10(R_km / 1000), 0,012, 0,08), R_km / (UA_KM · H))` unités
du cube : la formule exagérée, ou le rayon réel à l'échelle de la vue s'il est plus grand (zoom serré : Mars fait
0,11 unité dans son zoom).

**Vaisseau (T25, C49)** : affiché seulement si le système édité est le système courant du miroir client de
l'Environment, en `p_ship = P_ref(t_aff) + (s − P_ref(t_now))` (`s` = pose affichée `pose(partialTick)`, `ref` =
référentiel affiché `referenceBodyId()`, `t_aff` = temps du cube : `t_edit`, ou le temps animé ; sans référentiel :
`s`, vue du système entier seulement), dans la vue courante ; masqué hors du cube. Disque vert (`WORLD_COLOR`) de
rayon 0,01 unité du cube, avancé de 0,06 bloc vers la caméra pour rester visible devant le corps qu'il frôle ;
segment de 0,12 unité du cube dans la direction `Q · F_W` (axe avant exprimé dans le système, § 2). Calculé à chaque
frame (interpolé), hors du cache.

**Mode galaxie, système courant (T25)** : l'étoile du système courant est entourée d'un **double anneau vert**
(`WORLD_COLOR`, rayons `max(0,035 ; 1,9 r)` et +0,009 bloc), son nom est vert ; la sélection garde son anneau jaune
plus large. Dans la fenêtre, la ligne du système courant (« * ») est écrite en vert et la ligne d'information porte la
légende « vert : monde ici ».

**Croix de sélection et coordonnées (T26, C50)** : pour l'objet sélectionné en `q` (repère du cube, `[−1, 1]³`), trois
segments parallèles aux axes du cube passent par `q` et vont jusqu'aux faces (`x = ±1`, `y = ±1`, `z = ±1`) ; aux
six extrémités, une étiquette donne la coordonnée de cet axe, précise (3 décimales en al, ou en UA en mode
système). Mode galaxie : `g − g_origine` (al) ; mode système : position relative à l'étoile principale (UA), au
temps d'affichage. Rayon affiché d'un corps : voir « Rayon affiché (T25) » ci-dessus.

**Space Map (lot 3, T37)** : le cube et son renderer passent dans la Space Map (le Configurator a gardé le sien,
même renderer, jusqu'à T42). Vue **partagée**, sans `t_edit` : temps **vivant** de l'Environment (client
`displayDays(partialTick)`, serveur `days()`), donc `p_ship = s` (la formule de C49 reste celle du renderer). Carte
galactique : centre `c` et demi-étendue `H` continus (`[0,05 ; 200 000]` al) ; recentrer sur un système pose
`c = g_système`, `H` gardé ; « Vue d'ensemble » = recadrage ci-dessus. Vue système : centre = un corps (`q = (p − p_c) / H`)
ou l'origine du système, `H` continu (`[1e-6 ; 2e4]` UA) ; recentrer sur un corps pose
`H = framingHalf = max(H_zoom, nice(1,1 × apoapside))` (le second terme seulement pour un corps sans descendant en
orbite dont le parent n'est pas une étoile : une lune montre aussi sa planète ; Lune : `H = 0,005` UA, Terre à
0,54 unité) ; système entier : `H_sys`. Zoom : `H ← H · 1,5^(−crans)`, centre inchangé.
- **Orbites** : ellipse complète (64 segments, cache) si son apoapside `≤ 4 H` (corde à moins de `0,0048` unité du
  cube de l'ellipse) ; sinon **arc local** (comme l'arc du corps zoomé de T25) autour de chaque corps visible ; segments
  hors du cube omis ; corps hors du cube masqués (toujours, plus seulement en zoom).
- **Carte ajustée au cube** : avant T37, les tailles en blocs (étoiles, anneaux, textes, traits, écarts) ne suivaient
  pas l'échelle du cube ; elles sont maintenant multipliées par `σ / 1,5` (valeurs des constantes à l'échelle par
  défaut), portée de lecture des textes `24 · max(1, σ / 1,5)` blocs.
- **Corps texturés** : disque face caméra de 8 anneaux × 24 secteurs ; le sommet au rayon relatif `sin a`, angle `t`,
  représente le point de normale `sin a (cos t X + sin t Y) + cos a E` (vue orthographique ; `X`, `Y` axes du billboard,
  `E` vers l'œil), ramenée dans les axes du corps par `q_B⁻¹ · (R⁻¹ C)` (`SpaceSpin` au temps de la vue) ; `u`, `v`
  comme le ciel (§ 4, C85), couture par quad ; sans ombrage ; étoile teintée et éclaircie comme le ciel (C92). Repli
  en couleur unie tant que la texture n'est pas prête.

**Configurator branché (T42, C121–C124)** : poignées dans le repère du cube de la Map et de sa vue (centre, `H`) ;
chaque écriture retient le système / corps visé, le transform du cube et le cadrage, et la relecture utilise ce
contexte. La Map suit le temps vivant ; la poignée d'orbite est posée et relue au **temps figé** `t_edit` (état
synchronisé du Configurator), repris par le serveur seulement au changement de contexte (système montré ou sa copie,
corps sélectionné, centre, `H`, révision) : aucun envoi par tick, et `HoloEditRules.accepts` reste satisfait pendant
une édition. Le renderer décale l'ellipse du déplacement du parent entre `t_edit` et le temps vivant ; gizmos et
visée restent à `t_edit`. Existence : `b_aff ≥ 1e-5`, apoapside `≤ 4` unités du cube, parent à `≤ 2` unités du
centre. Bouton « Orbite » (action 10) : vue centrée sur le parent, `H = nice(1,1 × apoapside)`, l'orbite entière tient
dans le cube.

**Poignée d'orbite** (`SpaceOrbitHandle`, `HoloEditRotation` + `HoloEditScale`) : posée sur le parent affiché.

```
rotation monde de la poignee   = R · R_o                 R_o = HoloEditMath.rotation(orientation de l'orbite)
echelle de la poignee          = (a_aff · σ, a_aff · σ, b_aff · σ)     a_aff, b_aff : demi-axes affiches (unites du cube)
lecture : R_o = R⁻¹ · R_poignee -> (yaw, pitch, roll) par setRotation d'une instance de HoloEditMath (champ)
          a = scaleX / σ · H (-> UA, H de la vue) ; b = scaleZ / σ · H ; e = √(1 − (b/a)²) borne a [0 ; 0,95], e = 0 si b ≥ a
```

`scaleY` n'a pas de sens : il est réécrit à `scaleX`. Le renderer de la poignée dessine dans son repère local la
courbe `(cos θ − e, 0, sin θ)`, θ ∈ [0, 2π[ ; le pont applique l'échelle `(a, a, b)`, ce qui donne l'ellipse
`(a cos θ − a e, 0, b sin θ)` : parent au foyer, centre décalé de `−a·e` sur `X`. `e` se déduit du rapport
`scaleZ / scaleX` lu dans le transform de l'objet.

**Règles graduées** (T10) : trois règles partent du coin `(-1, 1, 1)` (haut, gauche, avant ; le bas était caché par
la fenêtre au lot 1) le long de `+X`, `+Y` (comptée depuis le bas) et `−Z` ; chaque étiquette donne la distance depuis le
début de la règle (« 20 al », zéro omis), pas `nice(H / 4)`.

**Mode système, choix de T12** :
- `H_sys` : portée d'un corps = somme des apoapses de sa chaîne de parents (lunes comprises) ; sans zoom, rien ne sort
  du cube ; en zoom, les corps hors du cube sont masqués.
- ~~Loupe des lunes arrondie~~ (T12, plafonnée en T16 par C54) : **supprimée en T25** (D13, D17), remplacée par le
  zoom sur un corps.
- Poignée d'orbite → orientation : décomposition yaw / pitch / roll **en double depuis la matrice**, avec blocage de
  cardan traité (roll = 0 si `cos(pitch) < 1e-3`), pas `HoloEditMath.setRotation` (float : jusqu'à 176° d'erreur près
  des orbites polaires ; maintenant ≤ 0,1°). Seules les composantes changées sont recalculées ; `e` conservée sur une
  mise à l'échelle uniforme ; conversion faite avec l'échelle de la vue d'avant l'édition.
- Rayon affiché en unités du cube (grandit avec le cube). « Animer » : temps d'affichage client seulement ; l'arrêter
  ramène à `t_edit` ; la poignée reste à `t_edit`.

## 8. Saut interstellaire (lot 2, T21 / T22)

**Phases** (`SpaceJump`, état de l'Environment, avancé dans le pas serveur **et** dans le rejeu client, `StrictMath`) :

| Phase | Durée | Effet sur la pose |
|---|---|---|
| `NONE` | — | mouvement normal (§ 5) ; drapeau saut + cible valide (système connu ≠ courant) → `CHARGE` dans le même pas |
| `CHARGE` | 60 ticks, tant que l'entrée garde le drapeau saut | au pas `n` de la charge (1 à 60) : pas « verrouillé » (frein forcé `V ← 0,92 V`, gaz, roulis et taux ignorés ; référentiel, entraînement, freinage d'approche et garde-fou inchangés, avec l'orientation du début du pas), puis `Q ← slerp(Q₀, Q_cible, lisse(min(n, 40)/40))` (C39, C40). Drapeau perdu → `NONE` et pas normal (orientation atteinte gardée). Au pas 60 → `TRAVEL`, compteur 0, `V = 0` (le frein seul ne l'annule pas : 10⁷ km/tick · 0,92⁶⁰ ≈ 67 000) |
| `TRAVEL` | `N = max(40, ceil(20 · D / v_w))` ticks (minimum 20 avant T35, C73) | pose figée dans le système d'origine (référentiel inchangé, `V = 0` ; les étoiles convergent puis rien n'est dessiné que la distorsion) ; compteur borné à `N` ; à `N`, **serveur seul** : arrivée |

- **Cible** (T22, C41) : à l'appui de Sneak, le client du pilote choisit l'étoile de fond dont la direction (vue
  depuis la pose, § 4) fait le plus petit angle avec le centre de l'écran, si cet angle ≤ 3° ; il envoie son **id**
  avec le drapeau. Le serveur vérifie que l'id est un système existant différent du courant. Réalisé en T22 par
  `SpaceJump.pickTarget` (regard du joueur, pose du ciel de la dernière frame dessinée) ; pas de nouvelle cible
  pendant un voyage (une cible tenue jusqu'à l'arrivée relancerait un saut).
- **État** (`SpaceJump`, mutable, une instance par suite de pas) : phase, id de la cible, pas faits dans la phase,
  durée `N`, `Q₀` (orientation au début de la charge), positions galactiques d'origine et de cible — tout est **figé
  au début de la charge** (la cible désignée ensuite par l'entrée est ignorée ; `N` est calculée avec la vitesse de
  distorsion du moment). Position de la cible = coordonnées du système arrondies en `float` (celles de son étoile de
  fond : le client n'a que celles-là) ; origine = système courant (`double`). Recherche de la cible :
  `SpaceJump.Targets` (serveur : galaxie ; client : étoiles de fond).
- **Orientation visée** : `F_cible` = direction de l'étoile cible dans le repère du système courant (même calcul que
  le ciel, § 4 : position galactique de la cible moins celle du système courant, normalisée). `Q_cible` = la rotation
  la plus courte qui envoie `Q₀ · F_W` sur `F_cible` composée avec `Q₀` (le roulis n'est pas imposé). `lisse(u) =
  u²(3 − 2u)`. Calcul (`SpaceJump.align`, `StrictMath`, sans JOML) : axe `a = normalize(f₀ × F_cible)`,
  `θ = atan2(|f₀ × F_cible|, f₀ · F_cible)`, `Q = Rot(a, θ · σ) · Q₀` — même chemin que le slerp. Directions
  opposées (sin θ < 1e-12) : demi-tour autour du haut du monde `Q₀ · U_W`. Banc : depuis 170°, aligné à 1,4e-16 rad
  au pas 40.
- **Durée** : `D` = distance galactique (al) entre système d'origine et cible ; `v_w` = vitesse de distorsion (al/s,
  réglage, défaut 0,5) ; 20 ticks par seconde. Au moins 40 ticks (`SpaceJump.MIN_TRAVEL_TICKS`, C73 : 20 ticks de
  convergence des étoiles, puis au moins autant de voyage ; un voyage sauvegardé plus court reste valide). Sol →
  Alpha du Centaure (`D` = 4,36999995 al avec la cible en `float`) : 175 ticks. Durée totale d'un saut : 60 + `N`
  ticks. Banc (T35) : `D` ≤ 1 al à 0,5 al/s → 40 ; 1,0001 al → 41 ; Alpha du Centaure → 175 ; vitesse nulle ou `D`
  non fini → 40.
- **Arrivée** (D10, C59), dans le pas serveur où le compte atteint `N` : système courant ← cible (révision
  incrémentée, comme `setCurrentSystem`) ; `u` = direction origine → cible exprimée dans le repère de la cible (même axes galactiques) ; pose `s = −u · r_a`
  avec `r_a = 10 × R_étoile` (km, étoile principale ; C59 : proche de l'étoile cible, disque d'environ 11°, Soleil
  0,047 UA) ; `Q` tel que
  `Q · F_W = u` (face à l'étoile) en gardant le roulis le plus proche de celui d'avant (`align` avec σ = 1) ; `V = 0` ;
  référentiel = étoile principale (pas de fondu). Cible retirée entre-temps : saut abandonné, le monde reste à l'arrêt
  dans le système d'origine. Un changement de système courant (Configurator) ou une remise à la pose par défaut
  annule le saut.
- **Pilote libéré** : pendant la charge, l'entrée remise à zéro fait perdre le drapeau → annulation ; pendant le
  voyage, rien ne change (C40).
- **Pendant le voyage**, l'interpolation d'affichage ne mélange jamais deux systèmes : le changement de système est un
  saut d'affichage (comme C22 / T07e), masqué par la distorsion.
- **Distorsion affichée** (lot 3, T35, D23, C73 ; client seulement, `client/render/modules/dimension/space/warp/`,
  rien sur le réseau). Temps `e` = `ticks + jumpFraction` du saut affiché, borné à `N` (l'intervalle qui mène à
  l'état de compte 0 est le premier : `e` va de 0 à `N`, et reste à `N` si le client attend l'arrivée au bout du
  compte) ; le même chez tous les joueurs. `C = min(20, N/2)` (20 dès que `N` ≥ 40) :

  | Phase (`SpaceWarpTimeline`) | Temps | Ce qui est dessiné |
  |---|---|---|
  | `CONVERGE` | `0 ≤ e < C` (1 s) | ciel normal ; chaque étoile du fond procédural et des autres systèmes glisse vers la cible ; blanc `lisse((e − (C − 3)) / 3)` sur les 3 derniers ticks |
  | `WARP` | `C ≤ e` | flash d'entrée (blanc 1 à `e = C`, `1 − lisse((e − C) / 10)`), scène du voyage qui apparaît dessous (`sceneRamp = lisse((e − C) / 10)`) ; blanc `lisse((e − (N − 4)) / 4)` sur les 4 derniers ticks (entier à `e = N`) |
  | `ARRIVAL` | `s` ticks d'affichage depuis la dernière lecture en voyage, `s < 16` | ciel du nouveau système sous un blanc tenu 4 ticks puis `1 − lisse((s − 4) / 12)` (chronométré par l'effet, comme C64 ; aussi après une annulation ou un abandon) |

  **Convergence** : fraction de la tête `c = (e / C)³` (départ lent, arrivée rapide), de la queue
  `c_q = max(0, e/C − 0,12)³`. Étoile de direction `d` (unitaire), cible `T` (axes du système pour le fond procédural :
  `normalize(cible − origine)` galactique ; axes du monde pour les autres systèmes : direction vue depuis la pose,
  comme le ciel), `θ = atan2(|d × T|, d · T)`, `k = (d × T) / |d × T|`, `p = (T − d cos θ) / sin θ` (tangente vers
  `T`) : point tourné de `φ` = `d cos φ + p sin φ`, tangente `m(φ) = p cos φ − d sin φ`. Quad de la queue (`φ = c_q θ`)
  à la tête (`φ = c θ`), demi-largeur = demi-taille de l'étoile `h` : coins `q − k h − m_q h`, `q + k h − m_q h`,
  `t + k h + m_t h`, `t − k h + m_t h` (même sens que les carrés du ciel, `k × m` vers l'œil) : un carré à l'arrêt,
  une traînée qui s'allonge avec la vitesse ; à `c = 1` toutes les têtes sont sur la cible. Étoile sur la cible :
  immobile ; opposée : grand cercle fixe (axe perpendiculaire quelconque). Corps du système : inchangés.

  **Voyage** : dégradé arc-en-ciel fonction du seul angle `θ` à la cible : violet jusqu'à 6°, spectre violet → bleu →
  cyan → vert → jaune → orange → rouge de 6° à 150° (`SpaceWarpSpectrum.SPECTRUM_START / END`), puis rouge assombri
  jusqu'à 180° ; éclat 0,8 ; bandes `1 + 0,12 sin(12 θ − 0,35 t)` qui défilent vers l'arrière. Source dans le plan
  tangent au point de fuite : noyau 0,7°, halos 2,5° / 6° / 13° / 26° et anneaux 9,5° / 19°, additifs, pulsation
  `± 7 %` ; deux flares à 4 branches (16° et 10°, trait net et flou superposés) tournant à +0,9° et −1,4° par tick ;
  240 particules blanches (modèle du tunnel de T23). Brouillard : couleur du dégradé dans le regard × 0,85, mélangée
  vers le blanc des flashs (hors fluides, C23).

## 9. Surcouche de Space Knowledge, lien et cible de Space Pilot (lot 2, T18 / T19 ; lot 3, T33, T43)

Depuis T33 (D25, C75), la surcouche (anneaux, orbites, étoiles, nom et distance au regard) est dessinée par **Space
Knowledge** (`SpaceKnowledgeSkyRenderer`) ; le **lien vers le référentiel** et l'anneau du référentiel le sont par
**Space Pilot**, seulement pendant le pilotage (`SpaceReferenceLinkRenderer`, voir plus bas). Rayon et couleur des
anneaux de corps sont écrits une seule fois (`SpaceOverlay.bodyRing`, `SpaceOverlay.kindColor`).

La surcouche est calculée dans le repère du **ciel** (`SkyRenderer`, même projection que `SpaceSkyRenderer`, § 4) à
partir de `pose(partialTick)` et `bodyPositions` du miroir de l'Environment :

- **Anneau d'un corps** : centré sur la direction du corps, rayon apparent = `max(α_corps · 1,6, α_min)` où `α_min`
  donne une taille **constante à l'écran** (≈ 10 px, à calculer depuis le FOV et la hauteur de la fenêtre) ; épaisseur
  constante à l'écran. Étoiles de fond : double anneau (C46).
- **Orbite** : ellipse du corps autour de la position **actuelle** de son parent, échantillonnée (≈ 128 points,
  tableau statique) dans son plan (§ 3), chaque point projeté sur la sphère du ciel ; un segment vu sous plus de 3° est
  subdivisé (anomalie excentrique, puis arc de grand cercle en cordes de 3° au plus) ; seul un segment qui traverse
  l'œil (directions quasi opposées) est coupé (T18 : couper au-delà d'un angle faisait clignoter l'orbite proche) ;
  orbite de moins de 3 à 6 px : fondu. Précision : calculer en `double` relatif à la pose,
  convertir en `float` après normalisation. **Découpe par les anneaux** (T18, demande de l'utilisatrice) : l'orbite
  n'est pas dessinée dans le cercle de l'anneau de **son propre corps** (rayon `max(α · 1,6, α_min)`, appliqué même si
  l'anneau n'est pas dessiné : `bodies` coupé) ; elle s'arrête net à son bord, des deux côtés. Dans le cercle de
  l'anneau d'un **autre** corps dont l'anneau est dessiné (`bodies` actif et corps repéré), elle est en **pointillés**
  (tirets de 4 px, espaces de 7 px, longueur angulaire constante à l'écran ; chaque traversée commence par un tiret
  au bord du cercle, dans le sens de parcours de l'orbite) ; trait plein ailleurs. Découpe exacte : un point
  `P(t) = A + t (B − A)` d'une corde est dans le cercle de centre `w` et de rayon `ρ` si `P·w ≥ cos ρ · |P|` ; les
  bornes sont les racines de `(P·w)² − cos² ρ · |P|²` et de `P·w` dans `]0, 1[` (calcul en `double`), chaque
  morceau est classé à son milieu ; seuls les corps dont le cercle élargi de 3° contient le milieu de la corde sont
  testés. Bornes continues avec la pose : pas de clignotement.
- **Couleurs** (D18) : planètes et objets mineurs bleu `0x4FA8FF`, étoiles du système jaune `0xFFD84A`, lunes gris
  `0xB8B8B8`, étoiles de fond double cercle blanc ; orbite et nom de la couleur de l'objet.
- **Nom au regard** : affiché si l'angle entre le regard de la caméra et la direction de l'objet ≤ `nameAngle`, pour
  l'objet le plus proche du centre seulement ; texte à côté de l'anneau, face caméra. **Distance** (T33, réglage
  `distance`) : sur une seconde ligne juste sous le nom, même couleur ; corps : distance à la **surface**
  (`‖P − s‖ − R`, `SpaceFormat.distance` : km, ou UA au-delà de 0,01 UA) ; étoile extérieure : distance entre les
  systèmes en al (`SpaceFormat.lightYears`) ; arrondie à trois chiffres significatifs, texte reconstruit seulement
  quand cette valeur arrondie (ou l'unité) change.
- **Visibilité** (D15, C47, D30) : étoiles et planètes toujours ; le référentiel toujours ; objet mineur si
  `‖s − P_objet‖ ≤ 0,5 UA`, fondu (alpha) sur les derniers 20 % ; **lune** (d'une planète ou d'un objet mineur,
  T40) selon la séparation angulaire de son orbite vue du monde, `θ = atan(a / d_parent)` (`a` demi-grand axe en km,
  `d_parent = ‖s − P_parent‖`), convertie en pixels de référence `px = tan θ / pixelTan = a / (d_parent · pixelTan)`
  (`SpaceOverlay.pixelTan` : FOV et longue-vue comptent) : masquée jusqu'à **24 px** (deux anneaux minimaux de 10 px
  côte à côte + marge), fondu linéaire de 24 à **36 px**, pleine au-delà (`MOON_MIN_PX`, `MOON_FULL_PX`) ; monde au
  centre du parent : repérée. Anneau, orbite et nom de la lune suivent ce fondu ; calculé une fois par frame, sans
  allocation. Remplace le seuil `10 · a_max_lunes(parent)` de C47. Apparition (FOV 70, fenêtre ≥ 1080 px de haut,
  sans longue-vue ; début du fondu / pleine) : Callisto 60,5 / 40,3 M km, Ganymède 34,4 / 22,9, Europe 21,6 / 14,4,
  Io 13,6 / 9,0 ; Lune 12,4 / 8,2 M km ; Charon 0,63 / 0,42 M km ; longue-vue (FOV × 0,1) : × 11,4 (Callisto dès
  4,6 UA). La séparation réelle à l'écran dépend de la phase (une lune devant ou derrière son parent le recouvre) :
  `a / d` en est le maximum.
- **Lien vers le référentiel** (T19, refait en T33) : arc pointillé de grand cercle entre le cercle avant (axe avant
  `F_W`) et la direction du référentiel ; tirets de longueur angulaire constante à l'écran. **Depuis T33** :
  `SpaceReferenceLinkRenderer` de **Space Pilot**, un `ViewRenderer` en projection `WORLD` (et non plus le ciel :
  devant le terrain, comme le joystick), sphère de 1,5 bloc, seulement pour le pilote local
  (`SpacePilotPad.localPilot() != null`), réglage `refLink`, hors voyage et distorsion ; le lien n'est tracé qu'en
  première personne (en F5 le cercle avant entoure l'œil du joueur, pas la caméra).
  - **Départ** : exactement sur le **bord extérieur** du cercle avant, sans écart : rayon
    `ρ = atan((R_F + e) / 2)` (`SpaceJoystickForward.RADIUS`, `DISTANCE` = 2, `SpaceJoystickRenderer.THICKNESS` = e).
    Le cercle avant est un objet `Local` dessiné en projection **`STABLE`** (FOV des options) alors que le lien est en
    **`WORLD`** (FOV effectif : vol créatif × 1,1, Rapidité, longue-vue, `fovEffectScale`) : dans l'espace vue
    (`ViewRenderContext.cameraRotation`), une direction `v` dessinée en `STABLE` tombe à l'écran là où tombe `M v` en
    `WORLD`, `M = diag(k, k, 1)`, `k = m11_stable / m11_world` (`m11_world = projection().m11()`,
    `m11_stable = 1 / tan(configuredFov / 2)`). `M` est linéaire : un grand cercle reste un grand cercle. Centre vu du
    cercle avant `F' = normalize(M F)` ; l'arc `F' → R` est l'image de l'arc `F → M⁻¹ R`, qui coupe le cône du cercle
    avant en `c = cos ρ · F + sin ρ · T` (`T` tangente en `F` vers `M⁻¹ R`) ; départ `normalize(M c)`, à l'angle
    `∠(F', M c)` de `F'` sur l'arc. Vérifié par banc (FOV des options, × 1,1, × 0,1, × 1,3, × 1,27) : écart au bord vu
    du cercle et à la droite `F' → R` ≤ 0,0001 px. Le bobbing est ignoré (joueur immobile aux commandes).
  - **Arrivée** : exactement sur le **bord extérieur** de l'anneau du référentiel (`ρ_anneau + demi-épaisseur`), sans
    écart (l'écart de 3 px de T19 est supprimé). Space Pilot dessine lui-même cet anneau dans le même renderer (devant
    le terrain), même rayon (`SpaceOverlay.bodyRing`) et même couleur (`kindColor`) que celui de Space Knowledge : les
    deux se confondent quand les deux modules sont actifs.
  - Inchangé : arc le plus court, tirets de 6 px, espaces de 4 px, un tiret part du bord du cercle avant (motif fixe
    par rapport à l'axe avant), dernier tiret coupé au bord de l'anneau, épaisseur 1,5 px, opacité 0,8, couleur du
    référentiel (D18) ; rien si l'axe avant est dans l'anneau ou si les deux directions sont quasi opposées. Plus de
    dépendance à un interrupteur général (C44 remplacée par C75).
- **Cible du vaisseau** (T43, D33, C125–C127) : `SpaceTargetRenderer` de Space Pilot, même projection, même sphère
  et mêmes conditions que le lien (pilote local aux commandes, hors voyage et distorsion), sans dépendre de `refLink`
  ni de Space Knowledge. Direction : corps du système courant (système entier : étoile principale) depuis
  `pose(partialTick)` et `bodyPositions`, sinon étoile de fond du système ciblé (comme le saut). **Anneau** vert
  `0x40FF40` au rayon de `SpaceOverlay.bodyRing` (épaisseur 2 px : il recouvre celui du référentiel et de Space
  Knowledge), ou de 12 px autour d'une étoile de fond (au moins 0,5°). **Flèche** (première personne) : même
  conversion `STABLE → WORLD` que le départ du lien ; `T` tangente en `F` vers la préimage `M⁻¹ R` ; flèche masquée
  si `∠(F, M⁻¹ R) ≤ ρ` (cible dans le cône du bord extérieur du cercle avant) ; base `normalize(M c)`,
  `c = cos ρ · F + sin ρ · T`, puis triangle dans le plan tangent en la base : direction « extérieure » = tangente du
  grand cercle `F' → base`, base à 1 px au-delà du bord, pointe 8 px plus loin, largeur 9 px (pixels de référence,
  `SpaceOverlay.pixelTan`). Deux instances enregistrées autour du lien : flèche avant (le lien passe dessus), anneau
  après (il recouvre l'anneau du référentiel).
- **Roue de charge** (T22) : anneau autour de la direction de l'étoile cible, rempli de `0` à `2π` selon
  `ticks / 60` (interpolé par `partialTick`), taille constante à l'écran. Réalisée en T22 dans un `ViewRenderer`
  (`SpaceJumpRenderer`, projection du monde : même FOV que le ciel, mais devant le terrain) : directions calculées
  comme ici (`pose(partialTick)`, positions figées du saut), puis posées sur une sphère de 1,5 bloc dans un repère
  orienté comme le monde ; rayon 20 px (au moins 5 px au-delà du double anneau de la cible), épaisseur 3 px, remplie
  depuis le haut de l'écran dans le sens horaire, reste du tour pâle.

## 10. Rotation propre (lot 3, T28)

Données : `SpaceRotation` du corps ([`03`](03-modele-donnees.md) § 2) — période sidérale `P` (heures, signée, `0` =
synchrone), obliquité `ε`, longitude du nœud `Ω`, phase `φ` (degrés). Calcul : `SpaceSpin` (`StrictMath`, produits
de quaternions de `SpaceRotations`, sans JOML ni `fma` : bit à bit identique serveur / clients, vérifié par le banc
T28 sur les 62 corps à plusieurs instants).

**Orientation** du corps au temps simulé `t` (jours, celui des orbites, § 3), quaternion `q_B` qui envoie les axes
locaux du corps dans ceux du système (`v_S = q_B · v_B`) :

```
q_B   = R_ref · Ry(−Ω) · Rx(ε) · Ry(W)
R_ref = Ry(−yaw) · Rx(pitch) · Rz(roll) de l'orbite (meme R_o qu'au § 3) ; identite pour un corps sans parent
W     = 2π · frac(frac(24 t / P) + φ / 360)          P ≠ 0 (P < 0 : W decroit, retrograde)
W     = atan2(−d_z, d_x) + φ                         P = 0 (synchrone), d = direction corps → parent dans le repere
                                                     du pole : d = Rx(−ε) · Ry(Ω) · (−x_O, 0, −z_O)
```

- **Axes locaux du corps** : `+Y` = pôle (nord pour `P > 0`), `+X` = méridien origine, équateur dans le plan local
  `XZ`. Pour une texture équirectangulaire (T30) : latitude `asin(y)`, longitude 0 sur `+X`, croissante dans le sens de
  la rotation directe (`Ry(W) · (1, 0, 0) = (cos W, 0, −sin W)` : de `+X` vers `−Z`).
- **Pôle** : `Ry(−Ω) · (0, cos ε, sin ε)` dans le repère de référence, c'est-à-dire incliné de `ε` depuis `+Y` vers
  la longitude `Ω + 90°` (comptée de `+X` vers `+Z`). `(ε, Ω, P)` ≡ `(180 − ε, Ω + 180, −P)` (même axe, même sens).
- **Synchrone** : `(x_O, 0, z_O)` = position sur l'orbite dans son repère (`x_O = cos E − e`, `z_O = √(1 − e²) sin E`,
  Kepler du § 3, demi-grand axe 1 : seule la direction compte). L'axe `+X` vise le parent à chaque instant (projeté sur
  l'équateur si `ε ≠ 0` : écart ≤ `ε`, Lune 6,68°) ; sur une orbite excentrique, la libration en longitude vient
  d'elle-même. Direction indéfinie (parent sur l'axe du pôle) : `W = φ`. Sans parent : pas de rotation (`W = φ`).
- `frac` à chaque terme : la précision ne se dégrade pas avec la durée de la partie (même règle que les orbites).
- **Variantes** : `SpaceSpin.orientation(corps, t, dest)` et `orientations(système, t, dest[])` (tableau de
  `newOrientationArray()`, une fois) n'allouent rien (banc : 816 o pour 340 000 orientations, bruit de mesure) ;
  `orientation(corps, t)` rend un quaternion neuf (hors chemins par frame) ; `spinAngle(corps, t)` rend `W`.
- **Convention et limite** : le haut `+Y` du repère de référence sert de « nord » (C14 : `+Y` = pôle nord céleste),
  si bien qu'un corps de période positive tourne comme la Terre vu de son pôle nord (la texture défile vers l'est).
  Les orbites du modèle, elles, vont de `+X` vers `+Z` (sens horaire vu de `+Y`, héritage du lot 1) : une rotation
  « directe » est donc de sens opposé au parcours de l'orbite. Invisible en pratique (le ciel n'a pas de haut, le
  pilote roule) ; la rotation synchrone n'en dépend pas.
- **Nœuds des planètes de Sol** (annexe) : `Ω = λ_pôle − 90° − ϖ`, où `λ_pôle` est la longitude écliptique du pôle
  nord IAU et `ϖ` le `yaw` de l'orbite (Soleil, sans parent : `Ω = λ_pôle − 90°`) ; l'inclinaison de l'orbite (quelques
  degrés) est négligée. Banc : pôle de la Terre à 23,4400° de `+Y`.

## 11. Vitesse de croisière (lot 3, T34)

Calculateur du HUD de Space Pilot (D25, C76) : `SpaceCruise` (fonctions pures, sans allocation), appelé chaque tick
client par `SpacePilotModule` ; rien n'est simulé ni envoyé.

**Cible** (`SpaceCruise.pickTarget`) : corps du système courant le plus proche du centre de l'écran. Regard de la
caméra `l_W` (axes du monde) ramené dans les axes du système, `l_S = Q · l_W` (pose `pose(1)`, § 2) ; pour chaque
corps, au même temps (`bodyPositions(1)`) :

```
theta_i = max(0, angle(l_S, p_i − s) − alpha_i)        alpha_i = asin(min(1, R_i / |p_i − s|))   (degres)
```

Viser le disque compte comme viser le centre ; à écart égal (deux disques sous le centre), le plus proche du monde
gagne (il occulte l'autre). Retenu si `theta ≤ 10°` ; **hystérésis** de 1° : la cible courante reste tant que son écart
ne dépasse pas 11° et n'est remplacée que par un corps plus près du centre d'au moins 1° (ou plus proche quand les deux
disques couvrent le centre). Aucune cible → partie droite de la ligne de vitesse masquée, « Cible : aucune ». Pas de
cible pendant la charge ou le voyage d'un saut.

**Distances** : `d = |p − s| − R`, distance à la surface de la cible au temps de la vitesse lue (`pose(1)`,
`bodyPositions(1)` : `speed()` est celle du dernier tick simulé, environ un tick après `pose(0)`) ; marge
`m = (0,01 + 2) · R = 2,01 R` (plancher de D9 plus zone de freinage d'approche, § 5 point 3 ; Lune 3 492 km, Terre
12 806 km) ; plancher `h_min = 0,01 R`. `d ≤ m` → `v* = 0`.

**Vitesse cible** (retouche T34, après essai en jeu) : la consigne s'adresse à un pilote **humain** qui suit la flèche
avec le **frein normal** (Espace seul, ou gaz opposé : `k = 0,92` par tick, `S(V)` = `SpaceMotion.stoppingDistance(V,
false)`, `S(V) ≤ c · V`, `c = k / (1 − k) = 11,5`) et un **temps de réaction** ; le frein rapide (Sprint + Espace)
n'est plus qu'un rattrapage. `v*` est la plus grande vitesse `V ∈ [0, V_max]` (relative au référentiel) qui respecte :

```
nominale : g_c(V) = (T_c + 1) · V + S(V)                        ≤ d − m       T_c = 6 ticks (300 ms)
garantie : g_a(V) = n_1 + … + n_(T_a+1) + S(n_(T_a+1))          ≤ d − h_min   T_a = 10 ticks (500 ms)
           n_0 = V,  n_i = min(V_max, max(1, a_b · n_(i−1))),  a_b = 1,157625
```

- **Nominale** : la flèche passe à la baisse, le pilote freine après `T_c` ticks de réaction (plus le tick de
  décision) passés à vitesse constante, et s'arrête à la marge.
- **Garantie** : même s'il continue d'**accélérer avec le boost** pendant `T_a + 1` ticks (c'est le cas courant : la
  consigne est croisée pendant l'accélération), puis freine normalement, il n'atteint pas le plancher ; il entame au
  pire la marge, où le freinage d'approche agit.

`g_c` et `g_a` sont croissantes : dichotomie (64 pas au plus) sur les fonctions exactes (`SpaceCruise.nominalDistance`,
`guaranteedDistance`). **Forme close** (sans plafond) :

```
v* ≈ min( (d − m) / 18,5 ,  (d − h_min) / 86,9 )
18,5 = T_c + 1 + c        86,9 = a_b (a_b¹¹ − 1) / (a_b − 1) + a_b¹¹ · c        (a_b¹¹ = 5,003)
```

(à moins de 5,4 % de la valeur exacte au-delà de 10 km/tick). Loin de la cible, la garantie domine ; la nominale ne
prend le relais qu'à moins d'environ 2,5 R de la surface. `v*` est **proportionnelle à la distance** : elle baisse à
mesure qu'on approche, la flèche passe à la baisse au bon moment, et suivre `v*` en freinage normal (frein sur `↓`,
roue libre sur `↑` vert) amène à l'arrêt juste avant la marge. La garantie coûte du temps : suivre une consigne
proportionnelle à la distance donne une approche exponentielle de constante ≈ 87 ticks (durée ≈ `87 · ln(v_croisement)`
ticks), d'où des trajets environ deux fois plus longs que l'ancienne consigne, qui elle finissait au plancher.

**Diagnostic de l'ancienne consigne** (`S_b(v*)` au frein rapide depuis le tick suivant ; banc jetable T34 bis, pilote
simulé qui suit la flèche avec un retard de 0, 3, 6 ou 10 ticks) : elle était dépassée **systématiquement**.
- (a) Frein normal au lieu du frein rapide : la distance d'arrêt est 3,3 fois plus longue (`11,5 V` au lieu de
  `3,52 V`). Terre → Lune, sans retard, gaz normal : arrêt **86 000 km sous la surface** (1D, sans garde-fou).
- (b) Temps de réaction : le gaz est multiplicatif ; pendant 6 ticks de réaction, le boost multiplie la vitesse par
  2,4 (10 ticks : 4,3). Frein rapide, 6 ticks de retard : 91 000 km sous la surface (gaz normal), 476 000 km (gaz +
  boost) ; frein normal et boost, 6 ticks : 1 100 000 km. Avec le vrai `SpaceMotion.step`, ces dépassements finissent
  tous au plancher (17 km pour la Lune), arrêtés par le freinage d'approche et le garde-fou, ou en longeant l'astre.
- (c) Chemin client : unités (km/tick partout, × 20 seulement à l'affichage), signe, repère (positions et pose au même
  temps d'affichage, repère système) corrects ; seul écart : `speed()` est celle du dernier tick simulé alors que
  `pose(0)` a environ un tick de retard (distance surestimée de `V`) : la consigne lit désormais `pose(1)`.
- (d) Freinage d'approche et bascule du référentiel : sans effet sur le dépassement (la marge est hors de la zone de
  freinage d'approche) ; voir aussi la vitesse relative ci-dessous.

**Vitesse relative au référentiel et interception** (T39, C118 ; remplace C104 et l'éloignement de C116) : `V`
(affichée et comparée à `v*`) est relative au référentiel, qui entraîne le monde (D7), et depuis D29 le référentiel ne
bascule sur la cible qu'à l'entrée de sa sphère d'influence. Le **mouvement complet** de la cible relatif au mouvement
d'entraînement du monde est donc compté, radial **et** transverse : `u = Δp_cible − Δp_frame` sur un tick (positions
un tick plus tard à l'échelle de temps courante ; `Δp_frame` = déplacement du référentiel, ou mélange des deux pendant
le fondu d'une bascule, comme le pas : la consigne ne saute pas quand la cible devient le référentiel ; `u = 0` pour
une cible référentiel hors fondu, consigne de T34 inchangée). Vitesse conseillée **`w = u + c · n`**
(`SpaceCruise.interceptSpeed`), `n` = direction monde → cible, `c` = la vitesse d'approche sûre ci-dessus pour la
distance à la surface ; `v* = min(|w|, V_max)` et **direction à viser `w / |w|`** : en la suivant à cette vitesse, le
monde se rapproche de la cible à `c` exactement (même sûreté que T34) tout en l'accompagnant, donc il entre dans sa
sphère et y reste le délai de bascule. **Point d'interception** : cette direction, dessinée par `SpaceJumpRenderer`
(petit anneau bleu pâle `0x8FD8FF`, 5 px, opacité 0,7) quand elle s'écarte du disque de la cible de plus de 0,5°
(`SpaceCruise.interceptShown`) ; coupé avec la croisière. Le pilote vise ce point : la cible est gardée tant que le
regard reste à moins de 11° du point (hystérésis de `pickTarget` appliquée au point d'interception), même loin de la
cible. Avance mesurée au banc : 9° pour la Lune, 20° pour Vénus, 67° pour Mars, jusqu'à 180° pour une lune quand le
monde part tout près d'elle (`c < |u|` : il faut d'abord voler dans son sens de marche).

Banc T39 (pilote qui suit la consigne et vise le point, réaction 6 ticks ; un pilote brutal, réaction 10 ticks et
boost) : Lune, Mars, Vénus, Mercure, Jupiter, Saturne, Neptune, Pluton, Cérès, Terre (depuis la Lune et Mars) atteints
en 24 à 81 s, arrêt à ≈ 1,97 R ; lunes (rayons de jeu, § 5 point 0) : Io, Europe, Ganymède, Callisto, Rhéa, Titan,
Charon atteintes depuis leur planète ou une lune voisine en 15 à 37 s ; jamais sous le plancher de D9. Avec
l'éloignement seul (C116), mêmes résultats sauf Phobos jamais captée et Déimos captée sans arrêt. Avec l'interception,
**Phobos et Déimos sont captées mais pas tenues** : le monde en ressort pendant le fondu. Limite physique : Phobos fait
le tour de Mars en 77 ticks (sa direction de marche tourne de 4,7°/tick, plus vite que le lacet du vaisseau,
1,5°/tick) à 770 km/tick pour une sphère de 451 km ; une erreur de vitesse de 5 % (38 km/tick) la fait quitter en
quelques ticks (Déimos : 1,2°/tick, 486 km/tick, sphère de 248 km). Restent ignorés : l'écart entre l'axe avant et la
direction conseillée (la consigne suppose qu'on la vise), les corps sur le chemin (garde-fou, § 5 point 4).

**Couleur** (`SpaceCruise.redness`, C76) : écart relatif `e = |v − v*| / v*` ; vert (`0x55FF55`) si `e ≤ 10 %`, rouge
(`0xFF5555`) si `e ≥ 30 %`, dégradé linéaire entre, quantifié en 8 paliers (le texte n'est reconstruit qu'au changement
de palier) ; pour `v* = 0`, vert seulement à l'arrêt. **Flèche** : `↑` si `v < v*` (ou `v = v* > 0`), `↓` sinon ;
valeurs comparées après arrondi à trois chiffres (celles qui sont affichées).

**Exemple numérique Terre → Lune** (`V_max = 10⁷` km/tick, distance moyenne 384 400 km, départ au plancher de la
Terre, 64 km) : `d = 384 400 − 6 371 − 64 − 1 737,4 = 376 228` km, `m = 3 492` km, `h_min = 17` km.

| `d` (km) | `v*` (km/tick) | `v*` affichée (arrondie en km/tick, × 20) | forme close | nominale seule `(d − m) / 18,5` |
|---|---|---|---|---|
| 376 228 | 4 328 | 86 600 km/s | 4 329 | 20 148 |
| 100 000 | 1 150 | 23 000 km/s | 1 151 | 5 217 |
| 20 000 | 230 | 4 600 km/s | 230 | 892 |
| 10 000 | 115 | 2 300 km/s | 115 | 352 |
| 5 000 | 57,5 | 1 150 km/s | 57,3 | 81,5 |
| 3 600 | 6,5 | 129 km/s | 5,8 | 5,8 |

(Ancienne consigne au départ : 71 259 km/tick, 16 fois plus.) **Banc d'acceptation** (jetable, `SpaceMotion.step` réel,
Sol par défaut, orientation tenue vers la cible ; pilote qui suit la flèche avec un retard de 0, 6 ou 10 ticks, frein
normal sur `↓`, sur `↑` gaz (règle stricte) ou gaz seulement si `v < 0,9 v*` (règle « zone verte »), gaz avec ou sans
boost ; Terre → Lune (`V_max` 10⁷ et 10⁵), Terre → Mars, Terre → Jupiter (`V_max` 10⁹), Terre → Soleil, Lune →
Terre ; orbites animées à 3 600 s/tick et figées ; 48 trajets par retard) :

| retard | nouvelle : au plancher | entament la marge | écart moyen arrêt / marge | durée moyenne | ancienne : au plancher | durée moyenne |
|---|---|---|---|---|---|---|
| 0 tick | 0 | 0 | 0,00 R | 38,8 s | 42 / 48 | 17,2 s |
| 6 ticks | 0 | 48 | 0,20 R | 33,4 s | 46 / 48 | 17,8 s |
| 10 ticks | 0 | 48 | 0,65 R | 29,1 s | 48 / 48 | 18,1 s |

Terre → Lune (orbites animées, règle zone verte, frein normal) : sans retard 629 ticks (31,5 s) gaz normal, 526 (26,3
s) gaz + boost, arrêt à 3 499 km ; 6 ticks de retard, gaz + boost : 387 ticks (19,4 s), arrêt à 2 648 km ; 10 ticks :
270 ticks (13,5 s), arrêt à 1 774 km ; l'ancienne consigne finissait au plancher (17 km) en 196 à 330 ticks. Banc 1D
(sans garde-fou, 432 cas : 6 cibles, `V_max` 10⁵ à 10⁹, retards 0 / 6 / 10, règles, gaz et frein normal ou rapide) :
aucun arrêt sous la surface. Phobos (période de 7,6 ticks à 3 600 s/tick) n'est pas une cible praticable, ni avant ni
après.

Réglages : `SpaceCruise.REACTION_TICKS` (6) et `WORST_REACTION_TICKS` (10). Avec `T_a = 6`, les trajets sont plus
courts (≈ 30 %) mais un pilote à 10 ticks de réaction qui accélère au boost dépasse la surface (banc 1D : 44 cas sur
432).
