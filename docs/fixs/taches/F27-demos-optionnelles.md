# F27 — Démos optionnelles

**Objectif** : les modules de démonstration ne sont plus disponibles en production par défaut, tout en gardant les
sauvegardes lisibles. Rapport § 4.6, recommandation 17.

**Lot** : B   **API** : non   **Dépendances** : aucune

## Constat
- `HoloLib.java:29` enregistre `DemoModules.REGISTER` sans condition. Les démos sont livrées dans le jar et
  installables en jeu.
- `client/render/demo/DemoRenderers` est enregistré sans condition.
- Le mod n'a aucun fichier de configuration (`ForgeConfigSpec`) aujourd'hui.

## À lire avant
- Rapport § 4.6 ; [`decisions.md`](../decisions.md) FC5.
- `HoloLib.java`, `demo/*`, `client/render/demo/*`, `command/HoloModuleCommand` (installation),
  `holo/module/HoloModuleTypes.java`.

## Zone
`HoloLib.java`, nouveau `HoloLibConfig` (racine ou `holo/`), `demo/*`, `client/render/demo/*`, la commande
d'installation de module.

## Livrables
1. Configuration commune `hololib-common.toml` avec `enableDemos` (FC5) : par défaut vrai en développement
   (`!FMLLoader.isProduction()`), faux en production.
2. Les types de démo **restent enregistrés**, sinon les sauvegardes qui les contiennent deviendraient orphelines. Si
   `enableDemos` est faux :
   - leur installation par `/hololib_module add` est refusée (message clair) ;
   - les démos installées sont chargées mais ne s'affichent pas, ou bien s'affichent : choisir après lecture et le
     noter ;
   - leurs renderers ne sont pas enregistrés ;
   - leur autocomplétion est masquée.
3. Documentation de l'option dans CLAUDE.md.

## Interdits
- Désenregistrer les types (perte de sauvegarde).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (dev) : les démos fonctionnent comme avant.
- Avec `enableDemos = false` : refus à l'installation, aucune erreur au chargement d'un monde qui contient une démo.

## Compte rendu attendu
Option, comportement retenu pour les démos installées, fichiers, scénario.
