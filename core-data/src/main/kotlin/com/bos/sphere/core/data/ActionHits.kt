package com.bos.sphere.core.data

/** Turns a typed query into launcher-action hits ("torch" -> Flashlight). Pure; no Android types. */
object ActionHits {
    /** Actions worth offering in search: every non-internal one plus a few useful internal ones. */
    private val offered: List<CornerActionId> = CornerActionId.entries.filter {
        !it.internal || it == CornerActionId.LauncherSettings || it == CornerActionId.AllApps || it == CornerActionId.Notifications
    }

    private val synonyms: Map<CornerActionId, List<String>> = mapOf(
        CornerActionId.Music to listOf("music", "songs", "player", "audio", "spotify", "playlist"),
        CornerActionId.Camera to listOf("camera", "photo", "picture", "selfie", "video", "snap"),
        CornerActionId.Phone to listOf("phone", "call", "dial", "dialer"),
        CornerActionId.Messages to listOf("messages", "sms", "text", "chat", "message"),
        CornerActionId.Browser to listOf("browser", "web", "internet", "chrome", "firefox"),
        CornerActionId.Email to listOf("email", "mail", "inbox", "gmail"),
        CornerActionId.Maps to listOf("maps", "map", "navigate", "navigation", "directions", "gps"),
        CornerActionId.Calendar to listOf("calendar", "agenda", "schedule", "event", "date"),
        CornerActionId.Clock to listOf("clock", "alarm", "timer", "stopwatch", "time", "wake"),
        CornerActionId.Gallery to listOf("gallery", "photos", "pictures", "images", "album"),
        CornerActionId.Contacts to listOf("contacts", "people", "address book"),
        CornerActionId.Calculator to listOf("calculator", "calc", "math"),
        CornerActionId.Assistant to listOf("assistant", "ai", "voice", "ask", "google"),
        CornerActionId.Flashlight to listOf("flashlight", "torch", "light", "lamp"),
        CornerActionId.RingerCycle to listOf("ringer", "sound", "ring", "volume"),
        CornerActionId.RingerNormal to listOf("ring", "ringer", "sound", "loud", "normal"),
        CornerActionId.RingerVibrate to listOf("vibrate", "vibration", "buzz", "haptic"),
        CornerActionId.RingerSilent to listOf("silent", "mute", "quiet", "silence"),
        CornerActionId.DoNotDisturb to listOf("do not disturb", "dnd", "focus", "quiet", "sleep"),
        CornerActionId.SystemSettings to listOf("settings", "wifi", "wi-fi", "bluetooth", "network", "battery", "display", "brightness", "system"),
        CornerActionId.LauncherSettings to listOf("home settings", "launcher", "settings", "preferences", "customize", "layout"),
        CornerActionId.AllApps to listOf("all apps", "apps", "drawer", "applications"),
        CornerActionId.Notifications to listOf("notifications", "alerts", "shade", "notices"),
    )

    private val verbs: Map<CornerActionId, String> = mapOf(
        CornerActionId.Music to "Open music",
        CornerActionId.Camera to "Take a photo",
        CornerActionId.Phone to "Make a call",
        CornerActionId.Messages to "Write a message",
        CornerActionId.Browser to "Browse the web",
        CornerActionId.Email to "Read your mail",
        CornerActionId.Maps to "Find a place",
        CornerActionId.Calendar to "See your day",
        CornerActionId.Clock to "Alarms and timers",
        CornerActionId.Gallery to "Browse photos",
        CornerActionId.Contacts to "Find people",
        CornerActionId.Calculator to "Do some math",
        CornerActionId.Assistant to "Ask the assistant",
        CornerActionId.Flashlight to "Toggle the torch",
        CornerActionId.RingerCycle to "Switch sound mode",
        CornerActionId.RingerNormal to "Ring for calls",
        CornerActionId.RingerVibrate to "Vibrate for calls",
        CornerActionId.RingerSilent to "Silence the phone",
        CornerActionId.DoNotDisturb to "Toggle do not disturb",
        CornerActionId.SystemSettings to "Open system settings",
        CornerActionId.LauncherSettings to "Tune this launcher",
        CornerActionId.AllApps to "Show every app",
        CornerActionId.Notifications to "Open the shade",
    )

    /** Score 0..1 for how well [action] matches the lower-cased, trimmed [q]; 0 means no match. */
    internal fun score(action: CornerActionId, q: String): Float {
        val label = action.label.lowercase()
        if (label == q) return 1f
        if (label.startsWith(q)) return 0.95f
        if (label.split(' ', '/').any { it.startsWith(q) }) return 0.85f
        val syn = synonyms[action].orEmpty()
        if (syn.any { it == q }) return 0.8f
        if (syn.any { it.startsWith(q) }) return 0.7f
        if (q.length >= 2 && label.contains(q)) return 0.5f
        if (q.length >= 3 && syn.any { it.contains(q) }) return 0.4f
        return 0f
    }

    fun match(query: String, limit: Int = 6): List<SearchHit> {
        val q = query.trim().lowercase()
        if (limit <= 0) return emptyList()
        // Nothing typed yet: offer the first few actions so the Actions wheel position isn't empty.
        return offered
            .map { it to (if (q.isEmpty()) 0.1f else score(it, q)) }
            .filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<CornerActionId, Float>> { it.second }.thenBy { it.first.ordinal })
            .take(limit)
            .map { (a, s) ->
                SearchHit(
                    id = "action:${a.name}",
                    category = SearchCategory.Actions,
                    title = a.label,
                    subtitle = verbs[a],
                    kind = HitKind.Action,
                    actionId = a,
                    score = s,
                )
            }
    }
}
