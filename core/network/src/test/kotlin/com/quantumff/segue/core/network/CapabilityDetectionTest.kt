package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CapabilityDetectionTest {
    @get:Rule
    val rule = SubsonicServerRule()

    @Test
    fun `an OpenSubsonic server reports its extensions`() = runTest {
        rule.enqueueFixture("extensions.json") // ping
        rule.enqueueFixture("extensions.json")

        val capabilities = rule.client.detectCapabilities()

        assertThat(capabilities.serverType).isEqualTo("navidrome")
        assertThat(capabilities.supportsIndexBasedQueue).isTrue()
        assertThat(capabilities.supportsSongLyrics).isTrue()
        assertThat(capabilities.supportsTranscodeOffset).isTrue()
        assertThat(capabilities.extensions["songLyrics"]).containsExactly(1, 2)
    }

    @Test
    fun `a plain Subsonic server is not asked for extensions`() = runTest {
        rule.enqueueBody("""{"subsonic-response":{"status":"ok","version":"1.15.0"}}""")

        val capabilities = rule.client.detectCapabilities()

        assertThat(capabilities.openSubsonic).isFalse()
        assertThat(capabilities.apiVersion).isEqualTo("1.15.0")
        assertThat(capabilities.extensions).isEmpty()
        assertThat(rule.server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a failing extensions call degrades to no extensions`() = runTest {
        rule.enqueueFixture("extensions.json")
        rule.enqueueBody(
            """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":0,"message":"nope"}}}""",
        )

        val capabilities = rule.client.detectCapabilities()

        assertThat(capabilities.openSubsonic).isTrue()
        assertThat(capabilities.extensions).isEmpty()
    }
}
