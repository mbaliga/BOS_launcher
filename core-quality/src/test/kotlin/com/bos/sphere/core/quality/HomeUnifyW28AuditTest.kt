package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 28 (HOM-22, homeUnify second review): the Spec row, the retired Scroll / Paginated flow and the 8-language tables agree with the one-pager design. */
class HomeUnifyW28AuditTest {
    private val row = Repo.file("docs/Spec.md").readLines().first { it.startsWith("| HOM-22 |") }

    @Test fun HOM_22_w28_theSpecRowDescribesTheOnePagerAndTheThreeValuePagesSettingNotTheWave26Deck() {
        assertTrue(row.contains("Off / Paginated / Continuous"))
        assertTrue(row.contains("ONE horizontal pager")); assertTrue(row.contains("One page indicator"))
        assertFalse("the add-page question is gone", row.contains("asks once") || row.contains("add-page flow"))
        assertFalse("Add page no longer turns pages on", row.contains("Add page** is reachable from Glance"))
    }

    @Test fun HOM_22_w28_pagesModeChosenAndTheModeDialogLiveOnlyInTheMigrationCode() {
        val allowed = setOf("GlanceModel.kt", "LauncherStores.kt", "StateCodec.kt")
        for (f in Repo.mainSources()) {
            val s = f.readText()
            if (s.contains("pagesModeChosen") || s.contains("homeScroll")) assertTrue("${f.name} reads a retired key", f.name in allowed)
            assertFalse("${f.name} PagesModeDialog", s.contains("PagesModeDialog"))
            assertFalse("${f.name} HomeScroll type", Regex("\\bHomeScroll\\b").containsMatchIn(s))
            assertFalse("${f.name} asks Scroll or Paginated", s.contains("addpage.scroll") || s.contains("Choose once how the pages"))
        }
    }

    @Test fun HOM_22_w28_everyStringOfTheWave28TableIsUsedByTheCodeAndTranslatedInEightLanguages() {
        val table = Repo.file("core-design/src/main/kotlin/com/bos/sphere/core/design/L10nW28homeUnify.kt").readText()
        val english = Regex("^    \"([^\"]+)\",$", RegexOption.MULTILINE).findAll(table).map { it.groupValues[1] }.toList()
        val code = Repo.mainSources().filter { it.name != "L10nW28homeUnify.kt" }.joinToString("\n") { it.readText() }
        for (e in english) {
            assertTrue("\"$e\" is in the table but no code uses it", code.contains("\"$e\"") || code.contains(e))
            assertTrue("\"$e\" has 8 translations", Regex("\"" + Regex.escape(e) + "\" to \"").findAll(table).count() == 8)
        }
    }

    @Test fun HOM_22_w28_theRowsTestWritesNoSnapshotStateOutsideAComposeHost() {
        val s = Repo.file("app/src/test/kotlin/com/bos/sphere/HomeUnifyRowsTest.kt").readText()
        assertFalse("a mutableStateOf field written from a plain test is a snapshot write outside composition", s.contains("mutableStateOf"))
    }
}
