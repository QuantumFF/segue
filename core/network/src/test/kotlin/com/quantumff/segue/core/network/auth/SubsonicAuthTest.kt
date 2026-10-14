package com.quantumff.segue.core.network.auth

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.network.SubsonicServerRule
import com.quantumff.segue.core.network.param
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SubsonicAuthTest {
    @get:Rule
    val rule = SubsonicServerRule()

    @Test
    fun `token is the md5 of password plus salt`() {
        // The example from the Subsonic API documentation.
        assertThat(SubsonicToken.create(password = "sesame", salt = "c19b2d"))
            .isEqualTo("26719a1196d2a940705a59634eb18eab")
    }

    @Test
    fun `secure salts are random hex`() {
        val generator = SecureRandomSaltGenerator()
        val a = generator.next()
        val b = generator.next()
        assertThat(a).matches("[0-9a-f]{16}")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `every request carries token auth and client parameters`() = runTest {
        rule.enqueueOk()

        rule.client.ping()

        val request = rule.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/music/rest/ping.view")
        assertThat(request.param("u")).isEqualTo("admin")
        assertThat(request.param("s")).isEqualTo("salt0")
        assertThat(request.param("t")).isEqualTo(SubsonicToken.create("sesame", "salt0"))
        assertThat(request.param("v")).isEqualTo("1.16.1")
        assertThat(request.param("c")).isEqualTo("Segue")
        assertThat(request.param("f")).isEqualTo("json")
        assertThat(request.param("p")).isNull()
    }

    @Test
    fun `each request gets a fresh salt`() = runTest {
        rule.enqueueOk()
        rule.enqueueOk()

        rule.client.ping()
        rule.client.ping()

        val first = rule.takeRequest()
        val second = rule.takeRequest()
        assertThat(first.param("s")).isNotEqualTo(second.param("s"))
        assertThat(second.param("t")).isEqualTo(SubsonicToken.create("sesame", second.param("s")!!))
    }

    @Test
    fun `an explicit client name is not overwritten`() = runTest {
        rule.enqueueOk()

        rule.client.savePlayQueueByIndex(
            songIds = listOf("a"),
            currentIndex = 0,
            positionMillis = 0,
            clientName = "Segue-3f2a",
        )

        assertThat(rule.takeRequest().url.queryParameterValues("c")).containsExactly("Segue-3f2a")
    }
}
