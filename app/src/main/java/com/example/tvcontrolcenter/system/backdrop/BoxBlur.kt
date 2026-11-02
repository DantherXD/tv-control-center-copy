package com.example.tvcontrolcenter.system.backdrop

/**
 * Blurs opaque ARGB pixels in place: [passes] rounds of a horizontal then a vertical moving
 * average. Three passes is close to a Gaussian. Edges repeat their outermost pixel. Pure
 * Kotlin so it can be unit-tested; the caller hands it a small (about 480 px wide) image.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
    require(pixels.size >= width * height) { "pixel buffer too small" }
    if (radius <= 0 || width <= 0 || height <= 0) return
    val scratch = IntArray(width * height)
    repeat(passes) {
        blurLines(pixels, scratch, width, height, radius, horizontal = true)
        blurLines(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurLines(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val divisor = 2 * radius + 1
    val half = divisor / 2 // rounds instead of truncating, so dark areas do not creep darker

    for (line in 0 until lines) {
        val base = if (horizontal) line * width else line
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val c = src[base + i.coerceIn(0, length - 1) * step]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (i in 0 until length) {
            dst[base + i * step] = (0xFF shl 24) or
                (((r + half) / divisor) shl 16) or (((g + half) / divisor) shl 8) or ((b + half) / divisor)
            val add = src[base + minOf(i + radius + 1, length - 1) * step]
            val drop = src[base + maxOf(i - radius, 0) * step]
            r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
    }
}
