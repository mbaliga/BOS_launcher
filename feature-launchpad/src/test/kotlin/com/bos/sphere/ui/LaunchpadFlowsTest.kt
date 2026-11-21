package com.bos.sphere.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.DeliveryMode
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadAction
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.LaunchpadRulesSnapshot
import com.bos.sphere.core.data.LaunchpadSettings
import com.bos.sphere.core.data.Rule as LpRule
import com.bos.sphere.core.data.RuleAction
import com.bos.sphere.core.design.GrantKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi", sdk = [35])
class LaunchpadFlowsTest {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val t0 = 1_710_000_000_000L

    private fun n(key: String) = HubNotification(id = key, key = key, source = "x", title = "Title $key", summary = "s", timestamp = t0, packageName = "demo.a", appLabel = "App A", sender = "Sam")

    // ---- STR-05 / CFG-07 ----

    @Test fun STR_05_mediaStripShowsTheGrantChipThenTheExplanationThenNothingIsEmpty() {
        rule.setContent { MediaStripGrantPrompt(visible = true) }
        rule.waitForIdle()
        rule.onNodeWithText("Allow notification access").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Show notifications here").assertExists()
        rule.onNodeWithText("Stays on this device. Nothing is sent anywhere.").assertExists()
        rule.onNodeWithText("Not now").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Allow notification access").assertExists()
    }

    @Test fun CFG_07_promptRunsTheSystemRequestOnlyAfterTheExplanationButton() {
        val ran = mutableListOf<String>()
        rule.setContent {
            val p = rememberGrantPrompter()
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("ask", modifier = androidx.compose.ui.Modifier.testTag("ask").clickable { p.ask(GrantKind.Contacts) { ran += "system prompt" } })
                p.Host()
            }
        }
        rule.onNodeWithTag("ask").performClick(); rule.waitForIdle()
        assertTrue("nothing requested before the explanation is accepted", ran.isEmpty())
        rule.onNodeWithText("Find people").assertExists()
        rule.onNodeWithText("Allow contacts").performClick(); rule.waitForIdle()
        assertEquals(listOf("system prompt"), ran)
    }

    @Test fun CFG_07_askedMemoryIsPerGrantAndPersists() {
        val ctx = rule.activity
        assertTrue(!GrantSupport.wasAsked(ctx, GrantKind.Folders))
        GrantSupport.markAsked(ctx, GrantKind.Folders)
        assertTrue(GrantSupport.wasAsked(ctx, GrantKind.Folders))
        assertTrue(!GrantSupport.wasAsked(ctx, GrantKind.Microphone))
        assertTrue(GrantSupport.settingsIntent(ctx, GrantKind.Notifications) != null)
        assertTrue(GrantSupport.settingsIntent(ctx, GrantKind.Folders) == null)
    }

    // ---- STR-03: snooze through the palette ----

    @Test fun STR_03_snoozeFromThePaletteHidesTheRowViaTheCallback() {
        val snoozed = mutableListOf<Pair<String, Long>>()
        rule.setContent {
            LaunchpadContent(
                items = listOf(n("a1")), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {}, onSetDefaultMode = {},
                onRequestAccess = {}, zone = ZoneOffset.UTC, onSnooze = { x, until -> snoozed += x.key to until },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown); pressKey(Key.Menu) }
        rule.waitForIdle()
        rule.onNodeWithText("Snooze").performClick(); rule.waitForIdle()
        rule.onNodeWithText("1 hour", substring = true).performClick(); rule.waitForIdle()
        assertEquals(listOf("a1" to t0 + 3_600_000L), snoozed)
    }

    @Test fun STR_03_makeARuleEntriesOfferMuteHidePinFromThePalette() {
        val rules = mutableListOf<LpRule>()
        rule.setContent {
            LaunchpadContent(
                items = listOf(n("a1")), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = { rules += it }, onSetDefaultMode = {},
                onRequestAccess = {}, zone = ZoneOffset.UTC,
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown); pressKey(Key.Menu) }
        rule.waitForIdle()
        rule.onNodeWithText("Make a rule").performClick(); rule.waitForIdle()
        rule.onNodeWithText("      Hide this app").performClick(); rule.waitForIdle()
        assertEquals(RuleAction.Hide, rules.single().action)
        assertEquals("demo.a", rules.single().packageName)
    }

    @Test fun STR_03_perAppPrimaryActionReplacesDismissOnTheLeftSlide() {
        val dismissed = mutableListOf<String>()
        rule.setContent {
            LaunchpadContent(
                items = listOf(n("a1")), rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.App), readKeys = emptySet(), hasAccess = true, now = t0,
                onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = { dismissed += it.key }, onClearAll = {}, onAddRule = {}, onSetDefaultMode = {},
                onRequestAccess = {}, zone = ZoneOffset.UTC,
                policies = listOf(AppPolicy("demo.a", primaryAction = LaunchpadAction.MarkRead)),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("lp.root").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown); pressKey(Key.Delete) }
        rule.waitForIdle()
        assertTrue("Delete runs the app's own primary action, not dismiss", dismissed.isEmpty())
        // The button on the focused row is labelled with that action.
        assertTrue(rule.onAllNodesWithText("Mark as read").fetchSemanticsNodes().isNotEmpty())
    }

    // ---- STR-04: persistence ----

    @Test fun STR_04_policiesAndRulesPersistAndRestoreThroughTheSnapshot() {
        val ctx = rule.activity
        LaunchpadSettings.init(ctx)
        LaunchpadSettings.setPolicy(AppPolicy("demo.mail", DeliveryMode.Digest, listOf(480, 1080), LaunchpadAction.MuteApp))
        LaunchpadSettings.addRule(LpRule.create(RuleAction.Pin, packageName = "demo.chat"))
        val snap = LaunchpadSettings.snapshot()
        assertEquals(DeliveryMode.Digest, snap.policies.single { it.packageName == "demo.mail" }.mode)
        // Round trip through JSON, as the state store and export do.
        val back = LaunchpadRulesSnapshot.fromJson(snap.toJson())
        assertEquals(snap.policies, back.policies)
        assertEquals(snap.rules.map { it.action to it.packageName }, back.rules.map { it.action to it.packageName })
        // A restore replaces everything and writes it through to the stored preferences.
        LaunchpadSettings.restore(LaunchpadRulesSnapshot(policies = listOf(AppPolicy("demo.only", DeliveryMode.SilentInLaunchpad))))
        assertEquals(listOf("demo.only"), LaunchpadSettings.policies.value.map { it.packageName })
        assertTrue(LaunchpadSettings.rules.value.isEmpty())
        val stored = ctx.getSharedPreferences("launchpad_rules", 0).getString("policies", "")!!
        assertTrue(stored.contains("demo.only") && !stored.contains("demo.mail"))
        // Setting a policy back to its defaults removes it.
        LaunchpadSettings.setPolicy(AppPolicy("demo.only"))
        assertTrue(LaunchpadSettings.policies.value.isEmpty())
    }

    @Test fun STR_04_snoozeComesBackWhenItsTimePasses() {
        LaunchpadSettings.snooze("k1", 1000L)
        assertTrue(LaunchpadSettings.snoozed.value.containsKey("k1"))
        LaunchpadSettings.pruneSnoozed(setOf("k1"), 2000L)
        assertTrue(LaunchpadSettings.snoozed.value.isEmpty())
    }
}

