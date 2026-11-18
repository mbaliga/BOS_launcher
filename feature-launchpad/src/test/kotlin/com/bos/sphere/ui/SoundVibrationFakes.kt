package com.bos.sphere.ui

import com.bos.sphere.core.data.HapticStrength

/** A fake for [VolumeController]; [locked] rows refuse changes with a SecurityException when [throwing] is set, else report false. */
class FakeVolumes(
    val volumes: MutableMap<SoundRowKind, Int> = mutableMapOf(SoundRowKind.Media to 8, SoundRowKind.Notifications to 2, SoundRowKind.Alarms to 4),
    val maxes: Map<SoundRowKind, Int> = mapOf(SoundRowKind.Media to 15, SoundRowKind.Notifications to 7, SoundRowKind.Alarms to 7),
    var locked: Set<SoundRowKind> = emptySet(),
    var throwing: Boolean = false,
) : VolumeController {
    val writes = mutableListOf<Pair<SoundRowKind, Int>>()
    override fun volume(kind: SoundRowKind) = volumes.getValue(kind)
    override fun max(kind: SoundRowKind) = maxes.getValue(kind)
    override fun setVolume(kind: SoundRowKind, value: Int): Boolean {
        if (kind in locked) { if (throwing) throw SecurityException("Not allowed to change Do Not Disturb state"); return false }
        writes += kind to value; volumes[kind] = value; return true
    }
    override fun isLocked(kind: SoundRowKind) = kind in locked && !throwing
}

class FakeHaptics(var strength: HapticStrength = HapticStrength.Medium) : HapticControl {
    override fun get() = strength
    override fun set(value: HapticStrength) { strength = value }
}
