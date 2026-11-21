package com.bos.sphere.ui

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 28: iteration harness (also kept as the screenshot producer): the mock-up frames at 411 dp x 909 dp, 352 dpi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h909dp-352dpi", sdk = [35])
class CommsMockShotTest : CommsLayoutCases("mock") {
    @Test fun shotScreen() { render(name = "screen") }
    @Test fun shotMenu() { render(); openMenu(); shoot("comms-mock-menu-dark") }
}
