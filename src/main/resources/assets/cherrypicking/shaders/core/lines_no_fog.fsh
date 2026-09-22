#version 330

// Vanilla's rendertype_lines.fsh without apply_fog. Blindness pulls the fog in to a few
// blocks, and fogged edges turn black against a black sky: a through-walls highlight
// has to stay lit when the player can see nothing else.

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
}
