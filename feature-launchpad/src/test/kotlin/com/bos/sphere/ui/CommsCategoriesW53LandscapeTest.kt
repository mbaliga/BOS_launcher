package com.bos.sphere.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1200dp-h1000dp-mdpi", sdk = [35])
class CommsCategoriesW53LandscapeTest : CommsCategoriesW53Cases("landscape")
