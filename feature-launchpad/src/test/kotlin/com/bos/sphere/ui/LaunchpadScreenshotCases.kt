package com.bos.sphere.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Test

/** The STR-07 matrix: no grant, empty, grouped, digest, in light and dark. Subclasses fix the orientation. */
abstract class LaunchpadScreenshotCases(private val orientation: String) : LaunchpadShots() {

    private fun render(name: String, dark: Boolean, body: @androidx.compose.runtime.Composable () -> Unit): Double {
        rule.setContent { Themed(dark) { body() } }
        return shoot("launchpad-$orientation-$name-${if (dark) "dark" else "light"}")
    }

    private fun shown(text: String, substring: Boolean = false) {
        val nodes = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
        assertTrue("'$text' is not on screen", nodes.isNotEmpty())
        rule.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    private fun check(name: String, dark: Boolean, body: @androidx.compose.runtime.Composable () -> Unit, verify: () -> Unit) {
        val lum = render(name, dark, body)
        verify()
        if (dark) assertTrue("dark page should be dark, luminance $lum", lum < 100) else assertTrue("light page should be light, luminance $lum", lum > 150)
    }

    private val noGrantChecks = {
        shown("Allow notification access"); shown("Stays on this device. Nothing is sent anywhere.")
    }
    @Test fun STR_07_noGrant_dark() = check("no-grant", true, { Body(grouped, hasAccess = false) }, noGrantChecks)
    @Test fun STR_07_noGrant_light() = check("no-grant", false, { Body(grouped, hasAccess = false) }, noGrantChecks)

    @Test fun STR_05_noGrantAfterAskedSaysStillOff() {
        render("no-grant-asked", true) { Body(grouped, hasAccess = false, asked = true) }
        shown("Notification access is still off, so Launchpad and the media strip stay empty.")
    }

    private val emptyChecks = { shown("All caught up") }
    @Test fun STR_07_empty_dark() = check("empty", true, { Body(emptyList()) }, emptyChecks)
    @Test fun STR_07_empty_light() = check("empty", false, { Body(emptyList()) }, emptyChecks)

    private val groupedChecks = { shown("Mum"); shown("Chat", substring = true) }
    @Test fun STR_07_grouped_dark() = check("grouped", true, { Body(grouped) }, groupedChecks)
    @Test fun STR_07_grouped_light() = check("grouped", false, { Body(grouped) }, groupedChecks)

    private val digestChecks = { shown("Digest"); shown("held for the digest", substring = true) }
    @Test fun STR_07_digest_dark() = check("digest", true, { Body(digestItems, policies = digestPolicies) }, digestChecks)
    @Test fun STR_07_digest_light() = check("digest", false, { Body(digestItems, policies = digestPolicies) }, digestChecks)

    @Test fun STR_10_systemRowShowsStateWithoutClaimingToToggle() {
        render("system", true) { Body(grouped) }
        shown("82%"); shown("Wi-Fi"); shown("Vibrate")
    }
}
