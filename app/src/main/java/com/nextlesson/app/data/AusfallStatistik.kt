package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate

/** Ergebnis der Ausfall-Statistik. [fehlend] = Tage, an denen die App den Plan nie gesehen hat. */
data class AusfallErgebnis(
    val tage: Int,
    val stundenGesamt: Int,
    val ausgefallen: Int,
    val kommend: Int,
    val proFach: List<Pair<String, Int>>
) {
    val anteilProzent: Int get() = if (stundenGesamt == 0) 0 else (ausgefallen * 100.0 / stundenGesamt).let { Math.round(it).toInt() }
}

/** Reine Logik: Tage kodieren und auswerten (ohne Android, damit testbar). */
object AusfallLogik {

    /**
     * Name des Fachs einer ausgefallenen Stunde: Bei Ausfall steht im Fach oft nur "---" oder nichts –
     * dann das Kurskürzel, sonst das erste Wort aus dem Hinweis ("PHY1 fällt aus" → "PHY1").
     */
    internal fun ausfallName(l: Lesson): String {
        fun hatBuchstaben(s: String?) = s != null && s.any { it.isLetter() }
        if (hatBuchstaben(l.fach)) return l.fach.trim()
        if (hatBuchstaben(l.kursKuerzel)) return l.kursKuerzel!!.trim()
        val wort = l.info.split(' ', '\n', ',', ':').firstOrNull { w -> w.any { it.isLetter() } && w.any { it.isDigit() } }
        return wort?.trim().orEmpty()
    }

    /** "n;stunde|fach,stunde|fach": n = Stunden des Tages, danach die ausgefallenen (ohne Klausuren). */
    fun kodieren(stunden: List<Lesson>): String {
        val echte = stunden.filter { it.fach.isNotBlank() || it.entfaellt }
        val gesamt = echte.distinctBy { it.stunde }.size
        val aus = echte.filter { it.entfaellt && !it.istKlausur }.distinctBy { it.stunde }
        return "$gesamt;" + aus.joinToString(",") { "${it.stunde}|${ausfallName(it).replace(',', ' ').replace('|', ' ')}" }
    }

    /** Auswertung für [von]..[bis] (einschließlich); Tage nach [heute] zählen als "kommend". */
    fun auswerten(eintraege: Map<String, String>, von: LocalDate, bis: LocalDate, heute: LocalDate): AusfallErgebnis {
        var tage = 0
        var gesamt = 0
        var ausgefallen = 0
        var kommend = 0
        val proFach = HashMap<String, Int>()
        val namen = HashMap<String, MutableList<String>>()
        for ((schluessel, wert) in eintraege) {
            val datum = runCatching { LocalDate.parse(schluessel) }.getOrNull() ?: continue
            if (datum.isBefore(von) || datum.isAfter(bis)) continue
            val teile = wert.split(';', limit = 2)
            val n = teile.getOrNull(0)?.toIntOrNull() ?: continue
            val aus = teile.getOrNull(1).orEmpty().split(',').filter { it.isNotBlank() }
            if (datum.isAfter(heute)) {
                kommend += aus.size
                continue
            }
            tage++
            gesamt += n
            ausgefallen += aus.size
            aus.forEach { e ->
                val fach = e.substringAfter('|', "").ifBlank { "Unbekannt" }
                // GEO2 und Geo zählen als dasselbe Fach.
                proFach.merge(FachName.schluessel(fach), 1, Int::plus)
                namen.getOrPut(FachName.schluessel(fach)) { mutableListOf() } += fach
            }
        }
        return AusfallErgebnis(
            tage, gesamt, ausgefallen, kommend,
            proFach.entries.sortedByDescending { it.value }.map { (schluessel, n) ->
                FachName.zusammen(namen[schluessel].orEmpty()).firstOrNull().orEmpty().ifBlank { "Unbekannt" } to n
            }
        )
    }
}

/** Merkt sich pro Tag, wie viele Stunden ausfielen – ab dem Moment, an dem die App den Plan sieht. */
class AusfallStatistik(context: Context) {

    private val prefs = context.getSharedPreferences("ausfall_statistik", Context.MODE_PRIVATE)

    /** Ersetzt den Stand für [datum] (ein später zurückgenommener Ausfall verschwindet wieder). */
    fun merken(datum: LocalDate, stunden: List<Lesson>) {
        val neu = AusfallLogik.kodieren(stunden)
        if (prefs.getString(datum.toString(), null) == neu) return
        prefs.edit().putString(datum.toString(), neu).apply()
    }

    fun auswerten(von: LocalDate, bis: LocalDate, heute: LocalDate = LocalDate.now()): AusfallErgebnis =
        AusfallLogik.auswerten(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(), von, bis, heute)

    /** Tage, die über ein Jahr zurückliegen, entfernen. */
    fun aufraeumen(heute: LocalDate = LocalDate.now()) {
        val grenze = heute.minusYears(1)
        val alt = prefs.all.keys.filter { k -> runCatching { LocalDate.parse(k).isBefore(grenze) }.getOrDefault(true) }
        if (alt.isNotEmpty()) prefs.edit().apply { alt.forEach { remove(it) } }.apply()
    }
}
