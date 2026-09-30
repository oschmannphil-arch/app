package com.nextlesson.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Speichert die Freunde (Name + Kurse) als JSON auf dem Gerät. */
class FreundeStore(context: Context) {

    private val prefs = context.getSharedPreferences("freunde", Context.MODE_PRIVATE)

    private val _freunde = MutableStateFlow(lesen())
    val freunde: StateFlow<List<Freund>> = _freunde.asStateFlow()

    /** Legt an oder ersetzt (gleiche [Freund.id]). */
    fun speichern(freund: Freund) {
        schreiben((_freunde.value.filterNot { it.id == freund.id } + freund).sortedBy { it.name.lowercase() })
    }

    fun loeschen(id: String) {
        schreiben(_freunde.value.filterNot { it.id == id })
    }

    private fun schreiben(liste: List<Freund>) {
        _freunde.value = liste
        val array = JSONArray()
        liste.forEach { f ->
            array.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("kurse", JSONArray(f.kurse.toList()))
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun lesen(): List<Freund> {
        val roh = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(roh)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val kurse = o.optJSONArray("kurse") ?: JSONArray()
                Freund(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    kurse = (0 until kurse.length()).mapTo(LinkedHashSet()) { kurse.getString(it) }
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "freunde_json"
    }
}
