package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test

class ServerCapabilitiesTest {
    private val navidrome = ServerCapabilities(
        apiVersion = "1.16.1",
        openSubsonic = true,
        serverType = "navidrome",
        serverVersion = "0.58.0",
        extensions = persistentMapOf(
            "transcodeOffset" to persistentListOf(1),
            "songLyrics" to persistentListOf(1, 2),
            "indexBasedQueue" to persistentListOf(1),
        ),
    )

    @Test
    fun `advertised extensions are supported`() {
        assertThat(navidrome.supportsIndexBasedQueue).isTrue()
        assertThat(navidrome.supportsSongLyrics).isTrue()
        assertThat(navidrome.supportsTranscodeOffset).isTrue()
    }

    @Test
    fun `missing extensions are not supported`() {
        val legacy = navidrome.copy(extensions = persistentMapOf())
        assertThat(legacy.supportsIndexBasedQueue).isFalse()
        assertThat(legacy.supportsSongLyrics).isFalse()
        assertThat(legacy.supportsTranscodeOffset).isFalse()
    }

    @Test
    fun `extensions are ignored when the server is not OpenSubsonic`() {
        assertThat(navidrome.copy(openSubsonic = false).supportsIndexBasedQueue).isFalse()
    }

    @Test
    fun `supports checks a specific extension version`() {
        assertThat(navidrome.supports(ServerCapabilities.SONG_LYRICS, version = 2)).isTrue()
        assertThat(navidrome.supports(ServerCapabilities.SONG_LYRICS, version = 3)).isFalse()
    }

    @Test
    fun `unknown capabilities support nothing`() {
        assertThat(ServerCapabilities.Unknown.supportsSongLyrics).isFalse()
        assertThat(ServerCapabilities.Unknown.openSubsonic).isFalse()
    }
}
