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
            runCatching { LocalDate.parse(key.removePrefix(PREFIX).removePrefix(STAND_PREFIX).substringBefore('|')) }
                .getOrNull()?.isBefore(heute) ?: true
        }
        if (alt.isNotEmpty()) prefs.edit().apply { alt.forEach { remove(it) } }.apply()
    }

    /**
     * Hat sich der Plan dieses Tages (Zeitstempel des Plans) und die Auswahl an Kursen und
     * Freunden seit dem letzten Mal nicht geändert? Dann muss nichts neu gerechnet werden.
     * Merkt sich [stand] dabei gleich. Ein leerer Zeitstempel zählt nie als gleich.
     */
    fun tagUnveraendert(datum: LocalDate, stand: String): Boolean = synchronized(SPERRE) {
        val key = "$STAND_PREFIX$datum"
        val gleich = stand.isNotBlank() && prefs.getString(key, null) == stand
        if (!gleich) prefs.edit().putString(key, stand).apply()
        gleich
    }

    private companion object {
        const val PREFIX = "frei_"
        const val STAND_PREFIX = "stand_"
        val SPERRE = Any()
    }
}
