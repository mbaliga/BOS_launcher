package com.bos.sphere.core.design

/** Wave 23 (a11yPolish; NFR-13): the three states the Radar mic announces as its state description, and the Launchpad's pane title (a product name, the same in every language). */
internal val L10nW23a11yPolishEnglish: Set<String> = setOf(
    "Ready",
    "Previous",
    "Previous %s",
    "Next %s",
    "Launchpad",
    "Listening",
    "Unavailable, microphone permission is off",
    "Page options",
    "Home page",
    "Page %s",
    "Page %s of %s",
)

internal val L10nW23a11yPolish: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf("Previous" to "Anterior", "Previous %s" to "Anterior: %s", "Next %s" to "Siguiente: %s", "Launchpad" to "Launchpad", "Ready" to "Listo", "Listening" to "Escuchando", "Unavailable, microphone permission is off" to "No disponible, el permiso del micrófono está desactivado", "Page options" to "Opciones de página", "Home page" to "Página de inicio", "Page %s" to "Página %s", "Page %s of %s" to "Página %s de %s"),
    "fr" to mapOf("Previous" to "Précédent", "Previous %s" to "%s, précédent", "Next %s" to "%s, suivant", "Launchpad" to "Launchpad", "Ready" to "Prêt", "Listening" to "À l'écoute", "Unavailable, microphone permission is off" to "Indisponible, l'autorisation du micro est désactivée", "Page options" to "Options de la page", "Home page" to "Page d'accueil", "Page %s" to "Page n° %s", "Page %s of %s" to "Page %s sur %s"),
    "de" to mapOf("Previous" to "Zurück", "Previous %s" to "%s, vorheriger Wert", "Next %s" to "%s, nächster Wert", "Launchpad" to "Launchpad", "Ready" to "Bereit", "Listening" to "Hört zu", "Unavailable, microphone permission is off" to "Nicht verfügbar, die Mikrofonberechtigung ist aus", "Page options" to "Seitenoptionen", "Home page" to "Startseite", "Page %s" to "Seite %s", "Page %s of %s" to "Seite %s von %s"),
    "pt" to mapOf("Previous" to "Anterior", "Previous %s" to "%s, anterior", "Next %s" to "%s, próximo", "Launchpad" to "Launchpad", "Ready" to "Pronto", "Listening" to "Ouvindo", "Unavailable, microphone permission is off" to "Indisponível, a permissão do microfone está desativada", "Page options" to "Opções da página", "Home page" to "Página inicial", "Page %s" to "Página %s", "Page %s of %s" to "Página %s de %s"),
    "hi" to mapOf("Previous" to "पिछला", "Previous %s" to "%s, पिछला", "Next %s" to "%s, अगला", "Launchpad" to "Launchpad", "Ready" to "तैयार", "Listening" to "सुन रहा है", "Unavailable, microphone permission is off" to "उपलब्ध नहीं, माइक्रोफ़ोन की अनुमति बंद है", "Page options" to "पेज विकल्प", "Home page" to "होम पेज", "Page %s" to "पेज %s", "Page %s of %s" to "पेज %s में से %s"),
    "zh" to mapOf("Previous" to "上一首", "Previous %s" to "%s，上一个", "Next %s" to "%s，下一个", "Launchpad" to "Launchpad", "Ready" to "就绪", "Listening" to "正在聆听", "Unavailable, microphone permission is off" to "不可用，麦克风权限已关闭", "Page options" to "页面选项", "Home page" to "主页", "Page %s" to "第 %s 页", "Page %s of %s" to "第 %s 页，共 %s 页"),
    "ja" to mapOf("Previous" to "前へ", "Previous %s" to "%s、前へ", "Next %s" to "%s、次へ", "Launchpad" to "Launchpad", "Ready" to "準備完了", "Listening" to "聞いています", "Unavailable, microphone permission is off" to "利用できません。マイクの権限がオフです", "Page options" to "ページのオプション", "Home page" to "ホームページ", "Page %s" to "%s ページ目", "Page %s of %s" to "%s / %s ページ"),
    "ar" to mapOf("Previous" to "السابق", "Previous %s" to "%s، السابق", "Next %s" to "%s، التالي", "Launchpad" to "Launchpad", "Ready" to "جاهز", "Listening" to "يستمع", "Unavailable, microphone permission is off" to "غير متاح، إذن الميكروفون متوقف", "Page options" to "خيارات الصفحة", "Home page" to "الصفحة الرئيسية", "Page %s" to "الصفحة %s", "Page %s of %s" to "الصفحة %s من %s"),
)
