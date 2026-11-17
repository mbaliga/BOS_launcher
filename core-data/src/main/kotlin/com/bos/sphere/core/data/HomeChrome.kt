package com.bos.sphere.core.data

/** The chrome elements a landing may draw around the home (wave 17, HOM-23, HOM-22, STR-01, STR-09, STR-14). */
enum class ChromeElement {
    /** The slim edge handle on the Launchpad side (the physical left edge; the right in right-to-left). */
    LeftHandle,
    /** The waiting-notifications indicator (STR-09): a pill on other landings, a quiet dot on Glance. */
    WaitingIndicator,
    /** The Launchpad rail, a list-style chrome that is only drawn when the rail setting is on. */
    Rail,
    /** The slim edge handle of the Recents panel on the other edge (STR-14). */
    RightHandle,
    /** The media strip (STR-06). Glance has its own music line, so it draws no strip. */
    MediaStrip,
    /** The dock (HOM-12). Glance draws it only when the right side is the dock rail. */
    Dock,
    /** The "work profile is paused" banner. */
    WorkPausedBanner,
}

/** What a rule needs to know besides the landing: the settings that switch an element on. */
data class ChromeSettings(
    val leftPage: LeftPage = LeftPage.Launchpad,
    val launchpadRail: Boolean = false,
    val rightSide: RightSide = RightSide.RecentsPanel,
    val mediaControls: Boolean = false,
) {
    companion object { fun of(p: LauncherPrefs) = ChromeSettings(p.leftPage, p.launchpadRail, p.rightSide, p.mediaControls) }
}

/**
 * Wave 17 (owner bug report on build 48: "The glance needs to have launchpad on the left. Why is that missing?"): the explicit table
 * of which chrome element each landing draws. It replaces the old single flag `showsChrome` (false for the enum value Minimal, which
 * the Glance home reuses), which silently took the Launchpad handle, the waiting dot and the recents handle away from Glance.
 * Every landing x element is stated here and tested, so a new landing cannot lose an element by accident.
 */
object HomeChromeRules {
    fun shows(landing: HomeLanding, element: ChromeElement, s: ChromeSettings = ChromeSettings()): Boolean {
        val glance = landing == HomeLanding.Glance
        val launchpad = s.leftPage == LeftPage.Launchpad
        return when (element) {
            ChromeElement.LeftHandle -> s.leftPage != LeftPage.Off
            ChromeElement.WaitingIndicator -> launchpad && !s.launchpadRail
            ChromeElement.Rail -> launchpad && s.launchpadRail
            ChromeElement.RightHandle -> s.rightSide == RightSide.RecentsPanel
            ChromeElement.MediaStrip -> s.mediaControls && !glance
            ChromeElement.Dock -> !glance || s.rightSide == RightSide.DockRail
            ChromeElement.WorkPausedBanner -> !glance
        }
    }

    /**
     * W20 audit (HOM-23, STR-14): Right side = Dock rail on Glance must actually show the rail, also on a phone where the dock setting is
     * off by default; otherwise the indicator dot "Show the dock" and the edge swipe did nothing. Other landings keep the dock setting.
     */
    fun forcesDock(landing: HomeLanding, s: ChromeSettings): Boolean = landing == HomeLanding.Glance && s.rightSide == RightSide.DockRail

    /** Handles and the waiting indicator are drawn in their quiet form (low alpha, fading when idle) on Glance only. */
    fun quiet(landing: HomeLanding, element: ChromeElement): Boolean =
        landing == HomeLanding.Glance && (element == ChromeElement.LeftHandle || element == ChromeElement.RightHandle || element == ChromeElement.WaitingIndicator)

    /** The edge zone (dp) in which a drag opens a card: the handle's width on Glance, so the swipe also starts where the handle is. */
    fun edgeZoneDp(landing: HomeLanding): Float = if (landing == HomeLanding.Glance) GLANCE_EDGE_DP else DEFAULT_EDGE_DP
    const val DEFAULT_EDGE_DP = 28f
    const val GLANCE_EDGE_DP = 48f
    const val HANDLE_WIDTH_DP = 48f
    const val HANDLE_HEIGHT_DP = 160f
}
