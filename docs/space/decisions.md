# Décisions

Journal des décisions de conception. **D** = validée par l'utilisatrice ; **C** = choix par défaut de
l'orchestrateur, non bloquant, modifiable sur simple demande ; **L** = limite connue acceptée. Toute nouvelle décision
prise pendant une tâche est ajoutée ici par l'orchestrateur.

## Validées par l'utilisatrice (2026-10-08)

| # | Décision | Conséquence |
|---|---|---|
| D1 | Ciel vanilla masqué par un **fond opaque** dessiné par le renderer de l'Environment, **plus** une extension d'API : relais de `ViewportEvent.ComputeFogColor` vers les `SkyRenderer` | seule modif d'API autorisée : [`06-extension-api.md`](06-extension-api.md), tâche T03 |
| D2 | La galaxie est stockée **dans le Space Environment** de la dimension (une galaxie par dimension) | le Configurator édite l'Environment de sa dimension ; pas de stockage global |
| D3 | **Un système à la fois**, orbites **animées** sur le temps de jeu ; les autres systèmes = étoiles de fond ; pas de voyage interstellaire | le système courant se choisit dans le Configurator |
| D4 | Yaw / pitch en **joystick virtuel** : caméra bornée dans un cône autour du cercle avant, l'écart donne une vitesse de rotation | [`04`](04-mathematiques.md) § 6 |

## Choix par défaut (non bloquants)

| # | Choix | Alternative si l'utilisatrice le demande |
|---|---|---|
| C1 | Axe avant du monde = **nord** (`(0, 0, −1)`), réglable dans l'Environment (via la fenêtre du Configurator, mode galaxie, ligne « Avant du monde ») | autre axe par défaut |
| C2 | Fenêtre du Controller : accès **`EVERYONE`** (tout joueur qui la voit peut piloter), un seul pilote à la fois | accès ops seulement, ou liste |
| C3 | Unités : km (rayons, positions dans le système), UA (orbites), jours (périodes), al (galaxie) ; vitesse du monde en km/tick | — |
| C4 | Translation des étoiles par **une seule poignée** posée sur le système sélectionné (pas un objet par étoile) : évite N panneaux d'outils dans la session d'édition | un objet éditable par étoile |
| C5 | Orbites éditées par une **poignée d'orbite** (outils Rotation + Échelle) ; les autres paramètres (rayon, couleur, période, phase, luminosité) dans la fenêtre | — |
| C6 | Bornes : 256 systèmes, 32 corps par système, noms de 32 caractères ([`03`](03-modele-donnees.md) § 3) | bornes plus hautes (coût réseau, [`05`](05-synchronisation.md) § 5) |
| C7 | Échelle de temps par défaut : 1 tick = 1 heure simulée (une année ≈ 7 min) ; réglable | — |
| C8 | Mode système du Configurator : **temps figé** à l'entrée (bouton « Animer » optionnel) ; **loupe des lunes** pour les rendre visibles | échelle logarithmique |
| C9 | Tout le Configurator (cube, fenêtre, poignées) est **masqué** aux non-ops (`shown()` via backend) | cube visible de tous, fenêtre réservée |
| C10 | Le système courant se choisit dans la fenêtre du Configurator (bouton « Monde ici ») | commande dédiée |
| C11 | Le cube du Configurator garde une **échelle uniforme** (ramenée à la moyenne s'il est étiré) | — |

| C12 | (T01) `SpaceGalaxy` mutable, éléments immuables (records) dont le constructeur valide et assainit : tout objet existant est valide | — |
| C13 | (T01) Angles d'orbite ramenés à [−180, 180[, phase à [0, 360[ ; rayons et orbites en `double` sur le réseau (calculs identiques serveur / clients) | — |
| C14 | (T01) Galaxie par défaut : Sol (Soleil, Terre + Lune, Mars, Jupiter) + 11 voisines réelles placées par ascension droite / déclinaison (`+Y` = pôle nord céleste) | autre contenu |
| C15 | (T01, clos en T13) Messages en `Component.literal` français sans accents, comme tout le mod (aucun `Component.translatable`) | clés de traduction |

| C16 | (T02) Garde-fou du doc 04 § 5 sur le **segment** du pas (arrêt à l'entrée de la sphère `R + h/2` puis glissement) au lieu de la projection de l'extrémité, qui laissait traverser le Soleil à `V_max` | — |
| C17 | (T02) Kepler : départ de Danby `M + 0,85·e·signe(sin M)` (résidu 5e-15 à e = 0,95 au lieu de 1,3e-4) ; rotations en `double` via `StrictMath` (`SpaceRotations`), pas JOML, pour un résultat identique sur toute JVM | — |
| C18 | (T02) Axe avant défini par yaw / pitch (pitch borné à ±80°) ; `R = normalize(F × Y)`, `U = R × F` | — |

| C19 | (T04) Envois de l'Environment limités : éditions du bloc froid et réglages au plus une fois / 4 ticks ; éditions hors bloc froid envoyées au plus une fois / 20 ticks (seul moyen de marquer la sauvegarde) ; un envoi à l'arrêt du monde | envoi immédiat (doc 05 d'origine) ou demande d'API « sauvegarder sans synchroniser » |
| C20 | (T04, revu en T07e) Affichage client sur une horloge propre (rattrapage doux du temps du niveau) avec historique de 32 ticks ; observateur en retard de ≈ 2 à 8 ticks pour corriger avant affichage | — |
| C21 | (T04) `Backend` de l'Environment : `clientInstance()`, `gameTime()`, `localPlayer()` ; `nearestBody()` calculé chaque tick serveur (+ `nearestBodyIndex()`, `nearestAltitude()`) ; galaxie par défaut créée dans `onLoad` (l'installation par commande n'appelle pas `load`) | — |
| C22 | (T04) `setCurrentSystem` incrémente la révision ; sur le système déjà courant, il remet la pose par défaut. Correction (revue en T07e) : voir C32. La prédiction du pilote démarre quand l'état reçu le désigne | — |

| C23 | (T05, T07f) Corps en calottes sphériques (LOD 4 niveaux et normale exacte en perspective depuis T07f) (rayons de dessin répartis géométriquement 105 → 50), halo d'étoile en couronne plafonnée à 100° ; fond = sphère de 120 blocs ; brouillard `(0,01 ; 0,01 ; 0,02)` hors fluide dès que l'Environment est présent (une ou deux frames d'horizon noir sous le ciel vanilla avant la synchro) | — |

| C24 | (T06, T08) Fenêtre du Controller 176 × 108 px (au lieu de ≈ 160 × 90 ; +12 px pour l'interrupteur D5) pour « Pilote par <nom de 16 car.> » sur une ligne ; couleurs de `ConfigWindow` | 160 px avec libellés courts (« Pilote : X ») |
| C25 | (T06) Le pad rend la main si un autre pilote apparaît, si l'Environment disparaît, ou si 40 ticks après `BEGIN` l'état ne désigne toujours pas le joueur ; sans Environment la zone ne prend pas le clic ; poste occupé = clic pris sans capture ; « Pilote depuis un autre poste » affiché | — |
| D9 | (2026-10-09) **Approche plus près des corps** : altitude plancher `0,01 · R` (Terre 64 km, Lune 17 km, Soleil 6 960 km), zone de freinage d'approche 2 rayons (au lieu de 10) ; taille apparente non plafonnée (jusqu'à 90°) ; sphères plus fines de près (LOD selon la taille apparente) | doc 04 §§ 4, 5 ; tâche T07f |
| D8 | (2026-10-09) **Ciblage du référentiel = distance × regard** (remplace la règle « plus proche + 75 % » de D7) : score `P · G` (proximité `1/h`, regard `1/(1+(θ/20°)²)` — θ₀ porté de 10° à 20° le 2026-10-09 sur la visée du pilote ou l'axe avant), bascule si un autre corps fait 1,25 × mieux pendant 10 ticks, cooldown 60 ticks, fondu du mouvement d'entraînement sur 40 ticks ; visée du pilote transmise dans `SpacePilotInput` ; affichage interpolé dans le repère du référentiel (lève L10) | doc 04 § 5 point 0 ; tâche T07d |
| D7 | (2026-10-09) **Vitesse relative au corps de référence** : le plus proche (altitude), avec hystérésis (bascule si un autre corps est à moins de 75 % de l'altitude du référentiel courant) ; le monde est entraîné par le mouvement orbital du référentiel (à `V = 0` près d'une planète, elle ne s'enfuit plus) ; référentiel synchronisé, sauvegardé, affiché par le Controller. Lève la limite L6 | doc 04 § 5 point 0 ; tâche T07c |
| D6 | (2026-10-09) **Vitesse signée** : Reculer fait partir le monde en arrière (jusqu'à `−V_max`) ; **Espace** (touche Saut, `key.jump`) maintenu = frein qui ramène `V` vers 0 des deux côtés jusqu'à l'arrêt, et **prime sur les gaz** (Avancer / Reculer ignorés) ; roulis et joystick restent actifs | doc 04 § 5 ; tâche T07b |
| D5 | (2026-10-09) Interrupteur « Pilotage : tous / ops » sur le Controller, ajouté en **T08** (réglable par un op ; `EVERYONE` par défaut, C2). Réalisé sans `HoloAccess` (qui ne sait pas dire « ops ») : accès de la fenêtre `EVERYONE`, `BEGIN` refusé aux non-ops en mode ops, pilote non-op libéré au passage en mode ops ou au de-op ; un non-op peut cliquer l'interrupteur mais le serveur refuse avec un message | interrupteur masqué / désactivé pour les non-ops (sous-classe de `HoloWidgetSwitch`) |

| C27 | (T07) Pendant le pilotage, clics gauche / droit / molette capturés sans effet (sinon on casse des blocs en visant) ; cône 30°, zone morte 2°, retour sur l'axe avant en 0,4 s ; prédiction appelée à l'envoi (même suite d'entrées que le serveur) | comportement strict de la carte (clics libres) |
| C28 | (T07) Signes du joystick : regard à droite → lacet `+` ; regard en haut → tangage `+` ; D → roulis `+`. Correction éventuelle **uniquement** dans `SpaceMotion.step` (serveur et prédiction partagent la fonction) | — |

| C29 | (T08) Cercles du joystick visibles seulement pour le pilote local **et** pendant sa capture (`localPilotHere()` exige `pad.piloting()`) ; anneaux 0,06 / 0,035 bloc à 2 blocs, épaisseur 0,007 ; `SpaceControllerModule.Backend` propre (`localPlayer`, `localOp`) | tailles à juger en jeu |

| C30 | (T07c) `step` reçoit les temps de début **et** de fin du pas (recalculer le début depuis la fin dérivait) ; chaque corps est traité dans son propre repère (segment relatif) ; freinage des autres corps par `min(v·n, (D−Δp)·n)` | — |

| C31 | (T07d) Choix du référentiel une seule fois au début du pas (`SpaceFrameState`) ; corps supprimé remplacé tout de suite par le meilleur score ; pose par défaut : meilleur score visée sur l'axe avant ; `AIM_RANGE` (30°) recopie `CONE_ANGLE` (le package `math` ne dépend pas de `monde`) ; NBT `frame { ref, prev, since, cand, candt }`, anciennes sauvegardes (`ref`) relues | — |

| C32 | (T07e) Réglages du lissage client (`SpaceEnvironmentModule`) : `HISTORY` 32, `CLOCK_GAIN` 0,1, `CLOCK_STRETCH` 0,25, `CLOCK_DEADBAND` 1 tick, `CLOCK_SNAP` 20 ticks, `MAX_OBSERVER_DELAY` 16, `LAG_DECAY` 0,05, `SOFT_TICKS` 5, `SOFT_MAX_ANGLE` 10° (orientation et déplacement apparent ; remplace « 1 % de l'altitude » ; seuil de position revu en T07f : `min(tan 10° · d / max(1, tan α), h/2)`), `RECONCILE` 0,1 | — |

| C33 | (T09) Configurator : « Recadrer » actif dès le socle, autres boutons désactivés jusqu'à T11 ; renderer provisoire déjà nommé `SpaceMapCubeRenderer` (T10 le complète) ; accès `players(<ops>)` sur la fenêtre seulement ; sans Environment, mode et sélection conservés (chargement tardif) ; sondage du cube chaque `serverTick` (échelle uniformisée côté serveur), placement de la fenêtre recalculé aussi côté client pendant le glisser | remettre en mode galaxie sans Environment |

| C34 | (T10) Cube : faces et halos dans un `RenderType` local sans écriture de profondeur (une face proche masquait les étoiles en vue oblique) ; textes à l'échelle 0,0125 (noms) / 0,01 (graduations) au lieu de 0,025 ; étoiles hors cadrage masquées ; noms seulement en famille monde à moins de 24 blocs ; cache de mise en page par module, reconstruit à la synchro ou au recadrage | texte 0,025 ; étoiles hors cadre atténuées sur le bord |

| C35 | (T11, T12) Fenêtre du Configurator 240 × 136 px, portée à 320 × 154 px en T12 pour les champs du mode système (liste à gauche, actions à droite) ; « Avant du monde » haut / bas à ±80° (borne du pitch, C18) ; « Monde ici » désactivé sur le système déjà courant (C22 y remettrait la pose par défaut) ; nom validé par Entrée **et** par Échap / regard qui s'éloigne (comportement de tous les champs du mod, `HoloTextInput`) ; « Éditer » actif dès T11 avec un panneau système provisoire et « Retour » ; « Retirer » confirmé par un second clic sous 60 ticks ; l'étoile ne bouge qu'au relâchement de la poignée | Échap annule le renommage (sous-classe du champ) ; « Monde ici » sur le courant = réinitialiser la pose |

| C36 | (T12) Mode système : loupe des lunes arrondie (1-2-5) ; décomposition de rotation propre en double ; « + Étoile » jamais racine (parent = étoile sélectionnée ou son ancêtre, sinon l'étoile principale) ; champs numériques en texte (virgule / point / `1e5`, valeur illisible = remise), palette de 10 couleurs, phase en barre à 1 % ; modification d'un corps champ par champ ; la période **suit** `a` quand on change la taille de l'orbite à la poignée (3e loi de Kepler, `P ∝ a^1,5`, validé par l'utilisatrice ; retouche manuelle possible ensuite) ; corps non masqués hors du cube | période indépendante de `a` ; onglets pour garder 240 × 136 ; masquer hors cube |

| C37 | (T13) Pose sauvegardée avec son tick (`time`) et recalée au premier tick serveur par le déplacement du référentiel (entraînement à `V = 0`, translation seule) : sans cela, un monde garé près d'une planète se retrouvait à des millions de km au rechargement. Ancienne sauvegarde sans `time` : pas de recalage ; fondu en cours ou `V ≠ 0` à la sauvegarde : approximation | — |
| C38 | (T13) Les renderers Space n'utilisent plus `VertexConsumer.vertex(Matrix4f, …)` (un `Vector4f` alloué par sommet) mais `transformPosition` dans un vecteur réutilisé. Le framework garde cette variante (`BufferHoloCanvas`, `DemoCubeRenderer`, `HoloHudPreview`) : hors périmètre, à signaler | — |

## Limites connues acceptées

| # | Limite |
|---|---|
| L1 | Nuages et pluie vanilla restent dessinés au-dessus du ciel spatial (les masquer demanderait `DimensionSpecialEffects` ou un Mixin : hors API actuelle) |
| L2 | Le ciel spatial disparaît quand vanilla ne dessine pas le ciel (lave, neige poudreuse, cécité / obscurité, brouillard épais) : `hiddenWithSky()` garde son défaut. Sous l'eau, il reste dessiné |
| L3 | L'éclairage et le cycle jour / nuit du monde Minecraft ne suivent pas l'étoile |
| L4 | Pas d'envoi partiel : un état complet de l'Environment circule à chaque mise à jour ([`05`](05-synchronisation.md) § 5) |
| L5 | Gizmos de translation et de rotation sur les axes du **monde**, pas ceux du cube |
| L7 | (T05) Ordre d'enregistrement `HoloSkyBridge` / `SpaceSkyRenderer` non garanti : la brume translucide de `planet_1` peut se mélanger au ciel vanilla (liseré bleuté). Correction = API d'ordre dans `HoloRenderers` (demande d'API à faire seulement si l'effet est constaté) |
| L8 | (T07) Une touche déjà enfoncée au moment de la prise de pilotage (Saut compris) n'est vue qu'après relâchement puis nouvel appui |
| L9 | (T07c, revue par D8) Un monde arrêté loin de deux corps peut changer de référentiel quand l'un passe (score 1,25 × meilleur pendant 0,5 s) : le fondu et le cooldown rendent la bascule douce et rare |
| ~~L10~~ | **Levée par D8 (T07d).** (T07c) Affichage : `pose(partialTick)` interpole le monde en ligne droite alors que le référentiel suit un arc : écart ≈ 14 km (Lune) à ≈ 1 100 km (lune type Io) en milieu de tick, saccade possible (≈ 0,4°) très près d'une lune rapide. Correction : interpoler `s − p_ref` puis rajouter `p_ref` au temps d'affichage |
| L11 | (T07d ; θ₀ porté à 20° à la demande de l'utilisatrice) Avec θ₀ = 10°, le regard pesait beaucoup : à 20 rayons de la Terre, la Lune peut devenir le référentiel si elle passe près de la visée (score 4,7 × celui de la Terre à 65° hors visée), puis la Terre revient. Réglage à décider après essai |
| ~~L12~~ | **Levée en T07e.** (T07d) Juste après un saut d'affichage (changement de système, correction par saut), un tick d'oscillation de l'ordre de l'ancienne saccade est possible |
| L13 | (T07e) Ciel de l'observateur en retard de ≈ 0,2 s (solo) à 0,45 s (réseau) ; quand un observateur prend les commandes, son ciel accélère (≤ +25 %) pendant ≈ 1 s pour rattraper |
| L14 | (T07e) Pilote : le serveur applique l'entrée à réception, d'où un écart prédiction / serveur ≈ vitesse × aller-retour (5,5–7,5° solo, ≈ 19° réseau) résorbé par la réconciliation à 10 %/tick : lisse, mais légère dérive après un relâché en réseau. Correction possible : entrées horodatées appliquées au tick du pilote avec rejeu serveur (change le format réseau et les règles : à valider) |
| L15 | (T09) Les non-ops reçoivent quand même la copie synchronisée du Configurator (objets `PUBLIC`, `writeSync` ne connaît pas le destinataire) : coût réseau seulement, rien n'est affiché |
| L16 | (T12) RESET de l'éditeur sur la poignée d'orbite donne une orbite sans grand sens (rotation identité, `a = H_sys / (σ·k)`) ; une synchro reçue pendant une molette sur une poignée peut écraser la valeur locale en cours (aussi la poignée de T11) |
| L17 | (T13) Après retrait puis réinstallation de l'Environment, les ids repartent de zéro : la sélection conservée du Configurator (C33) peut désigner un autre système |
| ~~L6~~ | **Levée par D7.** (T02) À l'échelle de temps par défaut (3 600 s/tick), la Terre avance d'environ 17 rayons par tick : le monde ne suit pas les planètes, on ne peut pas rester stationné près de l'une d'elles (le garde-fou empêche seulement d'y entrer) |

## Demandes d'API en attente de validation

*(aucune)*

Format d'une demande : tâche, besoin, pourquoi les outils existants ne suffisent pas (alternative tentée), modification
proposée (fichiers, signatures), impact sur l'existant. L'orchestrateur la présente à l'utilisatrice ; rien n'est
implémenté avant sa réponse, qui est alors déplacée dans les décisions validées.
