package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 4 (motion, legibility, back, privacy, budgets): source-level enforcement of what code can prove. */
class W4MotionAuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val grid = "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid"

    // ------------------------------------------------------------------ NFR-14
    @Test fun NFR_14_launchpadSlideBecomesACrossFadeUnderReducedMotion() {
        val t = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/Launchpad.kt")
        assertTrue(t.contains("rememberMotionPrefs()"))
        assertTrue("reduced enter is a fade", Regex("""motion\.reduced\) fadeIn\(""").containsMatchIn(t))
        assertTrue("reduced exit is a fade", Regex("""motion\.reduced\) fadeOut\(""").containsMatchIn(t))
        // The slide is only the non-reduced branch.
        val reducedAt = t.indexOf("motion.reduced) fadeIn(")
        assertTrue(t.indexOf("slideInHorizontally { -it }") > reducedAt)
    }

    @Test fun NFR_14_hostDrivenGoToPageHonoursTheMotionPreference() {
        val t = text("$grid/GridHome.kt")
        assertTrue(t.contains("if (animate && !stillPaging) pager.animateScrollToPage(p) else pager.scrollToPage(p)"))
        assertTrue(t.contains("state.stillPaging = com.bos.sphere.core.design.rememberMotionPrefs().reduced"))
    }

    /**
     * Every file that starts a one-shot `animate*AsState` is either gated (reads the motion preference and routes the spec
     * through `gate`/`MorphPolicy`) or is on this audited allow-list, with the exact number of call sites and why those
     * are acceptable: short, finite, springs or tweens of at most 260 ms on a colour, scale or position, none repeating.
     * A new call site, or a changed count, fails here until it is gated or audited.
     */
    @Test fun NFR_14_oneShotAnimationsAreGatedOrOnTheAuditedAllowList() {
        val allowed = mapOf(
            // press feedback and chip colour changes: under 200 ms, tied to a touch, no travel
            "WheelDrawer.kt" to 2, "Complications.kt" to 4, "Tactile.kt" to 2,
            // the item glide to a new slot (edit mode) and the page-dot pill: short position/size springs
            // wave 28: plus the one page indicator's quiet fade (it moved here from GlanceHome): one alpha read in graphicsLayer, a 600 ms tween that is 0 ms under reduced motion
            "GridHome.kt" to 4,
            // the edit card (HOM-12): one progress value read inside graphicsLayer; under reduced motion the card jumps and only the carousel cross-fades (160 ms)
            "EditCardGeometry.kt" to 1,
            // W25: the song title's push-down under the notch: a spring of about 250 ms on one offset, snap() under reduced motion (wave 28: the indicator fade moved to GridHome's PageIndicator)
            "GlanceHome.kt" to 1,
            "CommsStyle.kt" to 4, // wave 30: the Comms row's read/unread cross-fade (three colours and the icon opacity), a 150 ms tween that is 0 ms under reduced motion (rememberRowTone reads the motion prefs)
            "GlanceEdges.kt" to 1, // wave 17: the quiet edge handle fade, tween 0 when reduced (the caller passes rememberMotionPrefs().reduced)
            "PuckSections.kt" to 1, // wave 32: a wedge's pressed fill, a 90 ms tween that is snap() under reduced motion (reads rememberMotionPrefs().reduced)
            // press spring already snaps under reduced motion (`still`)
        )
        val gatedFiles = setOf("ContinuousHome.kt", "SettingsSpace.kt", "HomeScreen.kt", "FilesUi.kt") // W31: FilesUi fades a loaded thumbnail in through rememberMotionPrefs().gate(tween(180))
        val counts = HashMap<String, Int>()
        Repo.mainSources().forEach { f ->
            val n = f.readLines().count { Regex("""\banimate(Float|Color|Dp|Int|Offset|Size|IntOffset|Rect)AsState\(""").containsMatchIn(it) }
            if (n > 0) counts[f.name] = n
        }
        val unexpected = counts.filter { (name, n) -> name !in gatedFiles && (allowed[name] ?: 0) != n }
        assertTrue("unaudited animate*AsState: $unexpected", unexpected.isEmpty())
        gatedFiles.forEach { name ->
            val src = Repo.mainSources().first { it.name == name }.readText()
            assertTrue("$name must gate its animations", src.contains(".gate(") || src.contains("MorphPolicy") || src.contains("still"))
        }
        assertTrue(text("$grid/ContinuousHome.kt").contains("barMotion.gate(tween(250))"))
        assertTrue(text("$grid/SettingsSpace.kt").contains("tabMotion.gate("))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("rememberMotionPrefs().gate("))
    }

    // ------------------------------------------------------------------ NFR-15
    /** Wave 15: the live scenes and the Horizon picture are gone; the two slots are sampled the same way, from the real painters. */
    @Test fun NFR_15_theBackgroundItemsAreSampledForTheScrimOffTheMainThread() {
        val t = text("$grid/HomeLegibility.kt")
        assertTrue(t.contains("BackgroundSamples.colors(choice, intensity, dark)"))
        assertTrue("sampling runs on Dispatchers.Default", t.indexOf("Dispatchers.Default") in 0 until t.indexOf("BackgroundSamples.colors(choice, intensity, dark)"))
        assertTrue(t.contains("CanvasDrawScope()"))
        assertTrue("the real painters are drawn", t.contains("BackgroundPainter("))
        assertTrue("a small bitmap", Regex("""private const val W = \d+""").find(t)!!.value.filter { it.isDigit() }.toInt() <= 64)
    }

    @Test fun NFR_15_reachesAaIsStrengthenedAutomaticallyAndShownInTheLab() {
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/ui/Lab.kt").contains("LegibilityReport.line"))
        assertTrue(text("$grid/HomeLegibility.kt").contains("HomeLegibility.describe("))
    }

    // ------------------------------------------------------------------ NFR-19
    @Test fun NFR_19_launchpadBackHandlerIsOffAsSoonAsTheExitBegins() {
        val t = text("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/Launchpad.kt")
        val handler = t.indexOf("rememberPredictiveBackProgress(visible, onDismiss)")
        assertTrue(handler > 0)
        // The handler is outside the AnimatedVisibility content (which stays composed during the exit).
        assertTrue(handler < t.indexOf("AnimatedVisibility("))
        assertFalse("no bare BackHandler inside the animated content", Regex("""(?m)^\s*BackHandler\(""").containsMatchIn(t))
    }

    @Test fun NFR_19_animatedOverlaySurfacesFollowThePredictiveBackGesture() {
        val surfaces = listOf("$grid/SearchOverlay.kt", "$grid/AppDrawerSpace.kt", "$grid/WheelDrawer.kt", "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/Launchpad.kt")
        surfaces.forEach {
            val t = text(it)
            assertTrue("$it uses the predictive back progress", t.contains("rememberPredictiveBackProgress(visible, onDismiss)"))
            assertTrue("$it applies it", t.contains("predictiveBackLayer(backProgress)"))
        }
        val helper = text("core-design/src/main/kotlin/com/bos/sphere/core/design/PredictiveBack.kt")
        assertTrue(helper.contains("PredictiveBackHandler(enabled = enabled)"))
    }

    /** Every overlay surface has some back handling; a new overlay file must be added here consciously. */
    @Test fun NFR_19_everyOverlaySurfaceHandlesBack() {
        val backTokens = listOf("BackHandler", "rememberPredictiveBackProgress(")
        val overlays = listOf(
            "$grid/SearchOverlay.kt", "$grid/WheelDrawer.kt", "$grid/AppDrawerSpace.kt", "$grid/Corners.kt", "$grid/SettingsSpace.kt",
            "$grid/WidgetHosting.kt", "feature-launchpad/src/main/kotlin/com/bos/sphere/ui/Launchpad.kt", "app/src/main/kotlin/com/bos/sphere/HomeScreen.kt",
        )
        overlays.forEach { p -> assertTrue("$p has no back handling", backTokens.any { text(p).contains(it) }) }
        // Sheets and dialogs from the library (ModalBottomSheet, DropdownMenu, Dialog) dismiss on back by themselves.
        val sheetFiles = Repo.mainSources().filter { f -> f.readText().let { it.contains("Dialog(") && !it.contains("AlertDialog(") && it.contains("DialogProperties(dismissOnBackPress = false") } }
        assertTrue("a Dialog that swallows back: $sheetFiles", sheetFiles.isEmpty())
    }

    @Test fun NFR_19_predictiveBackMathShrinksAtMostTenPercentAndNeverGoesNegative() {
        assertEquals(1f, PredictiveBackMath.scale(0f, false), 0f)
        assertEquals(0.9f, PredictiveBackMath.scale(1f, false), 1e-6f)
        assertEquals(0.9f, PredictiveBackMath.scale(7f, false), 1e-6f)
        assertEquals(1f, PredictiveBackMath.scale(-3f, false), 0f)
        assertEquals(1f, PredictiveBackMath.scale(Float.NaN, false), 0f)
        assertEquals(1f, PredictiveBackMath.scale(1f, true), 0f) // reduced motion: no scale
        assertEquals(0.75f, PredictiveBackMath.alpha(1f), 1e-6f)
        assertTrue(PredictiveBackMath.alpha(0.5f) > PredictiveBackMath.alpha(1f))
    }

    // ------------------------------------------------------------------ NFR-12
    /**
     * The one main-thread preference wait that remains: `LauncherStores.get(this).prefs.prefs.value` in `onCreate`, because
     * the theme and language must be set before the first frame (an async load would flash default colours and English).
     * This pins the bound: a single store read before setContent, after the warm-up started in Application.onCreate, with
     * no runBlocking and no blocking commit anywhere in the activity.
     */
    @Test fun NFR_12_theOnlyMainThreadStoreWaitBeforeTheFirstFrameIsPinned() {
        val a = text("app/src/main/kotlin/com/bos/sphere/MainActivity.kt")
        val beforeContent = a.substring(0, a.indexOf("setContent {"))
        assertEquals("exactly one first-store read before the first frame", 1, Regex("""LauncherStores\.get\(this\)""").findAll(beforeContent).count())
        assertTrue(beforeContent.contains("// Apply theme and language before the first frame"))
        assertFalse(a.contains("runBlocking"))
        assertFalse(a.contains(".commit()"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt").contains("PrefsWarmup.start(this)"))
    }

    // ------------------------------------------------------------------ NFR-13
    /**
     * Every touch target in feature-grid and app/ui either carries a name or role within its own composable, or is on the
     * audited list of pure gesture surfaces (scroll drags, scrim taps that Back also handles). The scan looks for a
     * clickable/tap/drag call and requires a semantics marker or visible Text within a few lines.
     */
    @Test fun NFR_13_everyInteractiveSiteInGridAndUiHasSemanticsOrIsAudited() {
        val files = Repo.root.resolve("feature-grid/src/main").walkTopDown().filter { it.extension == "kt" }.toList() +
            Repo.root.resolve("app/src/main/kotlin/com/bos/sphere/ui").walkTopDown().filter { it.extension == "kt" }.toList() +
            listOf(Repo.file("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt"))
        val site = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\(|detectTapGestures|detectDragGestures|detectHorizontalDragGestures|detectVerticalDragGestures""")
        val marker = Regex("""semantics|contentDescription|Role\.|clearAndSetSemantics|onClickLabel|Text\(|text =|stateDescription|L10n\.tr""")
        val flagged = HashMap<String, Int>()
        for (f in files) {
            val lines = f.readLines()
            lines.forEachIndexed { i, l ->
                val s = l.trimStart()
                if (s.startsWith("import") || s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")) return@forEachIndexed
                if (site.containsMatchIn(l) && !marker.containsMatchIn(lines.subList(maxOf(0, i - 8), minOf(lines.size, i + 16)).joinToString("\n"))) flagged.merge(f.name, 1, Int::plus)
            }
        }
        // Audited: scrolling drags (the lists have their own scroll semantics), scrim taps closed by Back, the orbit tile click
        // (tiles are named by OrbitA11yTest), Radar's wheel blank-space tap (wave 25: the labels carry the names; the tap only puts the cursor in the field), the empty-home long press (Settings is also in the app menu), edge-swipe handles.
        val audited = mapOf(
            "ContinuousHome.kt" to 1, "XmbHome.kt" to 2, "ListHome.kt" to 1, "WidgetStackView.kt" to 1, "SearchChrome.kt" to 2, "SettingsSpace.kt" to 2,
            "SearchLayouts.kt" to 2, "Corners.kt" to 1, "WidgetHosting.kt" to 1, "HomeScreen.kt" to 4,
            // the colour square and hue bar have semantics on the Canvas; their drag handlers sit further than the scan window
            "ColorPicker.kt" to 3, "Tactile.kt" to 1,
        )
        val over = flagged.filter { (k, v) -> v > (audited[k] ?: 0) }
        assertTrue("unlabelled interactive sites (not audited): $over", over.isEmpty())
    }

    @Test fun NFR_13_theControlsFoundByTheAuditNowCarryNamesOrValues() {
        assertTrue(text("$grid/FolderUi.kt").contains("contentDescription = folder.name; role = Role.Button"))
        assertTrue(text("$grid/ColorPicker.kt").contains("""contentDescription = "#%06X".format("""))
        assertTrue(text("$grid/ColorPicker.kt").contains("""L10n.tr("Colour hue")"""))
        assertTrue(text("$grid/ColorPicker.kt").contains("""L10n.tr("Saturation and brightness")"""))
        assertTrue(text("$grid/Tactile.kt").contains("progressBarRangeInfo = ProgressBarRangeInfo(fraction"))
        assertTrue(text("$grid/WidgetEditUi.kt").contains("""L10n.tr("Resize handle")"""))
        assertTrue(text("$grid/WidgetEditUi.kt").contains("customActions"))
    }

    // ------------------------------------------------------------------ NFR-17
    private val networkTokens = listOf(
        "HttpURLConnection", "HttpsURLConnection", "okhttp", "OkHttp", "openConnection", "openStream", "java.net.Socket", "DatagramSocket",
        "java.net.http", "HttpClient", "URLConnection", "WebView", "DownloadManager", "retrofit", "io.ktor", "cronet", "com.android.volley",
    )

    @Test fun NFR_17_noNetworkClassOutsideTheWeatherModule() {
        val hits = Repo.mainSources().filter { it.name != "Weather.kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && networkTokens.any { l.contains(it) } }
                .map { "${f.name}:${it.index + 1}" }
        }
        assertTrue("network classes outside Weather.kt: $hits", hits.isEmpty())
        // The weather client itself uses one of them (the audit would be vacuous otherwise).
        assertTrue(networkTokens.any { text("core-data/src/main/kotlin/com/bos/sphere/core/data/Weather.kt").contains(it) })
    }

    @Test fun NFR_17_noNetworkLibraryInAnyBuildFile() {
        val builds = Repo.root.walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml" }.filter { !it.path.contains("/benchmark/") }
            .joinToString("\n") { it.readText() }.lowercase()
        listOf("okhttp", "retrofit", "ktor", "volley", "cronet", "coil-network", "glide", "apache.http").forEach { assertFalse(it, builds.contains(it)) }
    }

    @Test fun NFR_17_weatherIsTheOnlyInternetUser() {
        val manifests = Repo.root.walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.name == "AndroidManifest.xml" }.filter { !it.path.contains("/build/") }.toList()
        val internet = manifests.filter { it.readText().contains("android.permission.INTERNET") }.map { it.relativeTo(Repo.root).path }
        assertEquals(listOf("app/src/main/AndroidManifest.xml"), internet)
        // Everything that could reach the network is the weather file; the permission rationale names it.
        val w = text("core-data/src/main/kotlin/com/bos/sphere/core/data/Weather.kt")
        assertTrue(w.contains("openConnection") || w.contains("HttpURLConnection"))
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertFalse("no cleartext opt-in", manifest.contains("usesCleartextTraffic=\"true\""))
        assertNotNull(Repo.file("docs/decisions/NFR-16.md").takeIf { it.exists() })
    }

    // ------------------------------------------------------------------ NFR-03 / NFR-04
    @Test fun NFR_03_NFR_04_theJvmBenchmarkUsesTheSpecBudgetsAndKeepsTheTraceSections() {
        val t = text("search-engine/src/test/kotlin/dev/aarso/search/palette/PaletteBudgetTest.kt")
        assertTrue(t.contains("BUDGET_OPEN_MS = ${Budgets.PALETTE_OPEN.limit}"))
        assertTrue(t.contains("BUDGET_KEYSTROKE_MS = ${Budgets.PALETTE_KEYSTROKE.limit}"))
        assertTrue(t.contains("until 5000") && t.contains("until 500"))
        assertEquals("Liftoff.paletteFirstResults", TraceSections.PALETTE_FIRST_RESULTS)
        assertEquals("Liftoff.paletteKeystroke", TraceSections.PALETTE_KEYSTROKE)
        val trace = text("$grid/PaletteTrace.kt")
        assertTrue(trace.contains("TraceSections.PALETTE_FIRST_RESULTS") && trace.contains("TraceSections.PALETTE_KEYSTROKE"))
    }
}
