package com.quantumff.segue.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.source(name: String = "test") = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "$name.preferences_pb") },
    )

    @Test
    fun `defaults match the model defaults`() = runTest {
        assertThat(source().preferences.first()).isEqualTo(UserPreferences())
    }

    @Test
    fun `every preference persists when set`() = runTest {
        val source = source()

        source.setThemeMode(ThemeMode.Black)
        source.setAccentSource(AccentSource.AlbumArt)
        source.setAccentPreset(AccentPreset.Teal)
        source.setDefaultShuffleMode(ShuffleMode.Albums)
        source.setWifiStreamingQuality(StreamingQuality(maxBitRateKbps = 0, format = "raw"))
        source.setCellularStreamingQuality(StreamingQuality(maxBitRateKbps = 128, format = "opus"))
        source.setQueueSyncEnabled(false)
        source.setAdoptRemoteQueueAutomatically(true)
        source.setReplayGainMode(ReplayGainMode.Album)
        source.setReplayGainTarget(ReplayGainTarget.Ebu)
        source.setReplayGainBoost(true)
        source.setStreamCacheSizeMb(256)
        source.setStopWhenPausedMinutes(30)

        assertThat(source.preferences.first()).isEqualTo(
            UserPreferences(
                themeMode = ThemeMode.Black,
                accentSource = AccentSource.AlbumArt,
                accentPreset = AccentPreset.Teal,
                defaultShuffleMode = ShuffleMode.Albums,
                wifiStreamingQuality = StreamingQuality(maxBitRateKbps = 0, format = "raw"),
                cellularStreamingQuality = StreamingQuality(maxBitRateKbps = 128, format = "opus"),
                queueSyncEnabled = false,
                adoptRemoteQueueAutomatically = true,
                replayGainMode = ReplayGainMode.Album,
                replayGainTarget = ReplayGainTarget.Ebu,
                replayGainBoost = true,
                streamCacheSizeMb = 256,
                stopWhenPausedMinutes = 30,
            ),
        )
    }

    @Test
    fun `an unknown stored enum value falls back to the default`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "x.preferences_pb")
        }
        dataStore.updateData {
            it.toMutablePreferences().apply {
                set(androidx.datastore.preferences.core.stringPreferencesKey("theme_mode"), "Neon")
            }
        }

        assertThat(UserPreferencesDataSource(dataStore).preferences.first().themeMode).isEqualTo(ThemeMode.System)
    }

    @Test
    fun `the device id is generated once and then stable`() = runTest {
        val source = source()

        val first = source.deviceId()
        val second = source.deviceId()

        assertThat(first).matches("[0-9a-f-]{36}")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `different installs get different device ids`() = runTest {
        assertThat(source("a").deviceId()).isNotEqualTo(source("b").deviceId())
    }
}
