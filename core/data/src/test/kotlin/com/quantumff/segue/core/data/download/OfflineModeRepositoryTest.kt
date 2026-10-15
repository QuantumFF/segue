package com.quantumff.segue.core.data.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineModeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val network = object : NetworkConnectivity {
        override val isConnected = MutableStateFlow(true)
    }

    private fun TestScope.preferences() = DownloadPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") },
    )

    @Test
    fun `offline follows the connectivity callback and the manual switch`() = runTest {
        val preferences = preferences()
        val repository = DefaultOfflineModeRepository(network, preferences, backgroundScope)

        repository.isOffline.first { !it }
        network.isConnected.value = false
        assertThat(repository.isOffline.first { it }).isTrue()

        network.isConnected.value = true
        assertThat(repository.isOffline.first { !it }).isFalse()

        preferences.setForceOffline(true)
        assertThat(repository.isOffline.first { it }).isTrue()
    }
}
