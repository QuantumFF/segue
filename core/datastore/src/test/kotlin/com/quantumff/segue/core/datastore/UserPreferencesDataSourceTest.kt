package com.quantumff.segue.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `adopt remote queue defaults to off and persists when set`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        )
        val source = UserPreferencesDataSource(dataStore)

        assertThat(source.preferences.first().adoptRemoteQueueAutomatically).isFalse()

        source.setAdoptRemoteQueueAutomatically(true)

        assertThat(source.preferences.first().adoptRemoteQueueAutomatically).isTrue()
    }
}
