# T16 — Systèmes réels : Sol complet, voisins, objets mineurs

**Objectif** : demande de l'utilisatrice (D15) : « tous les corps célestes majeurs du système solaire représentés »,
et « la même chose pour toutes les étoiles actuellement dans le module, d'après les données les plus récentes ».
Ajoute le type **objet mineur** (`MINOR`) nécessaire à la règle de visibilité de la surcouche (T18).

**Dépendances** : T15.

## À lire avant
- [`donnees-systemes.md`](../donnees-systemes.md) (**toutes les valeurs, conventions de recopie, bornes à modifier**).
- [`03-modele-donnees.md`](../03-modele-donnees.md) §§ 2, 3, 7 ; [`decisions.md`](../decisions.md) C14, C36, C51.
- `modules/dimension/space/model/` : `SpaceBodyKind` (valeurs par défaut par type), `SpaceBody` (validation de
  parenté), `SpaceSystem`, `SpaceGalaxy` (`createDefault`, `addNeighbor`, `addBody`), `SpaceLimits`.
- `modules/dimension/space/math/SpacePose.java` (pose par défaut : comment elle trouve la planète de départ) et
  `SpaceMotion` (rien ne doit supposer `kind ∈ {STAR, PLANET, MOON}`).
- Tous les `switch` / tests sur `SpaceBodyKind` : `grep -rn "SpaceBodyKind\." src/main/java` (ciel, cube, fenêtre du
  Configurator « + corps », `SpaceSystemScale`).

## Livrables
1. **`SpaceBodyKind.MINOR`** ajouté **en dernier** (l'ordinal circule en NBT et sur le réseau : ne pas réordonner) :
   libellé « Objet mineur », valeurs par défaut (rayon 600 km, couleur grise, orbite 40 UA, 90 000 j). Parenté :
   `MINOR` → parent `STAR` ; `MOON` → parent `PLANET` **ou** `MINOR`. Validation, cycles, `addBody` à jour.
2. Prise en compte partout : ciel (`SpaceSkyRenderer` : dessiné comme une planète), cube (rendu et taille),
   `SpaceSystemScale`, fenêtre du Configurator (type proposé par « + corps », parent permis).
3. **Bornes** (`SpaceLimits`) : `MAX_BODIES` 48, `MIN_SEMI_MAJOR_AXIS` 1e-5, `MAX_PERIOD` 1e9 (tableau de l'annexe).
   Vérifier que rien d'autre ne dépend de 32 corps (tableaux statiques des renderers, `positions` de l'Environment).
4. **`createDefault`** réécrite d'après l'annexe : Sol (34 corps, ids dans l'ordre des tableaux) et les 11 voisins
   (positions inchangées, étoiles compagnes, planètes confirmées). Noms **sans accents** (convention de l'annexe).
   Une méthode d'aide lisible par corps (pas de tableau magique illisible) ; commentaire de source par système.
   `nextId` de chaque système cohérent.
5. **Pose par défaut** : toujours près de la Terre ; si `SpacePose` cherchait la planète par indice, la trouver par
   type / ordre robuste (première planète `PLANET` dont `a ≈ 1 UA`, ou par nom) et le documenter.
6. Docs : 03 § 7 et javadoc de `createDefault` à jour ; budget du doc 05 avec 34 corps.

## Interdits
- Modifier une classe hors des packages Space.
- Changer une valeur de l'annexe sans le signaler ; inventer une donnée absente de l'annexe.
- Modifier une galaxie déjà sauvegardée (C51) : seule la création par défaut change.

## Critères d'acceptation
- `compileJava` réussit.
- Vérification jetable : `createDefault()` construit sans exception, aucune valeur bornée par `SpaceLimits` (comparer
  chaque champ lu à l'annexe), 12 systèmes, 62 corps ; aller-retour NBT et réseau de la galaxie identique ; une
  ancienne galaxie (sans `MINOR`) se recharge.
- En jeu (nouvelle dimension) : depuis la Terre, Vénus, Mars, Jupiter, Saturne visibles comme points ; Uranus et
  Neptune faibles ; approcher Jupiter → les 4 lunes galiléennes ; Uranus → lunes sur un plan presque vertical ;
  Configurator : Sol en mode système montre toutes les orbites ; Alpha du Centaure montre A, B, Proxima et ses
  planètes.

## Compte rendu attendu
Fichiers modifiés, liste des usages de `SpaceBodyKind` adaptés, écarts à l'annexe (aucun attendu), résultats de la
vérification, scénario en jeu.
