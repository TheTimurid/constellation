// Color icons in one of two ways.
//
// If drawHitTest is false, draw a texture using pointColor[3].
// If drawHitTest is true, convert pointColor[2] to unique color to be used for hit testing.
#version 450


// === PUSH CONSTANTS ===
layout(std140, push_constant) uniform HitTestPushConstant {
    // If non-zero, use the texture to color the icon.
    // Otherwise, use a unique color for hit testing.
    // Offset is 64 as the MV matrix (in VertexIcon.vs) 
    // is before it in the pushConstant buffer.
    layout(offset = 64) int drawHitTest;
} htpc;


// === UNIFORMS ===
// Note this is an opaque uniform, ie not data we pass in but an object this shader can reference.  This 
// array image is generated by CVKIconTextureAtlas.
layout(binding = 4) uniform sampler2DArray images;


// === PER FRAGMENT DATA IN ===
layout(location = 0) flat in mat4 iconColor;
layout(location = 4) noperspective centroid in vec3 textureCoords;
layout(location = 5) flat in vec4 hitBufferValue;


// === PER FRAGMENT DATA OUT ===
layout(location = 0) out vec4 fragColor;


void main(void) {
    if (htpc.drawHitTest == 0) {
        fragColor = iconColor * texture(images, textureCoords);

        // Discarding only when fragColor.a==0.0 means that some "nearly transparent" pixels get drawn, which causes weird see-through
        // artifacts around the edges. Instead we'll discard nearly transparent pixels as well at an arbitrary cut-off point.
        if(fragColor.a < 0.1) {
            discard;
        }
    } else {
        if (iconColor[3][3] * texture(images, textureCoords).a > 0.1) {
            fragColor = hitBufferValue;
        } else {
            discard;
        }
    }
}
