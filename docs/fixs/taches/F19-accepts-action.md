# F19 — Crochet `acceptsAction` restrictif par défaut

**Objectif** : chaque module déclare qui peut lui envoyer une action, avec un défaut sûr. Un auteur tiers ne laisse
plus, par oubli, tous les joueurs d'une dimension agir sur son module. Les vérifications redondantes disparaissent.
Rapport § 3.4, § 7.2.

**Lot** : B   **API** : **oui : à valider** (méthode ajoutée, et défaut qui change le comportement)   **Dépendances** :
F02

## Constat
- `holo/memory/HoloContainers.java:214-292` (`handleAction`, `actionTarget`) :
  - une action **non sourcée** sur un module de dimension est acceptée de tout joueur de la dimension ;
  - sur un module de position, de tout joueur à portée.
- Chaque module doit donc vérifier les droits lui-même :
  - les Configurators vérifient l'op en premier ;
  - Radar, Targeting et Keys revérifient le propriétaire « par redondance » ;
  - Config et QuickAction s'en remettent au framework.

## À lire avant
- Rapport § 3.4, § 7.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.4.
- `holo/memory/HoloContainers.java` (`handleAction`, `actionTarget`, `mayUse`), `holo/module/HoloModule.java`
  (`handleAction`).
- **Tous** les `handleAction` des modules du mod et des démos (grep). Dresser leur table de droits actuelle.

## Demande d'API (à valider avant de commencer)
- `protected boolean acceptsAction(ServerPlayer player, @Nullable HoloObject source)` sur `HoloModule`, appelée
  après les vérifications du framework.
- Défaut :
  - mémoire de joueur : comme aujourd'hui ;
  - dimension ou position **sans source** : `player.hasPermissions(2)` ;
  - avec une source : comme aujourd'hui (`PUBLIC` + `HoloAccess`).
- Les modules qui doivent rester ouverts **surchargent** explicitement :
  - Space Map : actions 0-8 de tout joueur à portée ;
  - Space Controller : dépend de l'interrupteur « ops » ;
  - démos.

**Conséquence** : un module tiers sans surcharge devient réservé aux ops pour ses actions non sourcées. C'est voulu,
mais c'est un changement de comportement.

**Alternative** : pas de défaut restrictif ; le crochet existe mais vaut `true`. On documente seulement.

## Zone
`holo/module/HoloModule.java`, `holo/memory/HoloContainers.java`, les `handleAction` de `modules/**` et `demo/**`.

## Livrables
1. Table des droits avant / après de chaque module (compte rendu), **identique** pour les modules du mod.
2. Le crochet validé ; les surcharges nécessaires ; les vérifications « par redondance » retirées quand le crochet
   les couvre. Les vérifications métier restent : op en premier dans les Configurators, fenêtre source, bornes.
3. Javadoc du crochet : règle par défaut, exemples.

## Interdits
- Changer qui peut faire quoi pour un module du mod. La table avant / après doit être identique.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (LAN, un op et un non-op) :
  - Space Map : un non-op change la sélection ;
  - Space Configurator : un non-op est refusé ;
  - Space Controller : un non-op est refusé en mode « ops » et accepté sinon ;
  - Area Configurator : un non-op est refusé ;
  - Radar, Config, Quick Action et Keys Editor : le propriétaire peut agir ;
  - démos.

## Compte rendu attendu
Table des droits, crochet, surcharges, vérifications retirées, fichiers.
