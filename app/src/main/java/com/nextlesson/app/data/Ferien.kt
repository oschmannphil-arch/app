package com.nextlesson.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Ein Ferienabschnitt; [bis] ist der letzte freie Tag. */
data class Ferienabschnitt(val name: String, val von: LocalDate, val bis: LocalDate)

/**
 * Schulferien Sachsen-Anhalt als Countdown. Die Termine stammen aus öffentlichen Ferienkalendern
 * und sind ohne Gewähr – die Quellen weichen teils voneinander ab, verbindlich ist die Schule.
 */
object Ferien {

    val ABSCHNITTE: List<Ferienabschnitt> = listOf(
        Ferienabschnitt("Herbstferien", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 30)),
        Ferienabschnitt("Weihnachtsferien", LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 2)),
        Ferienabschnitt("Winterferien", LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 6)),
        Ferienabschnitt("Osterferien", LocalDate.of(2027, 3, 22), LocalDate.of(2027, 3, 27)),
        Ferienabschnitt("Sommerferien", LocalDate.of(2027, 7, 10), LocalDate.of(2027, 8, 20))
    )

    /** Wie weit im Voraus der Countdown gezeigt wird. */
    const val VORLAUF_TAGE = 60L

    private val TAG = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)

    /** Läuft gerade ein Ferienabschnitt? */
    fun laufend(heute: LocalDate, liste: List<Ferienabschnitt> = ABSCHNITTE): Ferienabschnitt? =
        liste.firstOrNull { !heute.isBefore(it.von) && !heute.isAfter(it.bis) }

    /** Der nächste Ferienabschnitt, der noch nicht begonnen hat. */
    fun naechster(heute: LocalDate, liste: List<Ferienabschnitt> = ABSCHNITTE): Ferienabschnitt? =
        liste.filter { it.von.isAfter(heute) }.minByOrNull { it.von }

    /** Text für die Startseite oder null, wenn nichts Ferienbezogenes ansteht. */
    fun text(heute: LocalDate, liste: List<Ferienabschnitt> = ABSCHNITTE): String? {
        laufend(heute, liste)?.let { return "${it.name}: noch bis ${it.bis.format(TAG)}" }
        val n = naechster(heute, liste) ?: return null
        val tage = ChronoUnit.DAYS.between(heute, n.von)
        return when {
            tage > VORLAUF_TAGE -> null
            tage == 1L -> "Morgen beginnen die ${n.name}"
            else -> "Noch $tage Tage bis zu den ${n.name} (${n.von.format(TAG)})"
        }
    }
}
