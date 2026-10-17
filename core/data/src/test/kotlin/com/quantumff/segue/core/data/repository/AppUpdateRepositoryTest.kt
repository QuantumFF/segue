package com.quantumff.segue.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.datastore.UserPreferencesDataSource
import com.quantumff.segue.core.model.AppRelease
import com.quantumff.segue.core.network.GitHubReleaseClient
import com.quantumff.segue.core.network.di.NetworkModule
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val clock = FakeClock()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private class Env(val repository: GitHubAppUpdateRepository, val preferences: UserPreferencesDataSource)

    private fun TestScope.env(installed: String = "0.1.1"): Env {
        val preferences = UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") },
        )
        val releases = GitHubReleaseClient(OkHttpClient(), NetworkModule.json(), server.url("/latest").toString())
        val repository = GitHubAppUpdateRepository(
            releases,
            preferences,
            clock,
            installed,
            StandardTestDispatcher(testScheduler),
        )
        return Env(repository, preferences)
    }

    private fun release(tag: String) = MockResponse.Builder()
        .body("""{"tag_name":"$tag","html_url":"https://github.com/QuantumFF/segue/releases/tag/$tag","draft":false}""")
        .build()

    @Test
    fun `a newer release is returned without its v prefix`() = runTest {
        server.enqueue(release("v0.2.0"))

        assertThat(env().repository.check())
            .isEqualTo(AppRelease("0.2.0", "https://github.com/QuantumFF/segue/releases/tag/v0.2.0"))
    }

    @Test
    fun `the installed release or an older one is not an update`() = runTest {
        server.enqueue(release("v0.1.1"))
        server.enqueue(release("v0.1.0"))
        val repository = env().repository

        assertThat(repository.check()).isNull()
        assertThat(repository.check()).isNull()
    }

    @Test
    fun `HTTP errors and bodies that are not a release are unreachable`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().body("<html>").build())
        val repository = env().repository

        assertThat(
            runCatching {
                repository.check()
            }.exceptionOrNull(),
        ).isInstanceOf(RemoteException.Unreachable::class.java)
        assertThat(
            runCatching {
                repository.check()
            }.exceptionOrNull(),
        ).isInstanceOf(RemoteException.Unreachable::class.java)
    }

    @Test
    fun `the automatic check runs at most once a day`() = runTest {
        server.enqueue(release("v0.2.0"))
        server.enqueue(release("v0.2.0"))
        val repository = env().repository

        assertThat(repository.checkAutomatically()?.version).isEqualTo("0.2.0")
        clock.now += 23 * HOUR
        assertThat(repository.checkAutomatically()).isNull()
        assertThat(server.requestCount).isEqualTo(1)

        clock.now += HOUR
        assertThat(repository.checkAutomatically()?.version).isEqualTo("0.2.0")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `a failed automatic check is retried on the next start`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(release("v0.2.0"))
        val repository = env().repository

        assertThat(repository.checkAutomatically()).isNull()
        assertThat(repository.checkAutomatically()?.version).isEqualTo("0.2.0")
    }

    @Test
    fun `the automatic check does nothing while it is turned off`() = runTest {
        val env = env()
        env.preferences.update { it.copy(checkForUpdates = false) }

        assertThat(env.repository.checkAutomatically()).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `versions compare part by part as numbers`() {
        assertThat(isNewerVersion("0.1.10", "0.1.9")).isTrue()
        assertThat(isNewerVersion("1.0", "0.9.9")).isTrue()
        assertThat(isNewerVersion("0.2", "0.1.5")).isTrue()
        assertThat(isNewerVersion("0.1.1", "0.1.1")).isFalse()
        assertThat(isNewerVersion("0.1", "0.1.0")).isFalse()
        assertThat(isNewerVersion("0.1.0", "0.1.1")).isFalse()
        assertThat(isNewerVersion("0.2.0-beta", "0.1.1")).isTrue()
    }

    private companion object {
        const val HOUR = 60 * 60 * 1_000L
    }
}
