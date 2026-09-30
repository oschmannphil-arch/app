package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate

/**
 * Merkt sich, welche Stunden beim letzten Abruf schon ausgefallen waren, damit die App
 * NEUEN Entfall von bereits bekanntem unterscheiden kann. Nur für neuen Entfall soll es
 * eine Benachrichtigung geben.
 *
 * Wichtig: Beim allerersten Abruf für ein Datum wird der Stand nur gespeichert und nichts
 * gemeldet – sonst käme direkt nach dem Einrichten eine Flut von Meldungen für Entfälle,
 * die längst bekannt sind.
 */
class EntfallTracker(context: Context) {

    private val prefs = context.getSharedPreferences("entfall_stand", Context.MODE_PRIVATE)

    /**
     * Vergleicht die aktuellen Entfälle eines Tages mit dem gespeicherten Stand.
     *
     * @return die Stunden, die seit dem letzten Abruf NEU ausgefallen sind.
     */
    fun neueEntfaelle(datum: LocalDate, stunden: List<Lesson>): List<Lesson> = synchronized(SPERRE) {
        // Gesperrt über alle Instanzen: Laufen zwei Hintergrund-Abrufe gleichzeitig, sähen
        // sonst beide denselben alten Stand und meldeten denselben Entfall doppelt.
        val key = KEY_PREFIX + datum
        val aktuell = stunden.filter { it.entfaellt }
        val aktuelleIds = aktuell.map { it.kennung() }.toSet()

        val bekannt = prefs.getStringSet(key, null)

        // Stand immer aktualisieren (auch wenn nichts gemeldet wird).
        prefs.edit().putStringSet(key, aktuelleIds).apply()

        // Erster Abruf für diesen Tag: nur merken, nicht melden.
        if (bekannt == null) return@synchronized emptyList()

        val neueIds = aktuelleIds - bekannt
        aktuell.filter { it.kennung() in neueIds }
    }

    /** Entfernt Einträge für vergangene Tage, damit die Preferences nicht zulaufen. */
    fun aufraeumen(heute: LocalDate = LocalDate.now()) {
        val zuLoeschen = prefs.all.keys.filter { key ->
            if (!key.startsWith(KEY_PREFIX)) return@filter false
            val datum = runCatching { LocalDate.parse(key.removePrefix(KEY_PREFIX)) }.getOrNull()
            datum == null || datum.isBefore(heute)
        }
        if (zuLoeschen.isEmpty()) return
        prefs.edit().apply { zuLoeschen.forEach { remove(it) } }.apply()
    }

    /** Nach einer Kursänderung ist der alte Stand wertlos. */
    fun zuruecksetzen() {
        prefs.edit().clear().apply()
    }

    /** Stabile Kennung einer Stunde innerhalb eines Tages. */
    private fun Lesson.kennung(): String =
        listOf(klasse, stunde.toString(), kursKuerzel.orEmpty(), unterrichtsNr.orEmpty())
            .joinToString("|")

    companion object {
        private const val KEY_PREFIX = "entfall_"
        private val SPERRE = Any()
    }
}
