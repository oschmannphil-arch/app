package com.nextlesson.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Speichert die Noten und die Klausur-Gewichtung dauerhaft auf dem Gerät (nur lokal). */
class NotenStore(context: Context) {

    private val prefs = context.getSharedPreferences("noten", Context.MODE_PRIVATE)

    private val _noten = MutableStateFlow(lesen())
    val noten: StateFlow<List<Note>> = _noten.asStateFlow()

    // Gewicht der Klausuren: getrennt für Punkte (Klasse 11–12) und Noten (Klasse 5–10).
    private val _anteile = MutableStateFlow(NotenSystem.entries.associateWith { anteilLesen(it) })
    val klausurAnteile: StateFlow<Map<NotenSystem, Int>> = _anteile.asStateFlow()

    fun anteilSetzen(system: NotenSystem, prozent: Int) {
        val wert = prozent.coerceIn(Noten.ANTEIL_MIN, Noten.ANTEIL_MAX)
        _anteile.value = _anteile.value + (system to wert)
        prefs.edit().putInt(anteilKey(system), wert).apply()
    }

    private fun anteilKey(system: NotenSystem) =
        if (system == NotenSystem.PUNKTE) KEY_ANTEIL else KEY_ANTEIL_NOTEN

    private fun anteilLesen(system: NotenSystem): Int =
        prefs.getInt(anteilKey(system), if (system == NotenSystem.PUNKTE) START_ANTEIL else START_ANTEIL_NOTEN)
            .coerceIn(Noten.ANTEIL_MIN, Noten.ANTEIL_MAX)

    fun hinzufuegen(system: NotenSystem, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) {
        if (fach.isBlank()) return
        aendern { it + Note(system = system, fach = fach.trim(), punkte = punkte.coerceIn(system.min, system.max), art = art, datumEpochDay = datum.toEpochDay(), notiz = notiz.trim()) }
    }

    fun bearbeiten(id: String, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) {
        if (fach.isBlank()) return
        aendern { liste ->
            liste.map {
                if (it.id != id) it
                else it.copy(fach = fach.trim(), punkte = punkte.coerceIn(it.system.min, it.system.max), art = art, datumEpochDay = datum.toEpochDay(), notiz = notiz.trim())
            }
        }
    }

    fun loeschen(id: String) = aendern { liste -> liste.filterNot { it.id == id } }

    private fun aendern(umbau: (List<Note>) -> List<Note>) {
        synchronized(SPERRE) {
            val neu = umbau(_noten.value).sortedByDescending { it.datumEpochDay }
            _noten.value = neu
            schreiben(neu)
        }
    }

    private fun schreiben(liste: List<Note>) {
        val array = JSONArray()
        liste.forEach { n ->
            array.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("fach", n.fach)
                    put("punkte", n.punkte)
                    put("system", n.system.name)
                    put("art", n.art.name)
                    put("datum", n.datumEpochDay)
                    put("notiz", n.notiz)
                }
            )
        }
        prefs.edit().putString(KEY_NOTEN, array.toString()).apply()
    }

    private fun lesen(): List<Note> {
        val roh = prefs.getString(KEY_NOTEN, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(roh)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val tag = o.optLong("datum", Long.MIN_VALUE)
                if (tag == Long.MIN_VALUE || o.optString("fach").isBlank()) return@mapNotNull null
                // Ältere Einträge ohne Angabe sind Punkte.
                val system = NotenSystem.ausName(o.optString("system"))
                Note(
                    system = system,
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    fach = o.optString("fach"),
                    punkte = o.optInt("punkte", system.min).coerceIn(system.min, system.max),
                    art = NotenArt.ausName(o.optString("art")),
                    datumEpochDay = tag,
                    notiz = o.optString("notiz")
                )
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.datumEpochDay }
    }

    private companion object {
        const val KEY_NOTEN = "noten_json"
        const val KEY_ANTEIL = "klausur_anteil"
        const val KEY_ANTEIL_NOTEN = "klausur_anteil_noten"
        const val START_ANTEIL = 40
        const val START_ANTEIL_NOTEN = 50
        val SPERRE = Any()
    }
}
