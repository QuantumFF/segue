package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile and startup (DEX layout) profile:
 * `./gradlew :app:generateBaselineProfile` with one device or emulator attached (a rooted
 * google_apis emulator works). Writes app/src/release/generated/baselineProfiles.
 *
 * Journey: cold start to Home, scroll the album grid, open an album, play it, open Now Playing
 * and the queue. The first run signs in to the demo server (see [signInIfNeeded]).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage,
        // Startup profile: only the cold start to Home, so DEX layout favors the startup path.
        includeInStartupProfile = true,
        profileBlock = {
            signInIfNeeded()
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        },
    )

    @Test
    fun generateJourneys() = rule.collect(
        packageName = targetPackage,
        maxIterations = 5,
        profileBlock = {
            signInIfNeeded()
            pressHome()
            startActivityAndWait()
            openLibraryAlbums()
            scrollAlbumGrid()
            openAlbumAndPlay()
            openNowPlayingAndQueue()
            stopPlayback()
        },
    )
}
