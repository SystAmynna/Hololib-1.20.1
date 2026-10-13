#version 150

// Bassin cosmique (hololib:cosmic_pool) : projection, position dans la vue (parallaxe) et donnees de surface pour
// cosmic_pool.fsh.
// Color = (masque des bords u- u+ v- v+, masque des coins, id de la couleur, 1) en octets, UV0 = coordonnees locales de
// la face (0..1), UV2 = origine (u, v) du bloc dans le plan de la face (coordonnees monde = origine + locales).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPos;
out vec3 viewPos;
flat out ivec2 origin;
flat out int edgeMask;
flat out int cornerMask;
flat out int colorId;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    localPos = UV0;
    origin = UV2;
    edgeMask = int(Color.r * 255.0 + 0.5);
    cornerMask = int(Color.g * 255.0 + 0.5);
    colorId = int(Color.b * 255.0 + 0.5);
}
