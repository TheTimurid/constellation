#version 450


// === CONSTANTS ===
const int LOOP_DIRECTED_INDEX = 2;
const int LOOP_UNDIRECTED_INDEX = 3;


// === UNIFORMS ===
layout(binding = 3) uniform sampler2DArray images;
layout(std140, binding = 4) uniform UniformBlock {
    // If non-zero, use the texture to color the icon.
    // Otherwise, use a unique color for hit testing.
    int drawHitTest;
} ub;


// === PER FRAGMENT DATA IN ===
layout(location = 0) in vec4 pointColor;
layout(location = 1) flat in ivec4 fData;
layout(location = 2) in vec2 pointCoord;


// === PER FRAGMENT DATA OUT ===
layout(location = 0) out vec4 fragColor;


void main() {
    int imgIx = fData.q;

    float iconOffsetX = float(imgIx % 8) / 8;
    float iconOffsetY = float((imgIx / 8) % 8) / 8;

    vec4 pixel = texture(images, vec3(pointCoord.x + iconOffsetX, pointCoord.y + iconOffsetY, imgIx / 64));
    fragColor = pixel;
    if(fragColor.a == 0) {
        discard;
    }

    if (ub.drawHitTest == 0) {
        int seldim = fData.p;
        bool isSelected = (seldim & 1) != 0;
        bool isDim = (seldim & 2) != 0;

        if (isSelected) {
            fragColor = vec4(1, 0.1, 0.1, 1);
        } else if (isDim) {
            fragColor = vec4(0.25, 0.25, 0.25, 1);
        } else {
            fragColor.rgb *= pointColor.rgb;
        }
    } else {
        fragColor = vec4(-(fData.s + 1), 0.0, 0.0, 1.0);
    }
}
