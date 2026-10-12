# F33 — `AreaScanner` et `AreaFeed` : budgets et silences

**Objectif** : le coût serveur de l'Area Displayer est borné **globalement** et **réellement**. Plusieurs displayers
ne cumulent plus leur budget, une lecture de chunk ne dépasse plus l'échéance, et rien n'est envoyé quand rien n'a
changé. Rapport § 6.3, recommandation 21.

**Lot** : C   **API** : non   **Dépendances** : aucune

## Constat
- `modules/monde/areadisplayer/AreaScanner.java` :
  - budget de 3 ms **par displayer** (`AreaDisplayerModule.java:90`) : N displayers = N × 3 ms par tick ;
  - échéance vérifiée seulement entre deux chunks (`:288`), alors qu'un `readChunk` (`:373-406`) peut lire environ
    262 000 `getBlockState` (16 × 16 × 4 × hauteur, au pas 4).
- `modules/monde/areadisplayer/AreaFeed.java` :
  - toutes les 2 ticks, `getEntitiesOfClass` porte sur la boîte de **toute** la zone (`:200-205`). Le stockage
    vanilla parcourt les sections sur l'axe X : une très grande zone coûte cette étendue à chaque appel ;
  - le paquet `Tracks` part vers chaque abonné toutes les 2 ticks, même sans changement (`:228-246`).

## À lire avant
- Rapport § 6.3 ; CLAUDE.md « Modules Area » (scan, Auto Update, canal dédié, protocole `"2"`).
- `modules/monde/areadisplayer/*`, `terrain/*` ; sources vanilla de `EntitySectionStorage` / `getEntities` pour
  confirmer le coût.

## Zone
`modules/monde/areadisplayer/**`, `client/modules/monde/areadisplayer/**` (seulement si le format `Tracks` change ;
voir Interdits).

## Livrables
1. Budget **global** du scanner, partagé entre tous les displayers du serveur : 3 ms par tick au total, réparti en
   tourniquet. La constante est documentée.
2. Échéance vérifiée **à l'intérieur** de `readChunk` (par colonne, ou par section), avec reprise au point d'arrêt.
   Résultat identique à un scan non interrompu.
3. `AreaFeed` :
   - interroge seulement les chunks **chargés** qui recoupent la zone (ou découpe la boîte par section chargée) ;
   - n'envoie pas `Tracks` si aucune position suivie n'a changé depuis le dernier envoi à cet abonné. Un battement
     reste (par exemple toutes les 40 ticks) pour que le client ne croie pas les points figés à tort : à valider en
     lisant l'interpolation client.
4. **Mesure avant / après** :
   - temps serveur par tick (`/debug start` / `stop`, ou profileur) avec 3 displayers sur des zones de 1024² en
     Auto Update ;
   - octets envoyés par seconde sur un canal sans mouvement.

## Interdits
- Changer le format du canal `hololib:area_terrain` sans incrémenter son protocole (`"2"` → `"3"`). Si un
  changement de format est indispensable, le décrire et s'arrêter.
- Lire un chunk non chargé autrement qu'aujourd'hui (disque, jamais généré).

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu :
  - scan complet identique (comparer le terrain affiché) ;
  - Auto Update ;
  - trainées et points fluides ;
  - plusieurs displayers en même temps ;
  - mesures jointes.

## Compte rendu attendu
Budgets, points d'arrêt, règle d'envoi, fichiers, mesures.
