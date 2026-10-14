package com.quantumff.segue.core.designsystem.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.toHsl
import org.junit.Test

class ArtTest {
    private val typicalDensity = Density(2.625f)

    @Test
    fun `size buckets follow the DESIGN md table at 2_625x`() {
        assertThat(CoverArtSize.bucketFor(40.dp, typicalDensity)).isEqualTo(128)
        assertThat(CoverArtSize.bucketFor(48.dp, typicalDensity)).isEqualTo(128)
        assertThat(CoverArtSize.bucketFor(96.dp, typicalDensity)).isEqualTo(256)
        assertThat(CoverArtSize.bucketFor(150.dp, typicalDensity)).isEqualTo(512)
        assertThat(CoverArtSize.bucketFor(120.dp, typicalDensity)).isEqualTo(512)
        assertThat(CoverArtSize.bucketFor(220.dp, typicalDensity)).isEqualTo(1024)
        assertThat(CoverArtSize.bucketFor(400.dp, typicalDensity)).isEqualTo(1024)
    }

    @Test
    fun `a bucket is used when it covers 90 percent of the display size`() {
        assertThat(CoverArtSize.bucketForPixels(142f)).isEqualTo(128)
        assertThat(CoverArtSize.bucketForPixels(143f)).isEqualTo(256)
    }

    @Test
    fun `art colors clamp tint and accent ranges`() {
        val colors = ArtColors.fromSeed(Color(0xFFFF2000))

        val tintDark = colors.tintDark.toHsl()
        // 8-bit sRGB storage quantizes the result slightly.
        assertThat(tintDark.saturation).isAtMost(0.56f)
        assertThat(tintDark.lightness).isIn(Range.closed(0.179f, 0.301f))
        val tintLight = colors.tintLight.toHsl()
        assertThat(tintLight.lightness).isIn(Range.closed(0.819f, 0.901f))
        assertThat(colors.accentDark.toHsl().lightness).isIn(Range.closed(0.549f, 0.701f))
        assertThat(colors.accentLight.toHsl().lightness).isIn(Range.closed(0.349f, 0.451f))
    }

    @Test
    fun `box blur keeps a flat image flat`() {
        val pixels = IntArray(32 * 32) { 0xFF336699.toInt() }
        boxBlur(pixels, width = 32, height = 32, radius = 3, passes = 2)
        assertThat(pixels.toSet()).containsExactly(0xFF336699.toInt())
    }

    @Test
    fun `box blur spreads a single bright pixel`() {
        val pixels = IntArray(32 * 32) { 0xFF000000.toInt() }
        pixels[16 * 32 + 16] = 0xFFFFFFFF.toInt()
        boxBlur(pixels, width = 32, height = 32, radius = 3, passes = 2)

        val center = pixels[16 * 32 + 16] and 0xFF
        val neighbour = pixels[16 * 32 + 18] and 0xFF
        val far = pixels[0] and 0xFF
        assertThat(center).isLessThan(0xFF)
        assertThat(neighbour).isGreaterThan(0)
        assertThat(far).isEqualTo(0)
        assertThat(pixels.all { it ushr 24 == 0xFF }).isTrue()
    }
}
