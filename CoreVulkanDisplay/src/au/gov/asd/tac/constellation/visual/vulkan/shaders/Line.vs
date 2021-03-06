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
layout(location = 0) in vec4 vColor;
layout(location = 1) in ivec4 data;


// === PER VERTEX DATA OUT ===
layout(location = 0) out vec4 vpointColor;
layout(location = 1) flat out ivec4 gData;


void main(void) {
    // Pass the color to the fragment shader.
    vpointColor = vColor;
    gData = data;

    // Decode the index into the xyzTexture and the LINE_INFO from each other and put the LINE_INFO back.
    int vxIndex = gData[1] / 4;
    gData[1] = gData[1] - vxIndex * 4;

    int offset = vxIndex * 2;
    vec3 v = texelFetch(xyzTexture, offset).stp;
    vec3 vEnd = texelFetch(xyzTexture, offset + 1).stp;
    vec3 mixedVertex = mix(v, vEnd, ub.morphMix);

    gl_Position = pc.mvMatrix * vec4(mixedVertex, 1);
}
