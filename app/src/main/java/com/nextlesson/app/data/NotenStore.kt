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

    private val _klausurAnteil = MutableStateFlow(
        prefs.getInt(KEY_ANTEIL, START_ANTEIL).coerceIn(Noten.ANTEIL_MIN, Noten.ANTEIL_MAX)
    )
    val klausurAnteil: StateFlow<Int> = _klausurAnteil.asStateFlow()

    fun anteilSetzen(prozent: Int) {
        val wert = prozent.coerceIn(Noten.ANTEIL_MIN, Noten.ANTEIL_MAX)
        _klausurAnteil.value = wert
        prefs.edit().putInt(KEY_ANTEIL, wert).apply()
    }

    fun hinzufuegen(fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) {
        if (fach.isBlank()) return
        aendern { it + Note(fach = fach.trim(), punkte = punkte.coerceIn(0, Noten.MAX), art = art, datumEpochDay = datum.toEpochDay(), notiz = notiz.trim()) }
    }

    fun bearbeiten(id: String, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) {
        if (fach.isBlank()) return
        aendern { liste ->
            liste.map {
                if (it.id != id) it
                else it.copy(fach = fach.trim(), punkte = punkte.coerceIn(0, Noten.MAX), art = art, datumEpochDay = datum.toEpochDay(), notiz = notiz.trim())
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
                Note(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    fach = o.optString("fach"),
                    punkte = o.optInt("punkte", 0).coerceIn(0, Noten.MAX),
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
        const val START_ANTEIL = 40
        val SPERRE = Any()
    }
}
