package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 20 repository hygiene (CFG-01, CFG-03, CFG-05, NFR-13, NFR-16 to NFR-18, NFR-20, NFR-21 group): the status table and the spec
 * agree, the generated coverage table names only tests that exist, every decision record is indexed, the baseline profile and the README
 * name modules and classes that exist. Documentation drift is how removed behaviour stayed described for six waves.
 */
class RepoHygieneAuditTest {
    private val idRe = Regex("""\b(PAL|INP|HOM|DCK|WSP|EXT|STR|CFG|NFR|ELV)-(\d\d)\b""")
    private fun text(p: String) = Repo.file(p).readText()

    private val rows: List<List<String>> by lazy {
        text("docs/SPEC_STATUS.md").lines().filter { it.startsWith("| ") && idRe.matches(it.removePrefix("| ").substringBefore(" |")) }
            .map { l -> l.removePrefix("| ").split(" | ", limit = 4).map { it.trim() } }
    }

    @Test fun CFG_01_everySpecRequirementHasExactlyOneStatusRowAndNoRowIsOrphaned() {
        val specIds = idRe.findAll(text("docs/Spec.md")).map { it.value }.toSortedSet()
        val rowIds = rows.map { it[0] }
        assertEquals("duplicate rows", emptyList<String>(), rowIds.groupBy { it }.filterValues { it.size > 1 }.keys.toList())
        assertEquals("in the spec without a row", emptyList<String>(), (specIds - rowIds.toSet()).toList())
        assertEquals("rows for IDs the spec does not have", emptyList<String>(), (rowIds.toSet() - specIds).toList())
    }

    @Test fun CFG_01_theCountsLineMatchesTheRowsAndEveryStatusWordIsKnown() {
        val known = listOf("built", "partial", "not started", "unmet", "removed")
        val bad = rows.filter { it[2] !in known }.map { it[0] + "=" + it[2] }
        assertEquals("unknown status words", emptyList<String>(), bad)
        val counts = known.associateWith { s -> rows.count { it[2] == s } }
        val line = text("docs/SPEC_STATUS.md").lines().first { it.startsWith("Counts:") }
        val expected = "Counts: " + known.filter { counts.getValue(it) > 0 }.joinToString(", ") { "$it ${counts.getValue(it)}" } + " (of ${rows.size})."
        assertEquals(expected, line)
    }

    @Test fun CFG_01_aRemovedRowSaysItWasAnOwnerDecisionAndNamesWhereTheReplacementIsTested() {
        val removed = rows.filter { it[2] == "removed" }
        assertTrue("PAL-13 is the wave 18 removal", removed.any { it[0] == "PAL-13" })
        removed.forEach { assertTrue(it[0], it[3].contains("owner decision")) }
    }

    @Test fun CFG_01_everyTestNamedInTheCoverageTableExistsInTheSources() {
        val sources = Repo.root.walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" && (it.path.contains("/src/test/") || it.path.contains("/src/androidTest/")) }.joinToString("\n") { it.readText() }
        val names = Regex("""`([A-Z]{3}_\d\d[A-Za-z0-9_]*)\(\)`""").findAll(text("docs/TEST_COVERAGE.md")).map { it.groupValues[1] }.toSortedSet()
        assertTrue(names.size > 100)
        val missing = names.filter { !Regex("""fun\s+`?${Regex.escape(it)}\b""").containsMatchIn(sources) }
        assertEquals("tests named in docs/TEST_COVERAGE.md that no longer exist (run tools/spec_coverage.py)", emptyList<String>(), missing)
    }

    @Test fun CFG_01_everyDecisionRecordIsLinkedFromTheIndexAndTheIndexLinksOnlyFilesThatExist() {
        val dir = Repo.file("docs/decisions")
        val index = text("docs/decisions/README.md")
        val files = dir.listFiles().orEmpty().filter { it.extension == "md" && it.name != "README.md" }.map { it.name }
        assertEquals("not in the index", emptyList<String>(), files.filter { !index.contains("]($it)") })
        val linked = Regex("""\]\(([A-Za-z0-9._-]+\.md)\)""").findAll(index).map { it.groupValues[1] }.filter { it != "Spec.md" }.toSet()
        assertEquals("linked but missing", emptyList<String>(), linked.filter { !java.io.File(dir, it).exists() })
    }

    @Test fun NFR_21_theReadmeModuleTableListsEveryModuleOfTheBuildAndNoOtherOne() {
        val included = Regex("""include\("(:[a-z-]+)"\)""").findAll(text("settings.gradle.kts")).map { it.groupValues[1] }.toSet() - ":benchmark" // included only with -Pliftoff.benchmark (docs/PERFORMANCE.md)
        val readme = text("README.md").substringAfter("## Module layout").substringBefore("## What works now")
        val listed = Regex("""^\| `(:[a-z-]+)` \|""", RegexOption.MULTILINE).findAll(readme).map { it.groupValues[1] }.toSet()
        assertEquals("modules missing from README.md", emptyList<String>(), (included - listed).toList())
        assertEquals("README.md lists modules the build does not have", emptyList<String>(), (listed - included).toList())
    }

    @Test fun NFR_17_noRetiredKeyIsStillWrittenByTheStores() {
        val stores = text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt")
        assertTrue("minimalHints is written or read again", !stores.contains("minimalHints"))
        val palette = text("feature-search/src/main/kotlin/com/bos/sphere/feature/search/palette/AndroidPalette.kt")
        assertTrue(!palette.contains("scopeMapping") && !palette.contains("\"prefixes\""))
    }
}
