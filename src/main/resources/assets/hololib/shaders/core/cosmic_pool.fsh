#version 150

// Bassin cosmique (bloc hololib:cosmic_pool) : shader des faces du bloc et de son item.
// Declaration des uniformes et attributs : cosmic_pool.json (tout nouvel uniforme doit y etre ajoute).
// Rechargement en jeu : F3+T.
//
// La face est une fenetre sur un trou profond. Le rayon du regard de chaque fragment est prolonge sous la surface
// (parallaxe) et rencontre, du plus profond au plus proche :
//   - le fond etoile (deux couches d'etoiles sur du noir) ;
//   - le plan bas : fluide anime (bruit a deformation de domaine) posterise dans les teintes sombres, vide la ou son
//     champ est le plus bas (le fond etoile y apparait) ;
//   - le plan haut : autre fluide, teintes claires, partiellement transparent (le plan bas transparait), en relief
//     (cretes eclaircies, creux assombris) ;
//   - des particules d'un texel sur plusieurs niveaux entre le plan bas et la surface (celles sous le plan haut
//     transparaissent a travers lui), errant comme dans un fluide, seules ou par groupes ;
//   - a la surface : bord lumineux ondulant comme le bord d'un fluide, lueur et ecume tramee.
// Chaque plan est pixelise dans son propre repere (TEXELS texels par bloc a sa profondeur) : les grilles glissent les
// unes sur les autres quand on bouge ; les particules glissent librement (position continue). Tout est continu d'un
// bloc a l'autre (coordonnees monde de la surface). L'interieur ignore le bobbing de la marche (u_bob) : il reste fixe
// a l'ecran, seul le bord suit la face. Teintes tirees de la couleur du bassin (16 couleurs, DYE_DARK / DYE_BASE).

// ---- Reglages ----

// Texels par bloc (pixelisation de chaque plan, 16 = resolution des textures vanilla)
#define TEXELS 16.0

// Pente minimale du regard sous la surface (evite l'infini en regard rasant)
#define MIN_SLOPE 0.08

// Fluides : octaves de la deformation et du champ, force de la deformation, contraste du champ (bruit de valeur
// centre sur 0,5)
#define WARP_OCTAVES 3
#define FIELD_OCTAVES 4
#define WARP_STRENGTH 3.0
#define CONTRAST 2.6

// Plan bas : profondeur (blocs), echelle du bruit (plus petit = volutes plus grandes), vitesse, sens du courant,
// seuil sous lequel le plan est vide (fond etoile visible)
#define LOWER_DEPTH 1.5
#define LOWER_SCALE 0.55
#define LOWER_SPEED 0.07
#define LOWER_FLOW vec2(0.6, 0.8)
#define LOWER_VOID 0.3

// Plan haut : profondeur, echelle, vitesse, sens, seuil de couverture, opacite
#define UPPER_DEPTH 1.1
#define UPPER_SCALE 0.7
#define UPPER_SPEED 0.1
#define UPPER_FLOW vec2(-0.9, 0.4)
#define UPPER_COVER 0.55
#define UPPER_ALPHA 0.72
// Relief du plan haut : direction de la lumiere (plan), pente du champ (sur deux texels) qui change la teinte d'un cran
#define LIGHT_DIR vec2(0.7, 0.7)
#define RELIEF_THRESHOLD 0.06

// Fond etoile : profondeur de la premiere couche et ecart, etoiles par bloc (taille), densite, eclat (0..1),
// scintillement / s
#define STAR_DEPTH 6.0
#define STAR_DEPTH_STEP 3.0
#define STAR_TEXELS 5.0
#define STAR_DENSITY 0.04
#define STAR_BRIGHTNESS 0.45
#define TWINKLE 1.2

// Particules : nombre de niveaux, profondeur du plus proche (blocs ; le plus profond est juste au-dessus du plan bas),
// taille des cellules (texels, une particule au plus par cellule), part des cellules occupees, duree de vie moyenne (s),
// vitesse de l'errance (multiplie les pulsations), part du mouvement commune a un groupe de PARTICLE_GROUP x
// PARTICLE_GROUP cellules (0 = toutes independantes, 1 = groupes rigides)
#define PARTICLE_LEVELS 7
#define PARTICLE_NEAR 0.08
#define PARTICLE_CELL 8.0
#define PARTICLE_DENSITY 0.25
#define PARTICLE_LIFE 4.0
#define PARTICLE_SPEED 1.0
#define PARTICLE_GROUP 3
#define PARTICLE_GROUP_SHARE 0.5
// Variete des particules : part des grandes (3 texels, coins attenues) et des moyennes (2 texels), le reste fait 1
// texel ; part de celles qui scintillent
#define PARTICLE_BIG 0.1
#define PARTICLE_MEDIUM 0.3
#define PARTICLE_TWINKLE 0.4

// Bord fluide : largeur moyenne du lisere (texels), amplitude de l'ondulation, echelle et vitesse de la vague,
// largeur et force de la lueur
#define RIM_WIDTH 0.6
#define RIM_WAVE 0.6
#define RIM_SCALE 0.18
#define RIM_SPEED 1.1
#define GLOW_WIDTH 1.6
#define GLOW_STRENGTH 0.7
// Ecume : portee (texels), densite au bord, decroissance (texels), echelle et vitesse du courant, changements / s
#define FOAM_REACH 6.0
#define FOAM_DENSITY 0.55
#define FOAM_FALLOFF 1.8
#define FOAM_SCALE 0.45
#define FOAM_SPEED 1.6
#define FOAM_RATE 6.0

// Couleurs du bassin, par id de DyeColor (0 blanc ... 15 noir) : ombre teintee et couleur vive. Toutes les teintes du
// rendu en sont tirees (setupColors). Magenta = apparence d'origine.
const vec3 DYE_DARK[16] = vec3[16](
    vec3(96.0, 102.0, 124.0) / 255.0,  // blanc
    vec3(84.0, 26.0, 6.0) / 255.0,     // orange
    vec3(62.0, 8.0, 36.0) / 255.0,     // magenta
    vec3(12.0, 42.0, 84.0) / 255.0,    // bleu clair
    vec3(78.0, 52.0, 4.0) / 255.0,     // jaune
    vec3(24.0, 62.0, 8.0) / 255.0,     // vert clair
    vec3(88.0, 24.0, 48.0) / 255.0,    // rose
    vec3(28.0, 30.0, 36.0) / 255.0,    // gris
    vec3(48.0, 50.0, 57.0) / 255.0,    // gris clair
    vec3(4.0, 52.0, 62.0) / 255.0,     // cyan
    vec3(40.0, 10.0, 72.0) / 255.0,    // violet
    vec3(10.0, 14.0, 78.0) / 255.0,    // bleu
    vec3(52.0, 26.0, 10.0) / 255.0,    // marron
    vec3(14.0, 44.0, 8.0) / 255.0,     // vert
    vec3(72.0, 6.0, 8.0) / 255.0,      // rouge
    vec3(16.0, 14.0, 22.0) / 255.0     // noir
);
const vec3 DYE_BASE[16] = vec3[16](
    vec3(238.0, 242.0, 255.0) / 255.0, // blanc
    vec3(245.0, 128.0, 36.0) / 255.0,  // orange
    vec3(205.0, 70.0, 170.0) / 255.0,  // magenta
    vec3(84.0, 176.0, 240.0) / 255.0,  // bleu clair
    vec3(245.0, 212.0, 56.0) / 255.0,  // jaune
    vec3(128.0, 218.0, 48.0) / 255.0,  // vert clair
    vec3(245.0, 138.0, 178.0) / 255.0, // rose
    vec3(100.0, 105.0, 116.0) / 255.0, // gris
    vec3(158.0, 162.0, 172.0) / 255.0, // gris clair
    vec3(36.0, 188.0, 198.0) / 255.0,  // cyan
    vec3(148.0, 68.0, 220.0) / 255.0,  // violet
    vec3(58.0, 78.0, 232.0) / 255.0,   // bleu
    vec3(150.0, 94.0, 52.0) / 255.0,   // marron
    vec3(78.0, 140.0, 38.0) / 255.0,   // vert
    vec3(222.0, 48.0, 42.0) / 255.0,   // rouge
    vec3(72.0, 70.0, 88.0) / 255.0     // noir
);

// Teintes du bassin, remplies par setupColors : palette posterisee des fluides (du plus sombre au plus clair), fond du
// trou, etoiles, particules, lisere et ecume du bord.
vec3 palette[6];
vec3 spaceColor;
vec3 starColors[3];
vec3 particleColors[3];
vec3 rimColor;
vec3 foamColor;

// ---- Entrees ----

uniform mat4 ProjMat;
// Secondes de jeu (fige en pause), mis a jour par CosmicPoolShaders.
uniform float u_time;
// Bobbing de la marche (repere de la vue -> point affiche ; identite sans bobbing), mis a jour par CosmicPoolShaders.
uniform mat4 u_bob;

// Coordonnees locales de la face (0..1, u, v selon la face).
in vec2 localPos;
// Position dans le repere de la vue (oeil a l'origine en perspective).
in vec3 viewPos;
// Origine du bloc dans le plan de la face, en blocs : coordonnees monde de la surface = origin + localPos.
flat in ivec2 origin;
// Bords lumineux de la face : bits u-, u+, v-, v+ (la surface du bassin s'arrete de ce cote).
flat in int edgeMask;
// Coins interieurs lumineux : bits u-v-, u+v-, u-v+, u+v+.
flat in int cornerMask;
// Couleur du bassin : id de DyeColor (0-15).
flat in int colorId;

out vec4 fragColor;

// ---- Bruit ----

// Hash entier PCG : stable meme avec de grandes coordonnees (contrairement a fract(sin(...))).
uint pcg(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// Entier 0..1.
float unit(uint h) {
    return float(h) * (1.0 / 4294967295.0);
}

// Hash entier de la cellule c, pour la graine seed (enchainer pcg(h + k) pour d'autres tirages).
uint hashu(ivec2 c, int seed) {
    return pcg(uint(c.x) + pcg(uint(c.y) + pcg(uint(seed))));
}

// Valeur pseudo-aleatoire 0..1 de la cellule c, pour la graine seed.
float hash(ivec2 c, int seed) {
    return unit(hashu(c, seed));
}

// Bruit de valeur 2D lisse.
float vnoise(vec2 p, int seed) {
    vec2 i = floor(p);
    vec2 f = p - i;
    ivec2 c = ivec2(i);
    float a = hash(c, seed);
    float b = hash(c + ivec2(1, 0), seed);
    float d = hash(c + ivec2(0, 1), seed);
    float e = hash(c + ivec2(1, 1), seed);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

// Somme d'octaves normalisee (0..1, centree sur 0,5), tournee d'un octave a l'autre (pas d'alignement sur les axes).
float fbm(vec2 p, int octaves, int seed) {
    const mat2 ROTATION = mat2(0.8, -0.6, 0.6, 0.8);
    float value = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * vnoise(p, seed + i * 31);
        total += amplitude;
        p = ROTATION * p * 2.03;
        amplitude *= 0.5;
    }
    return value / total;
}

// ---- Couches ----

// Point du plan a la profondeur depth (blocs) pixelise dans ce plan : centre du texel, en blocs.
vec2 planeTexel(vec2 surface, vec2 slope, float depth) {
    return (floor((surface + slope * depth) * TEXELS) + 0.5) / TEXELS;
}

// Deformation de domaine du fluide au point p (espace du bruit), animee le long de flow.
vec2 fluidWarp(vec2 p, float t, vec2 flow, int seed) {
    return vec2(fbm(p + flow * t, WARP_OCTAVES, seed),
                fbm(p + vec2(5.2, 1.3) - flow.yx * t, WARP_OCTAVES, seed + 7));
}

// Champ du fluide (0..1, contraste applique) au point p deforme par q.
float fluidField(vec2 p, vec2 q, float t, vec2 flow, int seed) {
    float f = fbm(p + WARP_STRENGTH * q + 0.3 * flow * t, FIELD_OCTAVES, seed + 13);
    return clamp((f - 0.5) * CONTRAST + 0.5, 0.0, 1.0);
}

// Fond etoile : couches d'etoiles de STAR_TEXELS par bloc, de la plus profonde a la plus proche.
vec3 starfield(vec2 surface, vec2 slope) {
    vec3 color = spaceColor;
    for (int i = 1; i >= 0; i--) {
        float depth = STAR_DEPTH + float(i) * STAR_DEPTH_STEP;
        ivec2 cell = ivec2(floor((surface + slope * depth) * STAR_TEXELS));
        float h = hash(cell, 101 + i);
        if (h > 1.0 - STAR_DENSITY) {
            float k = (h - 1.0 + STAR_DENSITY) / STAR_DENSITY;
            float twinkle = 0.7 + 0.3 * sin(u_time * TWINKLE * 6.2832 * (0.5 + k) + k * 97.0);
            float fade = (1.0 - float(i) * 0.35) * STAR_BRIGHTNESS;
            color = mix(spaceColor, starColors[int(k * 2.999)], twinkle * fade);
        }
    }
    return color;
}

// Plan bas, par-dessus color : opaque la ou il est couvert, vide ailleurs.
vec3 lowerPlane(vec3 color, vec2 surface, vec2 slope) {
    vec2 p = planeTexel(surface, slope, LOWER_DEPTH) * LOWER_SCALE;
    float t = u_time * LOWER_SPEED;
    vec2 q = fluidWarp(p, t, LOWER_FLOW, 11);
    float v = fluidField(p, q, t, LOWER_FLOW, 11);
    if (v < LOWER_VOID) {
        return color;
    }
    // Teintes 1..3 (bordeaux -> framboise)
    float k = clamp((v - LOWER_VOID) / (1.0 - LOWER_VOID), 0.0, 0.999);
    return palette[1 + int(k * 3.0)];
}

// Plan haut, par-dessus color : transparent, en relief.
vec3 upperPlane(vec3 color, vec2 surface, vec2 slope) {
    vec2 texel = planeTexel(surface, slope, UPPER_DEPTH);
    vec2 p = texel * UPPER_SCALE;
    float t = u_time * UPPER_SPEED;
    vec2 q = fluidWarp(p, t, UPPER_FLOW, 47);
    float v = fluidField(p, q, t, UPPER_FLOW, 47);
    if (v < UPPER_COVER) {
        return color;
    }
    // Relief : pente du champ vers la lumiere sur deux texels (meme deformation, approximation)
    vec2 lit = (texel + normalize(LIGHT_DIR) * 2.0 / TEXELS) * UPPER_SCALE;
    float relief = fluidField(lit, q, t, UPPER_FLOW, 47) - v;

    // Teintes 3..5 (framboise -> rose pale), un cran de plus sur les cretes, de moins dans les creux
    float k = clamp((v - UPPER_COVER) / (1.0 - UPPER_COVER), 0.0, 0.999);
    int index = 3 + int(k * 3.0);
    if (relief > RELIEF_THRESHOLD) index++;
    if (relief < -RELIEF_THRESHOLD) index--;
    return mix(color, palette[clamp(index, 2, 5)], UPPER_ALPHA);
}

// Errance (-1..1 par axe) : deux sinusoides par axe, pulsations et phases tirees de h. Les pulsations ne sont pas
// commensurables : la trajectoire tourne sans cesse, sans motif qui se repete a l'oeil.
vec2 wander(uint h, float t) {
    vec4 pulse = (0.25 + 0.55 * vec4(unit(pcg(h)), unit(pcg(h + 1u)), unit(pcg(h + 2u)), unit(pcg(h + 3u))))
               * PARTICLE_SPEED;
    vec4 shift = 6.2832 * vec4(unit(pcg(h + 4u)), unit(pcg(h + 5u)), unit(pcg(h + 6u)), unit(pcg(h + 7u)));
    vec4 s = sin(pulse * t + shift);
    return 0.5 * vec2(s.x + s.y, s.z + s.w);
}

// Particule de la cellule cell par-dessus color si elle couvre le point (texels du niveau, position continue) : carre
// net de 1 a 3 texels qui glisse sans s'aligner sur la grille, ancre au centre de sa cellule et decale d'au plus
// PARTICLE_CELL - taille / 2 texels par axe (errance propre + errance de son groupe), ne et eteint en fondu. A chaque
// renaissance : nouvelle errance, taille, opacite, scintillement et teinte.
vec3 particle(vec3 color, ivec2 cell, vec2 point, int seed, float fade) {
    if (hash(cell, seed) > PARTICLE_DENSITY) {
        return color;
    }
    uint h = hashu(cell, seed + 1);
    float cycles = u_time / (PARTICLE_LIFE * (0.6 + 0.8 * unit(pcg(h)))) + unit(pcg(h + 1u));
    float phase = fract(cycles);
    uint life = pcg(h + 2u + uint(cycles) * 16u);

    float pick = unit(pcg(life + 8u));
    float size = pick < PARTICLE_BIG ? 3.0 : (pick < PARTICLE_BIG + PARTICLE_MEDIUM ? 2.0 : 1.0);
    ivec2 group = ivec2(floor(vec2(cell) / float(PARTICLE_GROUP)));
    vec2 offset = mix(wander(life, u_time), wander(hashu(group, seed + 2), u_time), PARTICLE_GROUP_SHARE);
    vec2 center = (vec2(cell) + 0.5) * PARTICLE_CELL + offset * (PARTICLE_CELL - 0.5 * size - 0.01);

    // Position dans la particule (0..1)
    vec2 inside = (point - center) / size + 0.5;
    if (any(lessThan(inside, vec2(0.0))) || any(greaterThanEqual(inside, vec2(1.0)))) {
        return color;
    }
    float opacity = unit(pcg(life + 9u));
    float alpha = 0.2 + 0.8 * opacity * opacity;
    // Grande particule : coins attenues (croix lumineuse)
    if (size > 2.5) {
        ivec2 part = ivec2(inside * 3.0);
        if (part.x != 1 && part.y != 1) alpha *= 0.35;
    }
    float twinkle = unit(pcg(life + 10u));
    if (twinkle < PARTICLE_TWINKLE) {
        alpha *= 0.55 + 0.45 * sin(u_time * (3.0 + 6.0 * unit(pcg(life + 11u))) + twinkle * 50.0);
    }
    vec3 tint = particleColors[int(unit(pcg(life + 12u)) * 2.999)];
    return mix(color, tint, alpha * sin(phase * 3.14159) * fade);
}

// Particules par-dessus color, des niveaux sous le plan haut (below) ou au-dessus, du plus profond au plus proche.
vec3 particles(vec3 color, vec2 surface, vec2 slope, bool below) {
    for (int i = PARTICLE_LEVELS - 1; i >= 0; i--) {
        float depth = mix(PARTICLE_NEAR, LOWER_DEPTH - 0.05, float(i) / float(max(PARTICLE_LEVELS - 1, 1)));
        if ((depth > UPPER_DEPTH) != below) {
            continue;
        }
        vec2 point = (surface + slope * depth) * TEXELS;
        float fade = 1.0 - 0.5 * depth / LOWER_DEPTH;
        int seed = 200 + i * 13;
        // Une particule couvre au plus PARTICLE_CELL - 0,01 texels autour du centre de sa cellule : seules les 2 x 2
        // cellules dont les centres encadrent le point peuvent l'atteindre
        ivec2 first = ivec2(floor((point - 0.5 * PARTICLE_CELL) / PARTICLE_CELL));
        color = particle(color, first, point, seed, fade);
        color = particle(color, first + ivec2(1, 0), point, seed, fade);
        color = particle(color, first + ivec2(0, 1), point, seed, fade);
        color = particle(color, first + ivec2(1, 1), point, seed, fade);
    }
    return color;
}

// Distance (en texels) du texel de surface au bord lumineux le plus proche ; 1e3 sans bord.
float edgeDistance(vec2 local) {
    vec2 lo = local - 0.5;              // 0 sur le premier texel du cote u- / v-
    vec2 hi = TEXELS - 0.5 - local;     // 0 sur le dernier texel du cote u+ / v+
    float d = 1e3;
    if ((edgeMask & 1) != 0) d = min(d, lo.x);
    if ((edgeMask & 2) != 0) d = min(d, hi.x);
    if ((edgeMask & 4) != 0) d = min(d, lo.y);
    if ((edgeMask & 8) != 0) d = min(d, hi.y);
    if ((cornerMask & 1) != 0) d = min(d, max(lo.x, lo.y));
    if ((cornerMask & 2) != 0) d = min(d, max(hi.x, lo.y));
    if ((cornerMask & 4) != 0) d = min(d, max(lo.x, hi.y));
    if ((cornerMask & 8) != 0) d = min(d, max(hi.x, hi.y));
    return d;
}

// Bord fluide par-dessus color, sur la grille de la surface : lisere ondulant, lueur, ecume.
vec3 rim(vec3 color, vec2 localTexel) {
    float d = edgeDistance(localTexel + 0.5);
    if (d > FOAM_REACH + RIM_WIDTH + RIM_WAVE) {
        return color;
    }
    // Coordonnees monde du texel : la vague et l'ecume sont continues d'un bloc a l'autre
    vec2 world = vec2(origin) * TEXELS + localTexel;
    float t = u_time;
    float width = RIM_WIDTH + RIM_WAVE * (2.0 * vnoise(world * RIM_SCALE + vec2(t, -0.7 * t) * RIM_SPEED, 300) - 1.0);
    if (d < width) {
        return rimColor;
    }
    float beyond = d - width;
    color = mix(color, rimColor, exp(-beyond / GLOW_WIDTH) * GLOW_STRENGTH);

    // Ecume : courant lisse + scintillement par texel, plus dense pres du lisere
    int tick = int(floor(t * FOAM_RATE));
    float foam = 0.65 * vnoise(world * FOAM_SCALE + vec2(-0.6 * t, t) * FOAM_SPEED, 301)
               + 0.35 * hash(ivec2(world) + ivec2(tick * 5, tick * 11), 302);
    if (foam > 1.0 - FOAM_DENSITY * exp(-beyond / FOAM_FALLOFF)) {
        color = foamColor;
    }
    return color;
}

// Rayon du regard du fragment, sans le bobbing de la marche : point de la surface vise (coordonnees monde de la face,
// en blocs) et decalage (u, v) par bloc de profondeur. Le bobbing deplace l'image entiere (dans la projection) : le
// rayon passe par le point tel qu'il est affiche, l'interieur reste donc fixe a l'ecran pendant que la face bouge.
void viewRay(out vec2 surface, out vec2 slope) {
    surface = vec2(origin) + localPos;
    slope = vec2(0.0);

    // Tangentes de la surface dans le repere de la vue (d position / d u, d position / d v), depuis les derivees
    // (des coordonnees locales : precises, contrairement aux coordonnees monde)
    vec3 dp1 = dFdx(viewPos);
    vec3 dp2 = dFdy(viewPos);
    vec2 duv1 = dFdx(localPos);
    vec2 duv2 = dFdy(localPos);
    float det = duv1.x * duv2.y - duv2.x * duv1.y;
    if (abs(det) < 1e-12) {
        return;
    }
    vec3 t = (dp1 * duv2.y - dp2 * duv1.y) / det;
    vec3 b = (dp2 * duv1.x - dp1 * duv2.x) / det;
    vec3 n = normalize(cross(t, b));

    // Axe de la vue en orthographique (item dans l'inventaire) ; en perspective, depuis l'oeil vers le point affiche,
    // qui croise la face en un autre point que le fragment
    vec3 ray = vec3(0.0, 0.0, -1.0);
    if (ProjMat[2][3] != 0.0) {
        vec3 shown = (u_bob * vec4(viewPos, 1.0)).xyz;
        float along = dot(shown, n);
        if (abs(along) > 1e-6) {
            vec3 delta = shown * (dot(viewPos, n) / along) - viewPos;
            surface += vec2(dot(delta, t) / dot(t, t), dot(delta, b) / dot(b, b));
        }
        ray = normalize(shown);
    }
    float depth = abs(dot(ray, n));                  // vers l'interieur du bloc
    slope = vec2(dot(ray, t) / dot(t, t), dot(ray, b) / dot(b, b)) / max(depth, MIN_SLOPE);
}

// Remplit les teintes du bassin depuis sa couleur.
void setupColors(int id) {
    vec3 dark = DYE_DARK[id];
    vec3 base = DYE_BASE[id];
    palette[0] = dark * 0.22;
    palette[1] = dark;
    palette[2] = mix(dark, base, 0.4);
    palette[3] = mix(dark, base, 0.72);
    palette[4] = base;
    palette[5] = mix(base, vec3(1.0), 0.3);
    spaceColor = dark * 0.13;
    rimColor = min(base * 1.25 + 0.05, vec3(1.0));
    foamColor = mix(rimColor, vec3(1.0), 0.25);
    starColors[0] = mix(base, vec3(1.0), 0.55);
    starColors[1] = rimColor * 0.9;
    starColors[2] = mix(base, vec3(1.0), 0.8);
    particleColors[0] = mix(rimColor, vec3(1.0), 0.15);
    particleColors[1] = mix(rimColor, vec3(1.0), 0.5);
    particleColors[2] = mix(base, vec3(1.0), 0.3);
}

void main() {
    setupColors(clamp(colorId, 0, 15));
    vec2 surface;
    vec2 slope;
    viewRay(surface, slope);

    vec3 color = starfield(surface, slope);
    color = lowerPlane(color, surface, slope);
    color = particles(color, surface, slope, true);
    color = upperPlane(color, surface, slope);
    color = particles(color, surface, slope, false);

    vec2 localTexel = min(floor(localPos * TEXELS), vec2(TEXELS - 1.0));
    color = rim(color, localTexel);

    fragColor = vec4(min(color, vec3(1.0)), 1.0);
}
