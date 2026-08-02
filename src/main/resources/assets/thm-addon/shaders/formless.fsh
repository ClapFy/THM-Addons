#version 330 core
// Adapted from Shadertoy ("Formless" by Noztol) for THM Addons
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
// Formless
// By Noztol

// Empty your mind. Be formless, shapeless, like water.
// You put water into a cup, it becomes the cup.
// You put water into a bottle, it becomes the bottle.
// You put it into a teapot, it becomes the teapot.
// Now water can flow, or it can crash. Be water, my friend.
// - Bruce Lee

// Rotation matrix helper
mat2 rot(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}


vec2 map(vec3 p) {
    vec3 q = p;

    // Spiral the whole scene smoothly
    q.xy *= rot(q.z * 0.15);

    // Organic warping
    q.x += sin(q.z * 0.8 + iTime * 0.3) * 0.5;
    q.y += cos(q.z * 0.9 - iTime * 0.2) * 0.5;

    // Intersecting Gyroids
    float scale = 1.3;
    vec3 q1 = q * scale;
    vec3 q2 = q.yzx * scale;

    float g1 = dot(sin(q1), cos(q1.zxy));
    float g2 = dot(sin(q2), cos(q2.zxy));

    float angle = atan(g1, g2);
    float dist = length(vec2(g1, g2));

    // Geometric ridges
    float ridges = sin(angle * 5.0 + q.z * 6.0);
    float radius = 0.18 + 0.06 * ridges;

    float d = (dist - radius) * 0.35;
    return vec2(d, angle);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;

    // Cinematic Look-At Camera
    vec3 ro = vec3(sin(iTime * 0.2) * 0.8, cos(iTime * 0.15) * 0.8, iTime * 1.5);
    vec3 ta = vec3(sin(iTime * 0.1) * 0.4, cos(iTime * 0.1) * 0.4, ro.z + 4.0);

    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = normalize(cross(uu, ww));
    vec3 rd = normalize(uv.x * uu + uv.y * vv + 1.2 * ww);

    vec3 finalColor = vec3(0.0);
    float t = 0.0;
    float maxDepth = 25.0;

    for(int i = 0; i < 90; i++) {
        vec3 p = ro + rd * t;

        vec2 obj = map(p);
        float d = obj.x;
        float angle = obj.y;

        if (d < 0.25) {
            float core = smoothstep(0.02, 0.0, d);
            float mist = smoothstep(0.25, 0.0, d) * 0.15;

            // Localized Pulses moving along the strands
            float matPulse = sin(p.z * 2.5 + angle * 2.0 - iTime * 6.0);
            float pulse = smoothstep(-0.2, 0.8, matPulse);

            vec3 c1 = vec3(0.0, 0.0, 0.4); // Neon blue
            vec3 c2 = vec3(0.0, 0.8, 1.0); // Cyan
            vec3 c3 = vec3(0.0, 0.6, 0.0); // Neon Green
            vec3 c4 = vec3(0.5, 0.0, 1.0); // Deep Violet

            // Blend based on actual tube angles and depth
            float mix1 = sin(p.z * 0.4 + angle) * 0.5 + 0.5;
            float mix2 = cos(p.x * 0.5 - p.y * 0.5) * 0.5 + 0.5;

            vec3 baseColor = mix(mix(c1, c2, mix1), mix(c3, c4, mix1), mix2);
            vec3 emission = baseColor;

            float density = (core * 3.5 + mist) * pulse;
            float fade = smoothstep(maxDepth, 0.0, t);

            finalColor += emission * density * fade * 0.12;
        }

        t += max(d * 0.8, 0.015);
        if(t > maxDepth) break;
    }


    float maxCol = max(finalColor.r, max(finalColor.g, finalColor.b));
    if (maxCol > 1.0) {
        finalColor /= maxCol;
    }

    finalColor = finalColor * finalColor * (3.0 - 2.0 * finalColor);
    finalColor = pow(finalColor, vec3(0.8));
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.;
}
