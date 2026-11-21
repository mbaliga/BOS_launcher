package com.bos.sphere.core.design

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Wave 37 (specSync): the one title style of the three black bottom popups (Sound and Vibration, Connectivity, Home quick sheet).
 * The owner's Sound mock-up draws the title at about 28 to 30 sp; before this the sheets used 22 sp each, copied from one another.
 */
object PopupTokens {
    val TitleSize = 28.sp
    val TitleWeight = FontWeight.Light
}
