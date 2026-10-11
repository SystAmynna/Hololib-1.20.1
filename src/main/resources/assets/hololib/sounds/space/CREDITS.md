# Crédits des sons du vaisseau (`assets/hololib/sounds/space/`)

Les sons de ce dossier sont soit **synthétisés** pour ce mod (moteurs, charge du saut : aucune source extérieure, mis
dans le domaine public avec le reste de ces fichiers, CC0), soit dérivés d'enregistrements publiés sous **CC0 1.0**
(domaine public, <https://creativecommons.org/publicdomain/zero/1.0/>) ; la licence de chaque source a été vérifiée sur
sa page le 2026-10-10. Le crédit n'est pas exigé : il est donné par courtoisie.

Traitement commun (scripts de T36, ffmpeg + Python) : **mono 44,1 kHz**, composante continue retirée, fondus d'entrée
et de sortie, Ogg Vorbis (qualité 5). Sons ponctuels normalisés en crête (−1 dBFS) ; boucles normalisées en RMS (−12
dBFS, −14 pour `warp_wind` et `engine_whine`, crête ≤ −1 dBFS) et **sans clic** : composantes tonales à nombre entier
de périodes sur la boucle, couches de bruit dont la fin est fondue à puissance constante dans le début. « Vitesse
variable » = lecture plus lente ou plus rapide (hauteur et durée changent ensemble, comme une bande).

## Synthétisés (retouche de T36)

| Fichier | Durée | Contenu |
|---|---|---|
| `engine_hum.ogg` | 8,00 s (boucle) | ronronnement de réacteur : drone à 46 Hz (voix désaccordée à 46,25 Hz, battement lent ; harmoniques 92, 138, 184 Hz, respiration à 0,375 Hz) + bruit rose passe-bas 160 Hz (grondement) + bruit rose en bande 500–1 400 Hz très bas (souffle) |
| `engine_whine.ogg` | 6,00 s (boucle) | sifflement tonal de turbine : 320 Hz (voix à 320,5 Hz), harmoniques 640 et 960 Hz faibles, sous-harmonique 160 Hz, bruit en bande étroite autour de 320 / 640 Hz |
| `jump_charge.ogg` | 4,00 s (boucle) | bourdonnement de charge : 55 / 110 / 165 / 220 / 330 Hz (voix à 110,25 Hz), pulsation d'amplitude à 4 Hz ; sifflement léger à 1 760 Hz (tons + bruit en bande étroite) |

## Dérivés de sources CC0

| Fichier | Durée | Source(s) | Auteur | Traitement |
|---|---|---|---|---|
| `hull_groan1.ogg` | 4,44 s | `wobble_05_44k_32bit_stereo.wav`, « 27 Metal Audio Samples (SFX) », <https://opengameart.org/content/27-metal-audio-samples-sfx> | Black Lodge Games, LLC (blacklodgegames) | vitesse 0,30 (≈ −21 demi-tons), passe-bas 450 Hz (deux pôles), passe-haut 30 Hz, réverbération lointaine (45 %) |
| `hull_groan2.ogg` | 4,12 s | `wobble_08_44k_32bit_stereo.wav`, idem | Black Lodge Games | idem |
| `hull_groan3.ogg` | 4,42 s | `wobble_03_44k_32bit_stereo.wav`, idem | Black Lodge Games | vitesse 0,28, passe-bas 500 Hz, réverbération 45 % |
| `hull_groan4.ogg` | 6,99 s | `metal_grit_01_44k_32bit_stereo.wav`, idem | Black Lodge Games | vitesse 0,22, passe-bas 380 Hz, réverbération 45 % |
| `hull_tonk1.ogg` | 3,21 s | `dull_metal_collision_07_44k_32bit_stereo.wav`, idem | Black Lodge Games | vitesse 0,40, passe-bas 420 Hz, passe-haut 35 Hz, réverbération 55 % |
| `hull_tonk2.ogg` | 4,18 s | `dull_metal_collision_12_44k_32bit_stereo.wav`, idem | Black Lodge Games | vitesse 0,38, passe-bas 400 Hz, réverbération 55 % |
| `hull_tonk3.ogg` | 3,75 s | `dull_metal_collision_11_44k_32bit_stereo.wav`, idem | Black Lodge Games | vitesse 0,42, passe-bas 450 Hz, réverbération 55 % |
| `warp_engine.ogg` | 4,50 s | `warp engine engage_0-16bit.wav` (0 à 4,5 s), « Warp Engine Engage Sound », <https://opengameart.org/content/warp-engine-engage-sound> ; `qubodup-slomo1.wav`, « Wind, hit, time morph », <https://opengameart.org/content/wind-hit-time-morph> | bart ; qubodup | mélange (engagement 0,85, montée « slomo » 0,6 calée sur la première seconde = convergence), fondu de sortie 1,8 s |
| `warp_flash.ogg` | 1,00 s | `qubodup-megaswosh1.wav`, « Wind, hit, time morph » ; `lowFrequency_explosion_001.ogg`, « Sci-fi Sounds » 1.0, <https://kenney.nl/assets/sci-fi-sounds> | qubodup ; Kenney (www.kenney.nl) | souffle à vitesse 0,6 + coup sourd (0,5) |
| `warp_rumble.ogg` | 7,00 s (boucle) | `deep_rumble.ogg` (10 à 18 s), « underwater or space engine rumble », <https://opengameart.org/content/underwater-or-space-engine-rumble> | gmason | bouclé (fondu 1 s) |
| `warp_wind.ogg` | 5,16 s (boucle) | `wind woosh loop.ogg`, « wind whoosh loop », <https://opengameart.org/content/wind-whoosh-loop> | SketchMan3 | bouclé (fondu 0,8 s) |
| `warp_arrival.ogg` | 2,00 s | `lowFrequency_explosion_000.ogg`, « Sci-fi Sounds » ; `qubodup-megaswosh2.wav`, « Wind, hit, time morph » | Kenney ; qubodup | grondement (0,9) + souffle à vitesse 0,7 (0,6) |

Réverbération des sons de coque : convolution (ffmpeg `afir`) par une réponse impulsionnelle synthétique (bruit à
décroissance exponentielle, constante 0,55 s, longueur 2,4 s, assombrie par un passe-bas 1,8 kHz, pré-délai 30 ms),
mélangée au son direct.

Licences vérifiées :

- Kenney, « Sci-fi Sounds » : page <https://kenney.nl/assets/sci-fi-sounds> (« License : Creative Commons CC0 ») et
  `License.txt` de l'archive (« License: (Creative Commons Zero, CC0) »).
- Black Lodge Games, « 27 Metal Audio Samples (SFX) » : page OpenGameArt (CC0) et `cc0_license.txt` de l'archive.
- bart, qubodup, gmason, SketchMan3 : pages OpenGameArt citées, licence CC0 seule.
