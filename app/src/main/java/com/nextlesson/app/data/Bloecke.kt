package com.nextlesson.app.data

import java.time.Duration

/**
 * Aufeinanderfolgende Stunden desselben Unterrichts (z.B. eine Doppelstunde 1.–2.) als ein
 * Eintrag. [ersteIndex] ist die Position der ersten Stunde in der ursprünglichen Liste.
 */
data class StundenBlock(val ersteIndex: Int, val stunden: List<Lesson>) {

    val erste: Lesson get() = stunden.first()
    val letzte: Lesson get() = stunden.last()

    /** "1.–2. Std" bzw. "3. Std". */
    val stundenText: String get() = stundenListe(stunden.map { it.stunde })

    /** "1.–2." bzw. "3." – für enge Zeilen. */
    val stundenKurz: String get() = stundenText.removeSuffix(" Std")

    /** Eine Stunde für den ganzen Block: Beginn der ersten, Ende der letzten. */
    val zusammengefasst: Lesson
        get() = if (stunden.size == 1) erste
        else erste.copy(ende = letzte.ende, hatAufgaben = stunden.any { it.hatAufgaben })
}

/**
 * Fasst Stunden zusammen, die direkt aufeinander folgen und sonst gleich sind (Kurs, Lehrkraft,
 * Raum, Status, Hinweis). Pausen bis [maxPauseMinuten] trennen nicht – eine Doppelstunde über
 * die große Pause hinweg ist keine, aber 5 Minuten zwischen 1. und 2. Stunde schon.
 * Parallele Kurse (gleiche Stundennummer) werden einzeln verfolgt.
 */
fun List<Lesson>.alsBloecke(maxPauseMinuten: Long = 10): List<StundenBlock> {
    val bloecke = ArrayList<MutableList<Lesson>>()
    val erste = ArrayList<Int>()
    forEachIndexed { index, l ->
        val ziel = bloecke.indexOfLast { it.last().schliesstAn(l, maxPauseMinuten) }
        if (ziel >= 0) {
            bloecke[ziel] += l
        } else {
            bloecke += mutableListOf(l)
            erste += index
        }
    }
    return bloecke.mapIndexed { i, stunden -> StundenBlock(erste[i], stunden) }
}

private fun Lesson.schliesstAn(b: Lesson, maxPauseMinuten: Long): Boolean {
    if (b.stunde != stunde + 1) return false
    if (klasse != b.klasse || kursKuerzel != b.kursKuerzel || fach != b.fach) return false
    if (lehrer != b.lehrer || raum != b.raum || info != b.info) return false
    if (entfaellt != b.entfaellt || status != b.status || istKlausur != b.istKlausur) return false
    val ende = ende
    val beginn = b.beginn
    if (ende != null && beginn != null) {
        val pause = Duration.between(ende, beginn).toMinutes()
        if (pause < 0 || pause > maxPauseMinuten) return false
    }
    return true
}
