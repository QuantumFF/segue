package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeroLayoutTest {
    @Test
    fun `a portrait phone gets the centered compact hero`() {
        assertThat(heroLayout(width = 360.dp, screenHeight = 640.dp)).isEqualTo(HeroLayout.Compact)
        assertThat(heroLayout(width = 599.dp, screenHeight = 900.dp)).isEqualTo(HeroLayout.Compact)
        assertThat(heroArtSize(HeroLayout.Compact, circular = false, screenHeight = 640.dp)).isEqualTo(220.dp)
    }

    @Test
    fun `the wide hero needs both 600 of width and 480 of height`() {
        assertThat(heroLayout(width = 600.dp, screenHeight = 480.dp)).isEqualTo(HeroLayout.Wide)
        assertThat(heroLayout(width = 1280.dp, screenHeight = 800.dp)).isEqualTo(HeroLayout.Wide)
        assertThat(heroLayout(width = 600.dp, screenHeight = 479.dp)).isEqualTo(HeroLayout.Short)
    }

    @Test
    fun `a phone in landscape gets the short hero`() {
        assertThat(heroLayout(width = 800.dp, screenHeight = 360.dp)).isEqualTo(HeroLayout.Short)
    }

    @Test
    fun `a narrow short window keeps the centered hero, so the actions keep their width`() {
        assertThat(heroLayout(width = 320.dp, screenHeight = 470.dp)).isEqualTo(HeroLayout.Compact)
        assertThat(heroArtSize(HeroLayout.Compact, circular = false, screenHeight = 300.dp)).isEqualTo(120.dp)
    }

    @Test
    fun `the short hero's art is at most 40 percent of the screen height`() {
        assertThat(heroArtSize(HeroLayout.Short, circular = false, screenHeight = 360.dp)).isEqualTo(144.dp)
        assertThat(heroArtSize(HeroLayout.Short, circular = true, screenHeight = 360.dp)).isEqualTo(144.dp)
        assertThat(heroArtSize(HeroLayout.Short, circular = true, screenHeight = 470.dp)).isEqualTo(160.dp)
    }

    @Test
    fun `compact and wide heroes keep their art sizes`() {
        assertThat(heroArtSize(HeroLayout.Compact, circular = false, screenHeight = 640.dp)).isEqualTo(220.dp)
        assertThat(heroArtSize(HeroLayout.Wide, circular = false, screenHeight = 800.dp)).isEqualTo(250.dp)
        assertThat(heroArtSize(HeroLayout.Wide, circular = true, screenHeight = 800.dp)).isEqualTo(160.dp)
    }
}
