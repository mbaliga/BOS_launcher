package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 27 (commsRename): the product word "Launchpad" is "Comms" and "Notification menu" is "Comms menu" wherever the
 * user can see or hear it. Identifiers (package, module, classes, pref keys, test tags, enum names) keep the old name
 * on purpose (docs/decisions/commsRename.md), so the scan looks at string literals only, outside comments.
 */
class CommsRenameAuditTest {
    private val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val oldWord = Regex("""(?<![A-Za-z0-9_/])Launchpad(?![A-Za-z0-9_]|\.kt)""")

    /** Hidden search aliases are lower case and never shown; nothing in the source needs an exception today. */
    private val allowedLiterals: Set<String> = emptySet()

    private fun literalsOf(file: java.io.File): List<Pair<Int, String>> = file.readLines().withIndex().flatMap { (i, line) ->
        val t = line.trimStart()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) emptyList()
        else literal.findAll(line).map { (i + 1) to it.groupValues[1] }.toList()
    }

    @Test fun CMS_01_noUserVisibleStringOrL10nKeyStillSaysLaunchpad() {
        val offenders = Repo.mainSources().flatMap { f ->
            val isL10n = f.name.startsWith("L10n")
            literalsOf(f).filter { (_, s) ->
                s !in allowedLiterals && (
                    oldWord.containsMatchIn(s) || "लॉन्चपैड" in s || "Notification menu" in s ||
                        (isL10n && s.contains("launchpad", ignoreCase = true))
                    )
            }.map { "${f.name}:${it.first}: ${it.second.take(80)}" }
        }
        assertTrue("user-visible strings that still say Launchpad (use Comms): $offenders", offenders.isEmpty())
    }

    @Test fun CMS_02_theCommsMenuAndTheCommsTitleExistInEveryLanguageAsComms() {
        val menu = Repo.file("core-design/src/main/kotlin/com/bos/sphere/core/design/L10nW8launchpadCard.kt").readText()
        assertEquals(9, Regex(""""Comms menu"""").findAll(menu).count()) // the English entry and the eight translations' keys
        assertTrue(!menu.contains("Notification menu"))
    }

    @Test fun CMS_03_theOldNameStillFindsCommsThroughAHiddenSearchAlias() {
        val index = Repo.file("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SettingsIndex.kt").readText()
        assertTrue(index.contains("\"launchpad\" to \"comms\""))
        val actions = Repo.file("search-engine/src/main/kotlin/dev/aarso/search/palette/ShortcutsAndActions.kt").readText()
        assertTrue(actions.contains("\"launchpad\", \"comms\""))
    }
}
