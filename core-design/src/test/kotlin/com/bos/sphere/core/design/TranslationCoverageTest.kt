package com.bos.sphere.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CFG-19: every string shown through L10n must exist in all 8 UI languages. The test reads the real source tree, so a
 * new `L10n.tr("...")` literal without translations fails CI until the tables are filled.
 */
class TranslationCoverageTest {
    private val codes = listOf("es", "fr", "de", "pt", "hi", "zh", "ja", "ar")

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun sourceFiles(): List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path.replace('\\', '/') }
        .filter { !it.name.startsWith("L10n") }
        .toList()

    private val literal = """"((?:[^"\\$]|\\.)*)""""
    private val callers = listOf(Regex("""\bL10n\.tr\(\s*$literal"""), Regex("""(?<![\w.])t\(\s*$literal\s*[,)]"""))

    private fun unescape(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); '"' -> sb.append('"'); '\\' -> sb.append('\\'); '$' -> sb.append('$')
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    /** Every literal passed straight to L10n.tr / t(...) in a main source file, with where it is. */
    private fun usedLiterals(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (f in sourceFiles()) {
            val text = f.readText()
            val helper = Regex("""fun x\(""").containsMatchIn(text)
            val regs = if (helper) callers + Regex("""(?<![\w.])x\(\s*$literal\s*[,)]""") else callers
            for (r in regs) for (m in r.findAll(text)) out.putIfAbsent(unescape(m.groupValues[1]), f.name)
        }
        return out
    }

    @Test fun CFG_19_sourceTreeIsScannedAndFindsStrings() {
        assertTrue("scanned ${sourceFiles().size} files", sourceFiles().size > 50)
        assertTrue("found ${usedLiterals().size} literals", usedLiterals().size > 100)
    }

    @Test fun CFG_19_everyLiteralUsedInTheSourceExistsInAllEightLanguages() {
        val problems = ArrayList<String>()
        for ((key, file) in usedLiterals()) {
            val missing = codes.filter { L10n.tableFor(it)?.containsKey(key) != true }
            if (missing.isNotEmpty()) problems += "\"$key\" ($file) missing in $missing"
        }
        assertTrue("Add these to a core-design L10n table:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun CFG_19_allEightTablesHaveTheSameKeys() {
        val ref = L10n.tableFor("es")!!.keys
        for (c in codes) assertEquals("$c", ref, L10n.tableFor(c)!!.keys)
        assertEquals(L10n.englishKeys, ref)
    }

    @Test fun CFG_19_noTranslationIsBlank() {
        for (c in codes) for ((k, v) in L10n.tableFor(c)!!) assertTrue("$c: \"$k\" is blank", v.isNotBlank())
    }

    @Test fun CFG_19_placeholdersMatchTheEnglishText() {
        val re = Regex("%[sd]")
        for (c in codes) for ((k, v) in L10n.tableFor(c)!!) {
            assertEquals("$c: $k", re.findAll(k).map { it.value }.sorted().toList(), re.findAll(v).map { it.value }.sorted().toList())
        }
    }
}
