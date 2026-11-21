package com.bos.sphere.core.design

/** Wave 11 (notification tap-to-open and the roomy Launchpad list, STR-02): strings for all eight languages, merged by [L10n.tableFor]. */
internal val L10nW11notifFixEnglish: Set<String> = setOf(
    "Couldn't open %s",
    "+%d more",
    "Show fewer",
    "Jump to %s",
)

internal val L10nW11notifFix: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf(
        "Couldn't open %s" to "No se pudo abrir %s",
        "+%d more" to "+%d más",
        "Show fewer" to "Mostrar menos",
        "Jump to %s" to "Ir a %s",
    ),
    "fr" to mapOf(
        "Couldn't open %s" to "Impossible d'ouvrir %s",
        "+%d more" to "+%d de plus",
        "Show fewer" to "Afficher moins",
        "Jump to %s" to "Aller à %s",
    ),
    "de" to mapOf(
        "Couldn't open %s" to "%s konnte nicht geöffnet werden",
        "+%d more" to "+%d weitere",
        "Show fewer" to "Weniger anzeigen",
        "Jump to %s" to "Zu %s springen",
    ),
    "pt" to mapOf(
        "Couldn't open %s" to "Não foi possível abrir %s",
        "+%d more" to "+%d mais",
        "Show fewer" to "Mostrar menos",
        "Jump to %s" to "Ir para %s",
    ),
    "hi" to mapOf(
        "Couldn't open %s" to "%s नहीं खुल सका",
        "+%d more" to "+%d और",
        "Show fewer" to "कम दिखाएँ",
        "Jump to %s" to "%s पर जाएँ",
    ),
    "zh" to mapOf(
        "Couldn't open %s" to "无法打开%s",
        "+%d more" to "另外 %d 条",
        "Show fewer" to "收起",
        "Jump to %s" to "跳到%s",
    ),
    "ja" to mapOf(
        "Couldn't open %s" to "%s を開けませんでした",
        "+%d more" to "ほか %d 件",
        "Show fewer" to "表示を減らす",
        "Jump to %s" to "%s へ移動",
    ),
    "ar" to mapOf(
        "Couldn't open %s" to "تعذّر فتح %s",
        "+%d more" to "+%d أخرى",
        "Show fewer" to "عرض أقل",
        "Jump to %s" to "الانتقال إلى %s",
    ),
)
