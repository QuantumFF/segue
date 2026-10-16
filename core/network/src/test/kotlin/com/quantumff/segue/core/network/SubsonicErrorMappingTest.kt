package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class SubsonicErrorMappingTest {
    @get:Rule
    val rule = SubsonicServerRule()

    private fun failed(code: Int, message: String = "boom") =
        """{"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome","serverVersion":"0.58.0",""" +
            """"openSubsonic":true,"error":{"code":$code,"message":"$message"}}}"""

    private inline fun <reified T : Throwable> errorFor(body: String, httpCode: Int = 200): T {
        rule.enqueueBody(body, httpCode)
        return assertThrows(T::class.java) { kotlinx.coroutines.runBlocking { rule.client.ping() } }
    }

    @Test
    fun `every documented error code maps to a typed error`() {
        val expected = mapOf(
            0 to SubsonicException.Generic::class,
            10 to SubsonicException.MissingParameter::class,
            20 to SubsonicException.ClientTooOld::class,
            30 to SubsonicException.ServerTooOld::class,
            40 to SubsonicException.WrongCredentials::class,
            41 to SubsonicException.TokenAuthNotSupported::class,
            42 to SubsonicException.AuthMechanismNotSupported::class,
            43 to SubsonicException.ConflictingAuthMechanisms::class,
            44 to SubsonicException.InvalidApiKey::class,
            50 to SubsonicException.NotAuthorized::class,
            60 to SubsonicException.TrialExpired::class,
            70 to SubsonicException.NotFound::class,
        )
        expected.forEach { (code, type) ->
            val error = errorFor<SubsonicException.Api>(failed(code))
            assertThat(error).isInstanceOf(type.java)
            assertThat(error.code).isEqualTo(code)
            assertThat(error.serverMessage).isEqualTo("boom")
        }
    }

    @Test
    fun `an undocumented code is kept`() {
        val error = errorFor<SubsonicException.Unknown>(failed(99))
        assertThat(error.code).isEqualTo(99)
    }

    @Test
    fun `auth errors are flagged`() {
        assertThat(errorFor<SubsonicException.Api>(failed(40)).isAuthError).isTrue()
        assertThat(errorFor<SubsonicException.Api>(failed(70)).isAuthError).isFalse()
    }

    @Test
    fun `a Subsonic error inside an HTTP error body is still typed`() {
        assertThat(errorFor<SubsonicException.Api>(failed(40), httpCode = 401))
            .isInstanceOf(SubsonicException.WrongCredentials::class.java)
    }

    @Test
    fun `an HTTP error without a Subsonic body maps to Http`() {
        val error = errorFor<SubsonicException.Http>("<html>Bad gateway</html>", httpCode = 502)
        assertThat(error.httpCode).isEqualTo(502)
    }

    @Test
    fun `a body that is not a Subsonic response maps to Malformed`() {
        errorFor<SubsonicException.Malformed>("<html>Captive portal</html>")
    }

    @Test
    fun `a failed status without an error object maps to Generic`() {
        errorFor<SubsonicException.Generic>("""{"subsonic-response":{"status":"failed","version":"1.16.1"}}""")
    }

    @Test
    fun `connection failures map to Network`() = runTest {
        rule.server.close()
        val error = runCatching { rule.client.ping() }.exceptionOrNull()
        assertThat(error).isInstanceOf(SubsonicException.Network::class.java)
    }
}
