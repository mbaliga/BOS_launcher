package com.bos.sphere.core.catalog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * HOM-06: which profiles are paused right now, kept current from the system's profile availability
 * broadcasts and read by every surface that dims or labels work apps (home icons, the pause chip,
 * the drawer). All system calls (quiet-mode query and request) run off the main thread.
 *
 * [quietProfileIds] holds `UserHandle.hashCode()` of every secondary profile in quiet mode, the
 * same id [com.bos.sphere.core.data.AppEntry.user] gives. [workPaused] is null while there is no
 * work profile.
 */
object WorkProfileState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _quiet = MutableStateFlow<Set<Int>>(emptySet())
    private val _workPaused = MutableStateFlow<Boolean?>(null)
    val quietProfileIds: StateFlow<Set<Int>> = _quiet.asStateFlow()
    val workPaused: StateFlow<Boolean?> = _workPaused.asStateFlow()

    @Volatile private var workUser: UserHandle? = null

    /** Re-reads the profiles (off the main thread). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val catalog = AppCatalog(app)
            val profiles = runCatching { catalog.profiles() }.getOrDefault(emptyList())
            val secondary = profiles.filter { it.second.kind != ProfileKind.Personal }
            _quiet.value = secondary.filter { it.second.quiet }.mapTo(HashSet()) { it.first.hashCode() }
            val work = secondary.firstOrNull { it.second.kind == ProfileKind.Work }
            workUser = work?.first
            _workPaused.value = work?.second?.quiet
        }
    }

    /** Pauses or resumes the work profile; the state follows the result (and the broadcast the system sends). */
    fun setWorkPaused(context: Context, paused: Boolean) {
        val app = context.applicationContext
        val user = workUser ?: return
        scope.launch {
            val ok = AppCatalog(app).setPaused(user, paused)
            if (ok) {
                // Quiet mode changes asynchronously; an immediate re-read could still see the old value and
                // flip the chip back. Trust the accepted request, the availability broadcast confirms it.
                _workPaused.value = paused
                val id = user.hashCode()
                _quiet.value = if (paused) _quiet.value + id else _quiet.value - id
            } else refresh(app)
        }
    }

    /** Registers for the availability broadcasts; call [Registration.close] when the surface goes away. */
    fun start(context: Context): Registration {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { refresh(app) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }
        runCatching { app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) }
        refresh(app)
        return Registration { runCatching { app.unregisterReceiver(receiver) } }
    }

    fun interface Registration { fun close() }

    /** The label of the pause chip for a state; null when there is no work profile. */
    fun chipLabel(paused: Boolean?): String? = when (paused) { null -> null; true -> "Resume work apps"; false -> "Pause work apps" }
}
