package com.bos.sphere.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h909dp-352dpi", sdk = [35])
class CommsScalePortraitTest : CommsScaleCases("portrait")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w909dp-h411dp-352dpi", sdk = [35])
class CommsScaleLandscapeTest : CommsScaleCases("landscape")
