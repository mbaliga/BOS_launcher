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

/**
 * Wave 58 (defects58, CFG-63): the "Pick a look" choices. A fresh install has Pages Off, so a home style alone changed nothing visible: Glance only keeps
 * the calm Glance home (Pages Off); every page style turns Pages on (Paginated) so the style is what the first page shows.
 */
enum class OnboardingLook(val style: HomeStyle?) {
    GlanceOnly(null), Grid(HomeStyle.Grid), List(HomeStyle.List), Psp(HomeStyle.Psp), Dsi(HomeStyle.Dsi);

    companion object {
        /** The look the prefs show now: Glance only while Pages is Off and Glance is on, otherwise the page style. */
        fun of(p: LauncherPrefs): OnboardingLook =
            if (p.homePages == HomePages.Off && p.glanceEnabled) GlanceOnly else entries.first { it.style == p.homeStyle }
    }
}

object OnboardingFlow {
    /** The prefs after the user picks [look] in onboarding (see [OnboardingLook]). Continuous stays Continuous; only Off becomes Paginated. */
    fun applyLook(p: LauncherPrefs, look: OnboardingLook): LauncherPrefs = when (val s = look.style) {
        null -> p.copy(homePages = HomePages.Off, glanceEnabled = true)
        else -> p.copy(homeStyle = s, homePages = if (p.homePages == HomePages.Off) HomePages.Paginated else p.homePages)
    }

    val steps: List<OnboardingStep> = OnboardingStep.entries

    /** Runtime permissions or special access the first run may request. Always empty. */
    val upFrontGrants: List<String> = emptyList()

    /** Whether onboarding shows: only before it has been completed or skipped on this install. */
    fun shouldShow(prefs: LauncherPrefs) = !prefs.onboardingDone
}
