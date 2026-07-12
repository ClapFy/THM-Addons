#version 330 core
// Custom THM Addons shader - obsidian rift (purple energy veins over a dark obsidian field)
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float noise(vec2 p) {
    const float K1 = 0.366025404;
    const float K2 = 0.211324865;
    vec2 i = floor(p + (p.x + p.y) * K1);
    vec2 a = p - i + (i.x + i.y) * K2;
    vec2 o = (a.x > a.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 b = a - o + K2;
    vec2 c = a - 1.0 + 2.0 * K2;
    vec3 h = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
    vec3 n = h * h * h * h * vec3(dot(a, hash2(i)), dot(b, hash2(i + o)), dot(c, hash2(i + 1.0)));
    return dot(n, vec3(70.0));
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = rot * p * 2.0 + 4.0;
        a *= 0.5;
    }
    return v;
}

void main(void) {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / min(resolution.x, resolution.y);

    float t = time * 0.06;

    vec2 warp = vec2(fbm(uv * 1.6 + t), fbm(uv * 1.6 - t + 5.2));
    float veins = fbm(uv * 2.2 + warp * 1.4 + t * 0.5);

    float ridge = pow(1.0 - abs(veins), 8.0);

    vec3 purple = vec3(145.0, 60.0, 255.0) / 255.0;
    vec3 base = mix(vec3(0.02, 0.01, 0.04), purple * 0.25, veins * 0.5 + 0.5);
    vec3 glow = purple * ridge * 1.8;

    float pulse = 0.5 + 0.5 * sin(time * 0.5 + veins * 3.0);
    vec3 col = base + glow + purple * ridge * pulse * 0.6;

    float vig = 1.0 - dot(uv, uv) * 0.55;
    col *= vig;

    fragColor = vec4(col, 1.0);
}
