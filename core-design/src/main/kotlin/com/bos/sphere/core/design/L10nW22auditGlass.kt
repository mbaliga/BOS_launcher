package com.bos.sphere.core.design

/**
 * Wave 22 (auditGlass; INP-13): what the push-to-talk control says when the microphone permission was refused, and the two channel names the
 * contact buttons now announce through the translator (product names: the same in every language).
 */
internal val L10nW22auditGlassEnglish: Set<String> = setOf(
    "Microphone is off. Allow it in Settings to speak to search.",
    "WhatsApp",
    "Telegram",
)

internal val L10nW22auditGlass: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "El micrófono está desactivado. Permítelo en Ajustes para buscar con la voz.", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "fr" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "Le micro est désactivé. Autorisez-le dans les Réglages pour rechercher à la voix.", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "de" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "Das Mikrofon ist aus. Erlaube es in den Einstellungen, um per Sprache zu suchen.", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "pt" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "O microfone está desativado. Permita-o nas Configurações para pesquisar por voz.", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "hi" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "माइक्रोफ़ोन बंद है। बोलकर खोजने के लिए सेटिंग में अनुमति दें।", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "zh" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "麦克风已关闭。请在设置中允许后再语音搜索。", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "ja" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "マイクがオフです。話して検索するには、設定で許可してください。", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
    "ar" to mapOf("Microphone is off. Allow it in Settings to speak to search." to "الميكروفون متوقف. اسمح به في الإعدادات لتتمكن من البحث بالصوت.", "WhatsApp" to "WhatsApp", "Telegram" to "Telegram"),
)
