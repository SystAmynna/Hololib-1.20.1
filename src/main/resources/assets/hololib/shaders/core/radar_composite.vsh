#version 150

// Radar (hololib:radar) : quad plein ecran, deja en coordonnees de clip.

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
