package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGainTarget
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplayGainPreampMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val preamp = floatPreferencesKey("replay_gain_preamp_db")
    private val target = stringPreferencesKey("replay_gain_target")

    private suspend fun migrate(vararg pairs: Preferences.Pair<*>): Preferences {
        val prefs = preferencesOf(*pairs)
        return if (ReplayGainPreampMigration.shouldMigrate(prefs)) ReplayGainPreampMigration.migrate(prefs) else prefs
    }

    @Test
    fun `a preamp becomes the nearest target`() = runTest {
        assertThat(migrate(preamp to 0f)[target]).isEqualTo(ReplayGainTarget.ReplayGain2.name)
        assertThat(migrate(preamp to -2f)[target]).isEqualTo(ReplayGainTarget.ReplayGain2.name)
        assertThat(migrate(preamp to -3f)[target]).isEqualTo(ReplayGainTarget.Ebu.name)
        assertThat(migrate(preamp to -15f)[target]).isEqualTo(ReplayGainTarget.Ebu.name)
        // Positive preamps wanted louder; boost is a separate, battery-costing choice, so it stays off.
        assertThat(migrate(preamp to 6f)[target]).isEqualTo(ReplayGainTarget.ReplayGain2.name)
    }

    @Test
    fun `the preamp is removed`() = runTest {
        assertThat(migrate(preamp to -6f)[preamp]).isNull()
    }

    @Test
    fun `a target already chosen wins over an old preamp`() = runTest {
        assertThat(migrate(preamp to -6f, target to ReplayGainTarget.ReplayGain2.name)[target])
            .isEqualTo(ReplayGainTarget.ReplayGain2.name)
    }

    @Test
    fun `nothing to migrate without a preamp`() = runTest {
        assertThat(ReplayGainPreampMigration.shouldMigrate(preferencesOf())).isFalse()
    }

    @Test
    fun `the data source reads the migrated target`() = runTest {
        // Seeds the old preamp the way an earlier version stored it, then migrates on first read.
        val seed = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true

            override suspend fun migrate(currentData: Preferences) = preferencesOf(preamp to -4.5f)

            override suspend fun cleanUp() = Unit
        }
        val dataStore = PreferenceDataStoreFactory.create(
            migrations = listOf(seed) + UserPreferencesDataSource.MIGRATIONS,
            scope = backgroundScope,
        ) { File(tmp.root, "migrated.preferences_pb") }

        val prefs = UserPreferencesDataSource(dataStore).preferences.first()

        assertThat(prefs.replayGainTarget).isEqualTo(ReplayGainTarget.Ebu)
        assertThat(prefs.replayGainBoost).isFalse()
        assertThat(dataStore.data.first()[preamp]).isNull()
    }
}
