package com.bos.sphere.core.design

/** Wave 48 (editGaps): the history badge of Undo and Redo in the edit bar (their spoken description), and the preset refusal reuses an existing string. */
internal val L10nW48editGapsEnglish: Set<String> = setOf(
    "Undo, %d available",
    "Redo, %d available",
    "Nothing to undo",
    "Nothing to redo",
)

internal val L10nW48editGaps: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf(
        "Undo, %d available" to "Deshacer, %d disponibles",
        "Redo, %d available" to "Rehacer, %d disponibles",
        "Nothing to undo" to "Nada que deshacer",
        "Nothing to redo" to "Nada que rehacer",
    ),
    "fr" to mapOf(
        "Undo, %d available" to "Annuler, %d disponibles",
        "Redo, %d available" to "Rétablir, %d disponibles",
        "Nothing to undo" to "Rien à annuler",
        "Nothing to redo" to "Rien à rétablir",
    ),
    "de" to mapOf(
        "Undo, %d available" to "Rückgängig, %d verfügbar",
        "Redo, %d available" to "Wiederholen, %d verfügbar",
        "Nothing to undo" to "Nichts zum Rückgängigmachen",
        "Nothing to redo" to "Nichts zum Wiederholen",
    ),
    "pt" to mapOf(
        "Undo, %d available" to "Desfazer, %d disponíveis",
        "Redo, %d available" to "Refazer, %d disponíveis",
        "Nothing to undo" to "Nada para desfazer",
        "Nothing to redo" to "Nada para refazer",
    ),
    "hi" to mapOf(
        "Undo, %d available" to "पूर्ववत करें, %d उपलब्ध",
        "Redo, %d available" to "फिर से करें, %d उपलब्ध",
        "Nothing to undo" to "पूर्ववत करने के लिए कुछ नहीं",
        "Nothing to redo" to "फिर से करने के लिए कुछ नहीं",
    ),
    "zh" to mapOf(
        "Undo, %d available" to "撤销，可撤销 %d 步",
        "Redo, %d available" to "重做，可重做 %d 步",
        "Nothing to undo" to "没有可撤销的操作",
        "Nothing to redo" to "没有可重做的操作",
    ),
    "ja" to mapOf(
        "Undo, %d available" to "元に戻す（%d 件）",
        "Redo, %d available" to "やり直す（%d 件）",
        "Nothing to undo" to "元に戻せる操作はありません",
        "Nothing to redo" to "やり直せる操作はありません",
    ),
    "ar" to mapOf(
        "Undo, %d available" to "تراجع، %d متاحة",
        "Redo, %d available" to "إعادة، %d متاحة",
        "Nothing to undo" to "لا شيء للتراجع عنه",
        "Nothing to redo" to "لا شيء لإعادته",
    ),
)
