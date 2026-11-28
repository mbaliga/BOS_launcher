package dev.aarso.search.palette

/** One system settings screen. [action] is a public `android.settings.*` intent action (no hidden APIs). */
data class SettingsEntry(val id: String, val title: String, val keywords: List<String>, val action: String, val section: String)

/** Bundled index of system settings screens (PAL-04). Pure data; the Android layer decides which resolve. */
object SettingsIndex {
    private fun e(id: String, title: String, action: String, section: String, vararg kw: String) =
        SettingsEntry(id, title, kw.toList(), "android.settings.$action", section)

    val all: List<SettingsEntry> = listOf(
        e("settings", "Settings", "SETTINGS", "System", "system", "preferences"),
        e("wifi", "Wi-Fi", "WIFI_SETTINGS", "Network", "wifi", "wireless", "network", "internet", "hotspot"),
        e("bluetooth", "Bluetooth", "BLUETOOTH_SETTINGS", "Network", "pair", "headphones", "earbuds"),
        e("airplane", "Airplane mode", "AIRPLANE_MODE_SETTINGS", "Network", "flight", "offline"),
        e("network", "Network and wireless", "WIRELESS_SETTINGS", "Network", "mobile", "cellular", "sim"),
        e("roaming", "Mobile data and roaming", "DATA_ROAMING_SETTINGS", "Network", "data", "cellular", "roaming"),
        e("vpn", "VPN", "VPN_SETTINGS", "Network", "private network", "tunnel"),
        e("nfc", "NFC", "NFC_SETTINGS", "Network", "tap to pay", "contactless"),
        e("cast", "Cast", "CAST_SETTINGS", "Network", "screen mirroring", "chromecast", "display out"),
        e("display", "Display", "DISPLAY_SETTINGS", "Device", "brightness", "screen", "dark mode", "font size", "timeout", "rotation"),
        e("night", "Night light", "NIGHT_DISPLAY_SETTINGS", "Device", "blue light", "warm", "eye comfort"),
        e("sound", "Sound", "SOUND_SETTINGS", "Device", "volume", "ringtone", "vibration", "silent", "ringer"),
        e("battery", "Battery saver", "BATTERY_SAVER_SETTINGS", "Device", "power", "battery", "low power"),
        e("battopt", "Battery optimisation", "IGNORE_BATTERY_OPTIMIZATION_SETTINGS", "Device", "background", "doze", "unrestricted"),
        e("storage", "Storage", "INTERNAL_STORAGE_SETTINGS", "Device", "space", "memory", "free up", "disk"),
        e("apps", "Apps", "APPLICATION_SETTINGS", "Apps", "applications", "installed"),
        e("allapps", "All apps", "MANAGE_ALL_APPLICATIONS_SETTINGS", "Apps", "manage apps", "force stop", "clear cache"),
        e("defaultapps", "Default apps", "MANAGE_DEFAULT_APPS_SETTINGS", "Apps", "default browser", "default launcher", "home app"),
        e("home", "Default home app", "HOME_SETTINGS", "Apps", "launcher", "home screen"),
        e("notifications", "Notification access", "ACTION_NOTIFICATION_LISTENER_SETTINGS", "Apps", "notification listener", "alerts"),
        e("dnd", "Do not disturb access", "NOTIFICATION_POLICY_ACCESS_SETTINGS", "Apps", "dnd", "focus", "interruptions"),
        e("usage", "Usage access", "USAGE_ACCESS_SETTINGS", "Apps", "screen time", "app usage"),
        e("overlay", "Display over other apps", "action.MANAGE_OVERLAY_PERMISSION", "Apps", "draw over", "overlay"),
        e("alarms", "Alarms and reminders", "REQUEST_SCHEDULE_EXACT_ALARM", "Apps", "exact alarm", "schedule"),
        e("location", "Location", "LOCATION_SOURCE_SETTINGS", "Privacy", "gps", "find my device"),
        e("privacy", "Privacy", "PRIVACY_SETTINGS", "Privacy", "permissions", "ads"),
        e("security", "Security", "SECURITY_SETTINGS", "Privacy", "lock", "screen lock", "pin", "password", "encryption"),
        e("biometric", "Fingerprint and face", "BIOMETRIC_ENROLL", "Privacy", "fingerprint", "face unlock", "biometrics"),
        e("accounts", "Add account", "ADD_ACCOUNT_SETTINGS", "Accounts", "sign in", "google account", "email account"),
        e("sync", "Sync", "SYNC_SETTINGS", "Accounts", "backup", "auto sync"),
        e("accessibility", "Accessibility", "ACCESSIBILITY_SETTINGS", "System", "talkback", "magnification", "contrast", "captions"),
        e("date", "Date and time", "DATE_SETTINGS", "System", "clock", "timezone", "time zone", "24 hour"),
        e("language", "Language", "LOCALE_SETTINGS", "System", "locale", "region", "translate"),
        e("keyboard", "Keyboard", "INPUT_METHOD_SETTINGS", "System", "input method", "typing", "ime"),
        e("hwkeyboard", "Physical keyboard", "HARD_KEYBOARD_SETTINGS", "System", "external keyboard", "shortcuts"),
        e("dictionary", "Personal dictionary", "USER_DICTIONARY_SETTINGS", "System", "words", "autocorrect"),
        e("print", "Printing", "ACTION_PRINT_SETTINGS", "System", "printer"),
        e("developer", "Developer options", "APPLICATION_DEVELOPMENT_SETTINGS", "System", "usb debugging", "adb", "debug"),
        e("about", "About phone", "DEVICE_INFO_SETTINGS", "System", "device info", "android version", "build number", "model"),
    )

    /** Entries matching [query], best first, keeping only those for which [resolves] is true (no handler: hidden). */
    fun match(query: String, resolves: (SettingsEntry) -> Boolean = { true }, limit: Int = 8): List<Pair<SettingsEntry, Double>> {
        val q = TextMatch.norm(query)
        if (q.isEmpty()) return emptyList()
        return all.mapNotNull { e ->
            var s = TextMatch.score(q, TextMatch.Target(e.title))
            for (k in e.keywords) s = maxOf(s, TextMatch.score(q, TextMatch.Target(k)) * 0.85)
            if (s > 0 && resolves(e)) e to s else null
        }.sortedWith(compareByDescending<Pair<SettingsEntry, Double>> { it.second }.thenBy { it.first.title }).take(limit)
    }
}

class SettingsProvider(private val resolves: (SettingsEntry) -> Boolean) : PaletteProvider {
    override val id = "settings"
    override val scope = PaletteScope.Settings

    override suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult> =
        SettingsIndex.match(q.text, resolves, limit).map { (e, s) ->
            PaletteResult(
                id = "settings:${e.id}", provider = id, scope = scope, title = e.title, subtitle = e.section,
                // Settings sit just under apps of equal match quality.
                score = s * 0.95, actions = listOf(ResultAction(ResultActionId.Open)),
                payload = mapOf("intentAction" to e.action),
            )
        }
}
