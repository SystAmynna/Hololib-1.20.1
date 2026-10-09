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
SpaceBodyKind      STAR, PLANET, MOON
SpaceLimits        bornes ci-dessous + sanitize
```

- Les ids ne sont **jamais réutilisés** dans leur conteneur (`prochain id` monotone, sauvegardé) : une sélection ou
  une référence ne peut pas désigner par erreur un nouvel objet.
- Un corps sans parent est au **centre du système** (immobile). En pratique : l'étoile principale. Un système peut
  avoir plusieurs étoiles : les suivantes ont pour parent la première (orbite), ce qui fait des binaires simples.
- `MOON` : parent `PLANET` ; `PLANET` : parent `STAR` ; `STAR` : parent aucun ou `STAR`. La validation refuse le
  reste et les cycles.
- L'orientation de l'orbite est un triplet yaw / pitch / roll **avec les conventions de `HoloTransform`** (YXZ,
  `HoloEditMath.rotation`) : c'est exactement ce qu'édite l'outil Rotation sur la poignée d'orbite (T12). Le plan de
  l'orbite est le plan local `XZ`, le périapse sur `+X`, le sens de parcours de `+X` vers `+Z`.

## 3. Bornes (`SpaceLimits`)

| Borne | Valeur | Motif |
|---|---|---|
| Systèmes par galaxie | 256 | taille du bloc froid (en-têtes) |
| Corps par système | 32 | idem |
| Longueur d'un nom | 32 caractères | `HoloTextBuffer.sanitize`-like : retirer contrôles et retours à la ligne |
| Position galactique | ‖p‖ ≤ 100 000 al | |
| Rayon | 1 km à 10⁷ km | |
| Demi-grand axe | 10⁻⁴ à 10⁴ UA | |
| Excentricité | 0 à 0,95 | ellipse bien formée, Kepler convergent |
| Période | 0,01 à 10⁷ jours | |
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

L'Environment sauve aussi (hors galaxie) : `current`, `pose`, `speed`, `settings`, `rev`, `frame` (référentiel) et
**`time`** (tick de la pose, T13 : recalage au chargement, C37).

## 5. Réseau

Méthodes `write(FriendlyByteBuf)` / `static read(FriendlyByteBuf)`. Trois vues, chacune avec sa fonction d'écriture :

| Vue | Contenu | Utilisée par |
|---|---|---|
| `SystemHeader` | id, nom, position (al), couleur et luminosité de l'étoile principale | Configurator (liste de tous les systèmes) |
| `SpaceSystem` complet | en-tête + corps | Environment (système courant), Configurator (système édité) |
| `BackgroundStar` | position galactique (al, `float` ×3) + couleur + luminosité ; la direction et l'éclat vus depuis le système courant sont calculés par le client | Environment (tous les systèmes sauf le courant) |

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

À la première installation (galaxie vide au `load`), l'Environment crée (T04) :
- **Sol** à l'origine galactique : étoile jaune (rayon 696 000 km), une planète tellurique bleue (1 UA, 365 j,
  rayon 6 371 km) avec une lune (0,00257 UA, 27,3 j, 1 737 km), une planète rouge (1,52 UA, 687 j), une géante
  orangée (5,2 UA, 4 333 j, 69 911 km). Système courant.
- 8 à 12 systèmes voisins nommés, à quelques années-lumière, de couleurs et luminosités variées (étoiles de fond).

Pose par défaut : à 1,02 UA de l'étoile, près de la planète bleue (mais hors de son rayon), regard vers l'étoile.
