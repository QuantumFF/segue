package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Album grid fling jank (frame durations and overruns), without and with the Baseline Profile. */
@LargeTest
@RunWith(Parameterized::class)
class ScrollBenchmarks(private val compilation: CompilationMode) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun signIn() = signInBeforeRecording()

    @Test
    fun scrollAlbumGrid() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openLibraryAlbums()
        },
    ) {
        scrollAlbumGrid()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = comparedCompilationModes.map { arrayOf(it) }
    }
}
