package com.bos.sphere.feature.grid

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-23, STR-01 (wave 23 sweep): the quiet edge handles take the Glance ink, so they show on the light theme's light grey home as well as on the dark one. */
class GlanceHandleTintW23Test {
    @Test fun HOM_23_theQuietHandleIsLightOnDarkAndDarkOnLight() {
        assertTrue(GlanceHandleFade.tint(true).luminance() > 0.6f)
        assertTrue(GlanceHandleFade.tint(false).luminance() < 0.1f)
    }
}
