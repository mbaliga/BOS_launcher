package com.bos.sphere.feature.homewidgets

import android.content.Context
import com.bos.sphere.feature.homewidgets.model.NeedsYouModel
import com.bos.sphere.feature.homewidgets.model.NowPlayingModel
import com.bos.sphere.feature.homewidgets.model.PadRef
import com.bos.sphere.feature.homewidgets.model.PadsCodec

/**
 * Per-widget options, keyed by widget id: the apps on a pads widget, and the privacy choice of a
 * Needs you or Now playing widget. Also the font scale the widgets were last drawn for. Left out of backups (`:app` data_extraction_rules.xml): widget
 * ids belong to one device, so restored options would attach to whichever widget later got the id.
 */
internal object WidgetPrefs {
    const val FILE = "home_widgets"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun pads(c: Context, id: Int): List<PadRef> = PadsCodec.decode(prefs(c).getString("pads.$id", null))

    fun setPads(c: Context, id: Int, refs: List<PadRef>) {
        prefs(c).edit().putString("pads.$id", PadsCodec.encode(refs)).apply()
    }

    /** Needs you: app names shown (default) or counts only. */
    fun showNames(c: Context, id: Int): Boolean = prefs(c).getBoolean("names.$id", NeedsYouModel.SHOW_NAMES_DEFAULT)

    fun setShowNames(c: Context, id: Int, show: Boolean) {
        prefs(c).edit().putBoolean("names.$id", show).apply()
    }

    /**
     * Now playing: the app's name only (default) or track and artist too. Off by default because a
     * home screen is public and titles can name people (voice-message players put the sender in
     * them); whether the track should be the default is an open owner decision (docs/widgets.md).
     */
    fun showTrack(c: Context, id: Int): Boolean = prefs(c).getBoolean("track.$id", NowPlayingModel.SHOW_TRACK_DEFAULT)

    fun setShowTrack(c: Context, id: Int, show: Boolean) {
        prefs(c).edit().putBoolean("track.$id", show).apply()
    }

    /** The font scale the widgets' size keys were last drawn for (not per widget). */
    fun fontScale(c: Context): Float = prefs(c).getFloat("fontScale", -1f)

    fun setFontScale(c: Context, scale: Float) {
        prefs(c).edit().putFloat("fontScale", scale).apply()
    }

    fun forget(c: Context, ids: IntArray) {
        val e = prefs(c).edit()
        for (id in ids) e.remove("pads.$id").remove("names.$id").remove("track.$id")
        e.apply()
    }
}
