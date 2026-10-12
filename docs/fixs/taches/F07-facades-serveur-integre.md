# F07 — Façades client sûres sur le serveur intégré

**Objectif** : en solo, la copie **serveur** d'un module ne doit jamais appeler un backend client (police, thèmes du
client) depuis le fil serveur. Rapport § 7.2.

**Lot** : A   **API** : non   **Dépendances** : aucune

## Constat
- Les façades sont installées **globalement** au setup client (`HoloBridges.java:35` pour `HoloTextMetrics`,
  `ClientThemes.java:122` pour `HoloThemes`).
- Sur le serveur intégré, la copie serveur d'un module partage la JVM : si elle appelle `HoloTextMetrics.width`, le
  backend lit `Minecraft.getInstance().font` **depuis le fil serveur**.
- Appelants côté commun :
  - `HoloTextBuffer` (`:166,236,257`) et la mise en page des widgets ;
  - 9 appels de `HoloTextMetrics` hors `client/` ;
  - `HoloThemes.resolve`.
- `HoloRenderCanceler` s'en protège par `host().canAct()`, pas ces deux façades.
- Rien ne prouve qu'un appel côté serveur arrive aujourd'hui : c'est un risque latent, à mesurer d'abord.

## À lire avant
- Rapport § 3.5, § 7.2.
- `holo/widget/HoloTextMetrics.java`, `holo/theme/HoloThemes.java`, `holo/render/HoloRenderCanceler.java` (modèle
  `canAct`), `holo/widget/control/HoloTextBuffer.java`, les appels de `HoloTextMetrics` hors `client/` (grep).

## Zone
`holo/widget/HoloTextMetrics.java`, `holo/theme/HoloThemes.java`, `client/render/holo/HoloBridges.java` et
`client/theme/ClientThemes.java` (installation seulement).

## Livrables
1. **Mesure** : un journal temporaire (retiré avant la fin) montre si l'une des façades est appelée sur le fil
   serveur pendant un scénario solo complet (focus, Config, fenêtres de marker, Theme Configurator, champs de texte).
   Le résultat va dans le compte rendu.
2. Protection, sans changer la signature des façades :
   - le backend installé ne répond que sur le fil de rendu ou le fil client
     (`RenderSystem.isOnRenderThread()` / `Minecraft.getInstance().isSameThread()`) ;
   - ailleurs, la façade retombe sur son comportement inerte (largeur estimée, thème par défaut).
   
   Documenter la règle dans la javadoc des deux façades.
3. Si la mesure montre des appels serveur, les lister : un module qui mesure du texte côté serveur a peut-être une
   mise en page qui diffère entre les deux côtés. À signaler comme question, pas à corriger ici.

## Interdits
- Changer les signatures publiques des façades.
- Allocation par appel dans le chemin client.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (solo) : les textes et thèmes s'affichent comme avant ; la mesure est faite et rapportée.

## Compte rendu attendu
Résultat de la mesure (appels serveur trouvés ou non), garde retenue, fichiers.
