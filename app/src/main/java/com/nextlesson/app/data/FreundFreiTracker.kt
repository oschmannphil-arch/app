package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate

/**
 * Merkt sich je Tag und Freund, welche gemeinsamen Freistunden beim letzten Abruf schon bekannt
 * waren – damit nur NEUE (etwa weil bei einem Freund eine Stunde ausfällt) gemeldet werden.
 */
class FreundFreiTracker(context: Context) {

    private val prefs = context.getSharedPreferences("freund_frei_stand", Context.MODE_PRIVATE)

    /**
     * Speichert den aktuellen Stand und liefert die neuen Blöcke (beim ersten Abruf: keine).
     * [stand] gehört zum Schlüssel: Ändern sich die Kurse von dir oder dem Freund, beginnt der
     * Vergleich neu, statt alles als "neu" zu melden.
     */
    fun neueBloecke(datum: LocalDate, freundId: String, stand: Int, aktuell: List<Freiblock>): List<Freiblock> =
        synchronized(SPERRE) {
            val key = "$PREFIX$datum|$freundId|$stand"
            val alt = prefs.getStringSet(key, null)?.toSet()
            prefs.edit().putStringSet(key, aktuell.mapTo(HashSet()) { FreundFreiLogik.schluessel(it) }).apply()
            FreundFreiLogik.neu(alt, aktuell)
        }

    fun aufraeumen(heute: LocalDate = LocalDate.now()) {
        val alt = prefs.all.keys.filter { key ->
            runCatching { LocalDate.parse(key.removePrefix(PREFIX).substringBefore('|')) }.getOrNull()
                ?.isBefore(heute) ?: true
        }
        if (alt.isNotEmpty()) prefs.edit().apply { alt.forEach { remove(it) } }.apply()
    }

    fun zuruecksetzen() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFIX = "frei_"
        val SPERRE = Any()
    }
}
