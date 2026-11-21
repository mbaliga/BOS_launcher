package com.bos.sphere.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h400dp-xhdpi", sdk = [35])
class LaunchpadScreenshotLandscapeTest : LaunchpadScreenshotCases("landscape")
