# F17 — Un seul style de branchement client

**Objectif** : tous les modules qui ont une logique client la branchent de la même façon, par un `Backend` installé
au setup client. L'installation suit une forme unique. Rapport § 4.6.

**Lot** : B   **API** : non (chaque module ajoute son propre `Backend`, sans toucher au framework)   **Dépendances** :
F16

## Constat
- Branchement par Backend poussé : Radar, SpaceEnvironment, SpacePilot, SpaceController, SpaceConfigurator,
  AreaDisplayer, AreaConfigurator, Config, QuickAction.
- Branchement par polling à chaque tick d'une classe client, sans Backend : Targeting, AttackGuard, SpaceKnowledge,
  Keys. Radar fait les deux.
- L'installation varie : classe `Setup` imbriquée (`RadarClient`, `QuickActionController`, `ConfigController`),
  classe du backend elle-même, ou lambda (`QuickActionController.java:46`).

## À lire avant
- Rapport § 3.5, § 4.6 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.2.
- Les modules cités et leurs classes `client/modules/...`. Modèle : `RadarModule.Backend` / `RadarClient`.

## Zone
`modules/camera/targeting|attackguard|keys|spaceknowledge|radar|quickaction|config` (ajout ou ajustement d'un
`Backend`), les classes client correspondantes.

## Livrables
1. Forme cible, écrite dans le `package-info` de `client/modules` :
   - interface `Backend` dans le module commun ;
   - champ statique et `install(Backend)` `@ApiStatus.Internal` ;
   - implémentation dans `client/modules/...` ;
   - installation par **une** classe `XxxClientSetup` (ou le backend lui-même) au `FMLClientSetupEvent`.
2. Targeting, AttackGuard, SpaceKnowledge et Keys passent à cette forme quand une logique client existe. Le polling
   restant (lecture de l'état chaque tick) passe par `ClientHoloWorld.localModule` (F16).
3. Les installations existantes sont alignées sur la même forme (pas de lambda anonyme, pas de mélange).
4. Aucune différence de comportement.

## Interdits
- Toucher le framework (F16 doit avoir fourni le cache).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : ciblage (entité, bloc, à travers les murs avec radar), Attack Guard (croix, attaque bloquée), Keys Editor,
  Space Knowledge (surcouche, Quick Action), radar, roue, Config.

## Compte rendu attendu
Forme retenue, modules migrés, fichiers, scénarios.
