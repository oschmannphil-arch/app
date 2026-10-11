package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate

/**
 * Stand der Änderungen eines Tages über Kalendertage hinweg: [basis] ist der Stand vom letzten
 * Tag davor (null = noch kein Vergleich möglich), [neu] der zuletzt gesehene Stand von [neuTag].
 */
data class VerlaufsStand(val basis: Set<String>?, val neu: Set<String>, val neuTag: LocalDate) {

    companion object {
        /**
         * Schreibt den Stand fort: Ein neuer Kalendertag macht den zuletzt gesehenen Stand zur Basis,
         * am selben Tag wird nur der aktuelle Stand ersetzt.
         */
        fun fortschreiben(alt: VerlaufsStand?, aktuell: Set<String>, heute: LocalDate): VerlaufsStand = when {
            alt == null -> VerlaufsStand(null, aktuell, heute)
            alt.neuTag.isBefore(heute) -> VerlaufsStand(alt.neu, aktuell, heute)
            else -> alt.copy(neu = aktuell)
        }
    }
}

/**
 * Merkt sich je Schultag, welche Änderungen (Ausfall, Vertretung, Raum) schon an einem früheren
 * Kalendertag bekannt waren – daraus ergibt sich "seit gestern geändert". Erst ab dem zweiten
 * Tag, an dem die App den Plan eines Tages gesehen hat, gibt es etwas zu vergleichen.
 */
class AenderungsVerlauf(context: Context) {

    private val prefs = context.getSharedPreferences("aenderungs_verlauf", Context.MODE_PRIVATE)

    /** Speichert den aktuellen Stand und liefert die Änderungen, die gegenüber früher neu sind. */
    fun vergleiche(datum: LocalDate, aktuell: List<Aenderung>, heute: LocalDate = LocalDate.now()): List<Aenderung> =
        synchronized(SPERRE) {
            val keys = aktuell.mapTo(HashSet()) { it.schluessel }
            val alt = lesen(datum)
            val stand = VerlaufsStand.fortschreiben(alt, keys, heute)
            if (stand != alt) schreiben(datum, stand)
            val basis = stand.basis ?: return emptyList()
            aktuell.filter { it.schluessel !in basis }
        }

    private fun lesen(datum: LocalDate): VerlaufsStand? {
        val tag = prefs.getString(TAG + datum, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val neu = prefs.getStringSet(NEU + datum, null)?.toSet() ?: return null
        return VerlaufsStand(prefs.getStringSet(BASIS + datum, null)?.toSet(), neu, tag)
    }

    private fun schreiben(datum: LocalDate, s: VerlaufsStand) {
        prefs.edit().apply {
            putString(TAG + datum, s.neuTag.toString())
            putStringSet(NEU + datum, HashSet(s.neu))
            if (s.basis != null) putStringSet(BASIS + datum, HashSet(s.basis)) else remove(BASIS + datum)
        }.apply()
    }

    /** Entfernt Einträge für vergangene Tage. */
    fun aufraeumen(heute: LocalDate = LocalDate.now()) {
        val alle = prefs.all.keys
        val zuLoeschen = alle.filter { key ->
            val datum = key.substringAfter('_', "")
            runCatching { LocalDate.parse(datum) }.getOrNull()?.isBefore(heute) ?: true
        }
        if (zuLoeschen.isNotEmpty()) prefs.edit().apply { zuLoeschen.forEach { remove(it) } }.apply()
    }

    /** Nach einer Kursänderung ist der Vergleich wertlos. */
    fun zuruecksetzen() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "tag_"
        const val NEU = "neu_"
        const val BASIS = "basis_"
        val SPERRE = Any()
    }
}
