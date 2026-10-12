# F18 — `ObjectState` et version de format NBT

**Objectif** : l'état d'un objet holographique est décrit **une seule fois** (sauvegarde NBT et réseau), et le NBT
des mémoires porte un numéro de format. Ajouter un champ ne touche plus six endroits, et les migrations ne devinent
plus. Rapport § 5.3, § 7.2, recommandation 13.

**Lot** : B   **API** : **oui : à valider** (type public, `HoloObject.restore` retiré)   **Dépendances** : F25
(infrastructure de tests)

## Constat
- Deux chemins de sérialisation parallèles se rejoignent dans `HoloObject.restore(...)` à **9 paramètres
  positionnels** « null = inchangé » (`holo/object/HoloObject.java:285`) :
  - NBT : `HoloModule.saveAll` / `loadAll` (`:263-302`) ;
  - réseau : `ObjectData.of` / `write` / `read` (`network/HoloSyncData.java`).
- Les migrations reposent sur la présence des champs (`contains("scaleX")`, `display` optionnel, `HoloTransform.load`
  qui rend null sur un tag vide). Il n'y a ni `dataVersion` ni `format`.

## À lire avant
- Rapport § 5.3, § 7.2 ; [`01-architecture-cible.md`](../01-architecture-cible.md) § 3.3.
- `holo/object/HoloObject.java`, `HoloTransform.java`, `holo/module/HoloModule.java` (`saveAll`, `loadAll`),
  `network/HoloSyncData.java` (`ObjectData`), `holo/memory/HoloMemory.java` (sauvegarde des mémoires),
  `client/holo/ClientHoloContainer.java` (`apply`).

## Demande d'API (à valider avant de commencer)
- Record ou classe `holo/object/ObjectState` : ancrage, transform, visibilité, accès, affichage, ouvert, offset
  d'ouverture, repère de rotation, portée d'édition. Méthodes `save(CompoundTag)`, `load(CompoundTag, format)`,
  `write(FriendlyByteBuf)`, `read(FriendlyByteBuf)`, `applyTo(HoloObject)`, `of(HoloObject)`.
- `HoloObject.restore(...)` est remplacé (package-private ou retiré).
- Entier `format` à la racine du NBT d'une mémoire. Absent = format actuel `0`, relu à l'identique ; écrit `1` dès
  qu'un changement l'exige.

**Alternative** : garder `restore`, mais lui passer un objet de paramètres. Pas de version de format.

## Zone
`holo/object/*`, `holo/module/HoloModule.java`, `network/HoloSyncData.java`, `holo/memory/HoloMemory.java`,
`client/holo/ClientHoloContainer.java`, `src/test/...` (nouveaux tests).

## Livrables
1. `ObjectState` validé, utilisé par les deux chemins. Le format **binaire réseau** et le **NBT** sont identiques à
   aujourd'hui, octet pour octet (sauf l'ajout de `format` si validé). Le protocole reste `"9"`.
2. Tests (JUnit, F25) :
   - aller-retour NBT et réseau d'un état complet ;
   - relecture d'un NBT **ancien**, construit à la main d'après le code actuel : sans `display`, sans `scaleX`…
3. Javadoc de la procédure « ajouter un champ à un objet » (un seul endroit, plus la migration).

## Interdits
- Changer le format sur le réseau ou sur le disque, en dehors de l'entier `format` validé.
- Toucher les états propres des modules (`writeSync` / `save`).

## Critères d'acceptation
- `compileJava` et `test` réussissent.
- En jeu :
  - un monde d'avant la tâche (avec fenêtres déplacées, objets `OFF`, accès en liste, portée modifiée) se relit à
    l'identique ;
  - LAN : un second joueur reçoit les mêmes placements.

## Compte rendu attendu
API, tests ajoutés, preuve d'identité des formats (méthode), fichiers.
