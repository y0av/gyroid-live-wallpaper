package com.y0av.gyroidwall.gl

/**
 * GLSL ES 3.00 shaders for the gyroid live wallpaper.
 *
 * The vertex shader emits a single full-screen triangle from `gl_VertexID`
 * (no vertex buffer needed). All of the work happens in the fragment shader,
 * which sphere-traces a triply-periodic gyroid minimal surface and shades it
 * with an iridescent cosine palette, steered by device tilt.
 */
object Shaders {

    val VERTEX = """
        #version 300 es
        void main() {
            // Attribute-less full-screen triangle: ids 0,1,2 -> (-1,-1),(3,-1),(-1,3)
            vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
    """.trimIndent()

    val FRAGMENT = """
        #version 300 es
        precision highp float;

        uniform vec2  uResolution;
        uniform float uTime;
        uniform vec2  uTilt;        // smoothed device tilt, [-1, 1]
        uniform float uSpeed;
        uniform float uParallax;
        uniform float uThickness;   // gyroid shell half-thickness (field units)
        uniform int   uPalette;
        uniform int   uMaxSteps;    // early-out cap (<= MAX_STEPS)

        out vec4 fragColor;

        const float TAU      = 6.28318530718;
        const int   MAX_STEPS = 110;
        const float MAX_DIST = 16.0;
        const float SURF_EPS = 0.0011;
        const float MIN_STEP = 0.004;
        const float DENSITY  = 2.8;   // lattice cells per period; higher = busier
        const float TUBE_R   = 1.35;  // radius of the clear channel the camera flies down

        mat2 r2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }

        // Raw gyroid scalar field. Periodic, so the scene can be wrapped to keep
        // coordinates small (precision-safe over long fly-throughs).
        float gyroidField(vec3 p) {
            return dot(sin(p), cos(p.yzx));
        }

        // Conservative signed distance to the gyroid shell, in world units, with an
        // open tube carved around [center] along z so the camera always has a clear
        // channel to fly through (the shell half-thickness fades to <0 near the axis,
        // which removes the surface smoothly rather than inserting a cylinder wall).
        float mapScene(vec3 p, vec2 center) {
            float r = length(p.xy - center);
            float carve = smoothstep(TUBE_R * 0.45, TUBE_R, r);   // 0 in core -> 1 outside
            float localT = mix(-0.35, uThickness, carve);
            return (abs(gyroidField(p * DENSITY)) - localT) / (2.2 * DENSITY);
        }

        // 4-tap tetrahedron normal (cheaper than a 6-tap central difference).
        vec3 calcNormal(vec3 p, vec2 center) {
            const vec2 k = vec2(1.0, -1.0);
            const float h = 0.0015;
            return normalize(
                k.xyy * mapScene(p + k.xyy * h, center) +
                k.yyx * mapScene(p + k.yyx * h, center) +
                k.yxy * mapScene(p + k.yxy * h, center) +
                k.xxx * mapScene(p + k.xxx * h, center));
        }

        // Inigo Quilez cosine gradient palette.
        vec3 cosPalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
            return a + b * cos(TAU * (c * t + d));
        }

        vec3 palette(float t) {
            if (uPalette == 1) {            // Aurora
                return cosPalette(t, vec3(0.32, 0.45, 0.42), vec3(0.32, 0.45, 0.38),
                                     vec3(1.0), vec3(0.0, 0.22, 0.48));
            } else if (uPalette == 2) {     // Magma
                return cosPalette(t, vec3(0.55, 0.27, 0.18), vec3(0.55, 0.35, 0.22),
                                     vec3(1.0, 1.0, 0.55), vec3(0.0, 0.12, 0.28));
            } else if (uPalette == 3) {     // Cyan Neon
                return cosPalette(t, vec3(0.12, 0.42, 0.52), vec3(0.28, 0.45, 0.45),
                                     vec3(1.0), vec3(0.32, 0.45, 0.55));
            } else if (uPalette == 4) {     // Monochrome
                return cosPalette(t, vec3(0.58), vec3(0.45), vec3(1.0), vec3(0.0));
            }
            // 0: Iridescent oil-slick
            return cosPalette(t, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0, 0.33, 0.67));
        }

        void main() {
            vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / uResolution.y;

            float t = uTime * uSpeed;
            vec2 tilt = uTilt * uParallax;

            // Camera flies forward through the lattice with a gentle bob; tilt
            // shifts the origin (near/far parallax) and steers the ray direction.
            vec3 ro = vec3(0.8 * sin(t * 0.11), 0.6 * cos(t * 0.09), t * 0.9);
            ro.xy += tilt * 0.5;

            vec3 rd = normalize(vec3(uv, 1.5));
            rd.xz *= r2(tilt.x * 0.5);
            rd.yz *= r2(-tilt.y * 0.5);

            // Wrap into one period of the (density-scaled) field.
            vec3 roW = mod(ro, TAU / DENSITY);
            vec2 center = roW.xy;   // the clear tube is centered on the camera

            // March by |distance| so the camera works whether it's in the void or
            // momentarily inside a sheet; track step count for cheap crevice AO.
            float dist = 0.0;
            int used = 0;
            bool hit = false;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (i >= uMaxSteps) break;
                used = i;
                float st = abs(mapScene(roW + rd * dist, center));
                if (st < SURF_EPS) { hit = true; break; }
                dist += max(st, MIN_STEP);
                if (dist > MAX_DIST) break;
            }

            vec3 col;
            if (hit) {
                vec3 p = roW + rd * dist;
                vec3 n = calcNormal(p, center);
                vec3 lightDir = normalize(vec3(0.4, 0.75, -0.5));
                float diff = clamp(dot(n, lightDir), 0.0, 1.0);
                float amb = 0.4 + 0.6 * clamp(0.5 + 0.5 * n.y, 0.0, 1.0);
                float fres = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 4.0);
                float ao = clamp(1.0 - float(used) / float(uMaxSteps) * 1.3, 0.15, 1.0);

                // Iridescence: hue sweeps with view angle, depth and position.
                float tone = 0.5 + 0.5 * fres + 0.06 * dist
                           + 0.12 * sin(p.x + p.y + p.z) + 0.05 * t;
                vec3 base = palette(tone);
                col = base * (0.25 + 0.85 * diff) * amb * ao + fres * 0.7 * palette(tone + 0.3);

                float fog = 1.0 - exp(-0.012 * dist * dist);
                col = mix(col, vec3(0.01, 0.015, 0.03), clamp(fog, 0.0, 1.0));
            } else {
                float v = clamp(0.5 + 0.5 * rd.y, 0.0, 1.0);
                col = mix(vec3(0.01, 0.015, 0.03), palette(0.6) * 0.05, v);
            }

            // Vignette, Reinhard-ish tone map, gamma.
            col *= 1.0 - 0.30 * dot(uv * 0.7, uv * 0.7);
            col = col / (col + vec3(0.8));
            col = pow(max(col, vec3(0.0)), vec3(0.4545));
            fragColor = vec4(col, 1.0);
        }
    """.trimIndent()
}
