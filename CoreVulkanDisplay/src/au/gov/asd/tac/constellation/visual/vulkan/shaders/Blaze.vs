// Draw blazes next to nodes.
#version 450


// === PUSH CONSTANT ===
layout(std140, push_constant) uniform PushConstant {
    mat4 mvMatrix;
} pc;


// === UNIFORMS ===
layout(std140, binding = 0) uniform UniformBlock {
    float morphMix;
} ub;
layout(binding = 1) uniform samplerBuffer xyzTexture;


// === PER VERTEX DATA IN ===
layout(location = 0) in vec4 blazeColor;
layout(location = 1) in ivec4 blazeData;


// === PER VERTEX DATA OUT ===
layout(location = 0) flat out vec4 gColor;
layout(location = 1) flat out ivec4 gData;
layout(location = 2) out float gnradius;


void main(void) {
    // Pass stuff to the next shader.
    gColor = blazeColor;
    gData = blazeData;

    // Find the xyz of the vertex that this blaze belongs to,
    // specified by an offset into the xyzTexture buffer.
    int offset = blazeData[0] * 2;
    vec3 v = texelFetch(xyzTexture, offset).stp;
    vec3 vEnd = texelFetch(xyzTexture, offset + 1).stp;
    vec3 mixedVertex = mix(v, vEnd, ub.morphMix);

    gl_Position = pc.mvMatrix * vec4(mixedVertex, 1);

    // Get the side radius of the associated vertex and pass that through
    // so the blaze is drawn relative to the node size.
    gnradius = texelFetch(xyzTexture, offset).q;
}
