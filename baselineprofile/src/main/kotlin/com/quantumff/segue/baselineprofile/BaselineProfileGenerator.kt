package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile: ./gradlew :app:generateBaselineProfile
 *
 * Stub: only covers cold start. Extend with the critical user journeys (scroll the album grid,
 * open an album, start playback, open now playing) once those screens exist.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        // Set by the baseline profile Gradle plugin for the variant under test.
        packageName = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "targetAppId not passed; run through :app:generateBaselineProfile"
        },
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
