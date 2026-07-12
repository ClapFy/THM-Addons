#version 330 core
// Custom THM Addons shader - highway flyover: enclosed nether-brick tunnel
// (obsidian floor, nether-brick walls, gray light patches) viewed from a
// 45-degree top-down camera gliding forward. Palette sampled directly from
// run/screenshots/2026-07-12_12.10.40.png (floor/wall/ceiling/light-patch
// regions), not from generic vanilla texture guesses.
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// weighted multi-color pickers - cumulative thresholds match each region's
// actual pixel-count histogram in the reference screenshot, so the common
// shades stay common instead of a flat/uniform pick among all of them.

vec3 floorColor(float h) {
    if (h < 0.285) return vec3(0.020, 0.012, 0.039); // 05030A
    if (h < 0.569) return vec3(0.000, 0.000, 0.004); // 000001
    if (h < 0.810) return vec3(0.055, 0.043, 0.098); // 0E0B19
    if (h < 0.934) return vec3(0.137, 0.106, 0.216); // 231B37
    return vec3(0.208, 0.137, 0.294);                // 35234B
}

vec3 wallColor(float h) {
    if (h < 0.222) return vec3(0.184, 0.063, 0.063); // 2F1010
    if (h < 0.425) return vec3(0.149, 0.051, 0.051); // 260D0D
    if (h < 0.622) return vec3(0.251, 0.110, 0.110); // 401C1C
    if (h < 0.816) return vec3(0.118, 0.039, 0.039); // 1E0A0A
    return vec3(0.192, 0.075, 0.075);                // 311313
}

// brighter, more-lit brick tone (sampled from the ceiling, which catches
// more light than the side walls) - blended in near the wall top / patches
vec3 litWallColor(float h) {
    if (h < 0.269) return vec3(0.400, 0.176, 0.176); // 662D2D
    if (h < 0.531) return vec3(0.357, 0.141, 0.141); // 5B2424
    if (h < 0.712) return vec3(0.282, 0.094, 0.094); // 481818
    if (h < 0.822) return vec3(0.286, 0.075, 0.075); // 491313
    if (h < 0.895) return vec3(0.467, 0.231, 0.231); // 773B3B
    if (h < 0.960) return vec3(0.306, 0.118, 0.118); // 4E1E1E
    return vec3(0.227, 0.078, 0.078);                // 3A1414
}

// flat gray checker - the actual light-fixture color in the reference shot,
// not glowstone-orange
vec3 patchColor(float h) {
    if (h < 0.359) return vec3(0.180, 0.180, 0.180); // 2E2E2E
    if (h < 0.595) return vec3(0.306, 0.306, 0.306); // 4E4E4E
    if (h < 0.795) return vec3(0.533, 0.533, 0.533); // 888888
    if (h < 0.935) return vec3(0.349, 0.349, 0.349); // 595959
    return vec3(0.122, 0.122, 0.122);                // 1F1F1F
}

// offset-row brick coursing in an arbitrary 2D plane space; salt varies the
// hash per surface so left/right walls don't mirror-repeat the same pattern
float brickCellHash(vec2 uv, float salt) {
    float row = floor(uv.y * 3.0);
    float rowOffset = mod(row, 2.0) * 0.5;
    float rx = uv.x * 2.0 + rowOffset;
    return hash12(floor(vec2(rx, row)) + salt);
}

float brickGrout(vec2 uv) {
    float row = floor(uv.y * 3.0);
    float rowOffset = mod(row, 2.0) * 0.5;
    float rx = uv.x * 2.0 + rowOffset;
    float fx = fract(rx);
    float fy = fract(uv.y * 3.0);
    return max(1.0 - smoothstep(0.0, 0.06, fx), 1.0 - smoothstep(0.0, 0.08, fy));
}

void main(void) {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution) / resolution.y;

    // camera: inside the tunnel at mid-height, pitched 45 degrees down,
    // gliding forward (scrollZ slides the pattern past a static camera,
    // equivalent to and simpler than translating through an infinite tunnel)
    float speed = 6.0;
    float scrollZ = time * speed;
    float sway = sin(time * 0.35) * 0.5;
    float bob = sin(time * 0.9) * 0.1;

    float halfW = 3.0;
    float H = 5.0;

    vec3 ro = vec3(sway, H * 0.5 + bob, 0.0);
    vec3 forward = normalize(vec3(0.0, -1.0, 1.0));
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, forward);
    vec3 rd = normalize(uv.x * right + uv.y * up + 1.0 * forward);

    float hitT = 1e5;
    int face = -1; // 0 floor, 1 ceiling, 2 left wall, 3 right wall
    vec3 p = ro;

    if (rd.y < 0.0) {
        float t = -ro.y / rd.y;
        vec3 pp = ro + rd * t;
        if (t > 0.0 && abs(pp.x) <= halfW) { hitT = t; face = 0; p = pp; }
    }
    if (rd.y > 0.0) {
        float t = (H - ro.y) / rd.y;
        vec3 pp = ro + rd * t;
        if (t > 0.0 && t < hitT && abs(pp.x) <= halfW) { hitT = t; face = 1; p = pp; }
    }
    if (rd.x < 0.0) {
        float t = (-halfW - ro.x) / rd.x;
        vec3 pp = ro + rd * t;
        if (t > 0.0 && t < hitT && pp.y >= 0.0 && pp.y <= H) { hitT = t; face = 2; p = pp; }
    }
    if (rd.x > 0.0) {
        float t = (halfW - ro.x) / rd.x;
        vec3 pp = ro + rd * t;
        if (t > 0.0 && t < hitT && pp.y >= 0.0 && pp.y <= H) { hitT = t; face = 3; p = pp; }
    }

    vec3 col;

    if (face == -1) {
        col = vec3(0.008, 0.004, 0.004);
    } else {
        float pz = p.z + scrollZ;
        // fade fine detail out with distance so the tiled patterns don't
        // alias into noise near the vanishing point
        float detail = clamp(1.0 - hitT / 55.0, 0.0, 1.0);

        if (face == 0) {
            // obsidian floor: per-block mottling, one hash cell per block
            vec2 cell = floor(vec2(p.x, pz));
            vec3 mottled = floorColor(hash12(cell));
            col = mix(vec3(0.03, 0.02, 0.045), mottled, detail);
        } else {
            // nether-brick walls/ceiling, offset-row brick coursing.
            // Ceiling is essentially never in frame at a true 45-degree
            // pitch (top-of-frame ray angle never reaches horizontal), so
            // the reference's ceiling-mounted light patches are relocated
            // to the visible upper wall band instead, and the wall picks up
            // a brighter (more "lit") tone the closer it gets to that band
            // rather than being a single flat brick color throughout.
            vec2 brickUV = (face == 1) ? vec2(p.x, pz) : vec2(pz, p.y);
            float h = brickCellHash(brickUV, float(face) * 91.0);
            vec3 brick = wallColor(h);

            // "litness" is meant to brighten brick near the light patches;
            // walls are only ever visible up to roughly camera height (this
            // camera never looks far enough up a wall to see near H), so
            // normalize against that instead of the full tunnel height H -
            // normalizing against H directly always evaluated near 0 here.
            float wallHeightFrac = (face == 1) ? 1.0 : clamp(p.y / (H * 0.5), 0.0, 1.0);
            float litness = smoothstep(0.5, 1.0, wallHeightFrac);
            brick = mix(brick, litWallColor(h), litness * 0.7);

            float grout = brickGrout(brickUV);
            brick *= 1.0 - 0.35 * grout;

            col = mix(vec3(0.03, 0.015, 0.015), brick, detail);

            // scattered light patches - a 2D grid on both the ceiling and
            // (since the ceiling itself is essentially never in frame at
            // this pitch - see note above) the walls, standing in for the
            // reference's ceiling-mounted fixtures. Wall patch spacing is
            // tighter vertically since the visible wall band is much
            // shorter than it is long.
            vec2 patchUV = (face == 1) ? vec2(p.x, pz) : vec2(pz, p.y);
            vec2 patchSpacing = (face == 1) ? vec2(2.4) : vec2(2.4, 0.9);
            vec2 patchCell = floor(patchUV / patchSpacing);
            float patchThreshold = (face == 1) ? 0.62 : 0.72;
            float patchRand = hash12(patchCell + float(face) * 41.0 + 3.3);
            if (patchRand > patchThreshold) {
                vec2 local = fract(patchUV / patchSpacing);
                float mask = step(0.15, local.x) * step(local.x, 0.85) * step(0.15, local.y) * step(local.y, 0.85);
                vec3 patch = patchColor(hash12(floor(patchUV * 6.0) + 9.1));
                col = mix(col, patch, mask * detail);
            }
        }

        float fog = 1.0 - exp(-hitT * 0.06);
        col = mix(col, vec3(0.01, 0.005, 0.005), fog);
    }

    col *= 1.0 - 0.2 * dot(uv, uv);

    fragColor = vec4(col, 1.0);
}
