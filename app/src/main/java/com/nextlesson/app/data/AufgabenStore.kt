package com.nextlesson.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Speichert Hausaufgaben und Prüfungen dauerhaft auf dem Gerät (JSON in SharedPreferences).
 *
 * Zwei Aufräumregeln, die beim Start greifen ([beimStartAufraeumen]):
 *  - Abgehakte Hausaufgaben verschwinden beim nächsten App-Start. Bis dahin bleibt der
 *    Haken sichtbar, damit man sieht, was man gerade erledigt hat.
 *  - Prüfungen, die mehr als [AUFBEWAHRUNG_TAGE] Tage zurückliegen, werden entfernt.
 *    Alles andere – auch das Datum – bleibt unverändert erhalten.
 */
class AufgabenStore(context: Context) {

    private val prefs = context.getSharedPreferences("aufgaben", Context.MODE_PRIVATE)

    private val _hausaufgaben = MutableStateFlow<List<Hausaufgabe>>(emptyList())
    val hausaufgaben: StateFlow<List<Hausaufgabe>> = _hausaufgaben.asStateFlow()

    private val _pruefungen = MutableStateFlow<List<Pruefung>>(emptyList())
    val pruefungen: StateFlow<List<Pruefung>> = _pruefungen.asStateFlow()

    init {
        _hausaufgaben.value = hausaufgabenLesen()
        _pruefungen.value = pruefungenLesen()
    }

    // ---------- Hausaufgaben ----------

    fun hausaufgabeHinzufuegen(fach: String, text: String, faellig: LocalDate?) {
        val neu = Hausaufgabe(
            fach = fach.trim(),
            text = text.trim(),
            faelligEpochDay = faellig?.toEpochDay() ?: Hausaufgabe.KEIN_DATUM
        )
        _hausaufgaben.value = (_hausaufgaben.value + neu).sortiert()
        hausaufgabenSchreiben()
    }

    fun hausaufgabeBearbeiten(id: String, fach: String, text: String, faellig: LocalDate?) {
        _hausaufgaben.value = _hausaufgaben.value.map {
            if (it.id == id) {
                it.copy(
                    fach = fach.trim(),
                    text = text.trim(),
                    faelligEpochDay = faellig?.toEpochDay() ?: Hausaufgabe.KEIN_DATUM
                )
            } else it
        }.sortiert()
        hausaufgabenSchreiben()
    }

    fun hausaufgabeUmschalten(id: String) {
        _hausaufgaben.value = _hausaufgaben.value.map {
            if (it.id == id) it.copy(erledigt = !it.erledigt) else it
        }.sortiert()
        hausaufgabenSchreiben()
    }

    fun hausaufgabeLoeschen(id: String) {
        _hausaufgaben.value = _hausaufgaben.value.filterNot { it.id == id }
        hausaufgabenSchreiben()
    }

    fun offeneHausaufgaben(): List<Hausaufgabe> = _hausaufgaben.value.filterNot { it.erledigt }

    private fun List<Hausaufgabe>.sortiert(): List<Hausaufgabe> = sortedWith(
        compareBy(
            { it.erledigt },
            // Aufgaben ohne Datum ans Ende, sonst nach Fälligkeit.
            { if (it.faelligEpochDay <= Hausaufgabe.KEIN_DATUM) Long.MAX_VALUE else it.faelligEpochDay },
            { it.erstelltAm }
        )
    )

    // ---------- Prüfungen ----------

    fun pruefungHinzufuegen(
        fach: String,
        titel: String,
        datum: LocalDate,
        art: PruefungsArt,
        notiz: String
    ) {
        val neu = Pruefung(
            fach = fach.trim(),
            titel = titel.trim(),
            datumEpochDay = datum.toEpochDay(),
            art = art,
            notiz = notiz.trim()
        )
        _pruefungen.value = (_pruefungen.value + neu).sortedBy { it.datumEpochDay }
        pruefungenSchreiben()
    }

    fun pruefungBearbeiten(
        id: String,
        fach: String,
        titel: String,
        datum: LocalDate,
        art: PruefungsArt,
        notiz: String
    ) {
        _pruefungen.value = _pruefungen.value.map {
            if (it.id == id) {
                it.copy(
                    fach = fach.trim(),
                    titel = titel.trim(),
                    datumEpochDay = datum.toEpochDay(),
                    art = art,
                    notiz = notiz.trim()
                )
            } else it
        }.sortedBy { it.datumEpochDay }
        pruefungenSchreiben()
    }

    fun pruefungLoeschen(id: String) {
        _pruefungen.value = _pruefungen.value.filterNot { it.id == id }
        pruefungenSchreiben()
    }

    fun kommendePruefungen(heute: LocalDate = LocalDate.now()): List<Pruefung> =
        _pruefungen.value.filterNot { it.istVorbei(heute) }

    // ---------- Aufräumen ----------

    /** Wird genau einmal beim App-Start aufgerufen. */
    fun beimStartAufraeumen(heute: LocalDate = LocalDate.now()) {
        val vorherH = _hausaufgaben.value.size
        _hausaufgaben.value = _hausaufgaben.value.filterNot { it.erledigt }
        if (_hausaufgaben.value.size != vorherH) hausaufgabenSchreiben()

        val grenze = heute.minusDays(AUFBEWAHRUNG_TAGE)
        val vorherP = _pruefungen.value.size
        _pruefungen.value = _pruefungen.value.filter { it.datum.isAfter(grenze) }
        if (_pruefungen.value.size != vorherP) pruefungenSchreiben()
    }

    // ---------- Persistenz ----------

    private fun hausaufgabenSchreiben() {
        val array = JSONArray()
        _hausaufgaben.value.forEach { h ->
            array.put(
                JSONObject().apply {
                    put("id", h.id)
                    put("fach", h.fach)
                    put("text", h.text)
                    put("faellig", h.faelligEpochDay)
                    put("erledigt", h.erledigt)
                    put("erstellt", h.erstelltAm)
                }
            )
        }
        prefs.edit().putString(KEY_HAUSAUFGABEN, array.toString()).apply()
    }

    private fun hausaufgabenLesen(): List<Hausaufgabe> {
        val roh = prefs.getString(KEY_HAUSAUFGABEN, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(roh)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Hausaufgabe(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    fach = o.optString("fach"),
                    text = o.optString("text"),
                    faelligEpochDay = o.optLong("faellig", Hausaufgabe.KEIN_DATUM),
                    erledigt = o.optBoolean("erledigt", false),
                    erstelltAm = o.optLong("erstellt", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList()).sortiert()
    }

    private fun pruefungenSchreiben() {
        val array = JSONArray()
        _pruefungen.value.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("fach", p.fach)
                    put("titel", p.titel)
                    put("datum", p.datumEpochDay)
                    put("art", p.art.name)
                    put("notiz", p.notiz)
                }
            )
        }
        prefs.edit().putString(KEY_PRUEFUNGEN, array.toString()).apply()
    }

    private fun pruefungenLesen(): List<Pruefung> {
        val roh = prefs.getString(KEY_PRUEFUNGEN, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(roh)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                // Ohne gültiges Datum ist der Eintrag wertlos – lieber überspringen,
                // als ihn mit einem Ersatzdatum wie 01.01.0001 anzuzeigen.
                val tag = o.optLong("datum", Long.MIN_VALUE)
                if (tag == Long.MIN_VALUE) return@mapNotNull null
                Pruefung(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    fach = o.optString("fach"),
                    titel = o.optString("titel"),
                    datumEpochDay = tag,
                    art = PruefungsArt.ausName(o.optString("art")),
                    notiz = o.optString("notiz")
                )
            }
        }.getOrDefault(emptyList()).sortedBy { it.datumEpochDay }
    }

    companion object {
        private const val KEY_HAUSAUFGABEN = "hausaufgaben_json"
        private const val KEY_PRUEFUNGEN = "pruefungen_json"

        /** So lange bleiben vergangene Prüfungen noch sichtbar, bevor sie verschwinden. */
        private const val AUFBEWAHRUNG_TAGE = 7L
    }
}
