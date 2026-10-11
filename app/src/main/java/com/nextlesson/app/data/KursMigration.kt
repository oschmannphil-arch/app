package com.nextlesson.app.data

/**
 * Prüft die gewählten Kurse gegen die aktuelle Kursliste der Schule (z. B. nach dem Halbjahreswechsel):
 * Kurse, die es nicht mehr gibt, werden gemeldet; steht ein eindeutiger Nachfolger bereit
 * (gleiche Buchstaben im Kürzel, gleiche Klasse bzw. nächster Jahrgang), wird er vorgeschlagen.
 */
object KursMigration {

    data class Ergebnis(
        /** Alte Kurs-ID → neue Kurs-ID, wenn es genau einen passenden Nachfolger gibt. */
        val ersetzt: Map<String, String>,
        /** Kurse, die es nicht mehr gibt und für die kein eindeutiger Nachfolger gefunden wurde. */
        val fehlend: List<String>
    ) {
        val hatAenderung: Boolean get() = ersetzt.isNotEmpty() || fehlend.isNotEmpty()

        /** Die Auswahl nach Anwendung der Nachfolger, ohne die fehlenden. */
        fun neueAuswahl(gewaehlt: Set<String>): Set<String> =
            gewaehlt.filter { it !in ersetzt && it !in fehlend }.toSet() + ersetzt.values

        /** Nur die Nachfolger einsetzen; fehlende Kurse bleiben stehen, bis man sie selbst abwählt. */
        fun mitNachfolgern(gewaehlt: Set<String>): Set<String> =
            gewaehlt.filter { it !in ersetzt }.toSet() + ersetzt.values
    }

    private fun klasse(id: String) = id.substringBefore("::")
    private fun kuerzel(id: String) = id.substringAfter("::", "")
    private fun buchstaben(kuerzel: String) = kuerzel.filter { !it.isDigit() }.lowercase()

    fun pruefen(gewaehlt: Set<String>, verfuegbar: List<KursInfo>): Ergebnis {
        if (verfuegbar.isEmpty()) return Ergebnis(emptyMap(), emptyList())
        val ids = verfuegbar.map { it.id }.toSet()
        val klassen = verfuegbar.map { it.klasse }.toSet()
        val ersetzt = LinkedHashMap<String, String>()
        val fehlend = ArrayList<String>()
        for (alt in gewaehlt.sorted()) {
            if (alt in ids) continue
            // Ganze Klasse oder eine Klasse, die in der Liste gar nicht vorkommt: nichts Sicheres zu sagen.
            if (kuerzel(alt) == KursInfo.GANZE_KLASSE || klasse(alt) !in klassen) continue
            val kandidaten = verfuegbar.filter { k ->
                !k.istGanzeKlasse && k.id !in gewaehlt &&
                    buchstaben(k.kuerzel) == buchstaben(kuerzel(alt)) &&
                    buchstaben(k.kuerzel).isNotEmpty() &&
                    k.klasse == klasse(alt)
            }
            if (kandidaten.size == 1) ersetzt[alt] = kandidaten.first().id else fehlend += alt
        }
        return Ergebnis(ersetzt, fehlend)
    }
}
