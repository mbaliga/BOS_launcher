package com.bos.sphere.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INACTIVE sources, see README.md. The palette must emit the named trace sections (androidx.tracing) around
 * open-to-first-results and around each keystroke-to-updated-results; that belongs to the palette stream.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PaletteBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    private val pkg = "com.bos.sphere"
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** NFR-03: palette open to first results, 100 ms p95. */
    @Test fun NFR_03_paletteOpen() = rule.measureRepeated(
        packageName = pkg, metrics = listOf(TraceSectionMetric("Liftoff.paletteFirstResults")), iterations = 20,
        startupMode = StartupMode.WARM, compilationMode = CompilationMode.Partial(),
    ) {
        pressHome(); startActivityAndWait()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 15)
        device.waitForIdle()
    }

    /** NFR-04: each keystroke to updated app results, 50 ms p95. */
    @Test fun NFR_04_paletteKeystroke() = rule.measureRepeated(
        packageName = pkg, metrics = listOf(TraceSectionMetric("Liftoff.paletteKeystroke")), iterations = 20,
        startupMode = StartupMode.WARM, compilationMode = CompilationMode.Partial(),
    ) {
        pressHome(); startActivityAndWait()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 15)
        "calendar".forEach { device.executeShellCommand("input text $it") }
        device.waitForIdle()
    }
}
