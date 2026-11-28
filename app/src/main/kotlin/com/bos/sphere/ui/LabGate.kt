package com.bos.sphere.ui

import com.bos.sphere.BuildConfig

/**
 * CFG-17: whether the Lab (spikes) category is listed. Debug builds always show it. A release build shows it
 * while `LAB_VISIBLE` is true, which the alpha default and `-Pliftoff.lab=true` give; `-Pliftoff.lab=false` hides it.
 * The owner flips the default before a public build (docs/decisions/CFG-17.md).
 */
object LabGate {
    fun visible(debug: Boolean = BuildConfig.DEBUG, labVisible: Boolean = BuildConfig.LAB_VISIBLE): Boolean = debug || labVisible
}
