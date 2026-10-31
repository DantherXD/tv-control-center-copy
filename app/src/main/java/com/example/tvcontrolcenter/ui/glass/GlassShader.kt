package com.example.tvcontrolcenter.ui.glass

/**
 * AGSL for one glass tile (API 33+). Draws a rounded rectangle whose interior is
 * the pre-blurred wallpaper, bent by a lens near the rim, with chromatic fringing,
 * adaptive tint, a bright rim and a moving specular sheen.
 *
 * Coordinates: `frag` is local to the tile. `origin` is the tile's top-left in
 * wallpaper space, in screen pixels. `k` converts screen pixels to blurred-bitmap pixels.
 */
internal const val GLASS_AGSL = """
uniform shader wall;
uniform float2 size;
uniform float2 origin;
uniform float k;
uniform float radius;
uniform float focus;
uniform float phase;
uniform float lens;
uniform float chroma;
uniform half3 tint;
uniform float tintAmount;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 frag) {
    float2 halfSize = size * 0.5;
    float2 p = frag - halfSize;
    float d = sdRoundRect(p, halfSize, radius);

    // Surface normal from the distance field.
    float2 e = float2(1.0, 0.0);
    float2 n = normalize(float2(
        sdRoundRect(p + e.xy, halfSize, radius) - sdRoundRect(p - e.xy, halfSize, radius),
        sdRoundRect(p + e.yx, halfSize, radius) - sdRoundRect(p - e.yx, halfSize, radius)) + 1e-4);

    // 0 at the rim, 1 deep inside. Refraction is strongest within ~40% of the short side.
    float depth = clamp(-d / (min(size.x, size.y) * 0.4), 0.0, 1.0);
    float bend = pow(1.0 - depth, 3.0);
    float2 shift = -n * bend * lens * (0.85 + 0.35 * focus);

    float2 base = origin + frag + shift;
    float ca = bend * chroma;
    half r = wall.eval((base + n * ca) * k).r;
    half g = wall.eval(base * k).g;
    half b = wall.eval((base - n * ca) * k).b;
    half3 col = half3(r, g, b);

    // Adaptive tint, thinner and brighter when focused.
    col = mix(col, tint, half(tintAmount));
    col += half(0.04 + 0.12 * focus);

    // Rim highlight with a sheen that travels around the edge.
    float rim = 1.0 - smoothstep(0.0, 2.6, abs(d + 1.0));
    float2 lightDir = normalize(float2(cos(phase), sin(phase) - 0.7));
    float spec = pow(max(dot(n, lightDir), 0.0), 5.0);
    col += half(rim * (0.30 + 0.45 * spec + 0.30 * focus));

    // Soft top sheen across the upper third.
    col += half((1.0 - smoothstep(0.0, size.y * 0.55, frag.y)) * (0.05 + 0.05 * focus));

    float alpha = 1.0 - smoothstep(-1.0, 0.6, d);
    col = clamp(col, half3(0.0), half3(1.0));
    return half4(col * half(alpha), half(alpha));
}
"""
