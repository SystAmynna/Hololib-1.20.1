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
| Temps simulé | seconde simulée | `double` | `temps = (gameTime + partialTick) × échelle` ([`04`](04-mathematiques.md) § 3) |
| Vitesse du monde | km par **tick** de jeu | `double` | indépendante de l'échelle de temps des orbites : la conduite ne change pas quand on accélère le temps |
| Couleur | ARGB | `int` | alpha ignoré pour les corps opaques |
| Luminosité d'une étoile | relative au Soleil | `float` | éclat des étoiles de fond |

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
                   luminosite (etoiles), orbite (null si parentId == 0)
         └─ SpaceOrbit  demi-grand axe a (UA), excentricite e, orientation (yaw, pitch, roll en degres),
                        periode (jours), phase a t = 0 (degres)
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

Toute valeur venant d'un client est **assainie** (finie, bornée, nom nettoyé) avant d'entrer dans la galaxie ; une
valeur non finie fait rejeter l'opération.

## 4. Sauvegarde NBT

Méthodes `CompoundTag save()` (tag neuf, comme `HoloTransform`) / `static load(CompoundTag)` par classe. Clés courtes et stables (renommer une clé casse
les sauvegardes : ne jamais le faire sans migration). Un `load` tolère les clés manquantes (valeurs par défaut) et
ignore les éléments invalides (log + saut) plutôt que de lever : une galaxie corrompue ne doit pas empêcher le
chargement de la dimension.

```
galaxy: { next: int, systems: [ { id, name, x, y, z, next, bodies: [ { id, kind, name, parent, radius, color,
          lum, orbit?: { a, e, yaw, pitch, roll, period, phase } } ] } ] }
```

L'Environment sauve aussi (hors galaxie) : `current`, `pose`, `speed`, `settings` (`{ fyaw, fpitch, vmax, turn, roll,
tscale, warp }` ; `warp` = vitesse de distorsion, T15, absente = 0,5), `rev`, `frame`
(référentiel), **`time`** (tick de la pose, T13 : recalage au chargement, C37) et, au lot 2, `jump` (T21,
`SpaceJump.save`, écrit **seulement pendant un saut** ; absent = pas de saut) :
`jump: { phase (byte : 1 charge, 2 voyage), target (id du système cible), ticks (pas faits dans la phase), dur (durée
du voyage en ticks), qx, qy, qz, qw (Q₀), ox, oy, oz (origine, al), tx, ty, tz (cible, al) }`. Lecture tolérante : état
incohérent (cible ≤ 0, durée < 1, compteur hors de sa phase, orientation non unitaire, position non finie) = pas de
saut (log) ; l'orientation n'est renormalisée que si sa norme s'écarte de 1 de plus de 1e-12 (aller-retour exact). Un
voyage reprend au chargement (C43) ; une charge s'annule au premier pas (le pilote n'est pas sauvegardé).

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
| `SpaceSystem` complet | en-tête + corps | Environment (système courant), Configurator (système édité) |
| `BackgroundStar` | **id** du système (varint, T15) + **nom** du système (UTF borné à 32 caractères, T15) + position galactique (al, `float` ×3) + couleur + luminosité ; la direction et l'éclat vus depuis le système courant sont calculés par le client. Taille : 20 o + id (1 o jusqu'à 127, 2 o jusqu'à 16 383) + nom (1 o de longueur + octets UTF-8) : **≈ 31 o** pour un nom moyen de 9 caractères (voisins par défaut), 55 o pour 32 caractères ASCII, 120 o au pire (32 caractères de 3 octets) ; 20 o avant T15 | Environment (tous les systèmes sauf le courant) |

Les positions galactiques passent en `float` pour les étoiles de fond (précision suffisante pour une direction),
en `double` ailleurs. Le budget est dans [`05`](05-synchronisation.md).

## 6. Opérations d'édition

Exposées par l'Environment (T04), appelées par le Configurator (T11, T12) sur le fil serveur. Chacune valide, applique,
incrémente `galaxyRevision` et renvoie un résultat (succès / motif de refus) pour le message au joueur.

| Opération | Effet |
|---|---|
| `addSystem(name, position)` → id | ajoute un système avec une étoile par défaut |
| `removeSystem(id)` | refusé pour le système courant s'il est le dernier ; sinon si courant, le courant passe au premier restant |
| `renameSystem(id, name)`, `moveSystem(id, position)` | |
| `setCurrentSystem(id)` | **dans l'Environment seul** (le modèle ne connaît pas le système courant) : `galaxy.contains(id)`, puis change le système du monde ; la pose est remise à la pose par défaut du système ([`04`](04-mathematiques.md) § 5) |
| `addBody(systemId, kind, parentId)` → id | valeurs par défaut selon le type (rayon, couleur, orbite hors des orbites existantes) |
| `updateBody(systemId, body)` | remplace les champs éditables (nom, rayon, couleur, luminosité, orbite) ; `kind` et `parentId` immuables |
| `removeBody(systemId, id)` | retire aussi les descendants ; refusé pour le dernier corps sans parent |

## 7. Contenu par défaut

À la première installation (galaxie vide au `load`, et pas d'archive, T14), l'Environment crée (T04, refait en T16) ;
avec une archive, la galaxie par défaut n'existe que jusqu'au premier tick serveur, où l'archive la remplace :
- **Sol** à l'origine galactique, **34 corps** : Soleil, 8 planètes, 5 objets mineurs (`MINOR` : Cérès, Pluton, Éris,
  Haumea, Makémaké), 20 lunes majeures (dont Charon autour de Pluton). Système courant.
- Les **11 systèmes voisins** du lot 1, à leurs positions réelles, avec leurs étoiles compagnes (orbites `STAR` →
  `STAR`) et leurs planètes confirmées.

Toutes les valeurs (rayons, orbites, couleurs, luminosités, sources) sont dans
[`donnees-systemes.md`](donnees-systemes.md). Une galaxie **déjà sauvegardée** n'est pas modifiée (C51).

Pose par défaut : à 1,02 UA de l'étoile, près de la Terre (mais hors de son rayon), regard vers l'étoile. Depuis T16,
la planète de départ n'est plus « la première `PLANET` de la liste » (Mercure, dans le nouveau Sol) mais la `PLANET`
dont le demi-grand axe est le plus proche de 1 UA en échelle logarithmique (`SpaceMotion.startPlanet`, [`04`](04-mathematiques.md)
§ 5) : la Terre dans Sol, quel que soit son rang. Mesuré (banc T16, `t = 0`) : 1,02 UA du Soleil, 0,037 UA de la Terre
(proche de son périhélie), référentiel = Terre.
