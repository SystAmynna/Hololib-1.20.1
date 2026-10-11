# T45 — Import d'une texture d'astre

**Objectif** : (D21, D27) depuis la fenêtre du Configurator, un op **importe une image** de son ordinateur pour
l'astre sélectionné ; l'image est **envoyée au serveur**, stockée, appliquée à l'astre, puis récupérée par chaque
client qui en a besoin.

**Dépendances** : T44.

## API validée (D27)
L'utilisatrice a validé un **canal réseau dédié aux textures Space** (le transport du framework plafonne une action à
32 767 o et envoie la synchro d'un module à toute la dimension). Contraintes :
- canal propre (`SimpleChannel` `hololib:space_textures`, protocole propre) déclaré dans un package Space et enregistré
  par un abonné `FMLCommonSetupEvent` Space ; **aucune** modification de `HoloNetwork`, de son protocole ni du
  framework ;
- tout autre besoin hors de ce canal reste une demande d'API (s'arrêter et la décrire).

## À lire avant
- [`decisions.md`](../decisions.md) D21, D27, C79 ; [`03-modele-donnees.md`](../03-modele-donnees.md) (référence
  `custom:<sha1>`, T28) ; `network/HoloNetwork.java` (modèle d'enregistrement, **ne pas modifier**) ;
  `SpaceArchive` (stockage par niveau) ; `SpaceBodyTextureCache` (T30, point d'entrée des textures importées).
- Minecraft / LWJGL : `org.lwjgl.util.tinyfd.TinyFileDialogs` (présent dans les dépendances), `NativeImage`,
  `DynamicTexture`, `TextureManager.register`.

## Livrables
1. **Client** : bouton « Importer » (op, astre sélectionné) → sélecteur natif `TinyFileDialogs` ouvert hors du fil de
   rendu (le jeu continue) ; PNG / JPEG lu, redimensionné à ≤ 1024×512 (2:1, recadré ou étiré : le dire), réencodé en
   PNG, ≤ 2 Mo (C79) ; sha1 ; envoi en morceaux (≤ 30 Ko), puis demande d'application à l'astre.
2. **Serveur** : réception par joueur (op revérifié, une transmission à la fois, délai d'expiration, taille et
   dimensions validées en décodant le PNG), stockage `data/hololib_space_textures/<sha1>.png` du niveau, quota (64
   textures ; au-delà, refus avec message), puis édition de l'astre (`custom:<sha1>`) par l'API de l'Environment.
   Fichiers orphelins : laissés (le dire) ou nettoyés au chargement.
3. **Distribution** : un client qui rencontre `custom:<sha1>` inconnu le demande ; le serveur lui envoie **à lui
   seul** en morceaux ; cache client en mémoire (`DynamicTexture` par hash, libérée au changement de monde) ; repli
   `auto` en attendant.
4. Docs 02 (canal, flux), 03, 05 § 5 (coût d'un import, d'un arrivant), `decisions.md` (paramètres retenus).

## Interdits
- Modifier `HoloNetwork` ou toute classe hors des packages Space ; texture lue côté serveur sans validation.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu (LAN) : un op importe une image pour la Lune → appliquée dans le ciel et sur la carte chez les deux joueurs ;
  le second client se reconnecte → il la récupère ; image trop grande ou fichier non image → refus clair ; un non-op
  ne peut ni importer ni envoyer de morceau accepté.

## Compte rendu attendu
Fichiers, format des paquets, limites, écarts, scénario.
