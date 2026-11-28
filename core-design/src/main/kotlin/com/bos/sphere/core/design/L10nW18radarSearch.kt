package com.bos.sphere.core.design

/** Wave 18 (Radar search cleanup, input at the top, Orbit rings by use; PAL-06, PAL-19, PAL-20): the ring tier names and the quiet empty-history hint, in all eight languages. */
internal val L10nW18radarSearchEnglish: Set<String> = setOf(
    "Often used",
    "Sometimes",
    "Rarely",
    "Rings fill up as you use Radar",
    "The web row hands your search to this engine; nothing is sent until you tap it",
)

internal val L10nW18radarSearch: Map<String, Map<String, String>> = mapOf(
    "es" to mapOf(
        "Often used" to "Usado a menudo",
        "Sometimes" to "A veces",
        "Rarely" to "Rara vez",
        "Rings fill up as you use Radar" to "Los anillos se llenan a medida que usas Radar",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "La fila web pasa tu búsqueda a este motor; no se envía nada hasta que la toques",
    ),
    "fr" to mapOf(
        "Often used" to "Souvent utilisé",
        "Sometimes" to "Parfois",
        "Rarely" to "Rarement",
        "Rings fill up as you use Radar" to "Les anneaux se remplissent au fil de votre usage de Radar",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "La ligne web transmet votre recherche à ce moteur ; rien n’est envoyé avant que vous la touchiez",
    ),
    "de" to mapOf(
        "Often used" to "Oft genutzt",
        "Sometimes" to "Manchmal",
        "Rarely" to "Selten",
        "Rings fill up as you use Radar" to "Die Ringe füllen sich, während du Radar benutzt",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "Die Web-Zeile übergibt deine Suche an diese Suchmaschine; nichts wird gesendet, bevor du tippst",
    ),
    "pt" to mapOf(
        "Often used" to "Usado com frequência",
        "Sometimes" to "Às vezes",
        "Rarely" to "Raramente",
        "Rings fill up as you use Radar" to "Os anéis se enchem conforme você usa o Radar",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "A linha da web envia sua pesquisa a este mecanismo; nada é enviado até você tocar",
    ),
    "hi" to mapOf(
        "Often used" to "अक्सर इस्तेमाल",
        "Sometimes" to "कभी-कभी",
        "Rarely" to "कभी-कभार",
        "Rings fill up as you use Radar" to "Radar इस्तेमाल करने पर रिंग भरते जाते हैं",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "वेब पंक्ति आपकी खोज इसी इंजन को सौंपती है; आपके छूने तक कुछ नहीं भेजा जाता",
    ),
    "zh" to mapOf(
        "Often used" to "常用",
        "Sometimes" to "偶尔",
        "Rarely" to "很少",
        "Rings fill up as you use Radar" to "随着你使用 Radar，圆环会逐渐填满",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "网页行会把你的搜索交给此搜索引擎；在你点按之前不会发送任何内容",
    ),
    "ja" to mapOf(
        "Often used" to "よく使う",
        "Sometimes" to "ときどき",
        "Rarely" to "めったに使わない",
        "Rings fill up as you use Radar" to "Radar を使うほどリングが埋まっていきます",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "Web の行は検索をこのエンジンに渡します。タップするまで何も送信されません",
    ),
    "ar" to mapOf(
        "Often used" to "مستخدم كثيرًا",
        "Sometimes" to "أحيانًا",
        "Rarely" to "نادرًا",
        "Rings fill up as you use Radar" to "تمتلئ الحلقات كلما استخدمت Radar",
        "The web row hands your search to this engine; nothing is sent until you tap it" to "يسلّم صف الويب بحثك إلى هذا المحرك؛ ولا يُرسل شيء حتى تنقر عليه",
    ),
)
