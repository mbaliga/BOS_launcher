package com.bos.sphere.ui

import android.content.Context
import android.content.SharedPreferences
import com.bos.sphere.core.data.TolerantPrefs
import com.bos.sphere.core.quality.CheckEntry
import com.bos.sphere.core.quality.CheckStatus
import com.bos.sphere.core.quality.DeviceCheck
import com.bos.sphere.core.quality.ProbeResult
import com.bos.sphere.core.quality.ProbeSurface

/**
 * Wave 23 (CFG-17; docs/decisions/deviceKit.md): what the Lab's "Device checks" page remembers: the last frame-timing run per surface,
 * the last cold start and each checklist line's status and note. One preference file, device-local and excluded from backup, transfer and
 * export (it describes this phone: `StateSchema.BACKUP_EXCLUDED_PREF_FILES`). Open it off the main thread ([open] reads the file).
 */
class DeviceChecksStore private constructor(private val prefs: SharedPreferences) {
    fun probe(s: ProbeSurface): ProbeResult? = ProbeResult.decode(prefs.getString("probe.${s.id}", null))
    fun saveProbe(s: ProbeSurface, r: ProbeResult) { prefs.edit().putString("probe.${s.id}", r.encode()).apply() }

    fun check(c: DeviceCheck): CheckEntry = CheckEntry(c, CheckStatus.of(prefs.getString("check.${c.id}.status", null)), prefs.getString("check.${c.id}.note", "").orEmpty())
    fun setStatus(c: DeviceCheck, s: CheckStatus) { prefs.edit().putString("check.${c.id}.status", s.id).apply() }
    fun setNote(c: DeviceCheck, note: String) { prefs.edit().putString("check.${c.id}.note", note.take(MAX_NOTE)).apply() }
    fun checks(): List<CheckEntry> = DeviceCheck.entries.map(::check)

    /** The cold start of an earlier process (milliseconds), or null. */
    fun lastColdStartMs(): Long? = prefs.getLong("coldStartMs", -1L).takeIf { it >= 0 }
    fun saveColdStart(ms: Long) { prefs.edit().putLong("coldStartMs", ms).apply() }

    companion object {
        const val FILE = "device_checks"
        const val MAX_NOTE = 400
        fun open(context: Context) = DeviceChecksStore(TolerantPrefs.of(context.applicationContext, FILE))
    }
}
