# F30 — Terrain de l'Area Displayer dans des `VertexBuffer`

**Objectif** : le maillage du terrain est téléversé une fois par section, au remaillage, puis seulement dessiné
(matrice du cube). Il n'est plus ré-émis quad par quad à chaque frame. C'est le gain de performance le plus fort
relevé par le rapport (§ 6.1, recommandation 18).

**Lot** : C   **API** : non   **Dépendances** : aucune

## Constat (vérifié)
- `client/render/modules/monde/areadisplayer/AreaDisplayerCubeRenderer.java:294-306` (`terrain`) : pour chaque
  maillage de section et chaque quad (grille jusqu'à 256³ voxels), 4 appels `vertex` à **chaque frame** et pour
  chaque displayer.
- Le remaillage est limité à 32 sections par tick (`AreaTerrainClient.java:33`).
- Le terrain est translucide sans tri, avec élimination des faces arrière (`RenderType` local).

## À lire avant
- Rapport § 6.1 ; CLAUDE.md « Modules Area », « Conventions » (pas d'OpenGL brut).
- `client/render/modules/monde/areadisplayer/*`, `client/modules/monde/areadisplayer/*` (`AreaView`, `AreaMesher`,
  `AreaTerrainClient`).
- Sources vanilla : `com.mojang.blaze3d.vertex.VertexBuffer`, `BufferBuilder`, et leur usage par vanilla (nuages,
  ciel : `LevelRenderer`). Oculus et Embeddium : comportement des `VertexBuffer` sous shaderpack (vérifier, ou
  prévoir un repli vers l'émission immédiate quand `ShaderPacks.inUse()`).

## Zone
`client/render/modules/monde/areadisplayer/**`, `client/modules/monde/areadisplayer/**`.

## Livrables
1. Un `VertexBuffer` par section non vide, (re)construit **au remaillage**, sur le fil de rendu, avec la même
   couleur, le même format et le même `RenderType` qu'aujourd'hui. Il est libéré quand la section se vide, que la
   vue est retirée ou au déchargement (aucune fuite GPU : compteur de buffers dans le log de debug).
2. Dessin : un `drawWithShader` par section, avec la matrice du cube et l'état du `RenderType` posé une seule fois.
   Ordre et état GL identiques. Restauration en `finally`.
3. Repli avec shaderpack (si nécessaire) : émission immédiate comme aujourd'hui. Le choix est documenté.
4. **Mesure avant / après** :
   - zone de 512 × 128 × 512 blocs en 1:2, une fois puis trois displayers ;
   - FPS moyen et minimum sur 30 s, regard fixe ;
   - avec et sans Oculus.

## Interdits
- Changer l'apparence (couleurs, transparence, faces) ou le pas de remaillage.
- OpenGL brut hors des classes `blaze3d` (règle du projet).

## Critères d'acceptation
- `compileJava` réussit.
- En jeu :
  - rendu identique ;
  - Actualiser et Auto Update remaillent correctement ;
  - changer la résolution reconstruit les buffers ;
  - retirer le module ou le marker les libère ;
  - mesure jointe montrant un gain.

## Compte rendu attendu
Conception (cycle de vie des buffers), fichiers, mesures, comportement avec shaderpack.
