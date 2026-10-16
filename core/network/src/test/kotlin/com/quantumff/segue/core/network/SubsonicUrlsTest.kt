package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.network.auth.SubsonicToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test

class SubsonicUrlsTest {
    @get:Rule
    val rule = SubsonicServerRule()

    @Test
    fun `stream url carries auth and transcoding parameters`() {
        val url = rule.client.streamUrl(
            "song 1",
            maxBitRateKbps = 320,
            format = "opus",
            timeOffsetSeconds = 30,
        ).toHttpUrl()

        assertThat(url.encodedPath).isEqualTo("/music/rest/stream.view")
        assertThat(url.queryParameter("id")).isEqualTo("song 1")
        assertThat(url.queryParameter("maxBitRate")).isEqualTo("320")
        assertThat(url.queryParameter("format")).isEqualTo("opus")
        assertThat(url.queryParameter("timeOffset")).isEqualTo("30")
        assertThat(url.queryParameter("u")).isEqualTo("admin")
        assertThat(url.queryParameter("t")).isEqualTo(SubsonicToken.create("sesame", url.queryParameter("s")!!))
        assertThat(url.queryParameter("c")).isEqualTo("Segue")
        assertThat(url.queryParameter("v")).isEqualTo("1.16.1")
    }

    @Test
    fun `stream url without transcoding omits the optional parameters`() {
        val url = rule.client.streamUrl("s").toHttpUrl()

        assertThat(url.queryParameterNames).containsNoneOf("maxBitRate", "format", "timeOffset")
    }

    @Test
    fun `cover art url requests the display size`() {
        val url = rule.client.coverArtUrl("al-1", size = 300).toHttpUrl()

        assertThat(url.encodedPath).isEqualTo("/music/rest/getCoverArt.view")
        assertThat(url.queryParameter("id")).isEqualTo("al-1")
        assertThat(url.queryParameter("size")).isEqualTo("300")
    }

    @Test
    fun `download url points at the original file`() {
        val url = rule.client.downloadUrl("s").toHttpUrl()

        assertThat(url.encodedPath).isEqualTo("/music/rest/download.view")
        assertThat(url.queryParameter("id")).isEqualTo("s")
        assertThat(url.queryParameter("f")).isNull()
    }
}
