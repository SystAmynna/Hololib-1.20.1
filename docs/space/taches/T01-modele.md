# T01 — Modèle de données

**Objectif** : écrire les classes de données pures de la galaxie, avec sauvegarde NBT, format réseau, validation et
bornes. Aucune logique de jeu, aucun rendu.

**Dépendances** : aucune.

## À lire avant
- [`03-modele-donnees.md`](../03-modele-donnees.md) (en entier : c'est la spécification).
- [`02-architecture.md`](../02-architecture.md) § 1 (packages).
- Exemples de style NBT / réseau : `holo/object/HoloTransform.java` (`write/read`, `save/load`),
  `holo/object/HoloAccess.java`.
- Nettoyage de texte : `holo/widget/control/HoloTextBuffer.java` (`sanitize`).

## Livrables
`modules/dimension/space/model/` :
- `package-info.java` (rôle, style mutable / immuable retenu, renvoi vers `docs/space/03-modele-donnees.md`) ;
- `SpaceBodyKind`, `SpaceOrbit`, `SpaceBody`, `SpaceSystem`, `SpaceGalaxy`, `SpaceLimits` ;
- les vues réseau `SystemHeader` et `BackgroundStar` (records, peuvent être imbriqués dans `SpaceSystem` /
  `SpaceGalaxy`).
- `modules/dimension/space/package-info.java` (package parent : rôle des trois sous-parties, renvoi vers le dossier).

## Spécification
- Exactement les champs, unités, ids, règles de parenté et bornes du doc 03.
- `SpaceGalaxy` (si mutable) : accès en lecture (listes non modifiables), et les **opérations du doc 03 § 6** en
  méthodes qui valident et renvoient un résultat (`enum` ou record `SpaceEditResult` : succès + id créé, ou motif de
  refus en `Component` traduisible plus tard ; texte français sans accents en attendant). Elles **n'incrémentent pas**
  la révision : c'est le rôle de l'Environment (T04).
- `SpaceLimits.sanitize...` : noms (contrôles retirés, tronqués à 32), nombres (finis, bornés). Une valeur non finie
  fait refuser l'opération.
- Contenu par défaut du doc 03 § 7 : fabrique `SpaceGalaxy.createDefault()` (utilisée par T04). Les orbites de
  l'exemple utilisent des valeurs proches du système solaire réel (phases variées pour ne pas aligner les planètes).
- NBT tolérant (doc 03 § 4). Réseau strict : côté serveur, une action invalide lève et `HoloContainers.handleAction`
  la rattrape et la logue ; côté client, une exception dans `readSync` **n'est pas rattrapée** par le mod (avalée sans
  log par la file de Forge : miroir perdu à l'ajout, `onSync` sauté à la mise à jour) → valider avant d'écrire, et
  logger soi-même si utile.
- Méthodes `write/read` pour : système complet, en-tête, étoile de fond.

## Interdits
- Toute classe client, tout accès au monde ou au serveur.
- Toute modification hors de `modules/dimension/space/`.

## Critères d'acceptation
- `compileJava` réussit.
- Relecture : chaque borne et chaque règle du doc 03 a son code ; aller-retour NBT et réseau symétriques (même ordre
  de champs en écriture et en lecture).

## Compte rendu attendu
Fichiers créés ; style retenu (mutable / immuable) ; écarts au doc 03 et pourquoi ; signatures publiques des
opérations (T04 et T11 s'en servent).
