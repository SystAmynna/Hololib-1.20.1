#version 150

// Contour (client.render.outline) : Sampler0 = profondeur avant du masque (1 = vide). Un pixel hors masque dont un
// voisin (jusqu'a 2 px) est dans le masque prend la couleur ; aucun test de scene (visible a travers les murs).

uniform sampler2D Sampler0;

uniform vec2 TexelSize;
uniform vec4 OutlineColor;

in vec2 texCoord;

out vec4 fragColor;

const int OUTLINE_SAMPLES = 12;
const vec2 OUTLINE[OUTLINE_SAMPLES] = vec2[](
    vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
    vec2(2.0, 0.0), vec2(-2.0, 0.0), vec2(0.0, 2.0), vec2(0.0, -2.0),
    vec2(1.0, 1.0), vec2(-1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, -1.0)
);

void main() {
    if (texture(Sampler0, texCoord).r < 1.0) discard;
    for (int i = 0; i < OUTLINE_SAMPLES; i++) {
        if (texture(Sampler0, texCoord + OUTLINE[i] * TexelSize).r < 1.0) {
            fragColor = OutlineColor;
            return;
        }
    }
    discard;
}
