# F06 — `editRange` vérifiée côté serveur

**Objectif** : la portée d'édition d'un objet `World` (`HoloObject.editRange()`, 1 à 16 blocs) est aussi vérifiée
par le serveur, pas seulement par le client. Rapport § 7.2, recommandation 6.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat
- `holo/memory/HoloContainers.java:242` (`handleEdit`) ne compare jamais la distance entre le joueur et l'objet.
- La portée n'est appliquée que côté client (`HoloEditSession.inRange`).
- Un client modifié peut donc éditer un objet `World` de n'importe quelle distance, dans la limite de ses droits.

## À lire avant
- Rapport § 7.2 ; [`decisions.md`](../decisions.md) FC9.
- `holo/memory/HoloContainers.java` (`handleEdit`), `holo/edit/HoloEditRules.java`, `holo/object/HoloObject.java`
  (`editRange`), `client/edit/HoloEditSession.java` (`inRange`, pour reproduire la même mesure),
  `client/render/holo/ResolvedHolo.java` (position d'un objet `World` : hôte + transform).

## Zone
`holo/memory/HoloContainers.java` (`handleEdit` et méthode privée de position), éventuellement une méthode
package-private dans `holo/object` si la position monde d'un objet n'est calculable que là.

## Livrables
1. Pour un objet `World`, `handleEdit` calcule sa position monde côté serveur : hôte (joueur, marker, origine de
   dimension) + translation. Il refuse l'édition si la distance œil → objet dépasse `editRange() + 2` blocs (FC9).
   Refus = même renvoi correctif que les autres refus.
2. La mesure (point de l'objet, œil ou pieds) est la même que celle du client, ou plus large. Le noter.
3. Les objets non `World` sont inchangés.

## Interdits
- Changer `editRange` (bornes, synchronisation).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : éditer un objet `World` à 6 blocs (portée 8) fonctionne ; à la limite de la portée, aucune édition
  légitime n'est refusée à tort (tester en bougeant pendant le glisser).

## Compte rendu attendu
Fichiers, formule de position, marge, scénario.
