package com.nextlesson.app.data

/**
 * Oberstufenkurse heißen oft "GEO2" oder "Eng1", das normale Fach "Geo" – gemeint ist dasselbe Fach.
 * Für Gruppieren, Farben und Listen zählt darum nur das erste Wort aus Buchstaben, klein geschrieben.
 */
object FachName {

    private val WORT = Regex("[a-zäöüß]+")

    /** "GEO2" → "geo", "Geo" → "geo", "spo2_3JU" → "spo". */
    fun schluessel(fach: String): String = WORT.find(fach.lowercase())?.value ?: fach.trim().lowercase()

    /**
     * Fasst Schreibweisen desselben Fachs zusammen. Angezeigt wird die Variante ohne Ziffern ("Geo"),
     * sonst die erste. Leere Namen und Namen ohne Buchstaben fallen weg; sortiert nach Name.
     */
    fun zusammen(namen: Collection<String>): List<String> =
        namen.map { it.trim() }
            .filter { n -> n.any { it.isLetter() } }
            .groupBy { schluessel(it) }
            .values
            .map { gruppe -> gruppe.firstOrNull { n -> n.none { it.isDigit() } } ?: gruppe.first() }
            .sortedBy { it.lowercase() }
}

/** Eigene Anzeigenamen der Fächer (Einstellungen → Eigene Fachnamen) – auch für Widget und Meldungen. */
object EigeneFachNamen {

    /** Fach-Schlüssel → eigener Name, so wie [DesignStore] sie speichert. */
    fun laden(context: android.content.Context): Map<String, String> =
        (context.applicationContext.getSharedPreferences("design", android.content.Context.MODE_PRIVATE)
            .getStringSet("fach_namen", emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.indexOf('=')
            if (i > 0 && i < s.length - 1) s.substring(0, i) to s.substring(i + 1) else null
        }.toMap()

    /** Der eigene Name für [fach], sonst [fach] selbst. */
    fun anwenden(namen: Map<String, String>, fach: String): String = namen[FachName.schluessel(fach)] ?: fach
}

/** Farbton-Umrechnung für die Farbthemen (rein rechnerisch, damit testbar). */
object Farbton {

    /** Grundton der warmen (braunen) bzw. kalten (schieferblauen) Farben des Mocha-Designs. */
    const val WARM_BASIS = 28f
    const val KALT_BASIS = 197f

    /**
     * Setzt die Farbfamilie auf einen neuen Ton, behält aber die kleinen Unterschiede innerhalb der
     * Familie. Warm = Rot bis Gelb (bis 100°, ab 330°), alles andere kalt.
     */
    fun neu(farbton: Float, warmZiel: Float, kaltZiel: Float): Float {
        val warm = farbton <= 100f || farbton >= 330f
        val ergebnis = if (warm) {
            val abstand = ((farbton - WARM_BASIS + 540f) % 360f) - 180f
            warmZiel + abstand
        } else {
            kaltZiel + (farbton - KALT_BASIS)
        }
        return ((ergebnis % 360f) + 360f) % 360f
    }
}

/** Vorschläge fürs Fach-Feld: Fächer, die zur Eingabe passen – erst die, die so anfangen, dann die übrigen Treffer. */
object FachVorschlaege {

    fun filtern(alle: List<String>, eingabe: String, max: Int = 8): List<String> {
        val e = eingabe.trim()
        if (e.isEmpty()) return emptyList()
        val passend = alle.filter { it.contains(e, ignoreCase = true) && !it.equals(e, ignoreCase = true) }
        val (anfang, rest) = passend.partition { it.startsWith(e, ignoreCase = true) }
        return (anfang.sortedBy { it.lowercase() } + rest.sortedBy { it.lowercase() }).take(max)
    }
}
