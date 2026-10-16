package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflinePlaybackTest {
    @Test
    fun `row availability follows the downloaded set only when offline`() {
        assertThat(OfflinePlayback.isAvailable("a", isOffline = true, downloaded = emptySet())).isFalse()
        assertThat(OfflinePlayback.isAvailable("a", isOffline = true, downloaded = setOf("a"))).isTrue()
        assertThat(OfflinePlayback.isAvailable("a", isOffline = false, downloaded = emptySet())).isTrue()
    }
}
