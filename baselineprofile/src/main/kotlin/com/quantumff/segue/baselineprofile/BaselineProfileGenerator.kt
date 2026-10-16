package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile and startup (DEX layout) profile:
 * `./gradlew :app:generateBaselineProfile` with one device or emulator attached (a rooted
 * google_apis emulator works). Writes app/src/release/generated/baselineProfiles.
 *
 * Journey: cold start to Home, scroll the album grid, open an album, play it, open Now Playing
 * and the queue. Signing in to the demo server (see [signInIfNeeded]) happens before recording,
 * so login code stays out of the profiles.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun signIn() = signInBeforeRecording()

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage,
        // Startup profile: only the cold start to Home, so DEX layout favors the startup path.
        includeInStartupProfile = true,
        profileBlock = {
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
            pressHome()
            startActivityAndWait()
            openLibraryAlbums()
            scrollAlbumGrid()
            openAlbumAndPlay()
            openNowPlayingAndQueue()
            // Also stops playback, and saves the profile, so the next iteration starts quiet.
            killProcess()
        },
    )
}
