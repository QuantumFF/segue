package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerTest {
    @Test
    fun `servers with the same fields are equal`() {
        val a = Server(id = 1, name = "Home", baseUrl = "https://music.example", username = "me")
        assertThat(a.copy()).isEqualTo(a)
        assertThat(a.copy(id = 2)).isNotEqualTo(a)
    }
}
