#version 330 core

const int LINE_STYLE_SOLID = 0;
const int LINE_STYLE_DOTTED = 1;
const int LINE_STYLE_DASHED = 2;
const int LINE_STYLE_DIAMOND = 3;
const float LINE_DOT_SIZE = 0.3;

in vec4 pointColor;
flat in int hitTestId;
flat in int lineStyle;
in float pointCoord;

flat in float lineLength;

out vec4 fragColor;

void main(void) {
    // Line style.
    if(lineStyle != LINE_STYLE_SOLID && lineLength > 0) {
        float segmentSize = LINE_DOT_SIZE * (lineLength / (0.25 + lineLength));

        if(lineStyle == LINE_STYLE_DOTTED || lineStyle == LINE_STYLE_DIAMOND) {
            float seg = mod(pointCoord, 2 * segmentSize);
            if(seg>(1 * segmentSize) && seg < (2 * segmentSize)) {
                discard;
            }
        } else if(lineStyle == LINE_STYLE_DASHED) {
            float seg = mod(pointCoord, 3 * segmentSize);
            if(seg > (2 * segmentSize) && seg < (3 * segmentSize)) {
                discard;
            }
        }
    }

    fragColor = hitTestId == 0 ? pointColor : vec4(hitTestId, 0, 0, 1);
}
