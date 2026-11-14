package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CFG-20: the application ID is an open decision; these tests pin what is safe while it is open. */
class PackageIdentityTest {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }

    @Test fun CFG_20_defaultApplicationIdIsUnchangedAndOverridableByProperty() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("""?: "com.bos.sphere""""))
        assertTrue(gradle.contains("liftoff.applicationId"))
        assertEquals("the Kotlin namespace stays put whatever the ID", 1, Regex("""namespace = "com\.bos\.sphere"""").findAll(gradle).count())
    }

    @Test fun CFG_20_mainCodeNeverBuildsAComponentFromTheLiteralPackage() {
        val offenders = ArrayList<String>()
        root().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "test" && it.name != "benchmark" }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, l ->
                    if (Regex("""ComponentName\(\s*"com\.bos\.sphere"""").containsMatchIn(l) || Regex("""setClassName\(\s*"com\.bos\.sphere"""").containsMatchIn(l)) offenders += "${f.name}:${i + 1}"
                }
            }
        assertTrue("literal package in a component: $offenders", offenders.isEmpty())
    }

    @Test fun CFG_20_manifestHasNoAuthorityOrCustomPermissionThatDependsOnTheId() {
        val manifests = root().walkTopDown().onEnter { it.name != "build" && it.name != ".git" }.filter { it.name == "AndroidManifest.xml" }.toList()
        assertTrue(manifests.isNotEmpty())
        for (m in manifests) {
            val t = m.readText()
            assertFalse("${m.path} declares an authority", t.contains("android:authorities"))
            assertFalse("${m.path} declares a permission", Regex("""<permission\b""").containsMatchIn(t))
        }
    }

    @Test fun CFG_20_theDecisionRecordExistsAndSaysItIsOpen() {
        val d = File(root(), "docs/decisions/CFG-20.md").readText()
        assertTrue(d.contains("Status: open"))
        assertTrue(d.contains("com.bos.sphere"))
    }
}
