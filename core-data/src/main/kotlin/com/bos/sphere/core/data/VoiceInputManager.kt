package com.bos.sphere.core.data

/**
 * Wave 22 (audit): the old recogniser wrapper had no caller left (Radar's push-to-talk, `PushToTalk` in feature-grid, owns
 * the on-device recogniser) so only the no-op [release] that the home still calls on exit remains.
 */
object VoiceInputManager {
    /** Nothing to release: the push-to-talk control releases its own recogniser when it leaves the screen. */
    fun release() = Unit
}
