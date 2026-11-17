package com.bos.sphere.core.design

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CFG-19 (wave 13 audit): the coverage test only sees a literal that comes straight after `L10n.tr(`. A string chosen by an
 * if or when expression inside the call (`L10n.tr(if (on) "A" else "B")`, `x(when (s) { ... -> "C" })`) escaped it, and so
 * showed in English in every other language. This scan reads the whole first argument of each call, balanced, and checks
 * every plain string literal in it against all eight tables.
 */
class W13TranslationDeepScanTest {
    private val codes = listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")
    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** Names that are the same in every language. */
    private val brandNames = setOf("DSi", "PSP", "XMB")

    private fun sources() = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path.replace('\\', '/') && !it.name.startsWith("L10n") }
        .toList()

    private val call = Regex("""(?<![\w])(?:L10n\.tr|x|t)\(""")

    /** The text of the first argument of the call whose opening parenthesis ends at [from]. */
    private fun firstArgument(s: String, from: Int): String {
        var depth = 1
        var j = from
        while (j < s.length && depth > 0) {
            when (s[j]) {
                '"' -> { j++; while (j < s.length && s[j] != '"') { if (s[j] == '\\') j++; j++ } }
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 1) return s.substring(from, j)
            }
            j++
        }
        return s.substring(from, (j - 1).coerceAtLeast(from))
    }

    private val literal = Regex(""""((?:[^"\\$]|\\.)*)"""")

    private fun unescape(v: String) = v.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")

    @Test fun CFG_19_everyStringChosenInsideATranslatorCallExistsInAllEightLanguages() {
        val problems = ArrayList<String>()
        var seen = 0
        for (f in sources()) {
            val s = f.readText()
            for (m in call.findAll(s)) {
                val arg = firstArgument(s, m.range.last + 1)
                for (l in literal.findAll(arg)) {
                    val key = unescape(l.groupValues[1])
                    if (key.isBlank() || key in brandNames || !Regex("[A-Za-z]{2}").containsMatchIn(key)) continue
                    seen++
                    val missing = codes.filter { L10n.tableFor(it)?.containsKey(key) != true }
                    if (missing.isNotEmpty()) problems += "\"$key\" (${f.name}) missing in $missing"
                }
            }
        }
        assertTrue("scanned $seen literals", seen > 200)
        assertTrue("Add these to a core-design L10n table:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test fun CFG_19_enumLabelsOfTheNewScreensAreTranslatedWhereTheyAreShown() {
        for (l in listOf("Selected apps", "Recent apps", "All apps", "Minimal", "Apps", "Widgets", "Folders", "Shortcuts", "Move to group...", "Make a rule from this", "Cycle home landing")) {
            val missing = codes.filter { L10n.tableFor(it)?.containsKey(l) != true }
            assertTrue("\"$l\" missing in $missing", missing.isEmpty())
        }
    }
}
