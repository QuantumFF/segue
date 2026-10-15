package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Startup to Home, signed in, without and with the Baseline Profile:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` (docs/PERFORMANCE.md).
 */
@LargeTest
@RunWith(Parameterized::class)
class StartupBenchmarks(private val startupMode: StartupMode, private val compilation: CompilationMode) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = startupMode,
        iterations = ITERATIONS,
        setupBlock = {
            signInIfNeeded()
            pressHome()
        },
    ) {
        startActivityAndWait()
    }

    companion object {
        private const val ITERATIONS = 10

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun parameters() = listOf(StartupMode.COLD, StartupMode.WARM).flatMap { mode ->
            listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require)).map {
                arrayOf(mode, it)
            }
        }
    }
}
