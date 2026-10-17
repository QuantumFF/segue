package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepeatModeTest {
    @Test
    fun `the repeat button cycles off, all, one`() {
        assertThat(RepeatMode.Off.next()).isEqualTo(RepeatMode.All)
        assertThat(RepeatMode.All.next()).isEqualTo(RepeatMode.One)
        assertThat(RepeatMode.One.next()).isEqualTo(RepeatMode.Off)
    }
}
