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
        // Gezählt wird bis zum letzten Schultag (meist Freitag), nicht bis zum Wochenende davor.
        val letzter = letzterSchultagVor(n.von)
        val tage = ChronoUnit.DAYS.between(heute, letzter)
        return when {
            tage > VORLAUF_TAGE -> null
            tage < 0L ->
                if (ChronoUnit.DAYS.between(heute, n.von) == 1L) "Morgen beginnen die ${n.name}"
                else "Die ${n.name} beginnen am ${n.von.format(TAG)}"
            tage == 0L -> "Heute ist der letzte Schultag vor den ${n.name}"
            tage == 1L -> "Morgen ist der letzte Schultag vor den ${n.name}"
            else -> "Noch $tage Tage bis zu den ${n.name} (letzter Schultag: ${letzter.format(TAG)})"
        }
    }

    /**
     * Der Ferienabschnitt, in dem man schon "frei hat": Er zählt ab dem Tag nach dem letzten Schultag
     * (also schon am Wochenende davor) bis zum letzten Ferientag. Fürs Widget: "Du hast Ferien".
     */
    fun freiSeit(heute: LocalDate, liste: List<Ferienabschnitt> = ABSCHNITTE): Ferienabschnitt? =
        liste.firstOrNull { !heute.isBefore(letzterSchultagVor(it.von).plusDays(1)) && !heute.isAfter(it.bis) }

    /** Der letzte Tag Montag bis Freitag vor [ferienbeginn]. */
    fun letzterSchultagVor(ferienbeginn: LocalDate): LocalDate {
        var d = ferienbeginn.minusDays(1)
        while (d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d
    }
}
