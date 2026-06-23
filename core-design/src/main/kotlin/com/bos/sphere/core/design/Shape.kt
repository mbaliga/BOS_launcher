package com.bos.sphere.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Hyle corner ramp. The app tile in the prototype uses a 22px radius on a 132px tile;
 * panels use larger radii (Hub = 26px bottom corners). Mapped onto Material 3 shape slots.
 */
val HyleShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // chips, small affordances
    small = RoundedCornerShape(13.dp),      // search field, keys
    medium = RoundedCornerShape(22.dp),     // app tiles
    large = RoundedCornerShape(26.dp),      // Hub / large panels
    extraLarge = RoundedCornerShape(32.dp),
)
