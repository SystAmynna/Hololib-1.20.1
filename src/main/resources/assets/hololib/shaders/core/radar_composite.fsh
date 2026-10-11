#version 150

// Radar (hololib:radar) : composition plein ecran.
// Sampler0 = profondeur de la scene (copie), Sampler1 = profondeur avant des entites reperees (1 = vide).
// Positions reconstruites relatives a l'origine de l'onde (InvViewProj = inverse de projection x vue placee a
// l'origine). Onde : trait centre sur le rayon (demi-largeur Front) sur tout pixel non-ciel, seulement le bord de la
// sphere (son interieur n'est pas colore). Entites : interieur colore la ou la
// scene est devant elles (a travers les blocs), contour sur les pixels hors masque voisins du masque.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform mat4 InvViewProj;
uniform vec3 CameraPos;
uniform vec2 TexelSize;
uniform float Radius;
uniform float Front;
uniform float WaveAlpha;
uniform vec3 WaveColor;
uniform float HasMask;
uniform float FillAlpha;
uniform vec3 MarkColor;

in vec2 texCoord;

out vec4 fragColor;

const int OUTLINE_SAMPLES = 12;
const vec2 OUTLINE[OUTLINE_SAMPLES] = vec2[](
    vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
    vec2(2.0, 0.0), vec2(-2.0, 0.0), vec2(0.0, 2.0), vec2(0.0, -2.0),
    vec2(1.0, 1.0), vec2(-1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, -1.0)
);

vec3 position(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Melange "par-dessus" de deux couleurs non premultipliees.
vec4 over(vec4 top, vec4 bottom) {
    float a = top.a + bottom.a * (1.0 - top.a);
    if (a <= 0.0) return vec4(0.0);
    return vec4((top.rgb * top.a + bottom.rgb * bottom.a * (1.0 - top.a)) / a, a);
}

void main() {
    vec4 color = vec4(0.0);
    float scene = texture(Sampler0, texCoord).r;

    if (WaveAlpha > 0.0 && scene < 1.0) {
        float gap = abs(length(position(texCoord, scene)) - Radius);
        if (gap < Front) {
            color = vec4(WaveColor, (1.0 - gap / Front) * WaveAlpha);
        }
    }

    if (HasMask > 0.5) {
        float mask = texture(Sampler1, texCoord).r;
        if (mask < 1.0) {
            float toMask = length(position(texCoord, mask) - CameraPos);
            float toScene = scene < 1.0 ? length(position(texCoord, scene) - CameraPos) : 1.0e9;
            if (toScene < toMask - max(0.05, toMask * 0.002)) {
                color = over(vec4(MarkColor, FillAlpha), color);
            }
        } else {
            for (int i = 0; i < OUTLINE_SAMPLES; i++) {
                if (texture(Sampler1, texCoord + OUTLINE[i] * TexelSize).r < 1.0) {
                    color = over(vec4(MarkColor, 1.0), color);
                    break;
                }
            }
        }
    }

    if (color.a <= 0.0) discard;
    fragColor = color;
}
