package com.bos.sphere.core.quality

/**
 * NFR-01 to NFR-04: names of the trace sections the macrobenchmarks (benchmark/) read. The app emits them with
 * `android.os.Trace` async sections; a test pins that the benchmark sources and the app use these same strings.
 */
object TraceSections {
    const val PALETTE_OPEN = "Liftoff.paletteOpen"
    const val PALETTE_FIRST_RESULTS = "Liftoff.paletteFirstResults"
    const val PALETTE_KEYSTROKE = "Liftoff.paletteKeystroke"
    const val HOME_INTERACTIVE = "Liftoff.homeInteractive"
    val all = listOf(PALETTE_OPEN, PALETTE_FIRST_RESULTS, PALETTE_KEYSTROKE, HOME_INTERACTIVE)
}
