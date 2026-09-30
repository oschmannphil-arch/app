package com.nextlesson.app.data

import java.time.Duration
import java.time.LocalTime
import java.util.UUID

/** Ein Freund mit seinen Kursen (IDs wie bei der eigenen Kurswahl, z.B. "12/5::DEU1"). */
data class Freund(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kurse: Set<String>
)

/**
 * Freie Zeit im Tagesraster, z.B. 3.–4. Stunde. [von]/[bis] sind die Stundennummern (null,
 * wenn der Plan kein Zeitraster hergibt). [beginn]/[ende] schließen angrenzende Pausen ein.
 */
data class Freiblock(val von: Int?, val bis: Int?, val beginn: LocalTime, val ende: LocalTime) {
    /** "3. Std" bzw. "3.–4. Std"; null ohne Stundennummern. */
    val stundenText: String?
        get() = when {
            von == null || bis == null -> null
            von == bis -> "$von. Std"
            else -> "$von.–$bis. Std"
        }
}

/** Wann hat man frei, wann haben zwei Leute gleichzeitig frei, und welche Stunden zusammen? */
object Freizeit {

    /** Belegte Zeiten eines Tagesplans, zusammengelegt. Ausgefallene Stunden sind frei. */
    fun belegt(plan: TagesPlan): List<Pair<LocalTime, LocalTime>> =
        zusammenlegen(
            plan.stunden.filter { !it.entfaellt }.mapNotNull { l ->
                val b = l.beginn
                val e = l.ende
                if (b != null && e != null && b.isBefore(e)) b to e else null
            }
        )

    /**
     * Freistunden eines Plans im Zeitraster der Schule – auch die am Anfang des Tages
     * (Unterricht erst ab der 3. Stunde), nicht aber die nach der letzten Stunde.
     * [ausfallIstFrei]: zählen ausgefallene Stunden als frei? Für die eigene Stundenliste
     * nein – die stehen dort ohnehin als "entfällt".
     */
    fun freiBloecke(plan: TagesPlan, raster: List<Zeitfenster>, ausfallIstFrei: Boolean = false): List<Freiblock> {
        val belegend = plan.stunden.filter { !ausfallIstFrei || !it.entfaellt }
        val letzte = belegend.mapNotNull { it.ende }.maxOrNull() ?: return emptyList()
        val fenster = mitZeiten(raster)
        if (fenster.isEmpty()) {
            // Ohne Raster: nur Lücken zwischen zwei Stunden (wie bisher).
            return plan.freistunden().map { (_, von, bis) -> Freiblock(null, null, von, bis) }
        }
        return bloecke(fenster) { f -> !f.ende!!.isAfter(letzte) && belegend.none { belegt(it, f) } }
    }

    /**
     * Zeiten, in denen beide frei haben (Ausfall zählt als frei) – solange beide danach noch
     * Unterricht haben. Freie Stunden am Tagesanfang zählen also mit, die nach dem Schluss
     * des einen nicht. Ohne Zeitraster: nur Lücken ab [minMinuten], während beide da sind.
     */
    fun gemeinsamFrei(
        a: TagesPlan,
        b: TagesPlan,
        raster: List<Zeitfenster> = emptyList(),
        minMinuten: Long = 30
    ): List<Freiblock> {
        val fenster = mitZeiten(raster)
        if (fenster.isEmpty()) {
            return gemeinsamFreiOhneRaster(a, b, minMinuten).map { (von, bis) -> Freiblock(null, null, von, bis) }
        }
        val sa = a.stunden.filter { !it.entfaellt }
        val sb = b.stunden.filter { !it.entfaellt }
        val endeA = sa.mapNotNull { it.ende }.maxOrNull() ?: return emptyList()
        val endeB = sb.mapNotNull { it.ende }.maxOrNull() ?: return emptyList()
        val bis = minOf(endeA, endeB)
        return bloecke(fenster) { f ->
            !f.ende!!.isAfter(bis) && sa.none { belegt(it, f) } && sb.none { belegt(it, f) }
        }
    }

    /** Stunden aus [a], die [b] ebenfalls hat (gleicher Kurs zur gleichen Stunde) – ohne ausgefallene. */
    fun gemeinsameStunden(a: TagesPlan, b: TagesPlan): List<Lesson> {
        val kennungenB = b.stunden.filter { !it.entfaellt }.mapTo(HashSet()) { it.kennung() }
        return a.stunden.filter { !it.entfaellt && it.kennung() in kennungenB }
    }

    private fun mitZeiten(raster: List<Zeitfenster>) =
        raster.filter { it.beginn != null && it.ende != null && it.beginn.isBefore(it.ende) }
            .sortedBy { it.beginn }

    /** Liegt die Stunde im Fenster? Über die Uhrzeit, sonst über die Stundennummer. */
    private fun belegt(l: Lesson, f: Zeitfenster): Boolean {
        val b = l.beginn
        val e = l.ende
        return if (b != null && e != null) b.isBefore(f.ende) && f.beginn!!.isBefore(e) else l.stunde == f.stunde
    }

    /**
     * Fasst aufeinanderfolgende freie Fenster zu Blöcken zusammen. Die Pausen davor und danach
     * gehören mit zur freien Zeit (frei ab Ende der Stunde davor bis Beginn der danach).
     */
    private fun bloecke(fenster: List<Zeitfenster>, frei: (Zeitfenster) -> Boolean): List<Freiblock> {
        val out = ArrayList<Freiblock>()
        var i = 0
        while (i < fenster.size) {
            if (!frei(fenster[i])) { i++; continue }
            var j = i
            while (j + 1 < fenster.size && frei(fenster[j + 1])) j++
            val beginn = if (i > 0) fenster[i - 1].ende!! else fenster[i].beginn!!
            val ende = if (j + 1 < fenster.size) fenster[j + 1].beginn!! else fenster[j].ende!!
            out += Freiblock(fenster[i].stunde, fenster[j].stunde, minOf(beginn, fenster[i].beginn!!), maxOf(ende, fenster[j].ende!!))
            i = j + 1
        }
        return out
    }

    private fun gemeinsamFreiOhneRaster(a: TagesPlan, b: TagesPlan, minMinuten: Long): List<Pair<LocalTime, LocalTime>> {
        val za = belegt(a)
        val zb = belegt(b)
        if (za.isEmpty() || zb.isEmpty()) return emptyList()
        val von = maxOf(za.first().first, zb.first().first)
        val bis = minOf(za.last().second, zb.last().second)
        if (!von.isBefore(bis)) return emptyList()

        val frei = ArrayList<Pair<LocalTime, LocalTime>>()
        var t = von
        for ((beginn, ende) in zusammenlegen(za + zb)) {
            if (!beginn.isBefore(bis)) break
            if (beginn.isAfter(t)) frei += t to beginn
            if (ende.isAfter(t)) t = ende
        }
        if (t.isBefore(bis)) frei += t to bis
        return frei.filter { Duration.between(it.first, it.second).toMinutes() >= minMinuten }
    }

    private fun zusammenlegen(zeiten: List<Pair<LocalTime, LocalTime>>): List<Pair<LocalTime, LocalTime>> {
        val out = ArrayList<Pair<LocalTime, LocalTime>>()
        for ((beginn, ende) in zeiten.sortedBy { it.first }) {
            val letzte = out.lastOrNull()
            if (letzte != null && !beginn.isAfter(letzte.second)) {
                out[out.lastIndex] = letzte.first to maxOf(letzte.second, ende)
            } else {
                out += beginn to ende
            }
        }
        return out
    }
}
