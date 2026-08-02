#version 330 core
// Adapted from Shadertoy ("End Portal") for THM Addons
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

#define iTime time
#define iResolution resolution
#define iMouse mouse

// --------[ Original ShaderToy begins here ]---------- //
#define PI 3.1415926

float rand(vec2 pos) {
    return
        fract(sin(dot(pos, vec2(23.2342, 82.29561))) * 82931.1857193);

}

/* color palette:
76, 149, 156
33, 96, 198
85, 184, 130
74, 160, 115
47, 112, 168
*/

mat2 rotate2d(float angle) {
    return mat2(
            sin(angle), -cos(angle),
            cos(angle), sin(angle)
        );
}

vec3 pattern(vec2 pos, float size, float frequency, float angle, vec3 color, float transparency) {
    pos *= rotate2d(angle);
    return vec3(floor(rand(floor(pos*size+vec2(0.+iTime, 0.)))+frequency)
                + .4*floor(rand(floor(pos*size+vec2(-1.+iTime, 0.)))+frequency)
                + .2*floor(rand(floor(pos*size+vec2(-2.+iTime, 0.)))+frequency)
                + .1*floor(rand(floor(pos*size+vec2(iTime, -1.)))+frequency)
                + .1*floor(rand(floor(pos*size+vec2(iTime, 1.)))+frequency)
                + .1*floor(rand(floor(pos*size+vec2(1.+iTime, 0.)))+frequency)
        )*color*transparency*(1.-.5*rand(floor(pos*size+vec2(-1.+iTime, 0.))));
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = (fragCoord/iResolution.xy - .5);
    uv.x *= iResolution.x/iResolution.y;

    vec3 col = vec3(pattern(uv, 500., 0.009, PI/7., vec3(76, 149, 156)/255., .9)
            + pattern(uv, 250., 0.004, PI/3., vec3(33, 96, 198)/255., 0.8)
            + pattern(uv, 125., 0.008, 3.*PI/4., vec3(85, 184, 130)/255., .8)
            + pattern(uv, 60., 0.010, 5.*PI/4., vec3(47, 112, 168)/255., .8)
            + pattern(uv, 30., 0.012, PI, vec3(76, 149, 156)/255., .8)
        );
    fragColor = vec4(col,1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.;
}
