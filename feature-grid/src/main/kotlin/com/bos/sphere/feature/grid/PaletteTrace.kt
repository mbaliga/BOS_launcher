package com.bos.sphere.feature.grid

import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.bos.sphere.core.quality.TraceSections

/**
 * NFR-03 / NFR-04: trace sections the macrobenchmark reads (`TraceSectionMetric`). `paletteOpen` runs from the first
 * composition of the palette to the first frame drawn after it; `paletteFirstResults` is the same span (the results
 * are composed in that first pass); `paletteKeystroke` runs from each query change to the next drawn frame. Public
 * `android.os.Trace` async sections only; they cost a few microseconds and are no-ops when no trace is recording.
 */
@Composable
fun PaletteTraceEffects(query: String) {
    val seq = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val c = seq.intValue++
        Trace.beginAsyncSection(TraceSections.PALETTE_OPEN, c)
        Trace.beginAsyncSection(TraceSections.PALETTE_FIRST_RESULTS, c)
        withFrameNanos { }
        Trace.endAsyncSection(TraceSections.PALETTE_FIRST_RESULTS, c)
        Trace.endAsyncSection(TraceSections.PALETTE_OPEN, c)
    }
    LaunchedEffect(query) {
        if (query.isEmpty() && seq.intValue <= 1) return@LaunchedEffect // the initial empty query is the open, not a keystroke
        val c = seq.intValue++
        Trace.beginAsyncSection(TraceSections.PALETTE_KEYSTROKE, c)
        withFrameNanos { }
        Trace.endAsyncSection(TraceSections.PALETTE_KEYSTROKE, c)
    }
}
