# 04 — Mathématiques

Package `modules/dimension/space/math/` (T02) pour les §§ 1 à 6 ; le § 7 est implémenté par le Configurator (T11,
T12). Calculs en `double` (JOML `Vector3d`, `Quaterniond`, `Matrix3d` — JOML est fourni par Minecraft), conversion en
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

Temps simulé en jours : `t = (gameTime + partialTick) × échelle / 86 400`, avec `échelle` = secondes simulées par tick
(réglage de l'Environment, défaut 3 600 : un tick = une heure, une année terrestre ≈ 7 min 18 s). `gameTime` est
`level.getGameTime()` des deux côtés (synchronisé par vanilla) : le calcul est **déterministe**, rien n'est envoyé
pour animer les orbites.

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
- **Taille minimale** : si `α < 0,12°`, dessiner un point de taille `0,12°` dont l'éclat décroît avec `α` (une
  planète lointaine reste visible comme un point).
- **Étoiles de fond** : quads de taille angulaire fixe (≈ 0,15° à 0,4° selon l'éclat) à 110 blocs ; éclat
  `∝ luminosité / distance²` (al), en échelle logarithmique bornée.
- **Fond** : sphère (ou cube) opaque noire à 120 blocs, dessinée en premier (sous le plan lointain, ≥ 128).

## 5. Mouvement du monde (`SpaceMotion`, un pas par tick)

État : pose `(s, Q)`, vitesse nominale `V` (km/tick). Entrée du pilote `SpacePilotInput` : `throttle ∈ {−1, 0, 1}`,
`roll ∈ {−1, 0, 1}`, `yawRate, pitchRate ∈ [−1, 1]`, `brake` (booléen, touche Saut), visée `aimYaw, aimPitch` (écart du regard au cercle avant, octets signés, 1 = 30/127°,
`aimPitch > 0` = au-dessus). Réglages : `V_max` (défaut 10⁷ km/tick), `turn` (défaut
1,5 °/tick), `rollSpeed` (défaut 2 °/tick).

0. **Référentiel** (D7, règle de choix D8) : la vitesse `V` est relative à un **corps de référence** `ref` (état de
   l'Environment, synchronisé et sauvegardé, id de corps stable). Choix **une seule fois, au début du pas**, positions au temps `t` (état `SpaceFrameState` mis à jour en place) :
   - **Score** de chaque corps `S_i = P_i · G_i` :
     `P_i = 1 / max(h_i, 1 km)` (proximité, `h_i` = altitude) ;
     `G_i = 1 / (1 + (θ_i / θ₀)²)`, `θ₀ = 20°` (regard ; 10° jugé trop sélectif), où `θ_i = max(0, angle(a, w_i) − α_i)` : angle entre la
     **direction de visée** `a` et la direction du corps `w_i`, moins son rayon angulaire `α_i` (viser n'importe où
     sur le disque compte comme viser le centre). `a` = regard du pilote `(ψ_F + aimYaw, θ_F − aimPitch)` tourné par `Q`, ou l'axe avant du
     monde sans pilote (`ZERO`) ; le regard propre d'un joueur qui ne pilote pas ne compte pas. Rayon angulaire non
     plafonné dans le score ; égalité → premier corps. Très près d'un corps, `P` domine (il gagne même hors du
     regard) ; loin de tout, les `P` sont proches et `G` départage (le corps visé gagne).
   - **Bascule** : `c = argmax S`. Si `c ≠ ref` et `S_c > 1,25 · S_ref` pendant **10 ticks consécutifs** (délai,
     0,5 s), et si **60 ticks** (cooldown, 3 s) se sont écoulés depuis la bascule précédente → `ref = c`. `ref` absent
     (supprimé, système changé, aucun) → `c` immédiatement, sans délai ni fondu.
   - **Fondu** : à la bascule de `A` vers `B`, le mouvement d'entraînement passe de `A` à `B` en **40 ticks** (2 s) :
     `Δp_frame = (1 − σ) · Δp_A + σ · Δp_B`, `σ = smoothstep(ticks depuis la bascule / 40)` (`σ = 0` au pas de la bascule). Le changement ne
     modifie ni `s` ni `V`. Pendant le fondu, le freinage d'approche du référentiel utilise aussi la règle `min`.
   - Constantes réglables dans `SpaceMotion` (θ₀, 1,25, 10, 60, 40). État à synchroniser et sauver : `ref`, `ref`
     précédent, ticks depuis la bascule, candidat et ticks de candidature.
1. **Gaz** (vitesse **signée**, `V ∈ [−V_max, V_max]` ; `V < 0` = le monde part en arrière ; D6) :
   - `brake` (Espace maintenu) **prime** sur `throttle` : `V = V · 0,92`, et `V = 0` quand `|V| < 1` km/tick ;
   - sinon `throttle = s ≠ 0` : si `V` est de signe opposé à `s`, `V = V · 0,92` (et `0` sous 1 km/tick) ; sinon
     `V = s · min(V_max, max(1, |V| · 1,05))` (accélère dans le sens `s`, en partant de 1 km/tick) ;
   - pas de touche : `V` inchangée (le monde garde son élan). `|V|` est bornée à `V_max` à chaque pas.
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
   `tFin` = `settings.days(T, 0)` / `settings.days(T + 1, 0)` : passer les deux évite une dérive d'arrondi ; `V`
   bornée à `V_max` à chaque pas). Le serveur et les clients l'appellent avec les mêmes entrées
   ([`05`](05-synchronisation.md)).

Pose par défaut d'un système (`setCurrentSystem`, réinitialisation ; `SpaceMotion.defaultPose(système, t, réglages,
dest)`) : sur la droite parent → planète de départ, à `1,02 ×` son demi-grand axe (sans planète : 1 UA sur `+X`), hors de tout corps (altitude ≥ 20 rayons), avant du monde tourné vers l'étoile principale, `V = 0` ; référentiel =
meilleur score avec la visée sur l'axe avant (Terre pour Sol). **Planète de départ** (T16) : la `PLANET` dont le
demi-grand axe `a` est le plus proche de 1 UA en échelle logarithmique (`|ln a|` minimal, `StrictMath`, la première en
cas d'égalité) — la Terre dans Sol, indépendamment de son rang et de son nom ; dans un système voisin, la planète la
plus « tempérée » (Proxima b, Barnard e, Lalande 21185 c, Epsilon Eridani b, Tau Ceti f) ; objets mineurs et lunes
jamais retenus.

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

## 7. Cube du Configurator (T11, T12)

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
| `TRAVEL` | `N = max(20, ceil(20 · D / v_w))` ticks | pose figée dans le système d'origine (référentiel inchangé, `V = 0` ; rien n'est dessiné que la distorsion) ; compteur borné à `N` ; à `N`, **serveur seul** : arrivée |

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
  réglage, défaut 0,5) ; 20 ticks par seconde. Au moins 20 ticks. Sol → Alpha du Centaure (`D` = 4,36999995 al avec la
  cible en `float`) : 175 ticks. Durée totale d'un saut : 60 + `N` ticks.
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

## 9. Surcouche de Space Pilot (lot 2, T18 / T19)

Tout est calculé dans le repère du **ciel** (`SkyRenderer`, même projection que `SpaceSkyRenderer`, § 4) à partir de
`pose(partialTick)` et `bodyPositions` du miroir de l'Environment :

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
  l'objet le plus proche du centre seulement ; texte à côté de l'anneau, face caméra.
- **Visibilité** (D15, C47) : étoiles et planètes toujours ; lune si `‖s − P_parent‖ ≤ 10 · a_max_lunes(parent)` ;
  objet mineur si `‖s − P_objet‖ ≤ 0,5 UA` ; fondu (alpha) sur les derniers 20 % ; le référentiel toujours.
- **Lien vers le référentiel** (T19) : segment pointillé sur la sphère du ciel (grand cercle) entre la direction de
  l'axe avant `F_W` (centre du cercle avant) et la direction du référentiel ; tirets de longueur angulaire constante
  à l'écran. Réalisé en T19 dans `SpacePilotSkyRenderer` : le trait part du bord du cercle avant (`atan(R_F / 2)`,
  même sans pilotage) et s'arrête au bord de l'anneau du référentiel (3 px d'écart de chaque côté) ; tirets de 6 px,
  espaces de 4 px, épaisseur 1,5 px, comptés depuis l'axe avant ; couleur du référentiel (D18) ; rien si l'axe avant
  est dans l'anneau ou si les deux directions sont quasi opposées ; coupé par `enabled` faux (C44).
- **Roue de charge** (T22) : anneau autour de la direction de l'étoile cible, rempli de `0` à `2π` selon
  `ticks / 60` (interpolé par `partialTick`), taille constante à l'écran. Réalisée en T22 dans un `ViewRenderer`
  (`SpaceJumpRenderer`, projection du monde : même FOV que le ciel, mais devant le terrain) : directions calculées
  comme ici (`pose(partialTick)`, positions figées du saut), puis posées sur une sphère de 1,5 bloc dans un repère
  orienté comme le monde ; rayon 20 px (au moins 5 px au-delà du double anneau de la cible), épaisseur 3 px, remplie
  depuis le haut de l'écran dans le sens horaire, reste du tour pâle.
