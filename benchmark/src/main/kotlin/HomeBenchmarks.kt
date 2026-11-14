package com.bos.sphere.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** INACTIVE sources, see README.md. Budgets come from spec section 13 and core-quality Budgets. */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class HomeBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    private val pkg = "com.bos.sphere"
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** NFR-02: cold start to interactive home, 700 ms p95. Read the p95 of timeToInitialDisplayMs from the report. */
    @Test fun NFR_02_coldStart() = rule.measureRepeated(
        // Liftoff.homeInteractive runs from process start to the first frame with the app list drawn (HomeTiming).
        packageName = pkg, metrics = listOf(StartupTimingMetric(), TraceSectionMetric("Liftoff.homeInteractive")), iterations = 20,
        startupMode = StartupMode.COLD, compilationMode = CompilationMode.Partial(),
    ) { pressHome(); startActivityAndWait() }

    /** NFR-01: Home press to interactive home while the launcher is warm, 150 ms p95. */
    @Test fun NFR_01_warmHomePress() = rule.measureRepeated(
        packageName = pkg, metrics = listOf(StartupTimingMetric()), iterations = 20,
        startupMode = StartupMode.WARM, compilationMode = CompilationMode.Partial(),
    ) { pressHome(); startActivityAndWait() }

    /** NFR-05: at most 1% janky frames while paging the home and scrolling the drawer. */
    @Test fun NFR_05_homeAndDrawerFrames() = rule.measureRepeated(
        packageName = pkg, metrics = listOf(FrameTimingMetric()), iterations = 10,
        startupMode = StartupMode.WARM, compilationMode = CompilationMode.Partial(),
    ) {
        pressHome(); startActivityAndWait()
        val w = device.displayWidth; val h = device.displayHeight
        repeat(3) { device.swipe((w * 0.8).toInt(), h / 2, (w * 0.2).toInt(), h / 2, 20) }        // page the home
        device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.2).toInt(), 20)                       // open the drawer
        repeat(5) { device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.3).toInt(), 20) }         // scroll it
        device.wait(androidx.test.uiautomator.Until.hasObject(By.pkg(pkg)), 2000)
    }
}
