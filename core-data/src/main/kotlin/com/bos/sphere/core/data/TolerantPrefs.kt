package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Wave 18 (NFR-09, CFG-03; docs/decisions/startupCrashes.md): a [SharedPreferences] whose getters never throw. The platform getters throw
 * `ClassCastException` when a key holds another type than the one asked for (a file written by an older build, a restore from backup,
 * a hand edit), and every store reads its file in its constructor on the first frame, so one such key was a crash on every start. Here a
 * key of the wrong type reads as the default, exactly as a missing key does. Writes go to the real file unchanged.
 */
class TolerantPrefs(private val delegate: SharedPreferences) : SharedPreferences by delegate {
    override fun getString(key: String?, defValue: String?): String? = try { delegate.getString(key, defValue) } catch (_: ClassCastException) { defValue }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        try { delegate.getStringSet(key, defValues) } catch (_: ClassCastException) { defValues }

    override fun getInt(key: String?, defValue: Int): Int = try { delegate.getInt(key, defValue) } catch (_: ClassCastException) { defValue }
    override fun getLong(key: String?, defValue: Long): Long = try { delegate.getLong(key, defValue) } catch (_: ClassCastException) { defValue }
    override fun getFloat(key: String?, defValue: Float): Float = try { delegate.getFloat(key, defValue) } catch (_: ClassCastException) { defValue }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = try { delegate.getBoolean(key, defValue) } catch (_: ClassCastException) { defValue }

    companion object {
        /** The named preference file, read tolerantly. */
        fun of(context: Context, name: String): SharedPreferences = TolerantPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}
