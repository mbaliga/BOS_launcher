package com.bos.sphere.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.BuildConfig
import com.bos.sphere.ColdStart
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.quality.CheckEntry
import com.bos.sphere.core.quality.CheckStatus
import com.bos.sphere.core.quality.DeviceCheck
import com.bos.sphere.core.quality.DeviceReport
import com.bos.sphere.core.quality.ProbeResult
import com.bos.sphere.core.quality.ProbeSurface
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.TextButtonPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Wave 23 (NFR-05, NFR-06, NFR-12 to NFR-14, INP-12, CFG-17, SPK-01; docs/decisions/deviceKit.md): the Lab's "Device checks" page. A frame-timing
 * probe per surface, the cold-start readout, a checklist of the device-only checks with Pass / Fail / Skip and a note, and "Copy report".
 * Everything stays on the phone: the report goes to the clipboard and nowhere else. It is evidence for the owner to paste back; it closes no spec row.
 */
fun deviceChecksItem(): SettingItem = SettingItem.Pane(
    L10n.tr("Device checks"), Glyphs.Bolt, L10n.tr("Frame timing, cold start and a checklist, with a report to copy"),
) { DeviceChecksPane() }

internal fun surfaceLabel(s: ProbeSurface): String = when (s) {
    ProbeSurface.Glance -> L10n.tr("Glance")
    ProbeSurface.DrawerScroll -> L10n.tr("Drawer scroll")
    ProbeSurface.RadarTyping -> L10n.tr("Radar: open and type")
    ProbeSurface.LaunchpadOpenClose -> L10n.tr("Launchpad: open and close")
    ProbeSurface.PageSwipe -> L10n.tr("Page swipe")
}

internal fun checkTitle(c: DeviceCheck): String = when (c) {
    DeviceCheck.TalkBack -> L10n.tr("TalkBack order")
    DeviceCheck.GestureNav -> L10n.tr("Gesture navigation: edge swipe against the back gesture")
    DeviceCheck.NoAnimations -> L10n.tr("Animations removed")
    DeviceCheck.Font200 -> L10n.tr("Font size 200 percent")
    DeviceCheck.DisplaySize -> L10n.tr("Display size large")
    DeviceCheck.GlassLook -> L10n.tr("Radar glass in daylight, dark and light")
    DeviceCheck.RealCrash -> L10n.tr("Real crash path")
    DeviceCheck.Speech -> L10n.tr("Speech engine")
    DeviceCheck.WorkBadge -> L10n.tr("Work profile badge")
    DeviceCheck.NotificationTap -> L10n.tr("Notification tap opens the app")
    DeviceCheck.ExternalDisplay -> L10n.tr("External display")
}

internal fun checkInstruction(c: DeviceCheck): String = when (c) {
    DeviceCheck.TalkBack -> L10n.tr("Turn TalkBack on, then swipe through home, the drawer and Radar. The order should follow what you see and every control should have a name.")
    DeviceCheck.GestureNav -> L10n.tr("With gesture navigation on, swipe in from the left and right edges of home. Note which edge opens a Liftoff panel and which one goes Back.")
    DeviceCheck.NoAnimations -> L10n.tr("Turn on Remove animations in Accessibility, then open Radar, open Launchpad and turn pages. Nothing should slide or loop.")
    DeviceCheck.Font200 -> L10n.tr("Set the font to its largest size (Developer options font scale for 200 percent). Look at home, Radar and Settings for cut-off text.")
    DeviceCheck.DisplaySize -> L10n.tr("Set Display size to Large. Look at the home grid, the dock and Radar for overlap or controls that no longer fit.")
    DeviceCheck.GlassLook -> L10n.tr("Open Radar in daylight at full brightness, in the dark theme and in the light theme. Can you read the field, the buttons and the results?")
    DeviceCheck.RealCrash -> L10n.tr("Use the Lab row Crash on purpose (tap twice). On the next start the recovery screen should appear; tap Continue and check Liftoff opens normally.")
    DeviceCheck.Speech -> L10n.tr("Open Radar, tap the microphone and speak a query. Note whether a speech engine answered and whether it works offline.")
    DeviceCheck.WorkBadge -> L10n.tr("With a work profile on this phone, check that its apps carry the work badge in the drawer and in Radar results.")
    DeviceCheck.NotificationTap -> L10n.tr("Tap a notification that Liftoff shows (the hub or a preview). The app that posted it should open.")
    DeviceCheck.ExternalDisplay -> L10n.tr("Connect an external display. Note whether Liftoff became its home or a desktop surface was offered (see the Lab row SPK-01 Displays).")
}

private fun statusLabel(s: CheckStatus): String = when (s) {
    CheckStatus.Unset -> L10n.tr("Not checked")
    CheckStatus.Pass -> L10n.tr("Pass")
    CheckStatus.Fail -> L10n.tr("Fail")
    CheckStatus.Skip -> L10n.tr("Skip")
}

private fun probeText(r: ProbeResult): String = L10n.tr(
    "%d frames · p50 %s · p95 %s · p99 %s · janky %s%% over %s · slowest %s",
    r.frames, ProbeResult.ms(r.p50Ns), ProbeResult.ms(r.p95Ns), ProbeResult.ms(r.p99Ns),
    "%.1f".format(java.util.Locale.ROOT, r.jankyPercent), ProbeResult.ms(r.intervalNs), ProbeResult.ms(r.slowestNs),
)

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
fun ColumnScope.DeviceChecksPane() {
    val context = LocalContext.current
    var store by remember { mutableStateOf<DeviceChecksStore?>(null) }
    val probes = remember { mutableStateMapOf<ProbeSurface, ProbeResult?>() }
    val checks = remember { mutableStateMapOf<DeviceCheck, CheckEntry>() }
    var lastColdMs by remember { mutableStateOf<Long?>(null) }
    var copied by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { DeviceChecksStore.open(context) }
        withContext(Dispatchers.IO) {
            ProbeSurface.entries.forEach { probes[it] = s.probe(it) }
            DeviceCheck.entries.forEach { checks[it] = s.check(it) }
            lastColdMs = s.lastColdStartMs()
        }
        store = s
    }
    val phase = DeviceProbe.phase
    LaunchedEffect(phase) { while (phase != DeviceProbe.Phase.Idle) { now = System.currentTimeMillis(); delay(500) } }
    val coldMs = ColdStart.thisProcessMs

    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            L10n.tr("These checks need a real phone. Nothing is sent anywhere: Copy report puts plain text on the clipboard for you to paste back. A result here is evidence, not a verdict."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
        )

        // Cold start (NFR-12)
        Text(L10n.tr("Cold start"), color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(
            when {
                coldMs != null -> L10n.tr("This start: %s", "$coldMs ms")
                lastColdMs != null -> L10n.tr("Not captured in this process. Last saved: %s", "${lastColdMs} ms")
                else -> L10n.tr("Not captured yet")
            },
            color = HyleColors.Ink, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            L10n.tr("Swipe Liftoff away from recents, open it, then open this page. The time runs from process start to the first drawn home frame; if something else started the process earlier, it includes that wait."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 4.dp),
        )

        // Frame timing (NFR-05)
        Text(L10n.tr("Frame timing"), color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(
            L10n.tr("Tap a surface, leave Settings within 3 seconds, then use that surface for 10 seconds. Come back here to read the result. A run stops if Liftoff leaves the screen."),
            color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        if (phase != DeviceProbe.Phase.Idle) {
            val left = ((DeviceProbe.phaseEndsAtMs - now + 999) / 1000).coerceAtLeast(0)
            Text(
                if (phase == DeviceProbe.Phase.LeadIn) L10n.tr("Starting in %d s: go to the surface now", left) else L10n.tr("Measuring: %d s left. Keep using the surface", left),
                color = HyleColors.Violet, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
            ActionRow(L10n.tr("Stop"), null, Glyphs.Info) { DeviceProbe.cancel() }
        }
        for (s in ProbeSurface.entries) {
            ActionRow(surfaceLabel(s), probes[s]?.let(::probeText) ?: L10n.tr("Not run yet"), Glyphs.Bolt) {
                val a = context.activity()
                val st = store
                if (a != null && st != null) DeviceProbe.start(a, s) { surface, r -> if (r != null) { probes[surface] = r; st.saveProbe(surface, r) } }
            }
        }

        // Checklist
        Text(L10n.tr("Checklist"), color = HyleColors.InkBright, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
        for (c in DeviceCheck.entries) {
            val entry = checks[c] ?: CheckEntry(c, CheckStatus.Unset, "")
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("${checkTitle(c)} · ${c.closes}", color = HyleColors.InkBright, fontSize = 13.5.sp)
                Text(checkInstruction(c), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                Row {
                    for (st in listOf(CheckStatus.Pass, CheckStatus.Fail, CheckStatus.Skip)) {
                        TextButtonPill(statusLabel(st), primary = entry.status == st) {
                            // Tapping the chosen status again clears it.
                            val next = if (entry.status == st) CheckStatus.Unset else st
                            checks[c] = entry.copy(status = next); store?.setStatus(c, next)
                        }
                    }
                }
                Text(statusLabel(entry.status), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                BasicTextField(
                    value = entry.note, onValueChange = { v -> val n = v.take(DeviceChecksStore.MAX_NOTE); checks[c] = entry.copy(note = n); store?.setNote(c, n) },
                    textStyle = TextStyle(color = HyleColors.Ink, fontSize = 12.5.sp), cursorBrush = SolidColor(HyleColors.InkBright),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).border(1.dp, HyleColors.InkDim.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(10.dp),
                    decorationBox = { inner -> if (entry.note.isEmpty()) Text(L10n.tr("Note (optional)"), color = HyleColors.InkDim, fontSize = 12.5.sp); inner() },
                )
            }
        }

        ActionRow(L10n.tr("Copy report"), if (copied) L10n.tr("Copied to the clipboard") else L10n.tr("Plain text: version, device, frame timing, cold start and the checklist. Nothing is sent"), Glyphs.Info) {
            val report = DeviceReport.build(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                coldStartMs = coldMs ?: lastColdMs,
                probes = ProbeSurface.entries.associateWith { probes[it] },
                checks = DeviceCheck.entries.map { checks[it] ?: CheckEntry(it, CheckStatus.Unset, "") },
            )
            runCatching { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Liftoff device report", report)) }
            copied = true
        }
    }
}
