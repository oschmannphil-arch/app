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

/** Wann haben zwei Leute gleichzeitig frei, und welche Stunden haben sie zusammen? */
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
     * Zeiten, in denen beide in der Schule sind (jeweils zwischen erster und letzter Stunde)
     * und beide keinen Unterricht haben – mindestens [minMinuten] lang, damit normale Pausen
     * nicht zählen.
     */
    fun gemeinsamFrei(a: TagesPlan, b: TagesPlan, minMinuten: Long = 30): List<Pair<LocalTime, LocalTime>> {
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

    /** Stunden aus [a], die [b] ebenfalls hat (gleicher Kurs zur gleichen Stunde) – ohne ausgefallene. */
    fun gemeinsameStunden(a: TagesPlan, b: TagesPlan): List<Lesson> {
        val kennungenB = b.stunden.filter { !it.entfaellt }.mapTo(HashSet()) { it.kennung() }
        return a.stunden.filter { !it.entfaellt && it.kennung() in kennungenB }
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
