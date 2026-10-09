# T10 — Rendu du cube galaxie

**Objectif** : le renderer de `SpaceMapCube` en mode galaxie : arêtes, graduations en années-lumière, étoiles des
systèmes et leur nom au-dessus, mise en évidence de la sélection.

**Dépendances** : T09.

## À lire avant
- [`04-mathematiques.md`](../04-mathematiques.md) § 7 (cadrage, graduations `nice`) ; [`01-vision.md`](../01-vision.md).
- `client/render/holo/HoloObjectRenderer.java`, `HoloObjectRenderers.java`, `HoloDrawContext.java`,
  `HoloWorldBridge.java` (repère reçu : translation, orientation, échelle déjà appliquées).
- `client/render/demo/DemoCubeRenderer.java` (cube en quads), `client/render/edit/HoloGizmoRenderer.java`
  (segments fins en quads croisés).
- `client/render/holo/BufferHoloCanvas.java` (`text` : `font.drawInBatch`), pour les étiquettes.
- `client/render/type/HoloRenderTypes.java`.

## Livrables
- `client/render/modules/monde/spaceconfigurator/SpaceMapCubeRenderer.java`, `SpaceConfiguratorRenderers.java`,
  `package-info.java` ; appel dans `ModuleRenderers` (remplace le renderer provisoire de T09).

## Spécification
- Le renderer lit la copie synchronisée du module (`object.module()`), ne calcule rien d'autre que la mise en page.
- Arêtes : 12 segments fins (quads croisés, `TRANSLUCENT_QUADS`, couleur accent atténuée) ; faces très légèrement
  teintées (alpha faible) pour lire le volume.
- Graduations : pas `nice(H / 4)` al sur trois arêtes (doc 04 § 7), petits traits + étiquettes (« 5 al »).
- Étoiles : billboard (face caméra, échelle du cube compensée pour garder une taille constante), taille et couleur
  selon la luminosité / couleur de l'étoile principale ; système courant de l'Environment marqué (anneau), système
  sélectionné en surbrillance.
- Noms : texte billboard au-dessus de chaque étoile (compenser orientation et échelle de l'objet, puis rotation de la
  caméra `Minecraft.getInstance().gameRenderer.getMainCamera().rotation()`, échelle texte `±0,025` comme les
  étiquettes de nom vanilla), seulement en famille `WORLD`, et seulement à moins de ~24 blocs pour la lisibilité.
- Mode système : déléguer à une méthode vide documentée (T12 la remplira).
- Aucune allocation par frame (matrices, vecteurs, chaînes des graduations mises en cache quand `H` change).

## Interdits
- Logique d'état dans le renderer ; `RenderSystem` direct ; modifier le framework.

## Critères d'acceptation
- `compileJava` réussit.
- En jeu : la galaxie par défaut apparaît dans le cube avec ses noms lisibles, l'échelle graduée est cohérente avec
  les positions ; tourner / mettre à l'échelle le cube garde étoiles et textes droits et lisibles.

## Compte rendu attendu
Fichiers ; choix visuels ; performances observées avec beaucoup de systèmes (si testé).
