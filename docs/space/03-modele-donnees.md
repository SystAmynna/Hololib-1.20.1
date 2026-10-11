# 03 — Modèle de données

Package `modules/dimension/space/model/` (T01). Code **commun et pur** : aucune dépendance client, aucun accès au
monde. Seules dépendances : `CompoundTag` / `ListTag` (NBT), `FriendlyByteBuf` (réseau), JOML.

## 1. Unités

| Grandeur | Unité de stockage | Type | Remarque |
|---|---|---|---|
| Position d'un système dans la galaxie | année-lumière (al) | `double` ×3 | repère galactique, axes alignés sur ceux des systèmes |
| Rayon d'un corps | km | `double` | |
| Demi-grand axe d'une orbite | UA | `double` | 1 UA = 149 597 870,7 km |
| Période orbitale | jour simulé | `double` | |
| Position dans un système (pose du monde, corps) | km | `double` | calculée, jamais stockée pour un corps |
| Temps simulé | jour simulé | `double` | `t = t_e + (gameTime − T_e + partialTick) × échelle / 86 400`, époque `(T_e, t_e)` (T38) ; échelle par défaut 360 s/tick ([`04`](04-mathematiques.md) § 3) |
| Vitesse du monde | km par **tick** de jeu | `double` | indépendante de l'échelle de temps des orbites : la conduite ne change pas quand on accélère le temps |
| Couleur | ARGB | `int` | alpha ignoré pour les corps opaques |
| Luminosité d'une étoile | relative au Soleil | `float` | éclat des étoiles de fond |
| Masse d'un corps (T39) | kg | `double` | choix du référentiel seulement (sphères d'influence, [`04`](04-mathematiques.md) § 5) |

Les constantes et conversions sont dans `math/SpaceUnits` (T02).

## 2. Classes

Classes **immuables** (records Java 17) pour tout ce qui circule ; les modifications passent par des méthodes
`withXxx` ou par les opérations du § 6, qui renvoient une nouvelle galaxie ou modifient une galaxie mutable tenue par
l'Environment — au choix de T01, **un seul** des deux styles, documenté dans `model/package-info.java`.
Recommandé : `SpaceGalaxy` mutable (tenue par l'Environment seul, jamais exposée en écriture), éléments immuables.

```
SpaceGalaxy        systemes (ordre d'insertion), prochain id libre
 └─ SpaceSystem    id (int > 0, unique dans la galaxie), nom, position (al), corps (ordre d'insertion), prochain id
     └─ SpaceBody  id (int > 0, unique dans le systeme), kind, nom, parentId (0 = aucun), rayon (km), couleur,
                   luminosite (etoiles), masse (kg, lot 3, T39), orbite (null si parentId == 0), rotation propre,
                   texture (lot 3, T28)
         ├─ SpaceOrbit  demi-grand axe a (UA), excentricite e, orientation (yaw, pitch, roll en degres),
         │              periode (jours), phase a t = 0 (degres)
         ├─ SpaceRotation  (T28) periode siderale signee (heures ; < 0 retrograde ; 0 synchrone), obliquite (0-180),
         │                 longitude du noeud du pole (degres), phase du meridien origine a t = 0 (degres)
         └─ SpaceTextureRef (T28) auto | builtin:<id> | custom:<sha1>
SpaceBodyKind      STAR, PLANET, MOON, MINOR (lot 2, T16 : planete naine / objet mineur ; ajoute EN FIN, ordinal stable)
SpaceLimits        bornes ci-dessous + sanitize
```

- Les ids ne sont **jamais réutilisés** dans leur conteneur (`prochain id` monotone, sauvegardé) : une sélection ou
  une référence ne peut pas désigner par erreur un nouvel objet.
- Un corps sans parent est au **centre du système** (immobile). En pratique : l'étoile principale. Un système peut
  avoir plusieurs étoiles : les suivantes ont pour parent la première (orbite), ce qui fait des binaires simples.
- `MOON` : parent `PLANET` ou (lot 2) `MINOR` ; `PLANET` et `MINOR` : parent `STAR` ; `STAR` : parent aucun ou
  `STAR`. La validation refuse le reste et les cycles.
- L'orientation de l'orbite est un triplet yaw / pitch / roll **avec les conventions de `HoloTransform`** (YXZ,
  `HoloEditMath.rotation`) : c'est exactement ce qu'édite l'outil Rotation sur la poignée d'orbite (T12). Le plan de
  l'orbite est le plan local `XZ`, le périapse sur `+X`, le sens de parcours de `+X` vers `+Z`.
- **Rotation propre** (`SpaceRotation`, T28, C71) : jamais `null` dans un `SpaceBody` (rotation par défaut du type si
  absente — sauf à la lecture d'un ancien NBT d'un corps de Sol, § 4 : étoile 609,12 h comme le Soleil, planète 24 h, lune synchrone, objet mineur 9,074 h comme Cérès ; axe droit,
  phase 0). Le pôle est incliné de l'**obliquité** depuis le haut `+Y` du **repère de référence** — repère de l'orbite
  pour un corps qui a un parent, repère du système sinon — autour de la ligne des nœuds `(cos Ω, 0, sin Ω)` (`Ω` =
  longitude du nœud, comptée de `+X` vers `+Z` comme le `yaw` d'une orbite). Période `> 0` = rotation directe (main
  droite) autour du pôle, `< 0` = rétrograde, `0` = **synchrone** (l'axe local `+X`, méridien origine, vise le parent ;
  sans parent : pas de rotation). `(ε, Ω, P)` et `(180 − ε, Ω + 180, −P)` décrivent la même rotation ; les données par
  défaut suivent la convention IAU des planètes (pôle nord du côté `+Y`, obliquité ≤ 90°, rétrograde par le signe).
  Calcul : [`04`](04-mathematiques.md) § 10.
- **Masse** (T39, D29) : `double` en kg, finie et bornée (§ 3). Elle ne sert **qu'à choisir le référentiel** (sphères
  d'influence : base `a (m / m_parent)^0,4`, au moins 10 rayons du corps ; rayon de jeu = base × 4 pour une lune,
  × 1,5 pour une planète ou un objet mineur, au plus `0,5 a` ; étoile compagne : rayon de base ; [`04`](04-mathematiques.md) § 5 point 0) : aucune
  gravité n'est appliquée au vaisseau et les orbites gardent leur période. `withMass` ; `withRadius` garde la masse. **Masse estimée** (`SpaceBody.estimatedMass`, nouveau
  corps `addBody`, ancien NBT d'un corps inconnu, constructeurs sans masse) : étoile `M☉ · (R / R☉)^1,25` (séquence
  principale), au moins 0,08 M☉ ; sinon `ρ · 4/3 π R³` avec `ρ` = 5 500 kg/m³ (planète rocheuse, jusqu'à 1,6 R⊕),
  1 300 (planète gazeuse au-delà), 2 500 (lune), 2 000 (objet mineur). Exemples : Terre estimée 5,96e24 (réelle
  5,97e24), Mars ×1,40, Neptune ×0,79, Lune ×0,75, Titan ×1,33 ; corps ajoutés par défaut : étoile 1,99e30, planète
  5,96e24, lune 5,49e22, objet mineur 1,81e21 kg.
- **Texture** (`SpaceTextureRef`, T28, D21, C69) : `auto` (défaut de tout corps : choisie à l'affichage), `builtin:<id>`
  (texture intégrée, T29 ; id `[a-z0-9_]{1,32}`), `custom:<sha1>` (texture importée, T45 ; 40 chiffres hexadécimaux,
  gardés en minuscules). Le modèle ne vérifie pas que la texture existe (une référence inconnue s'affiche comme `auto`).
  Résolution (T29, `SpaceBodyTextures.builtinFor`) : `builtin` connue → elle-même ; `auto`, `builtin` inconnue ou
  `custom` sans image chargée → table **nom exact + type** de Sol (C84), sinon catégorie générique (type, rayon,
  température d'équilibre, C87) ; une étoile → texture `star` teintée par sa couleur. Détail et affectations :
  [`donnees-systemes.md`](donnees-systemes.md) § Textures.
  **Texture importée** (T45, D27, C136–C140) : l'empreinte est le SHA-1 du **PNG stocké** par le serveur
  (`<dossier data de la dimension>/hololib_space_textures/<sha1>.png`, PNG RGB ou RGBA, exactement 2:1, 16 à 1024 px
  de large, au plus 2 Mio, validé avant écriture) ; quota de 64 fichiers par dimension. Le modèle reste agnostique
  (aucune vérification d'existence dans `SpaceTextureRef` ni `SpaceGalaxy`) ; c'est le Configurator qui refuse
  `custom:<sha1>` si le fichier n'est pas stocké pour la dimension (« Texture importee absente du serveur »). Un
  fichier supprimé à la main laisse la référence : le corps s'affiche en `auto` (le serveur répond « absente »).

## 3. Bornes (`SpaceLimits`)

| Borne | Valeur | Motif |
|---|---|---|
| Systèmes par galaxie | 256 | taille du bloc froid (en-têtes) |
| Corps par système | 48 (32 au lot 1 ; T16, C51 : Sol en compte 34) | idem |
| Longueur d'un nom | 32 caractères | `HoloTextBuffer.sanitize`-like : retirer contrôles et retours à la ligne |
| Position galactique | ‖p‖ ≤ 100 000 al | |
| Rayon | 1 km à 10⁷ km | |
| Demi-grand axe | 10⁻⁵ (T16 : Phobos) à 10⁴ UA | |
| Excentricité | 0 à 0,95 | ellipse bien formée, Kepler convergent |
| Période | 0,01 à 10⁹ jours (T16 : Proxima autour d'Alpha Cen A) | |
| Vitesse de distorsion (réglage, T15) | 0,01 à 1 000 al/s, défaut 0,5 | |
| Luminosité | 10⁻⁴ à 10⁶ | |
| Masse (T39) | 10¹⁰ à 10³³ kg (finie ; nulle ou négative → refusée à l'édition, bornée à 10¹⁰ au chargement) | Deimos 1,4·10¹⁵ ; ≈ 500 M☉ |
| Période de rotation propre (T28) | 0 (synchrone) ou `0,01 ≤ \|P\| ≤ 10⁶` heures, signe gardé | 36 s à ≈ 114 ans (Vénus : 5 833 h) |
| Obliquité (T28) | 0 à 180° (bornée) | |
| Nœud du pôle, phase de rotation (T28) | ramenés à `[−180, 180[` et `[0, 360[` (comme C13) | |

Les quatre valeurs de la rotation propre sont de plus **ramenées à la précision d'un `float`** (T28) : elles circulent
en `float` (16 o par corps au lieu de 32) avec un aller-retour exact, et serveur et clients calculent l'orientation
(en `double`) à partir des mêmes valeurs.

Toute valeur venant d'un client est **assainie** (finie, bornée, nom nettoyé) avant d'entrer dans la galaxie ; une
valeur non finie fait rejeter l'opération.

## 4. Sauvegarde NBT

Méthodes `CompoundTag save()` (tag neuf, comme `HoloTransform`) / `static load(CompoundTag)` par classe. Clés courtes et stables (renommer une clé casse
les sauvegardes : ne jamais le faire sans migration). Un `load` tolère les clés manquantes (valeurs par défaut) et
ignore les éléments invalides (log + saut) plutôt que de lever : une galaxie corrompue ne doit pas empêcher le
chargement de la dimension.

```
galaxy: { next: int, systems: [ { id, name, x, y, z, next, bodies: [ { id, kind, name, parent, radius, color,
          lum, mass, orbit?: { a, e, yaw, pitch, roll, period, phase }, spin: { period, obl, node, phase }, tex } ] } ] }
```

**Corps (T28)** : `spin` (float ×4 : période en heures, obliquité, nœud, phase en degrés) et `tex` (chaîne `auto`,
`builtin:<id>` ou `custom:<sha1>`), toujours écrits. Lecture **tolérante** :
- `spin` **absent** (corps sauvegardé avant T28) : rotation « **auto** » par nom — si le nom (nettoyé) est **exactement**
  celui d'un corps de Sol de la galaxie par défaut (`Venus`, `Terre`, `Lune`, `Charon`…), sa rotation réelle (table
  `SpaceSolRotations`, seule source, partagée avec `createDefault`) ; sinon la rotation par défaut du type ;
- `spin` **présent** : jamais remplacé ; une clé manquante prend la valeur par défaut du type pour ce champ, une
  valeur non finie donne la rotation par défaut du type (log) ;
- `tex` absent ou mal formé = `auto` (log).

**Corps (T39)** : `mass` (double, kg), toujours écrit. Lecture tolérante : **absent** (corps sauvegardé avant T39) :
masse réelle du corps de la galaxie par défaut de **même nom exact** (table `SpaceKnownMasses`, 62 corps, seule source,
aussi lue par `createDefault` ; C117), sinon masse **estimée** (§ 2) ; **présent** : jamais remplacé (borné) ; non fini :
masse estimée (log). Un monde du lot 3 (galaxie par défaut) se relit donc avec les masses réelles (banc T39 : les
62 corps sans clé `mass` relus égaux à la galaxie par défaut).

Une galaxie sauvegardée avant T28 se relit donc sans perte (banc T28 : toutes les clés retirées des 62 corps, galaxie
relue entière ; Vénus rétrograde, Uranus à 82,23° avec période négative, soit les 97,77° de la NASA, Barnard et un nom
inconnu au défaut du type) ; elle n'est pas modifiée sur le disque avant sa prochaine sauvegarde, qui écrit alors ces
rotations. Les corps des systèmes voisins n'ont pas de rotation « auto » (défaut du type).

L'Environment sauve aussi (hors galaxie) : `current`, `pose`, `speed`, `settings` (`{ fyaw, fpitch, vmax, turn, roll,
tscale, warp, etick, edays }` ; `warp` = vitesse de distorsion, T15, absente = 0,5 ; `etick` (long) / `edays`
(double) = **époque de l'horloge des orbites**, T38, toujours écrites, absentes = `(0, 0)`, c'est-à-dire la formule
d'avant T38 ; `tscale` absente = 360, ou 3 600 si l'époque manque aussi), `rev`, `frame`
(référentiel), **`time`** (tick de la pose, T13 : recalage au chargement, C37) et, au lot 2, `jump` (T21,
`SpaceJump.save`, écrit **seulement pendant un saut** ; absent = pas de saut) :
`jump: { phase (byte : 1 charge, 2 voyage), target (id du système cible), ticks (pas faits dans la phase), dur (durée
du voyage en ticks), qx, qy, qz, qw (Q₀), ox, oy, oz (origine, al), tx, ty, tz (cible, al) }`. Au lot 3 (T31, C72),
`seed` (long, toujours écrit) : **graine du fond étoilé procédural**, jamais nulle. Tirée au hasard
(`ThreadLocalRandom`) à la création d'une galaxie (installation sans état ni archive, galaxie illisible) ; **absente
ou nulle** (état d'avant T31) : tirée au premier chargement, puis écrite à la sauvegarde suivante (envoi demandé au
premier tick, donc `markDirty`). L'archive la recopie avec le reste (copie exacte du tag) ; une archive d'avant T31
restaurée garde la graine déjà tirée par le module. Le champ d'étoiles lui-même (`SpaceStarField`, ≈ 4 000 étoiles)
n'est ni sauvegardé ni envoyé : chaque client le régénère depuis la graine. Lecture tolérante : état
incohérent (cible ≤ 0, durée < 1, compteur hors de sa phase, orientation non unitaire, position non finie) = pas de
saut (log) ; l'orientation n'est renormalisée que si sa norme s'écarte de 1 de plus de 1e-12 (aller-retour exact). Un
voyage reprend au chargement (C43) ; une charge s'annule au premier pas (le pilote n'est pas sauvegardé).

**Migration de l'échelle de temps (T38, D28, C111)** : des réglages chargés **sans époque** (`(0, 0)`) à l'ancien
défaut `tscale = 3 600` passent à 360 s/tick ; l'époque est posée au **premier tick serveur**, après le recalage C37
(fait avec l'ancienne horloge), sur le temps simulé courant de l'ancienne horloge : aucun astre ne saute. Une autre
échelle (réglée à la main) est gardée telle quelle, avec l'époque `(0, 0)`. Jusqu'à ce tick, sauvegarde et
synchronisation écrivent encore l'ancienne horloge (une sauvegarde faite entre-temps se migrera au chargement suivant).
Même règle pour l'archive (son tag passe par le même `load`).

**Archive (T14, D12)** : `SpaceArchive` (`SavedData` du niveau, clé `hololib_space`, fichier `data/hololib_space.dat`
de la dimension) garde une copie **exacte** du tag de `save` de l'Environment, sous `env`, plus `saved` (long : tick de
la pose copiée, égal à la clé `time` de l'état). L'Environment s'y branche à son premier tick serveur (`onLoad` ne
connaît pas le niveau) ; tant qu'il est branché, l'archive est toujours à écrire et recopie son état **vivant** à
chaque sauvegarde du niveau (même quand la mémoire de dimension, propre, n'est pas réécrite) ; à son retrait
(`onUnload`), elle reçoit son état final. Au premier tick serveur, elle **remplace** l'état courant si le module a été
installé sans état (commande) ou si elle est plus récente que l'état chargé de la mémoire (`saved` > `time`) ; la
révision est alors portée au-dessus de celle déjà envoyée (un client qui a reçu la galaxie par défaut à l'installation
reprend le bloc froid), les ids sont ceux de l'archive, et la pose est recalée (C37). Elle survit au retrait du module ;
une installation qui la relit ne la supprime pas (elle sera réécrite). Aucune clé n'est ajoutée au tag de
l'Environment lui-même.

**Configurator (T24)** : clé `detached` (booléen, toujours vrai à l'écriture) ; absente = sauvegarde d'avant T24, la
fenêtre est replacée une fois devant la base du cube.
**Configurator (T25)** : clé `zoom` (id du corps zoomé en mode système, 0 = système entier ; absente = 0, remise à 0
hors mode système ou si le corps n'existe plus).
**Configurator (T26)** : clé `origin` (id du système origine des coordonnées, 0 = défaut Sol / premier).
**Space Pilot (T17)** : booléens `enabled`, `orbits`, `bodies`, `stars`, `names`, `hud`, `refLink` (absent = vrai) et
`nameAngle` (entier, degrés, borné à 1–30, absent = 5). Objets sauvegardés par le framework (HUD, fenêtre de Config,
cercles du joystick). Les clés `joystick_forward` / `joystick_aim` d'un ancien marker de Controller (lot 1) sont
ignorées au chargement.

## 5. Réseau

Méthodes `write(FriendlyByteBuf)` / `static read(FriendlyByteBuf)`. Trois vues, chacune avec sa fonction d'écriture :

| Vue | Contenu | Utilisée par |
|---|---|---|
| `SystemHeader` | id, nom, position (al), couleur et luminosité de l'étoile principale | Configurator (liste de tous les systèmes) |
| `SpaceSystem` complet | en-tête + corps ; chaque corps : `id, kind, name, parent, radius, color, lum, mass` (T39 : `double`, **+8 o par corps**), `[orbite]`, puis (T28) rotation propre (4 `float`, 16 o) et texture (octet de forme, puis id UTF pour `builtin`, 20 octets bruts pour `custom` : **1 o** pour `auto`, 2 + longueur pour `builtin` — 34 o au plus —, 21 o pour `custom`) | Environment (système courant, bloc froid), Configurator (système édité), Space Map (système affiché, T37) |
| `BackgroundStar` | **id** du système (varint, T15) + **nom** du système (UTF borné à 32 caractères, T15) + position galactique (al, `float` ×3) + couleur + luminosité ; la direction et l'éclat vus depuis le système courant sont calculés par le client. Taille : 20 o + id (1 o jusqu'à 127, 2 o jusqu'à 16 383) + nom (1 o de longueur + octets UTF-8) : **≈ 31 o** pour un nom moyen de 9 caractères (voisins par défaut), 55 o pour 32 caractères ASCII, 120 o au pire (32 caractères de 3 octets) ; 20 o avant T15 | Environment (tous les systèmes sauf le courant) |

Les positions galactiques passent en `float` pour les étoiles de fond (précision suffisante pour une direction),
en `double` ailleurs.

Réglages de l'Environment (`SpaceMotionSettings.write`, bloc chaud) : 7 `double` (axe avant yaw / pitch, `V_max`,
`turn`, `rollSpeed`, échelle de temps, vitesse de distorsion), puis l'**époque** (T38 : `long` tick + `double` jours),
**72 o** (56 avant T38). Lecture stricte (valeur non finie = état ignoré). Le budget est dans [`05`](05-synchronisation.md).

## 6. Opérations d'édition

Exposées par l'Environment (T04), appelées par le Configurator (T11, T12) sur le fil serveur. Chacune valide, applique,
incrémente `galaxyRevision` et renvoie un résultat (succès / motif de refus) pour le message au joueur.

| Opération | Effet |
|---|---|
| `addSystem(name, position)` → id | ajoute un système avec une étoile par défaut |
| `removeSystem(id)` | refusé pour le système courant s'il est le dernier ; sinon si courant, le courant passe au premier restant |
| `renameSystem(id, name)`, `moveSystem(id, position)` | |
| `setCurrentSystem(id)` | **dans l'Environment seul** (le modèle ne connaît pas le système courant) : `galaxy.contains(id)`, puis change le système du monde ; la pose est remise à la pose par défaut du système ([`04`](04-mathematiques.md) § 5) |
| `addBody(systemId, kind, parentId)` → id | valeurs par défaut selon le type (rayon, couleur, orbite hors des orbites existantes, masse estimée, T39) |
| `updateBody(systemId, body)` | remplace les champs éditables (nom, rayon, couleur, luminosité, masse (T39), orbite) ; `kind` et `parentId` immuables |
| `setBodyMass(systemId, id, masse)` (T39) | masse en kg ; refusée si non finie, nulle ou négative, sinon bornée (§ 3). Le Configurator passe par `updateBody` (action 7 depuis T42, champ 6 « Masse kg ») |
| `removeBody(systemId, id)` | retire aussi les descendants ; refusé pour le dernier corps sans parent |
| `setBodyRotation(systemId, id, période, obliquité, nœud, phase)` (T28) | rotation propre (heures, degrés) ; refusé si une valeur n'est pas finie, sinon bornée ; variante qui prend un `SpaceRotation` (`null` = défaut du type) |
| `setBodyTexture(systemId, id, texte)` (T28) | `auto`, `builtin:<id>` ou `custom:<sha1>` ; refusé si mal formé ; variante qui prend un `SpaceTextureRef` (`null` = `auto`) |

`updateBody` remplace aussi la rotation propre et la texture par celles du corps reçu (T28) : une copie faite par
`withXxx` les conserve. **Configurator (T44)** : rotation propre et texture s'éditent dans les onglets « Rotation » et
« Texture » de sa fenêtre, chacune **champ par champ** par l'action 7 (champs 7 période de rotation en heures, 8
obliquité, 9 nœud, 10 phase de rotation, 11 texture en forme texte) : le serveur part du corps qu'il tient
(`withRotation(rotation.withPeriod(…))`, `withPole`, `withPhase`, `withTexture`) puis appelle `updateBody` ; une
texture `builtin` doit exister (`SpaceBodyTextures.exists`), une texture `custom` doit être stockée pour la dimension
(T45, `SpaceTextureStore.exists`), une forme mal écrite est refusée ; rien n'est envoyé si le corps ne change pas. Les deux opérations de T28 existent dans `SpaceGalaxy` et dans l'API serveur de
l'Environment (validation, révision, envoi du bloc froid si le système est courant, comme les autres).

## 7. Contenu par défaut

À la première installation (galaxie vide au `load`, et pas d'archive, T14), l'Environment crée (T04, refait en T16) ;
avec une archive, la galaxie par défaut n'existe que jusqu'au premier tick serveur, où l'archive la remplace :
- **Sol** à l'origine galactique, **34 corps** : Soleil, 8 planètes, 5 objets mineurs (`MINOR` : Cérès, Pluton, Éris,
  Haumea, Makémaké), 20 lunes majeures (dont Charon autour de Pluton). Système courant.
- Les **11 systèmes voisins** du lot 1, à leurs positions réelles, avec leurs étoiles compagnes (orbites `STAR` →
  `STAR`) et leurs planètes confirmées.

Toutes les valeurs (rayons, orbites, couleurs, luminosités, sources) sont dans
[`donnees-systemes.md`](donnees-systemes.md). Une galaxie **déjà sauvegardée** n'est pas modifiée (C51).
**Rotation propre** (T28) : chaque corps de la galaxie par défaut reçoit sa rotation réelle ou estimée (section
« Rotation propre » de l'annexe : `SpaceGalaxy.createDefault` ; Sol depuis la table `SpaceSolRotations`, aussi lue
pour les anciennes sauvegardes, les voisins par appels `spin` / `sync` par nom après la liste des corps) ; texture
`auto` partout. Seules les galaxies créées après T28 en profitent.
**Masse** (T39) : masse réelle de chaque corps (annexe, section « Masses » ; exoplanètes : masse minimale `M sin i`,
sauf Epsilon Eridani b, masse vraie), lue dans la table `SpaceKnownMasses` (aussi pour les galaxies sauvegardées avant
T39, par nom).

Pose par défaut (refaite en T39, C115) : dans la sphère d'influence de la Terre, au-delà d'elle sur la droite
Soleil → Terre, à `min(0,02 a, 0,8 r)` (`r` = rayon de **base** de la sphère, sans le facteur de jeu) = **739 720 km** d'elle (Lune à 982 000 km), regard vers l'étoile, référentiel
= Terre ([`04`](04-mathematiques.md) § 5). Avant T39 : à 1,02 UA de l'étoile, près de la Terre (mais hors de son
rayon). Depuis T16,
la planète de départ n'est plus « la première `PLANET` de la liste » (Mercure, dans le nouveau Sol) mais la `PLANET`
dont le demi-grand axe est le plus proche de 1 UA en échelle logarithmique (`SpaceMotion.startPlanet`, [`04`](04-mathematiques.md)
§ 5) : la Terre dans Sol, quel que soit son rang. Mesuré (banc T16, `t = 0`) : 1,02 UA du Soleil, 0,037 UA de la Terre
(proche de son périhélie), référentiel = Terre (score de D8 ; avec les sphères d'influence, cette ancienne pose donnerait
le Soleil, d'où C115).
