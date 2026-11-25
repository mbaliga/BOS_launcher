package com.bos.sphere.feature.homewidgets

import android.app.AlarmManager
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import com.bos.sphere.feature.homewidgets.model.BatteryModel
import com.bos.sphere.feature.homewidgets.model.BatteryReading
import com.bos.sphere.feature.homewidgets.model.BatteryState
import com.bos.sphere.feature.homewidgets.model.ClockModel
import com.bos.sphere.feature.homewidgets.model.MediaCommand
import com.bos.sphere.feature.homewidgets.model.NeedsYouModel
import com.bos.sphere.feature.homewidgets.model.NeedsYouView
import com.bos.sphere.feature.homewidgets.model.NowPlayingModel
import com.bos.sphere.feature.homewidgets.model.NowPlayingView
import com.bos.sphere.feature.homewidgets.model.PadRef
import com.bos.sphere.feature.homewidgets.model.PadsModel
import com.bos.sphere.feature.homewidgets.model.Playback
import com.bos.sphere.feature.homewidgets.model.PlugSource
import com.bos.sphere.feature.homewidgets.model.Tier
import com.bos.sphere.feature.homewidgets.model.WidgetAction
import com.bos.sphere.feature.homewidgets.model.WidgetKind
import com.bos.sphere.feature.homewidgets.model.WidgetTiers
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Builds each widget's RemoteViews from [Sources]: one layout per [Tier], handed to the launcher as
 * size-mapped RemoteViews (API 31+, Sphere's minSdk) so it picks the layout for the widget's size
 * without waking Sphere. The size keys grow with the system font scale ([WidgetTiers.atFontScale]),
 * so larger text picks a smaller layout instead of clipping. Strings come from resources in the
 * current locale; colours are day/night resources the launcher resolves.
 */
internal object Renderers {

    fun render(c: Context, kind: WidgetKind, ids: IntArray): List<Pair<Int, Drawn>> = when (kind) {
        WidgetKind.CLOCK -> clock(c, ids)
        WidgetKind.BATTERY -> battery(c, ids)
        WidgetKind.NOW_PLAYING -> nowPlaying(c, ids)
        WidgetKind.NEEDS_YOU -> needsYou(c, ids)
        WidgetKind.PADS -> pads(c, ids)
    }

    // --- shared ---

    private fun sized(c: Context, kind: WidgetKind, build: (Tier) -> RemoteViews): RemoteViews =
        RemoteViews(
            WidgetTiers.atFontScale(kind.tiers, c.resources.configuration.fontScale)
                .associate { SizeF(it.minWidth, it.minHeight) to build(it.tier) },
        )

    private fun layout(c: Context, @LayoutRes id: Int) = RemoteViews(c.packageName, id)

    private fun time(c: Context, millis: Long): String = DateFormat.getTimeFormat(c).format(Date(millis))

    private fun asOf(c: Context, millis: Long) = c.getString(R.string.hw_as_of, time(c, millis))

    private fun RemoteViews.show(id: Int, visible: Boolean) = setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

    /**
     * A state instead of data: glyph + title + body, one tap target. Small: one compact line of
     * title and two of body. [asOf] (states whose truth can lapse while Sphere isn't running:
     * nothing playing, connecting) is printed at every size; in the small layout it takes the
     * body's place.
     */
    private fun state(
        c: Context,
        kind: WidgetKind,
        @DrawableRes glyph: Int,
        title: String,
        body: String,
        tap: android.app.PendingIntent?,
        cd: String? = null,
        asOf: String? = null,
    ) = sized(c, kind) { tier ->
        layout(c, if (tier == Tier.SMALL) R.layout.hw_state_s else R.layout.hw_state).apply {
            setImageViewResource(R.id.hw_state_glyph, glyph)
            setTextViewText(R.id.hw_state_title, title)
            if (tier == Tier.SMALL) {
                setTextViewText(R.id.hw_state_body, asOf ?: body)
            } else {
                setTextViewText(R.id.hw_state_body, body)
                show(R.id.hw_state_asof, asOf != null)
                setTextViewText(R.id.hw_state_asof, asOf.orEmpty())
            }
            if (tap != null) setOnClickPendingIntent(android.R.id.background, tap)
            setContentDescription(android.R.id.background, cd ?: listOfNotNull(title, body, asOf).joinToString(". "))
        }
    }

    fun error(c: Context, kind: WidgetKind, id: Int): RemoteViews = state(
        c, kind, R.drawable.hw_ic_warning, c.getString(R.string.hw_error_title), c.getString(R.string.hw_error_body),
        Taps.openHome(c, id, hub = false),
    )

    // --- Gate clock ---

    private fun clock(c: Context, ids: IntArray): List<Pair<Int, Drawn>> {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val locale = c.resources.configuration.locales[0] ?: Locale.getDefault()
        val trigger = runCatching { c.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
        val alarm = ClockModel.nextAlarm(trigger, now, zone)
        val alarmText = if (alarm == null) {
            c.getString(R.string.hw_clock_no_alarm)
        } else {
            val dayPattern = if (alarm.day == ClockModel.AlarmDay.WEEKDAY) "EEE" else DateFormat.getBestDateTimePattern(locale, "dMMM")
            val day = DateTimeFormatter.ofPattern(dayPattern, locale).format(alarm.at)
            c.getString(R.string.hw_clock_alarm, "$day ${time(c, alarm.at.toInstant().toEpochMilli())}")
        }
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        return ids.map { id ->
            val open = Taps.openHome(c, id, hub = false)
            val views = sized(c, WidgetKind.CLOCK) { tier ->
                layout(c, when (tier) { Tier.SMALL -> R.layout.hw_clock_s; Tier.MEDIUM -> R.layout.hw_clock_m; Tier.LARGE -> R.layout.hw_clock_l }).apply {
                    if (tier != Tier.SMALL) {
                        setCharSequence(R.id.hw_clock_date, "setFormat12Hour", datePattern)
                        setCharSequence(R.id.hw_clock_date, "setFormat24Hour", datePattern)
                    }
                    if (tier == Tier.LARGE) {
                        setTextViewText(R.id.hw_clock_alarm, alarmText)
                        setImageViewResource(R.id.hw_clock_alarm_glyph, if (alarm == null) R.drawable.hw_ic_alarm_off else R.drawable.hw_ic_alarm)
                    }
                    // No content description on the card: TalkBack reads the TextClocks themselves.
                    if (open != null) setOnClickPendingIntent(android.R.id.background, open)
                }
            }
            id to Drawn(views, "clock|$alarmText|$datePattern")
        }
    }

    // --- Battery ---

    private fun battery(c: Context, ids: IntArray): List<Pair<Int, Drawn>> {
        val r = Sources.battery(c)
        return ids.map { id ->
            val refresh = Taps.refreshBattery(c, id)
            val views = if (r == null) {
                state(c, WidgetKind.BATTERY, R.drawable.hw_ic_warning, c.getString(R.string.hw_battery_unavailable_title), c.getString(R.string.hw_battery_unavailable_body), refresh)
            } else {
                sized(c, WidgetKind.BATTERY) { tier -> batteryViews(c, tier, r, refresh) }
            }
            id to Drawn(views, "battery|${BatteryModel.redrawKey(r)}|${r?.at}")
        }
    }

    private fun batteryViews(c: Context, tier: Tier, r: BatteryReading, refresh: android.app.PendingIntent): RemoteViews {
        val word = c.getString(
            when (r.state) {
                BatteryState.CHARGING -> R.string.hw_battery_charging
                BatteryState.FULL -> R.string.hw_battery_full
                BatteryState.PLUGGED -> R.string.hw_battery_plugged
                BatteryState.ON_BATTERY -> R.string.hw_battery_on_battery
                BatteryState.LOW -> R.string.hw_battery_low
            },
        )
        val glyph = when (r.state) {
            BatteryState.CHARGING -> R.drawable.hw_ic_bolt
            BatteryState.FULL, BatteryState.PLUGGED -> R.drawable.hw_ic_plug
            BatteryState.ON_BATTERY -> R.drawable.hw_ic_battery
            BatteryState.LOW -> R.drawable.hw_ic_battery_low
        }
        val asOf = asOf(c, r.at)
        return layout(c, when (tier) { Tier.SMALL -> R.layout.hw_battery_s; Tier.MEDIUM -> R.layout.hw_battery_m; Tier.LARGE -> R.layout.hw_battery_l }).apply {
            setImageViewResource(R.id.hw_battery_glyph, glyph)
            setTextViewText(R.id.hw_battery_percent, c.getString(R.string.hw_battery_percent, r.percent))
            setTextViewText(R.id.hw_battery_state, word)
            setTextViewText(R.id.hw_battery_asof, asOf)
            if (tier != Tier.SMALL) setProgressBar(R.id.hw_battery_bar, 100, r.percent, false)
            if (tier == Tier.LARGE) {
                val detail = r.minutesToFull?.let { m ->
                    val (h, min) = BatteryModel.hoursAndMinutes(m)
                    if (h > 0) c.getString(R.string.hw_battery_full_in_hm, h, min) else c.getString(R.string.hw_battery_full_in_m, min)
                } ?: when (r.plug) {
                    PlugSource.AC -> c.getString(R.string.hw_battery_source_ac)
                    PlugSource.USB -> c.getString(R.string.hw_battery_source_usb)
                    PlugSource.WIRELESS -> c.getString(R.string.hw_battery_source_wireless)
                    PlugSource.DOCK -> c.getString(R.string.hw_battery_source_dock)
                    PlugSource.NONE -> null
                }
                show(R.id.hw_battery_detail, detail != null)
                setTextViewText(R.id.hw_battery_detail, detail.orEmpty())
                val temp = r.temperatureC?.let { String.format(Locale.getDefault(), "%.0f", it) }
                show(R.id.hw_battery_temp, temp != null)
                setTextViewText(R.id.hw_battery_temp, temp?.let { c.getString(R.string.hw_battery_temperature, it) }.orEmpty())
            }
            setOnClickPendingIntent(android.R.id.background, refresh)
            setContentDescription(android.R.id.background, c.getString(R.string.hw_battery_cd, r.percent, word, asOf))
        }
    }

    // --- Now playing ---

    private fun nowPlaying(c: Context, ids: IntArray): List<Pair<Int, Drawn>> {
        val access = Sources.notificationAccess(c)
        val sessions = if (access) Sources.mediaSessions(c) else null
        val hidden = NeedsYouModel.hiddenPackages(Sources.hiddenKeys(c))
        val now = System.currentTimeMillis()
        return ids.map { id ->
            val view = NowPlayingModel.view(access, sessions, WidgetPrefs.showTrack(c, id), hidden)
            val views = when (view) {
                NowPlayingView.NotConnected -> state(
                    c, WidgetKind.NOW_PLAYING, R.drawable.hw_ic_link_off, c.getString(R.string.hw_access_off_title), c.getString(R.string.hw_np_off_body),
                    Taps.notificationAccess(c, id), c.getString(R.string.hw_access_off_cd),
                )
                NowPlayingView.Nothing -> state(
                    c, WidgetKind.NOW_PLAYING, R.drawable.hw_ic_note, c.getString(R.string.hw_np_nothing_title), c.getString(R.string.hw_np_nothing_body),
                    Taps.openHome(c, id, hub = false), asOf = asOf(c, now),
                )
                is NowPlayingView.Track -> sized(c, WidgetKind.NOW_PLAYING) { tier -> trackViews(c, id, tier, view, now) }
            }
            id to Drawn(views, "np|$view")
        }
    }

    private fun trackViews(c: Context, id: Int, tier: Tier, t: NowPlayingView.Track, now: Long): RemoteViews {
        val word = c.getString(
            when (t.playback) {
                Playback.PLAYING -> R.string.hw_np_playing
                Playback.PAUSED -> R.string.hw_np_paused
                else -> R.string.hw_np_loading
            },
        )
        val glyph = when (t.playback) {
            Playback.PLAYING -> R.drawable.hw_ic_play
            Playback.PAUSED -> R.drawable.hw_ic_pause
            else -> R.drawable.hw_ic_loading
        }
        // The playing app's name, or, for an app hidden in Sphere, no name at all.
        val app = t.appLabel ?: c.getString(R.string.hw_np_unnamed_app)
        val what = when {
            t.appHidden -> app
            t.withheld -> c.getString(R.string.hw_np_withheld)
            t.title != null -> listOfNotNull(t.title, t.artist).joinToString(", ")
            else -> app
        }
        val asOf = asOf(c, now)
        return layout(c, when (tier) { Tier.SMALL -> R.layout.hw_np_s; Tier.MEDIUM -> R.layout.hw_np_m; Tier.LARGE -> R.layout.hw_np_l }).apply {
            setImageViewResource(R.id.hw_np_state_glyph, glyph)
            if (tier == Tier.SMALL) {
                setTextViewText(R.id.hw_np_title, t.title ?: app)
                // The sub line wraps to a second line rather than drop the "as of" when narrow.
                setTextViewText(R.id.hw_np_sub, listOfNotNull(word, if (t.title != null) app else null, asOf).joinToString(" · "))
            } else {
                setTextViewText(
                    R.id.hw_np_eyebrow,
                    t.appLabel?.let { c.getString(R.string.hw_np_eyebrow, it) } ?: c.getString(R.string.hw_np_eyebrow_unnamed),
                )
                val titleText = t.title ?: when {
                    t.appHidden -> app
                    t.withheld -> c.getString(R.string.hw_np_withheld)
                    else -> app
                }
                setTextViewText(R.id.hw_np_title, titleText)
                // A resource, not a colour int, so the launcher resolves it for day or night.
                if (t.title == null && (t.withheld || t.appHidden)) setColorStateList(R.id.hw_np_title, "setTextColor", R.color.hw_ink_dim)
                show(R.id.hw_np_artist, t.artist != null)
                setTextViewText(R.id.hw_np_artist, t.artist.orEmpty())
                setTextViewText(R.id.hw_np_state, "$word · $asOf")
            }
            if (tier == Tier.LARGE) {
                show(R.id.hw_np_prev, t.controls.previous)
                show(R.id.hw_np_playpause, t.controls.playPause)
                show(R.id.hw_np_next, t.controls.next)
                setOnClickPendingIntent(R.id.hw_np_prev, Taps.media(c, id, WidgetAction.PREVIOUS, t.packageName, MediaCommand.PREVIOUS))
                setOnClickPendingIntent(R.id.hw_np_playpause, Taps.media(c, id, WidgetAction.PLAY_PAUSE, t.packageName, MediaCommand.PLAY_PAUSE))
                setOnClickPendingIntent(R.id.hw_np_next, Taps.media(c, id, WidgetAction.NEXT, t.packageName, MediaCommand.NEXT))
                val playing = t.playback == Playback.PLAYING || t.playback == Playback.LOADING
                setImageViewResource(R.id.hw_np_playpause, if (playing) R.drawable.hw_ic_pause else R.drawable.hw_ic_play)
                setContentDescription(R.id.hw_np_playpause, c.getString(if (playing) R.string.hw_np_pause else R.string.hw_np_play))
            }
            // A hidden app isn't opened from the home screen: the card opens Sphere instead.
            if (t.appHidden) {
                Taps.openHome(c, id, hub = false)?.let { setOnClickPendingIntent(android.R.id.background, it) }
            } else {
                setOnClickPendingIntent(android.R.id.background, Taps.launchPackage(c, id, WidgetAction.OPEN, t.packageName, null))
            }
            val opens = if (t.appHidden) c.getString(R.string.hw_sphere) else app
            setContentDescription(android.R.id.background, c.getString(R.string.hw_np_cd, what, "$word, $asOf", opens))
        }
    }

    // --- Needs you ---

    private fun needsYou(c: Context, ids: IntArray): List<Pair<Int, Drawn>> {
        val access = Sources.notificationAccess(c)
        val facts = Sources.notifications()
        val hidden = NeedsYouModel.hiddenPackages(Sources.hiddenKeys(c))
        val labels = Sources.Labels(c)
        val now = System.currentTimeMillis()
        return ids.map { id ->
            val names = WidgetPrefs.showNames(c, id)
            fun viewFor(tier: Tier) = NeedsYouModel.view(access, facts, c.packageName, hidden, names, NeedsYouModel.rowsFor(tier)) { pkg, user -> labels.get(pkg, user) }
            val large = viewFor(Tier.LARGE)
            val views = when (large) {
                NeedsYouView.NotConnected -> state(
                    c, WidgetKind.NEEDS_YOU, R.drawable.hw_ic_link_off, c.getString(R.string.hw_access_off_title), c.getString(R.string.hw_ny_off_body),
                    Taps.notificationAccess(c, id), c.getString(R.string.hw_access_off_cd),
                )
                NeedsYouView.Connecting -> state(
                    c, WidgetKind.NEEDS_YOU, R.drawable.hw_ic_clock, c.getString(R.string.hw_ny_connecting_title), c.getString(R.string.hw_ny_connecting_body),
                    Taps.openHome(c, id, hub = true), asOf = asOf(c, now),
                )
                is NeedsYouView.Counts -> sized(c, WidgetKind.NEEDS_YOU) { tier ->
                    needsYouViews(c, id, tier, viewFor(tier) as NeedsYouView.Counts, now)
                }
            }
            id to Drawn(views, "ny|$large")
        }
    }

    private val rowIds = listOf(
        intArrayOf(R.id.hw_ny_row1, R.id.hw_ny_row1_label, R.id.hw_ny_row1_count),
        intArrayOf(R.id.hw_ny_row2, R.id.hw_ny_row2_label, R.id.hw_ny_row2_count),
        intArrayOf(R.id.hw_ny_row3, R.id.hw_ny_row3_label, R.id.hw_ny_row3_count),
        intArrayOf(R.id.hw_ny_row4, R.id.hw_ny_row4_label, R.id.hw_ny_row4_count),
        intArrayOf(R.id.hw_ny_row5, R.id.hw_ny_row5_label, R.id.hw_ny_row5_count),
    )

    private fun needsYouViews(c: Context, id: Int, tier: Tier, v: NeedsYouView.Counts, now: Long): RemoteViews {
        val res = c.resources
        val total = res.getQuantityString(R.plurals.hw_ny_total, v.total, v.total)
        val fromApps = res.getQuantityString(R.plurals.hw_ny_from_apps, v.apps, v.apps)
        val nothing = v.total == 0
        val asOf = asOf(c, now)
        return layout(c, when (tier) { Tier.SMALL -> R.layout.hw_ny_s; Tier.MEDIUM -> R.layout.hw_ny_m; Tier.LARGE -> R.layout.hw_ny_l }).apply {
            if (tier == Tier.SMALL) {
                setTextViewText(R.id.hw_ny_message, if (nothing) c.getString(R.string.hw_ny_nothing) else total)
                // Wraps to a second line rather than drop the "as of" when narrow.
                setTextViewText(R.id.hw_ny_message_sub, if (nothing) asOf else "$fromApps · $asOf")
            } else {
                setTextViewText(R.id.hw_ny_total, v.total.toString())
                show(R.id.hw_ny_total, !nothing)
                // Counts only, nothing waiting, or only apps that are never named: a sentence instead of rows.
                val sentence = nothing || v.rows.isEmpty()
                show(R.id.hw_ny_message, sentence)
                show(R.id.hw_ny_message_sub, sentence && !nothing)
                setTextViewText(R.id.hw_ny_message, if (nothing) c.getString(R.string.hw_ny_nothing) else total)
                setTextViewText(R.id.hw_ny_message_sub, fromApps)
                val rows = rowIds.take(NeedsYouModel.rowsFor(tier))
                rows.forEachIndexed { i, (row, label, count) ->
                    val r = v.rows.getOrNull(i)
                    show(row, r != null)
                    if (r == null) return@forEachIndexed
                    setTextViewText(label, r.label)
                    setTextViewText(count, r.count.toString())
                    setOnClickPendingIntent(row, Taps.launchPackage(c, id, WidgetAction.ROWS[i], r.packageName, r.userSerial))
                    setContentDescription(row, c.getString(R.string.hw_ny_row_cd, r.label, r.count))
                }
                val footer = listOfNotNull(
                    // With no rows, the sentence already says how many apps.
                    if (v.moreApps > 0 && v.rows.isNotEmpty()) res.getQuantityString(R.plurals.hw_ny_more_apps, v.moreApps, v.moreApps) else null,
                    if (!v.namesShown) c.getString(R.string.hw_ny_counts_only) else null,
                    asOf,
                ).joinToString(" · ")
                setTextViewText(R.id.hw_ny_footer, footer)
            }
            Taps.openHome(c, id, hub = true)?.let { setOnClickPendingIntent(android.R.id.background, it) }
            val summary = if (nothing) c.getString(R.string.hw_ny_nothing) else "$total $fromApps"
            setContentDescription(android.R.id.background, c.getString(R.string.hw_ny_cd, "$summary, $asOf"))
        }
    }

    // --- App pads ---

    private val padIds = listOf(
        intArrayOf(R.id.hw_pad_1, R.id.hw_pad_1_icon, R.id.hw_pad_1_label),
        intArrayOf(R.id.hw_pad_2, R.id.hw_pad_2_icon, R.id.hw_pad_2_label),
        intArrayOf(R.id.hw_pad_3, R.id.hw_pad_3_icon, R.id.hw_pad_3_label),
        intArrayOf(R.id.hw_pad_4, R.id.hw_pad_4_icon, R.id.hw_pad_4_label),
        intArrayOf(R.id.hw_pad_5, R.id.hw_pad_5_icon, R.id.hw_pad_5_label),
        intArrayOf(R.id.hw_pad_6, R.id.hw_pad_6_icon, R.id.hw_pad_6_label),
    )

    private fun pads(c: Context, ids: IntArray): List<Pair<Int, Drawn>> {
        val apps = Sources.launchableApps(c)
        val facts = apps.mapValues { it.value.fact }
        val icons = HashMap<PadRef, Bitmap?>() // one bitmap per app, shared by every layout and widget
        return ids.map { id ->
            val view = PadsModel.resolve(WidgetPrefs.pads(c, id), facts)
            val views = if (view.pads.isEmpty()) {
                state(c, WidgetKind.PADS, R.drawable.hw_ic_apps, c.getString(R.string.hw_pads_choose_title), c.getString(R.string.hw_pads_choose_body), Taps.options(c, id))
            } else {
                sized(c, WidgetKind.PADS) { tier ->
                    layout(c, when (tier) { Tier.SMALL -> R.layout.hw_pads_s; Tier.MEDIUM -> R.layout.hw_pads_m; Tier.LARGE -> R.layout.hw_pads_l }).apply {
                        padIds.take(PadsModel.padsFor(tier)).forEachIndexed { i, (pad, icon, label) ->
                            val p = view.pads.getOrNull(i)
                            setViewVisibility(pad, if (p == null) View.INVISIBLE else View.VISIBLE)
                            if (p == null) return@forEachIndexed
                            val bmp = icons.getOrPut(p.ref) { apps[p.ref]?.let { Sources.icon(c, it.info) } }
                            if (bmp != null) setImageViewBitmap(icon, bmp) else setImageViewResource(icon, R.drawable.hw_ic_apps)
                            if (tier == Tier.LARGE) setTextViewText(label, p.label)
                            setOnClickPendingIntent(pad, Taps.launchApp(c, id, WidgetAction.PADS[i], p.ref))
                            setContentDescription(pad, c.getString(R.string.hw_pad_cd, p.label))
                        }
                    }
                }
            }
            id to Drawn(views, "pads|$view")
        }
    }
}
