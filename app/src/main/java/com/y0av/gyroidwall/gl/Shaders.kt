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
        uniform float uThickness;   // gyroid shell half-thickness
        uniform int   uPalette;
        uniform int   uMaxSteps;    // early-out cap (<= MAX_STEPS)

        out vec4 fragColor;

        const float TAU = 6.28318530718;
        const int   MAX_STEPS = 72;
        const float MAX_DIST  = 22.0;
        const float SURF_EPS  = 0.0018;

        mat2 r2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }

        // Raw gyroid scalar field. TAU-periodic in every axis, so the whole scene
        // can be wrapped into one period to keep coordinates small & precise.
        float gyroidField(vec3 p) {
            return dot(sin(p), cos(p.yzx));
        }

        // Distance estimate to the gyroid shell. The 0.5 factor compensates for the
        // field's gradient (~2) so sphere tracing never overshoots the surface.
        float mapScene(vec3 p) {
            return (abs(gyroidField(p)) - uThickness) * 0.5;
        }

        // 4-tap tetrahedron normal (cheaper than a 6-tap central difference).
        vec3 calcNormal(vec3 p) {
            const vec2 k = vec2(1.0, -1.0);
            const float h = 0.0025;
            return normalize(
                k.xyy * mapScene(p + k.xyy * h) +
                k.yyx * mapScene(p + k.yyx * h) +
                k.yxy * mapScene(p + k.yxy * h) +
                k.xxx * mapScene(p + k.xxx * h));
        }

        // Inigo Quilez cosine gradient palette.
        vec3 cosPalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
            return a + b * cos(TAU * (c * t + d));
        }

        vec3 palette(float t) {
            if (uPalette == 1) {            // Aurora
                return cosPalette(t, vec3(0.35, 0.45, 0.45), vec3(0.35, 0.45, 0.40),
                                     vec3(1.0), vec3(0.0, 0.20, 0.45));
            } else if (uPalette == 2) {     // Magma
                return cosPalette(t, vec3(0.50, 0.25, 0.18), vec3(0.55, 0.35, 0.25),
                                     vec3(1.0, 1.0, 0.6), vec3(0.0, 0.15, 0.30));
            } else if (uPalette == 3) {     // Cyan Neon
                return cosPalette(t, vec3(0.16, 0.42, 0.52), vec3(0.30, 0.45, 0.45),
                                     vec3(1.0), vec3(0.30, 0.45, 0.55));
            } else if (uPalette == 4) {     // Monochrome
                return cosPalette(t, vec3(0.55), vec3(0.45), vec3(1.0), vec3(0.0));
            }
            // 0: Iridescent oil-slick
            return cosPalette(t, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0, 0.33, 0.67));
        }

        void main() {
            vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / uResolution.y;

            float t = uTime * uSpeed;
            vec2 tilt = uTilt * uParallax;

            // Camera drifts forward through the lattice with a gentle bob;
            // tilt nudges the origin for near/far parallax.
            vec3 ro = vec3(0.55 * sin(t * 0.13), 0.45 * cos(t * 0.10), t * 0.55);
            ro.xy += tilt * 0.55;

            // Camera basis looking down +z; tilt yaws/pitches the ray direction.
            vec3 rd = normalize(vec3(uv, 1.7));
            rd.xz *= r2(tilt.x * 0.45);
            rd.yz *= r2(-tilt.y * 0.45);

            // Wrap the origin into one gyroid period (precision-safe over long runs).
            vec3 roW = mod(ro, TAU);

            float dist = 0.0;
            bool hit = false;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (i >= uMaxSteps) break;
                float d = mapScene(roW + rd * dist);
                if (d < SURF_EPS) { hit = true; break; }
                dist += d;
                if (dist > MAX_DIST) break;
            }

            vec3 col;
            if (hit) {
                vec3 p = roW + rd * dist;
                vec3 n = calcNormal(p);
                vec3 lightDir = normalize(vec3(0.5, 0.7, -0.45));
                float diff = clamp(dot(n, lightDir), 0.0, 1.0);
                float amb = 0.35 + 0.65 * clamp(0.5 + 0.5 * n.y, 0.0, 1.0);
                float fres = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
                float g = gyroidField(p);

                float tone = 0.55 + 0.32 * g + 0.22 * fres + 0.04 * dist + 0.05 * t;
                vec3 base = palette(tone);
                col = base * (0.25 * amb + 0.85 * diff) + fres * 0.7 * palette(tone + 0.25);

                float fog = 1.0 - exp(-0.018 * dist * dist);
                col = mix(col, palette(0.62) * 0.10, clamp(fog, 0.0, 1.0));
            } else {
                float v = 0.5 + 0.5 * rd.y;
                col = palette(0.6 + 0.15 * v) * 0.10 + 0.02;
            }

            // Vignette, Reinhard-ish tone map, gamma.
            col *= 1.0 - 0.28 * dot(uv * 0.7, uv * 0.7);
            col = col / (col + vec3(0.85));
            col = pow(max(col, vec3(0.0)), vec3(0.4545));
            fragColor = vec4(col, 1.0);
        }
    """.trimIndent()
}
