package com.bos.sphere.core.design

/**
 * Wave 37 (specSync): the Connectivity sheet spells its labels as the owner's mock-up does, "WiFi" for the row and "Share Wifi" for the share
 * button (the rest of the app keeps "Wi-Fi"). "Share Wi-Fi" of wave 34 is no longer shown.
 */
internal val L10nW37specSyncEnglish: Set<String> = setOf(
    "WiFi",
    "Share Wifi",
)

internal val L10nW37specSync: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf("WiFi" to "WiFi", "Share Wifi" to "Compartir WiFi"),
    "fr" to mapOf("WiFi" to "WiFi", "Share Wifi" to "Partager le WiFi"),
    "de" to mapOf("WiFi" to "WLAN", "Share Wifi" to "WLAN teilen"),
    "pt" to mapOf("WiFi" to "WiFi", "Share Wifi" to "Partilhar WiFi"),
    "hi" to mapOf("WiFi" to "वाईफ़ाई", "Share Wifi" to "वाईफ़ाई साझा करें"),
    "zh" to mapOf("WiFi" to "WiFi", "Share Wifi" to "分享 WiFi"),
    "ja" to mapOf("WiFi" to "WiFi", "Share Wifi" to "WiFi を共有"),
    "ar" to mapOf("WiFi" to "واي فاي", "Share Wifi" to "مشاركة واي فاي"),
)
