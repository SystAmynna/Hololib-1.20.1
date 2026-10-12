# F29 — Nettoyage du socle

**Objectif** : supprimer les petites duplications, le code mort et les incohérences de nommage du framework relevés
par le rapport (§ 5.3, § 5.5).

**Lot** : B   **API** : non (aucune signature publique supprimée sans être inutilisée ; voir Interdits)
**Dépendances** : F28

## Constats
1. `MAX_KEY_LENGTH = 64` recopié dans trois paquets (`network/`) : une seule constante.
2. Dix blocs `messageBuilder` recopiés dans `HoloNetwork` : une méthode d'enregistrement générique.
3. `byOrdinal` réécrit dans trois énumérations : une méthode utilitaire.
4. `hasPermission(2)` / `hasPermissions(2)` en dur dans les commandes et dans `HoloContainers`, alors que
   `HoloThemeLibrary.OP_LEVEL` existe : une constante du framework (par exemple `HoloPermissions.OP_LEVEL`), interne
   ou publique selon F19.
5. Code mort : `HoloNetwork.sendToTracking(Entity)` (jamais appelé ; la variante `LevelChunk` est utilisée).
6. `ResolvedHolo` expose des champs mutables package-private (`attachment`, `family`, `object`) à côté de ses
   accesseurs : n'utiliser que les accesseurs hors de la classe, ou que les champs dans le package. Choisir et
   uniformiser.
7. `BufferHoloCanvas.textWidth` / `lineHeight` (`:178-181`) ignorent le champ `font` mis en cache.
8. Nommage :
   - `HoloEditTool` (énumération) et `HoloEditTools` (contrôleur) se confondent : renommer le contrôleur (par
     exemple `HoloEditToolController`) ;
   - `HoloEditSession.reset` et `HoloEditTools.reset` n'ont pas le même sens : nommer selon l'effet.
9. `HoloDraggable.clickableBy` compare des `HoloControl` avec `==` : utiliser `equals`.
10. `HoloModuleType.id()` fait une recherche dans le registre à chaque appel. Ce n'est pas du nettoyage : c'est F34.

## À lire avant
Rapport § 5.3, § 5.5 ; les fichiers cités.

## Zone
`network/*`, `holo/**` (constantes, énumérations, `HoloDraggable`), `command/*` (constante d'op),
`client/render/holo/ResolvedHolo.java`, `BufferHoloCanvas.java`, `client/edit/*` (renommages).

## Livrables
Un correctif par constat, liste dans le compte rendu. Les renommages de classes publiques sont faits en une fois,
avec tous les usages.

## Interdits
- Supprimer une méthode publique **utilisée**.
- Changer un format réseau (l'ordre d'enregistrement des messages de `HoloNetwork` doit rester identique : les
  index en dépendent).

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu : connexion LAN (index des messages inchangés), édition, commandes.

## Compte rendu attendu
Liste des corrections, renommages, fichiers.
