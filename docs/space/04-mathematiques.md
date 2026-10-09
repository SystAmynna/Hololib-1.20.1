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
dest)`) : sur la droite étoile → première planète, à `1,02 ×` son demi-grand axe (sans planète : 1 UA sur `+X`), hors de tout corps (altitude ≥ 20 rayons), avant du monde tourné vers l'étoile principale, `V = 0` ; référentiel =
meilleur score avec la visée sur l'axe avant (Terre pour Sol).

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

L'écart est mesuré dans l'espace des angles (suffisant dans un cône de 30° autour d'un avant horizontal). À la prise
du pilotage, la caméra est ramenée **en douceur** dans le cône (interpolation sur quelques frames), pas d'un coup.

Dessin (T08) : le cercle avant est un objet `Local(false)` en `(x, y, z) = (ψ_F, θ_F, 2)` ; le cercle de visée un
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
`q = p / H_sys` avec `H_sys = nice(1,1 × apoapse max)` en UA. **Loupe des lunes** : les distances lune → planète sont
multipliées par `m = max(1, 0,06 · H_sys / a_min_lune)` (même facteur pour l'affichage et pour la conversion de la
poignée). Rayon affiché d'un corps : `clamp(0,015 + 0,01 · log10(R_km / 1000), 0,012, 0,08)` unités du cube.

**Poignée d'orbite** (`SpaceOrbitHandle`, `HoloEditRotation` + `HoloEditScale`) : posée sur le parent affiché.

```
rotation monde de la poignee   = R · R_o                 R_o = HoloEditMath.rotation(orientation de l'orbite)
echelle de la poignee          = (a_aff · σ, a_aff · σ, b_aff · σ)     a_aff, b_aff : demi-axes affiches (unites du cube)
lecture : R_o = R⁻¹ · R_poignee -> (yaw, pitch, roll) par setRotation d'une instance de HoloEditMath (champ)
          a = scaleX / σ (-> UA, loupe comprise) ; b = scaleZ / σ ; e = √(1 − (b/a)²) borne a [0 ; 0,95], e = 0 si b ≥ a
```

`scaleY` n'a pas de sens : il est réécrit à `scaleX`. Le renderer de la poignée dessine dans son repère local la
courbe `(cos θ − e, 0, sin θ)`, θ ∈ [0, 2π[ ; le pont applique l'échelle `(a, a, b)`, ce qui donne l'ellipse
`(a cos θ − a e, 0, b sin θ)` : parent au foyer, centre décalé de `−a·e` sur `X`. `e` se déduit du rapport
`scaleZ / scaleX` lu dans le transform de l'objet.

**Règles graduées** (T10) : trois règles partent du coin `(-1, 1, 1)` (haut, gauche, avant ; le bas est caché par
la fenêtre) le long de `+X`, `+Y` (comptée depuis le bas) et `−Z` ; chaque étiquette donne la distance depuis le
début de la règle (« 20 al », zéro omis), pas `nice(H / 4)`.

**Mode système, choix de T12** :
- `H_sys` : portée d'un corps = somme des apoapses de sa chaîne de parents (lunes comprises, sans loupe) ; les corps ne
  sont pas masqués hors du cube (une lune agrandie peut dépasser un peu).
- **Loupe des lunes arrondie** à la valeur 1-2-5 la plus proche (échelle log) : avec la formule continue, modifier
  l'orbite de la plus petite lune changeait la loupe dans la même proportion et son ellipse ne bougeait pas à
  l'écran. Pour Sol : ×200.
- Poignée d'orbite → orientation : décomposition yaw / pitch / roll **en double depuis la matrice**, avec blocage de
  cardan traité (roll = 0 si `cos(pitch) < 1e-3`), pas `HoloEditMath.setRotation` (float : jusqu'à 176° d'erreur près
  des orbites polaires ; maintenant ≤ 0,1°). Seules les composantes changées sont recalculées ; `e` conservée sur une
  mise à l'échelle uniforme ; conversion faite avec l'échelle et la loupe d'avant l'édition.
- Rayon affiché en unités du cube (grandit avec le cube). « Animer » : temps d'affichage client seulement ; l'arrêter
  ramène à `t_edit` ; la poignée reste à `t_edit`.
