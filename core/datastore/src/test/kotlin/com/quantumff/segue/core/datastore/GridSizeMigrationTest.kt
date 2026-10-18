package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.GridSize
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GridSizeMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val albumSize = stringPreferencesKey("album_grid_size")
    private val artistSize = stringPreferencesKey("artist_grid_size")
    private val albumColumns = intPreferencesKey("album_grid_extra_columns")
    private val artistColumns = intPreferencesKey("artist_grid_extra_columns")

    private suspend fun migrate(vararg pairs: Preferences.Pair<*>): Preferences {
        val prefs = preferencesOf(*pairs)
        return if (GridSizeMigration.shouldMigrate(prefs)) GridSizeMigration.migrate(prefs) else prefs
    }

    @Test
    fun `each named size becomes the columns it added`() = runTest {
        assertThat(migrate(albumSize to "ExtraSmall")[albumColumns]).isEqualTo(2)
        assertThat(migrate(albumSize to "Small")[albumColumns]).isEqualTo(1)
        assertThat(migrate(albumSize to "Medium")[albumColumns]).isEqualTo(0)
        assertThat(migrate(artistSize to "Large")[artistColumns]).isEqualTo(-1)
    }

    @Test
    fun `the named sizes are removed`() = runTest {
        val migrated = migrate(albumSize to "Small", artistSize to "Bogus")

        assertThat(migrated[albumSize]).isNull()
        assertThat(migrated[artistSize]).isNull()
        assertThat(migrated[artistColumns]).isNull()
    }

    @Test
    fun `columns already chosen win over an old name`() = runTest {
        assertThat(migrate(albumSize to "Large", albumColumns to 4)[albumColumns]).isEqualTo(4)
    }

    @Test
    fun `nothing to migrate without a named size`() = runTest {
        assertThat(GridSizeMigration.shouldMigrate(preferencesOf(albumColumns to 1))).isFalse()
    }

    @Test
    fun `the data source reads the migrated sizes`() = runTest {
        // Seeds the names the way an earlier version stored them, then migrates on first read.
        val seed = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true

            override suspend fun migrate(currentData: Preferences) =
                preferencesOf(albumSize to "ExtraSmall", artistSize to "Large")

            override suspend fun cleanUp() = Unit
        }
        val dataStore = PreferenceDataStoreFactory.create(
            migrations = listOf(seed) + UserPreferencesDataSource.MIGRATIONS,
            scope = backgroundScope,
        ) { File(tmp.root, "migrated.preferences_pb") }

        val prefs = UserPreferencesDataSource(dataStore).preferences.first()

        assertThat(prefs.albumGridSize).isEqualTo(GridSize(2))
        assertThat(prefs.artistGridSize).isEqualTo(GridSize(-1))
    }
}
