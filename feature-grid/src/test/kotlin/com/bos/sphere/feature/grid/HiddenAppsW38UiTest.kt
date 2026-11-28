package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HiddenAppsModel
import com.bos.sphere.core.data.HideCandidate
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.Rule as CommsRule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.core.data.SearchLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 38 (popupsHidden): the Hidden apps page (listing with the source named, search, Unhide with one confirmation line, Unhide all, the "Hidden by Comms
 * rules" list with Remove) and Radar's quiet "N hidden apps match: Show" row. Screenshots: `build/screenshots/hidden-w38-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class HiddenAppsW38UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    @Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") }

    private fun app(label: String, pkg: String) = AppEntry(pkg, ComponentName(pkg, "$pkg.Main"), label, Process.myUserHandle()) { null }
    private val whatsapp = app("WhatsApp", "com.whatsapp")
    private val maps = app("Maps", "com.maps")
    private val mail = app("Mail", "com.mail")
    private val all = listOf(whatsapp, maps, mail)

    private var hiddenKeys by mutableStateOf(setOf(whatsapp.key, maps.key))
    private var rules by mutableStateOf(listOf(CommsRule.create(RuleAction.Hide, packageName = "com.whatsapp"), CommsRule.create(RuleAction.Hide, packageName = "com.mail", sender = "Promo Bot")))
    private val unhidden = mutableListOf<String>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "hidden-w38-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun showPane(initialQuery: String = "") = rule.setContent {
        val rows = HiddenAppsModel.rows(all.map { HideCandidate(it.key, it.packageName, it.label) }, hiddenKeys, rules)
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12)).verticalScroll(rememberScrollState()).padding(16.dp)) {
            HiddenAppsPane(
                rows = rows, commsRules = HiddenAppsModel.commsRules(rules), appLabel = { p -> all.firstOrNull { it.packageName == p }?.label },
                onUnhide = { r -> unhidden += r.key; hiddenKeys = hiddenKeys - r.key; rules = rules - HiddenAppsModel.rulesToClear(r.packageName, rules).toSet() },
                onUnhideAll = { unhidden += "ALL"; hiddenKeys = emptySet(); rules = rules.filterNot { it.action == RuleAction.Hide } },
                onRemoveRule = { r -> rules = rules - r }, icon = {}, initialQuery = initialQuery,
            )
        }
    }

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun HOM_05_w38_theListShowsEveryHiddenAppWithItsSourceNamed() {
        showPane()
        assertTrue(exists("hid.row.${whatsapp.key}") && exists("hid.row.${maps.key}")); assertFalse(exists("hid.row.${mail.key}"))
        rule.onNodeWithTag("hid.source.${whatsapp.key}").assertTextContainsAll("Hidden from home, the drawer and Radar", "Hidden in Comms by a rule")
        rule.onNodeWithTag("hid.source.${maps.key}").assertTextContainsAll("Hidden from home, the drawer and Radar")
        shot("pane")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextContainsAll(vararg parts: String) {
        val t = fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        parts.forEach { assertTrue("'$t' has '$it'", t.contains(it)) }
    }

    @Test fun HOM_05_w38_unhideAsksOnceInOneLineThenClearsTheLauncherSetAndTheCommsRuleForThatApp() {
        showPane()
        rule.onNodeWithTag("hid.unhide.${whatsapp.key}").performScrollTo().performClick()
        assertTrue("the confirmation line is shown first", exists("hid.confirm")); assertTrue(unhidden.isEmpty())
        shot("confirm")
        rule.onNodeWithTag("hid.confirm.ok").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(whatsapp.key), unhidden)
        assertFalse(whatsapp.key in hiddenKeys)
        assertTrue("its app-wide Comms rule went with it", rules.none { it.packageName == "com.whatsapp" && it.action == RuleAction.Hide })
        assertTrue("the other hidden app stays hidden", maps.key in hiddenKeys)
        assertFalse(exists("hid.row.${whatsapp.key}"))
    }

    @Test fun HOM_05_w38_cancelLeavesEverythingHidden() {
        showPane()
        rule.onNodeWithTag("hid.unhide.${maps.key}").performScrollTo().performClick()
        rule.onNodeWithTag("hid.confirm.cancel").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(unhidden.isEmpty() && maps.key in hiddenKeys && !exists("hid.confirm"))
    }

    @Test fun HOM_05_w38_unhideAllAsksOnceAndClearsEverything() {
        showPane()
        rule.onNodeWithTag("hid.unhideAll").performScrollTo().performClick()
        assertTrue(exists("hid.confirmAll"))
        rule.onNodeWithTag("hid.confirmAll.ok").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(hiddenKeys.isEmpty()); assertTrue(rules.none { it.action == RuleAction.Hide })
        assertTrue(exists("hid.empty"))
    }

    @Test fun HOM_05_w38_theSearchFieldNarrowsTheListByName() {
        showPane()
        rule.onNodeWithTag("hid.search").performTextInput("whats")
        rule.waitForIdle()
        assertTrue(exists("hid.row.${whatsapp.key}")); assertFalse(exists("hid.row.${maps.key}"))
        rule.onNodeWithTag("hid.search").performTextInput("zzz"); rule.waitForIdle()
        assertTrue(exists("hid.nomatch"))
    }

    @Test fun HOM_05_w38_rules_theCommsRulesListShowsEveryHideRuleWithRemoveAndRemovingUndoesIt() {
        showPane()
        val ids = rules.map { it.id }
        ids.forEach { assertTrue("rule $it listed", exists("hid.rule.$it")) }
        rule.onNodeWithTag("hid.remove.${ids[1]}").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(ids[0]), rules.map { it.id }); assertFalse(exists("hid.rule.${ids[1]}"))
        shot("rules")
    }

    // ---- Radar ----
    private fun openRadar(query: String, hidden: List<AppEntry>, onUnhide: (AppEntry) -> Unit = {}, onLaunch: (AppEntry) -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = listOf(maps, mail), recent = listOf(maps, mail), prefs = LauncherPrefs(searchMotion = false, searchLayout = SearchLayout.Flat, searchLayoutPinned = true), contacts = null,
                onLaunch = onLaunch, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null, initialQuery = query,
                learnedOverride = emptyMap(), nowMillis = { 0L }, voiceAvailable = false, hiddenApps = hidden, onUnhideApp = onUnhide,
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun PAL_02_w38_radarKeepsHiddenAppsOutOfTheResultsAndOffersOneQuietRowAtTheEnd() {
        openRadar("what", listOf(whatsapp))
        assertTrue("the quiet row is there", exists("radar.hidden.toggle"))
        assertFalse("the hidden app is not mixed into the results", exists("radar.hidden.${whatsapp.key}"))
        rule.onNodeWithTag("radar.hidden.toggle").assertTextContainsAll("1 hidden app matches: Show")
        shot("radar-collapsed")
        rule.onNodeWithTag("radar.hidden.toggle").performClick(); rule.mainClock.advanceTimeBy(300)
        assertTrue("tapping the row reveals the hidden app inline", exists("radar.hidden.${whatsapp.key}"))
        shot("radar-open")
    }

    @Test fun PAL_02_w38_unhideFromRadarCallsBackAndALaunchStillWorks() {
        var unhid: AppEntry? = null; var launched: AppEntry? = null
        openRadar("what", listOf(whatsapp), { unhid = it }, { launched = it })
        rule.onNodeWithTag("radar.hidden.toggle").performClick(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("radar.unhide.${whatsapp.key}").performClick(); rule.mainClock.advanceTimeBy(100)
        assertEquals(whatsapp, unhid)
    }

    @Test fun PAL_02_w38_noRowWhenNothingHiddenMatchesTheQuery() {
        openRadar("mail", listOf(whatsapp))
        assertFalse(exists("radar.hidden.toggle"))
    }
}
