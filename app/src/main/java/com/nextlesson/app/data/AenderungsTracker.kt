package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate

/**
 * Merkt sich je Tag, welche Änderungen (Ausfall, Vertretung, Raumänderung) beim letzten Abruf
 * schon bekannt waren, damit die App NEUE Änderungen von bekannten unterscheiden kann. Nur
 * neue sollen eine Benachrichtigung auslösen.
 *
 * Gespeichert wird immer der vollständige Stand – auch für Arten, deren Meldung abgeschaltet
 * ist. So kommt beim späteren Einschalten keine Flut alter Meldungen.
 */
class AenderungsTracker(context: Context) {

    // Dateiname aus der Zeit, als nur Ausfälle verfolgt wurden – bewusst beibehalten.
    private val prefs = context.getSharedPreferences("entfall_stand", Context.MODE_PRIVATE)

    /**
     * Vergleicht die aktuellen Änderungen eines Tages mit dem gespeicherten Stand und speichert
     * den neuen Stand.
     *
     * @return die Änderungen, die seit dem letzten Abruf NEU sind (beim ersten Abruf: keine).
     */
    fun neueAenderungen(datum: LocalDate, aktuell: List<Aenderung>): List<Aenderung> = synchronized(SPERRE) {
        // Gesperrt über alle Instanzen: Laufen zwei Hintergrund-Abrufe gleichzeitig, sähen
        // sonst beide denselben alten Stand und meldeten dieselbe Änderung doppelt.
        val key = KEY_PREFIX + datum
        val altKey = ALT_PREFIX + datum
        val bekannt = prefs.getStringSet(key, null)?.toSet()
            ?: prefs.getStringSet(altKey, null)?.let { Aenderung.ausAltemStand(it, aktuell) }

        prefs.edit()
            .putStringSet(key, aktuell.mapTo(HashSet()) { it.schluessel })
            .remove(altKey)
            .apply()

        Aenderung.neue(bekannt, aktuell)
    }

    /** Entfernt Einträge für vergangene Tage (und Unlesbares), damit der Speicher nicht zuläuft. */
    fun aufraeumen(heute: LocalDate = LocalDate.now()) {
        val zuLoeschen = prefs.all.keys.filter { key ->
            val datum = key.removePrefix(KEY_PREFIX).removePrefix(ALT_PREFIX)
            runCatching { LocalDate.parse(datum) }.getOrNull()?.isBefore(heute) ?: true
        }
        if (zuLoeschen.isEmpty()) return
        prefs.edit().apply { zuLoeschen.forEach { remove(it) } }.apply()
    }

    /** Nach einer Kursänderung ist der alte Stand wertlos. */
    fun zuruecksetzen() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PREFIX = "aenderung_"
        /** Speicherformat älterer Versionen: nur Kennungen ausgefallener Stunden. */
        private const val ALT_PREFIX = "entfall_"
        private val SPERRE = Any()
    }
}
