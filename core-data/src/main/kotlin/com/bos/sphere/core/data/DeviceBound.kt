package com.bos.sphere.core.data

import android.content.SharedPreferences

/**
 * NFR-18: values that mean something on one device only (app widget ids, the path of the private background picture).
 * They are kept in their own preference file, [FILE], which the platform backup and device transfer rules exclude, instead
 * of beside the setup in `launcher_prefs` and `shortcuts` (which are backed up and exported).
 *
 * Every function takes the main file and the device file. With no device file (tests that build a store over one in-memory
 * file) the values stay in the main file as they did before. Older installs hold the values in the main file: the first
 * read moves them across ([migratePrefs], [migrateSlots]), so nothing is lost on update.
 */
object DeviceBound {
    const val FILE = "device_bound"
    private const val PICTURE = "bgPicture"
    private const val LEFT_WIDGET = "leftWidgetId"

    fun pictureOf(sp: SharedPreferences, device: SharedPreferences?): String =
        (device?.getString(PICTURE, null) ?: sp.getString(PICTURE, "")) ?: ""

    fun leftWidgetOf(sp: SharedPreferences, device: SharedPreferences?, default: Int): Int =
        if (device != null && device.contains(LEFT_WIDGET)) device.getInt(LEFT_WIDGET, default) else sp.getInt(LEFT_WIDGET, default)

    fun writePrefs(sp: SharedPreferences, device: SharedPreferences?, picture: String, leftWidget: Int) {
        if (device == null) { sp.edit().putString(PICTURE, picture).putInt(LEFT_WIDGET, leftWidget).apply(); return }
        device.edit().putString(PICTURE, picture).putInt(LEFT_WIDGET, leftWidget).apply()
        if (sp.contains(PICTURE) || sp.contains(LEFT_WIDGET)) sp.edit().remove(PICTURE).remove(LEFT_WIDGET).apply()
    }

    /** Moves the two values an older build wrote into the main file over to the device file. */
    fun migratePrefs(sp: SharedPreferences, device: SharedPreferences?) {
        if (device == null || !(sp.contains(PICTURE) || sp.contains(LEFT_WIDGET))) return
        val e = device.edit()
        if (sp.contains(PICTURE) && !device.contains(PICTURE)) e.putString(PICTURE, sp.getString(PICTURE, "") ?: "")
        if (sp.contains(LEFT_WIDGET) && !device.contains(LEFT_WIDGET)) e.putInt(LEFT_WIDGET, sp.getInt(LEFT_WIDGET, -1))
        e.apply()
        sp.edit().remove(PICTURE).remove(LEFT_WIDGET).apply()
    }

    fun getSlot(sp: SharedPreferences, device: SharedPreferences?, key: String): CornerSlot? =
        (device?.getString(key, null) ?: sp.getString(key, null))?.let(ShortcutCodec::decodeSlot)

    /** A widget slot goes to the device file; any other slot goes to the main file and clears a widget left under the same key. */
    fun putSlot(sp: SharedPreferences, device: SharedPreferences?, key: String, slot: CornerSlot) {
        val raw = ShortcutCodec.encodeSlot(slot)
        if (device == null) { sp.edit().putString(key, raw).apply(); return }
        if (slot is CornerSlot.Widget) {
            device.edit().putString(key, raw).apply()
            // The main file keeps an empty marker so a restore that brings only this file back shows an empty corner, not a default.
            sp.edit().putString(key, ShortcutCodec.encodeSlot(CornerSlot.Empty)).apply()
        } else {
            sp.edit().putString(key, raw).apply()
            if (device.contains(key)) device.edit().remove(key).apply()
        }
    }

    fun migrateSlots(sp: SharedPreferences, device: SharedPreferences?) {
        if (device == null) return
        for ((k, v) in sp.all) {
            if (!(k.startsWith("corner.") || k.startsWith("lcorner."))) continue
            val raw = v as? String ?: continue
            if (ShortcutCodec.decodeSlot(raw) is CornerSlot.Widget) {
                device.edit().putString(k, raw).apply()
                sp.edit().putString(k, ShortcutCodec.encodeSlot(CornerSlot.Empty)).apply()
            }
        }
    }
}
