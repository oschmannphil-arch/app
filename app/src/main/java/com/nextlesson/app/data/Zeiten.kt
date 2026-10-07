package com.nextlesson.app.data

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** "12 Min", "2 Std", "1 Std 20 Min". */
fun dauerText(minuten: Long): String {
    if (minuten < 60) return "$minuten Min"
    val std = minuten / 60
    val min = minuten % 60
    return if (min == 0L) "$std Std" else "$std Std $min Min"
}

/**
 * Countdown zu einer Stunde, für App und Widget gleich. [bisBeginnMs]: Millisekunden bis zum
 * Beginn (negativ = schon begonnen); [bisEndeMs]: bis zum Ende (null = unbekannt).
 * Aufgerundet auf volle Minuten, damit die Anzeige genau zur vollen Minute springt.
 *
 * "läuft noch 12 Min" · "in 8 Min" · "jetzt" · null (vorbei bzw. mehr als
 * 10 Stunden – dann sagt die Uhrzeit mehr als ein Countdown).
 */
fun countdownText(bisBeginnMs: Long, bisEndeMs: Long?, laeuftPraefix: String = "läuft noch"): String? {
    if (bisEndeMs != null && bisBeginnMs <= 0L && bisEndeMs > 0L) {
        return "$laeuftPraefix ${dauerText((bisEndeMs + 59_999) / 60_000)}"
    }
    if (bisBeginnMs < 0L) return null
    val bis = (bisBeginnMs + 59_999) / 60_000
    return when {
        bis == 0L -> "jetzt"
        bis > 600 -> null
        else -> "in ${dauerText(bis)}"
    }
}

/**
 * Der nächste der Zeitpunkte [zeiten] (Uhrzeiten, heute oder morgen), mindestens eine Minute in
 * der Zukunft – damit ein Lauf, der kurz vor "seiner" Zeit startet, nicht gleich wieder dieselbe
 * Zeit plant. Mit Zeitzone gerechnet (Zeitumstellung).
 */
fun naechsterZeitpunkt(zeiten: List<LocalTime>, jetzt: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
    require(zeiten.isNotEmpty())
    val fruehestens = jetzt.plusMinutes(1)
    return (0..1).flatMap { tag -> zeiten.map { jetzt.toLocalDate().plusDays(tag.toLong()).atTime(it).atZone(jetzt.zone) } }
        .filter { it.isAfter(fruehestens) }
        .minOrNull() ?: jetzt.toLocalDate().plusDays(1).atTime(zeiten.min()).atZone(jetzt.zone)
}

/**
 * Zeit bis zum nächsten Auftreten der Uhrzeit [ziel] – heute, sonst morgen. Mit Zeitzone
 * gerechnet: Am Tag der Zeitumstellung ist ein Tag 23 oder 25 Stunden lang.
 */
fun verzoegerungBis(ziel: LocalTime, jetzt: ZonedDateTime = ZonedDateTime.now()): Duration {
    var zielZeitpunkt = jetzt.toLocalDate().atTime(ziel).atZone(jetzt.zone)
    if (!zielZeitpunkt.isAfter(jetzt)) {
        zielZeitpunkt = jetzt.toLocalDate().plusDays(1).atTime(ziel).atZone(jetzt.zone)
    }
    return Duration.between(jetzt, zielZeitpunkt)
}
