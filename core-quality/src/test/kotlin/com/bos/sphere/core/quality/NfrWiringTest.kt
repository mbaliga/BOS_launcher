package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Wave 2: checks that the wave-1 logic is really called from the live screens, plus the math behind the new wiring. */
class NfrWiringTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun text(path: String) = Repo.file(path).readText()
    private val reduced = MotionPrefs(0f, 0f, 0f)

    // ---- NFR-14
    @Test fun NFR_14_reducedMotionDoesNotMoveGeometryOnlyFades() {
        assertEquals(0.3f, MorphPolicy.geometry(MotionPrefs.Default, 0.3f, true), 0f)
        assertEquals(1f, MorphPolicy.geometry(reduced, 0.1f, true), 0f)
        assertEquals(1f, MorphPolicy.geometry(reduced, 0.5f, false), 0f) // still fading out: geometry stays final
        assertEquals(0f, MorphPolicy.geometry(reduced, 0f, false), 0f)
        assertEquals(1f, MorphPolicy.alpha(MotionPrefs.Default, 0.2f), 0f)
        assertEquals(0.2f, MorphPolicy.alpha(reduced, 0.2f), 0f)
        assertEquals(90, MorphPolicy.progressMs(reduced, 300))
        assertEquals(300, MorphPolicy.progressMs(MotionPrefs.Default, 300))
    }

    @Test fun NFR_14_surfaceMorphsAskTheMotionPreference() {
        val users = listOf(
            "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/WheelDrawer.kt",
            "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SettingsSpace.kt",
            "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/AppDrawerSpace.kt",
            "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/LaunchpadReveal.kt",
            "app/src/main/kotlin/com/bos/sphere/HomeScreen.kt",
        )
        users.forEach { assertTrue("$it must read rememberMotionPrefs()", text(it).contains("rememberMotionPrefs()")) }
        assertTrue(text("core-design/src/main/kotlin/com/bos/sphere/core/design/ReducedMotion.kt").contains("Settings.Global"))
    }

    // ---- NFR-15
    @Test fun NFR_15_planUsesWhicheverScrimNeedsLess() {
        val darkText = HomeLegibility.plan(listOf(0xFF0E1018.toInt()), listOf(Contrast.BLACK))
        assertEquals(Contrast.WHITE, darkText.scrimColor) // dark text over a black wallpaper: a light scrim is the cheap fix
        assertTrue(darkText.reachesAa)
        val lightText = HomeLegibility.plan(listOf(0xFFE7E9F0.toInt()), listOf(Contrast.WHITE))
        assertEquals(Contrast.BLACK, lightText.scrimColor)
        assertTrue(lightText.alpha in 0.3..0.6)
    }

    @Test fun NFR_15_everyBuiltInColourReachesAaWithTheCappedScrim() {
        val inks = listOf(0xFFE7E9F0.toInt(), 0xFFC9CDD6.toInt())
        listOf(0xFFFFFFFF.toInt(), 0xFFF1EBDD.toInt(), 0xFFB9C2CC.toInt(), 0xFF808080.toInt(), 0xFFFFE000.toInt(), 0xFF000000.toInt()).forEach { w ->
            val p = HomeLegibility.plan(inks, listOf(w))
            assertTrue("over $w", p.reachesAa && p.alpha <= HomeLegibility.MAX_ALPHA)
            inks.forEach { t -> assertTrue(Contrast.ratio(t, Contrast.blend(p.scrimColor, w, p.alpha)) >= Contrast.AA_NORMAL - 0.02) }
        }
    }

    @Test fun NFR_15_largeTextNeedsALighterScrim() {
        val w = listOf(0xFF808080.toInt())
        val normal = HomeLegibility.plan(listOf(Contrast.WHITE), w)
        val large = HomeLegibility.plan(listOf(Contrast.WHITE), w, largeText = true)
        assertTrue(large.alpha < normal.alpha)
    }

    @Test fun NFR_15_homeDrawsTheScrimBehindTheTextLayer() {
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        assertTrue(home.contains("HomeScrim("))
        // The scrim sits right after the wallpaper layer and before the indicator layer and the home content.
        assertTrue(home.indexOf("BackgroundLayer(") < home.indexOf("HomeScrim(") && home.indexOf("HomeScrim(") < home.indexOf("IndicatorLayer("))
    }

    // ---- NFR-17
    @Test fun NFR_17_shareBodyKeepsTheReportAndSaysItWasShareByTheUser() {
        val dir = tmp.newFolder("crash")
        val r = CrashReports(dir)
        val f = r.record(IllegalStateException("boom"), "main", 1700000000000L)
        val body = CrashShare.body(r.textForSharing(f))
        assertTrue(body.contains("IllegalStateException: boom"))
        assertTrue(body.contains("never uploaded"))
        assertTrue(body.contains("Liftoff never sends reports by itself"))
        assertEquals("java.lang.IllegalStateException: boom", CrashShare.summary(f))
    }

    @Test fun NFR_17_ledgerLineShowsPercentAndTarget() {
        assertEquals("99.50% of recorded sessions ended without a crash (target 99.8%)", CrashShare.ledgerLine(99.5))
    }

    @Test fun NFR_17_theOnlyWayOutIsTheShareSheetAndGroundControlOffersIt() {
        val pane = text("app/src/main/kotlin/com/bos/sphere/ui/CrashReportsPane.kt")
        assertTrue(pane.contains("ACTION_SEND") && pane.contains("createChooser"))
        assertFalse(pane.contains("HttpURLConnection") || pane.contains("openConnection"))
        // Reachable from Ground Control (Backup category).
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/ui/BackupPane.kt").contains("crashReportsItem()"))
        // Sharing happens only in a click handler of an ActionRow, never from the crash handler or the application class.
        val app = text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt")
        assertFalse(app.contains("ACTION_SEND"))
        assertFalse(text("core-quality/src/main/kotlin/com/bos/sphere/core/quality/CrashReports.kt").contains("Intent"))
    }

    // ---- NFR-12
    @Test fun NFR_12_photoPickerCopiesTheFileOffTheMainThread() {
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        val start = home.indexOf("val photoPicker")
        val block = home.substring(start, home.indexOf("var pickingLeft", start))
        assertTrue(block.contains("Dispatchers.IO"))
        assertTrue("the copy must be inside withContext", block.indexOf("copyTo") > block.indexOf("withContext"))
    }

    @Test fun NFR_12_preferenceFilesHomeNeedsAreWarmedFromTheApplication() {
        val warm = text("app/src/main/kotlin/com/bos/sphere/PrefsWarmup.kt")
        val names = Regex("""getSharedPreferences\("([a-z_]+)"""").findAll(text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt")).map { it.groupValues[1] }.toList()
        assertTrue(names.size >= 5)
        names.forEach { assertTrue("$it not warmed", warm.contains("\"$it\"")) }
        assertTrue(warm.contains("hidden_apps") && warm.contains("crash_loop"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt").contains("PrefsWarmup.start(this)"))
        assertTrue("runs on a thread", warm.contains("Thread {"))
    }

    @Test fun NFR_12_noBlockingCallsInTheHomeComposablesOfFeatureGrid() {
        val files = listOf("GridHome", "ContinuousHome", "XmbHome", "ListHome", "HomeFrame", "StatusStrip", "ClockBlock", "IconTheme", "HomeBadges", "FolderUi", "MediaStrip", "IndicatorLayer")
        val blocking = listOf("runBlocking", "Thread.sleep", ".commit()", "getInstalledPackages", "queryIntentActivities", "contentResolver.query(", "getInstalledApplications", "BitmapFactory.decode", "getPackageInfo", "loadLabel(")
        val hits = files.flatMap { n ->
            val f = Repo.file("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/$n.kt")
            if (!f.exists()) emptyList() else f.readLines().withIndex().filter { (_, l) -> !l.trimStart().startsWith("//") && blocking.any { l.contains(it) } }.map { "$n:${it.index + 1}" }
        }
        assertTrue("blocking calls on the home render path: $hits", hits.isEmpty())
    }

    @Test fun NFR_12_iconsAreRasterisedOffTheMainThread() {
        val t = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/AppIcon.kt")
        assertTrue(t.indexOf("Dispatchers.Default") in 0 until t.indexOf("sources.rasterize("))
        assertTrue(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/IconSources.kt").contains("IconCache.rasterize("))
    }

    // ---- NFR-01 to NFR-04
    @Test fun NFR_03_NFR_04_traceSectionNamesMatchBetweenAppAndBenchmark() {
        val bench = text("benchmark/src/main/kotlin/PaletteBenchmarks.kt")
        assertTrue(bench.contains("\"${TraceSections.PALETTE_FIRST_RESULTS}\""))
        assertTrue(bench.contains("\"${TraceSections.PALETTE_KEYSTROKE}\""))
        assertTrue(text("benchmark/src/main/kotlin/HomeBenchmarks.kt").contains("\"${TraceSections.HOME_INTERACTIVE}\""))
        val trace = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/PaletteTrace.kt")
        listOf("PALETTE_OPEN", "PALETTE_FIRST_RESULTS", "PALETTE_KEYSTROKE").forEach { assertTrue(it, trace.contains("TraceSections.$it")) }
        assertTrue(text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/SearchOverlay.kt").contains("PaletteTraceEffects("))
        assertEquals(TraceSections.all.size, TraceSections.all.toSet().size)
        assertTrue(TraceSections.all.all { it.startsWith("Liftoff.") })
    }

    @Test fun NFR_01_NFR_02_homeReportsFullyDrawnWhenTheAppListIsDrawn() {
        val timing = text("app/src/main/kotlin/com/bos/sphere/HomeTiming.kt")
        assertTrue(timing.contains("reportFullyDrawn()") && timing.contains("TraceSections.HOME_INTERACTIVE"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("HomeTiming.homeDrawn(context)"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt").contains("HomeTiming.processStarted()"))
    }

    @Test fun NFR_01_NFR_02_benchmarkVariantExistsAndIsProfileableOnlyThere() {
        assertTrue(text("app/build.gradle.kts").contains("create(\"benchmark\")"))
        assertTrue(text("app/src/benchmark/AndroidManifest.xml").contains("profileable"))
        assertFalse(text("app/src/main/AndroidManifest.xml").contains("profileable"))
    }

    @Test fun NFR_02_baselineProfileRulesPointAtClassesThatExist() {
        val rules = text("app/src/main/baseline-prof.txt").lines().filter { it.startsWith("HSPL") || it.startsWith("L") }
        assertTrue(rules.size > 10)
        val sources = Repo.mainSources()
        rules.forEach { r ->
            val cls = r.removePrefix("HSPL").removePrefix("HSL").removePrefix("PL").substringBefore(";").substringBefore("**").substringBefore("$")
                .removePrefix("L").removeSuffix("Kt")
            val dir = cls.substringBeforeLast('/', "")
            val name = cls.substringAfterLast('/')
            val ok = sources.any { f ->
                f.path.replace('\\', '/').substringBeforeLast('/').endsWith("/" + dir) &&
                    (f.name.startsWith(name) || Regex("""\b(class|object|interface)\s+$name""").containsMatchIn(f.readText()))
            } || (name.isEmpty() && sources.any { it.path.replace('\\', '/').contains("/$dir/") })
            assertTrue("no source for baseline rule $r", ok)
        }
    }

    @Test fun NFR_01_homeTimingScriptAndBenchmarkSourcesCoverAllBudgets() {
        val perf = text("tools/perf.sh")
        listOf("NFR-01", "NFR-02", "NFR-05").forEach { assertTrue(it, perf.contains(it)) }
        val src = Repo.file("benchmark/src/main/kotlin").listFiles()!!.joinToString("\n") { it.readText() }
        listOf("NFR_01", "NFR_02", "NFR_03", "NFR_04", "NFR_05").forEach { assertTrue(it, src.contains(it)) }
    }

    // ---- NFR-07
    @Test fun NFR_07_everyScheduledJobCarriesConstraintsAndIndexingRunsOnlyFromTheJob() {
        val jobs = Repo.mainSources().filter { it.readText().contains("JobInfo.Builder") }
        assertTrue(jobs.isNotEmpty())
        jobs.forEach { f ->
            val t = f.readText()
            assertTrue("${f.name} sets no constraint", t.contains("setRequiresBatteryNotLow(true)") || t.contains("setRequiresCharging(true)") || t.contains("setRequiresDeviceIdle(true)"))
            assertTrue("${f.name} not periodic or expedited-free", !t.contains("setExpedited") && !t.contains("setImportantWhileForeground"))
        }
        val callers = Repo.mainSources().filter { it.readText().contains("FileIndexer.reconcile(") }.map { it.name }
        // The job, plus the explicit "Index now" / "Rebuild" buttons in the search settings (user-triggered, on Dispatchers.IO).
        assertEquals(setOf("FileIndexAndroid.kt", "PaletteSettingsPanes.kt"), callers.toSet())
        val svc = text("feature-search/src/main/kotlin/com/bos/sphere/feature/search/palette/FileIndexAndroid.kt")
        assertTrue(svc.indexOf("FileIndexer.reconcile(applicationContext)") > svc.indexOf("class FileIndexJobService"))
    }

    @Test fun NFR_07_noRepeatingAlarmsTimersOrForegroundPollingInProduction() {
        val bad = listOf("setRepeating", "setInexactRepeating", "scheduleAtFixedRate", "Timer()", "PowerManager.PARTIAL_WAKE_LOCK", "newWakeLock", "setExactAndAllowWhileIdle")
        val hits = Repo.mainSources().flatMap { f -> f.readLines().filter { l -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && bad.any { l.contains(it) } }.map { "${f.name}: $it" } }
        assertTrue("background-work anti-patterns: $hits", hits.isEmpty())
    }

    // ---- NFR-11 / NFR-12
    @Test fun NFR_12_NFR_11_packageAndProfileCallbacksRunOffTheMainThread() {
        val t = text("core-data/src/main/kotlin/com/bos/sphere/core/data/AppRepository.kt")
        // Every callback that signals a package or profile change (install, update, removal, quiet mode, suspend) re-reads the inventory...
        listOf("onPackageAdded", "onPackageRemoved", "onPackageChanged", "onPackagesAvailable", "onPackagesUnavailable", "onPackagesSuspended", "onPackagesUnsuspended")
            .forEach { assertTrue(it, t.contains("override fun $it(")) }
        // ...and registers on a background handler with the whole flow on a background dispatcher.
        assertTrue(t.contains("registerCallback(callback, android.os.Handler(thread.looper))"))
        assertTrue(t.contains("flowOn("))
        assertFalse("registered without a handler (main thread)", Regex("""(?<![a-z])registerCallback\(callback\)""").containsMatchIn(t))
    }

    @Test fun NFR_11_displayHotPlugIsTrackedByAListenerNotPolled() {
        val src = text("feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/DisplaySource.kt")
        assertTrue(src.contains("registerDisplayListener"))
    }

    @Test fun NFR_11_everyDurableStoreIsSharedPreferencesBackedAndWarmed() {
        // Durable state is in SharedPreferences files that survive process death, reboot and package update; the warm-up
        // list must cover every file LauncherStores opens (see NFR_12_preferenceFilesHomeNeedsAreWarmedFromTheApplication).
        val stores = text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt")
        assertTrue(Regex("""getSharedPreferences\("""").findAll(stores).count() >= 5)
        // Surfaces that hold only transient state (open palette, drawer) are reset on process death by design: stateNotNeeded.
        assertTrue(text("app/src/main/AndroidManifest.xml").contains("android:stateNotNeeded=\"true\""))
    }

    // ---- NFR-06
    @Test fun NFR_06_everyBitmapCacheIsBoundedAndTheByteBudgetsFitTheMemoryBudget() {
        val re = Regex("""LruCache<[^>]*>\(\s*([0-9 *]+)\s*\)""")
        var bytes = 0L
        var found = 0
        Repo.mainSources().forEach { f ->
            re.findAll(f.readText()).forEach { m ->
                found++
                val v = m.groupValues[1].split('*').map { it.trim().toLong() }.reduce { a, b -> a * b }
                if (v >= 1024 * 1024) bytes += v // byte-sized caches; the others count entries (the catalog keeps 256 icon drawables)
            }
        }
        assertTrue("LruCache constructions found: $found", found >= 3)
        assertTrue("byte-bounded caches add up to $bytes", bytes <= 48L * 1024 * 1024)
        // The share of the 180 MB budget the caches may take is a quarter at most.
        assertTrue(bytes <= Budgets.PSS.limit.toLong() * 1024 * 1024 / 4)
    }
}
