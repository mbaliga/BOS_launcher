package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 13 cross-cutting audit of waves 8 to 12 (groups, recents, landing, edit card, roomy list): privacy of personal content,
 * classification of every stored file, touch targets of the new controls.
 */
class W13CrossAuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private fun rel(f: java.io.File) = f.relativeTo(Repo.root).path.replace('\\', '/')

    @Test fun NFR_17_nothingIsLoggedOrPrintedExceptByTheOptInPerformanceMonitor() {
        val sinks = Regex("""\bLog\.[dviwef]\(|\bprintln\(|printStackTrace\(|\bTimber\.""")
        val offenders = Repo.mainSources().filter { sinks.containsMatchIn(it.readText()) }.map(::rel).filter { !it.endsWith("PerformanceMonitor.kt") }
            // Wave 18: the vendored shared crash-recovery utility writes its own capture to logcat (device-local, never leaves the phone
            // unless the user attaches a bug report); these two files are the allow-listed exception (docs/decisions/crashRecovery.md).
            .filter { it != "crash-recovery/src/main/java/dev/aarso/crashrecovery/CrashReport.kt" && it != "crash-recovery/src/main/java/dev/aarso/crashrecovery/CrashRecovery.kt" }
        assertEquals("these files log or print: $offenders", emptyList<String>(), offenders)
        // The monitor's own messages carry names of timed operations, never notification, sender or contact text.
        val monitor = text("core-data/src/main/kotlin/com/bos/sphere/core/data/PerformanceMonitor.kt")
        assertFalse(monitor.contains("sender") || monitor.contains("lastName") || monitor.contains("contact"))
    }

    @Test fun NFR_17_noExceptionMessageInThePersonalCodeCarriesUserText() {
        // A crash report holds the stack trace and the exception message; a message built from a name, a sender or a title would leak it.
        val personal = listOf(
            "GroupsStore", "GroupResolver", "LaunchpadGroups", "GroupedView", "PersonNames", "ContactsDirectoryLoader", "HomeRecents",
            "PendingIntentLauncher", "HomeLanding",
        ).map { "core-data/src/main/kotlin/com/bos/sphere/core/data/$it.kt" } + listOf(
            "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/GroupAssignSheet.kt", "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/GroupRuleEditor.kt",
            "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/LaunchpadGroupsPane.kt", "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/GroupTexts.kt",
        )
        val raising = Regex("""(?:error|require|check|requireNotNull|checkNotNull)\([^\n]*\$|Exception\([^\n]*\$|throw [^\n]*\$""")
        for (f in personal) assertFalse("$f builds an exception message from values", raising.containsMatchIn(text(f)))
    }

    @Test fun NFR_18_everyPreferenceFileTheSourcesOpenIsClassified() {
        // The user's own setup (exported through the file as a section or settings; theming is appearance only), or device-local and excluded from backup and transfer, or audited as holding no personal content.
        val exported = setOf("launcher_prefs", "home_layout", "hidden_apps", "shortcuts", "launchpad_rules", "home_state", "home_tags", "home_icons", "home_badges",
            "action_apps", "radar_ui", "orbit_feel", "puck_actions", "home_layout_by_class", "home_extras", "theming")
        val excluded = setOf("launch_history", "search_habits", "steps", "weather", "crash_loop", "icon_colors", "palette", "liftoff_spikes", "desktop_surface",
            "liftoff_misc", "device_bound", "launchpad_groups")
        val benign = setOf("grant_history") // when each permission was asked for, nothing about contacts or messages
        val opened = Regex("""getSharedPreferences\(\s*"([A-Za-z0-9_]+)"|\bFILE = "([A-Za-z0-9_]+)"""")
        val found = Repo.mainSources().flatMap { f -> opened.findAll(f.readText()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList() }.toSet()
        val unknown = found - exported - excluded - benign
        assertEquals("a preference file with no classification: $unknown", emptySet<String>(), unknown)
        for (f in excluded) for (rules in listOf("app/src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/backup_rules.xml"))
            assertTrue("$rules must exclude $f", text(rules).contains("""domain="sharedpref" path="$f.xml""""))
    }

    @Test fun NFR_18_theMinimalAndRecentsAndGroupsFilesAreInTheTransferExclusionBlockToo() {
        val body = text("app/src/main/res/xml/data_extraction_rules.xml")
        val transfer = body.substring(body.indexOf("<device-transfer>"))
        for (p in listOf("""domain="sharedpref" path="launchpad_groups.xml"""", """domain="file" path="usage"""", """domain="file" path="crash-reports"""", """domain="file" path="notifications""""))
            assertTrue("device-transfer must exclude $p", transfer.contains(p))
    }

    @Test fun NFR_13_theNewControlsHaveA48dpTouchTargetAndSayWhatTheyAre() {
        val roomy = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/LaunchpadRoomy.kt")
        assertFalse("the +N more line and the section chips must not be a 40 dp target", Regex("""heightIn\(min = 40\.dp\)[^\n]*clickable""").containsMatchIn(roomy))
        assertTrue(roomy.contains("clickable(role = androidx.compose.ui.semantics.Role.Button"))
        assertTrue(roomy.contains("Modifier.heightIn(min = 48.dp)"))
        val carousel = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/PageCarousel.kt")
        assertTrue("carousel buttons keep a 48 dp target around the small pill", carousel.contains("defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)"))
        val rows = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/LaunchpadRows.kt")
        assertFalse("Launchpad action pills and sheet lines are announced as buttons", Regex("""\.clickable\(onClick = onClick\)""").containsMatchIn(rows))
        val kit = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/GroupUiKit.kt")
        assertEquals("both group controls are at least 48 dp high", 2, Regex("""heightIn\(min = 48\.dp\)""").findAll(kit).count())
    }

    @Test fun NFR_02_everyPreferenceFileTheFirstHomeFrameReadsIsWarmedOffTheMainThread() {
        val warm = text("app/src/main/kotlin/com/bos/sphere/PrefsWarmup.kt")
        // The waiting indicator is composed with the first frame and initialises both Launchpad stores on the main thread.
        val indicator = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/WaitingIndicator.kt")
        assertTrue(indicator.contains("LaunchpadSettings.init(ctx)") && indicator.contains("GroupsSettings.init(ctx)"))
        for (f in listOf("launchpad_rules", "launchpad_groups")) assertTrue("$f must be in PrefsWarmup.FILES", warm.contains("\"$f\""))
    }

    @Test fun CFG_10_theLandingSwitcherUsesTheThemeTokensNotAFixedWhiteOverlay() {
        // A 7 percent white overlay is invisible on the light theme; the unselected card must follow the ink tokens.
        for (f in listOf("HomeLandingUi", "HomeLandingScreens", "PageCarousel", "AddCarousel", "EditCardHost"))
            assertFalse("$f has a fixed white overlay", text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/$f.kt").contains("Color(0x12FFFFFF)"))
    }
}
