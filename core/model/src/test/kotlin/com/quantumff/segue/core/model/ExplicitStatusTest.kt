package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExplicitStatusTest {
    @Test
    fun `parses OpenSubsonic values`() {
        assertThat(ExplicitStatus.fromApi("explicit")).isEqualTo(ExplicitStatus.Explicit)
        assertThat(ExplicitStatus.fromApi("clean")).isEqualTo(ExplicitStatus.Clean)
    }

    @Test
    fun `empty, missing, and unknown values mean not marked`() {
        assertThat(ExplicitStatus.fromApi("")).isEqualTo(ExplicitStatus.None)
        assertThat(ExplicitStatus.fromApi(null)).isEqualTo(ExplicitStatus.None)
        assertThat(ExplicitStatus.fromApi("rude")).isEqualTo(ExplicitStatus.None)
    }

    @Test
    fun `round-trips through the API value`() {
        ExplicitStatus.entries.forEach { assertThat(ExplicitStatus.fromApi(it.apiValue)).isEqualTo(it) }
    }
}
