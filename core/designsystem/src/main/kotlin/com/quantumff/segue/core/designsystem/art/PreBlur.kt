package com.quantumff.segue.core.designsystem.art

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Builds the "blurred art" bitmap (DESIGN.md §6): downscale to 32×32, then a CPU box blur
 * (radius 3, 2 passes). Coil runs transformations off the main thread. Draw the result scaled to
 * fill with `FilterQuality.Low`; bilinear upscaling of 32px looks blurred enough.
 *
 * The same 32px bitmap feeds [ArtColorExtractor].
 */
class PreBlurTransformation : Transformation() {
    override val cacheKey: String = "segue.preBlur.$SIZE.r$RADIUS.p$PASSES"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val small = input.scale(SIZE, SIZE, filter = true)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small !== input) small.recycle()
        boxBlur(pixels, width = SIZE, height = SIZE, radius = RADIUS, passes = PASSES)
        return createBitmap(SIZE, SIZE).apply { setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE) }
    }

    override fun equals(other: Any?): Boolean = other is PreBlurTransformation

    override fun hashCode(): Int = cacheKey.hashCode()

    companion object {
        const val SIZE = 32
        private const val RADIUS = 3
        private const val PASSES = 2
    }
}

/**
 * In-place separable box blur over ARGB pixels. Edges clamp. Each pass is one horizontal and one
 * vertical sweep, so two passes approximate a Gaussian.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int) {
    require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    val scratch = IntArray(pixels.size)
    repeat(passes) {
        blurLine(src = pixels, dst = scratch, lines = height, length = width, radius = radius, horizontal = true)
        blurLine(src = scratch, dst = pixels, lines = width, length = height, radius = radius, horizontal = false)
    }
}

private fun blurLine(src: IntArray, dst: IntArray, lines: Int, length: Int, radius: Int, horizontal: Boolean) {
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        fun index(i: Int): Int {
            val clamped = i.coerceIn(0, length - 1)
            return if (horizontal) line * length + clamped else clamped * lines + line
        }
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = src[index(i)]
            a += p ushr 24
            r += p shr 16 and 0xFF
            g += p shr 8 and 0xFF
            b += p and 0xFF
        }
        for (i in 0 until length) {
            dst[index(i)] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
            val out = src[index(i - radius)]
            val incoming = src[index(i + radius + 1)]
            a += (incoming ushr 24) - (out ushr 24)
            r += (incoming shr 16 and 0xFF) - (out shr 16 and 0xFF)
            g += (incoming shr 8 and 0xFF) - (out shr 8 and 0xFF)
            b += (incoming and 0xFF) - (out and 0xFF)
        }
    }
}
