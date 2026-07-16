package com.eatbefore.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test

/**
 * Measures cold startup with and without the Baseline Profile, so the profile's value can
 * be checked rather than assumed:
 *   ./gradlew :baselineprofile:connectedBenchmarkAndroidTest
 *
 * Emulator numbers are noisy — treat them as a direction, not a benchmark. Real figures
 * need a physical device.
 */
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measure(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        measure(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measure(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = "com.eatbefore",
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
