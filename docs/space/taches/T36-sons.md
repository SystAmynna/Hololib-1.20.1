# T36 — Sons du vaisseau

**Objectif** : (D26) donner des sons au vaisseau, entendus par **tous les joueurs de la dimension** (le monde est le
vaisseau) et déduits par chaque client de l'état de l'Environment :
- accélération / décélération : **moteurs spatiaux** dont le son **monte** ou **descend** ;
- rotation (joystick, roulis) : **léger craquement de la coque** ;
- saut : **bruit de moteur** au départ (convergence, T35), souffle au flash, puis pendant la distorsion **craquements de
  coque** et **effet de vitesse** ; flash d'arrivée.

**Dépendances** : T32 (boost), T35 (phases visuelles du saut).

## À lire avant
- [`decisions.md`](../decisions.md) D26, C80 ; [`05-synchronisation.md`](../05-synchronisation.md) § 2 (entrée du
  pilote dans le bloc chaud).
- `client/interact/HoloSounds.java` (patron de lecture d'un son) ; `SpaceEnvironmentModule` (vitesse, entrée du
  pilote, saut, `displayTicks`) ; phases visuelles de T35.
- Sources Minecraft : `SoundManager`, `AbstractTickableSoundInstance`, `SimpleSoundInstance`, format de `sounds.json`.

## Livrables
1. Sons **CC0** (Freesound, OpenGameArt ; licence de chaque fichier vérifiée) : boucle moteur, montée / descente,
   craquements (plusieurs variantes), grondement de distorsion en boucle, souffles / flash. Téléchargés dans un dossier
   vide du scratchpad ; convertis en `.ogg` Vorbis **mono** (ffmpeg), normalisés, boucles sans clic. Repli par synthèse
   (script Python / ffmpeg) seulement pour un son introuvable, à signaler.
2. `src/main/resources/assets/hololib/sounds/space/*.ogg`, `assets/hololib/sounds.json` (sans sous-titres : le mod n'a
   pas de clés de traduction, C15), crédits dans `assets/hololib/sounds/space/CREDITS.md`.
3. Client `client/modules/dimension/space/SpaceSoundController` : sons non positionnels, catégorie `AMBIENT` (C80),
   `SoundEvent.createVariableRangeEvent` (aucun registre serveur) ; boucle moteur `AbstractTickableSoundInstance` dont
   hauteur et volume suivent gaz / vitesse (montée à l'accélération, descente à la décélération, plus marquée en
   boost) ; craquements aléatoires au-delà d'un seuil de taux de rotation ; séquence du saut calée sur T35. Appelé depuis
   `SpaceEnvironmentModule.clientTick` via le backend (pas d'abonnement Forge depuis un renderer) ; arrêt propre au
   changement de dimension, au retrait du module, à la pause.
4. Docs 02 (arborescence, § 5), 01.

## Interdits
- Sons sous licence autre que CC0 sans accord ; modifier une classe hors des packages Space ; ressources autres que
  `assets/hololib/sounds/**` et `assets/hololib/sounds.json`.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : le moteur monte en accélérant et redescend en ralentissant ; la coque craque en tournant ; la séquence du saut
  s'entend en entier ; un second joueur LAN qui ne pilote pas entend la même chose ; aucun son ne reste bloqué après
  avoir quitté la dimension.

## Compte rendu attendu
Fichiers (source, auteur, licence, durée), paramètres (seuils, volumes, hauteurs), écarts, scénario.
