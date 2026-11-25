package com.bos.sphere.feature.homewidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.bos.sphere.feature.homewidgets.model.MediaCommand
import com.bos.sphere.feature.homewidgets.model.NowPlayingModel
import com.bos.sphere.feature.homewidgets.model.Playback
import com.bos.sphere.feature.homewidgets.model.WidgetKind

/**
 * Widget taps that act without opening anything: the Now playing controls and the battery's
 * "read it again". Not exported; only Sphere's own PendingIntents reach it. Extras are checked: a
 * media command must name a known command and a package that has an active session right now.
 * Both keep the broadcast alive (goAsync) until their redraw is done, since a tap may be what
 * started Sphere's process.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_MEDIA -> {
                val command = MediaCommand.parse(intent.getStringExtra(EXTRA_COMMAND)) ?: return
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
                // The package's session the widget shows (NowPlayingModel's rule), not just its
                // first: one app can have several (two browser tabs, one paused).
                val controller = NowPlayingModel.chooseBy(Sources.activeControllers(app).orEmpty().filter { it.packageName == pkg }) {
                    NowPlayingModel.playback(it.playbackState?.state ?: NowPlayingModel.STATE_NONE)
                }
                if (controller != null) {
                    val t = controller.transportControls
                    runCatching {
                        when (command) {
                            MediaCommand.PREVIOUS -> t.skipToPrevious()
                            MediaCommand.NEXT -> t.skipToNext()
                            MediaCommand.PLAY_PAUSE -> {
                                val playback = NowPlayingModel.playback(controller.playbackState?.state ?: NowPlayingModel.STATE_NONE)
                                if (playback == Playback.PLAYING || playback == Playback.LOADING) t.pause() else t.play()
                            }
                        }
                    }
                }
                // The session's own callbacks redraw while Sphere's runtime watches; this covers the
                // rest, a moment later so the app has applied the command.
                val pendingResult = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    HomeWidgets.executor.execute {
                        try {
                            HomeWidgets.draw(app, WidgetKind.NOW_PLAYING, HomeWidgets.ids(app, WidgetKind.NOW_PLAYING), force = true)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }, AFTER_COMMAND_MS)
            }
            ACTION_REFRESH_BATTERY -> {
                WidgetRuntime.get(app).sync()
                val pendingResult = goAsync()
                HomeWidgets.executor.execute {
                    try {
                        HomeWidgets.draw(app, WidgetKind.BATTERY, HomeWidgets.ids(app, WidgetKind.BATTERY), force = true)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    internal companion object {
        const val ACTION_MEDIA = "com.bos.sphere.homewidgets.MEDIA"
        const val ACTION_REFRESH_BATTERY = "com.bos.sphere.homewidgets.REFRESH_BATTERY"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_COMMAND = "command"
        private const val AFTER_COMMAND_MS = 600L
    }
}
