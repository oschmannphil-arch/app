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
