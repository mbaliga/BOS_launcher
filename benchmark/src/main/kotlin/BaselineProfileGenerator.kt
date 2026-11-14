package com.bos.sphere.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** INACTIVE sources, see README.md. Journeys: cold start, home, drawer scroll, palette open and typing (NFR-01 to NFR-05). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun generate() = rule.collect(packageName = "com.bos.sphere", includeInStartupProfile = true) {
        pressHome(); startActivityAndWait()
        val w = device.displayWidth; val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 15)
        device.executeShellCommand("input text cal")
        device.pressBack()
        repeat(3) { device.swipe((w * 0.8).toInt(), h / 2, (w * 0.2).toInt(), h / 2, 20) }
    }
}
