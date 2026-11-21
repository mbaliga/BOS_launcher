package com.bos.sphere.core.data

/**
 * CFG-06: first run asks for exactly three things, in this order, and nothing else. Every other grant (notification
 * access, usage access, contacts, folders) waits until the user first touches the feature that needs it (CFG-07).
 */
enum class OnboardingStep(val title: String, val optional: Boolean) {
    SetAsHome("Make it home", optional = true),
    HomeStyle("Pick a look", optional = false),
    Import("Bring your setup", optional = true),
}

object OnboardingFlow {
    val steps: List<OnboardingStep> = OnboardingStep.entries

    /** Runtime permissions or special access the first run may request. Always empty. */
    val upFrontGrants: List<String> = emptyList()

    /** Whether onboarding shows: only before it has been completed or skipped on this install. */
    fun shouldShow(prefs: LauncherPrefs) = !prefs.onboardingDone
}
