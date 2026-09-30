package com.nextlesson.app.data

import java.time.LocalDate
import java.util.UUID

/**
 * Eine selbst eingetragene Hausaufgabe.
 *
 * [faelligEpochDay] wird bewusst als Zahl gehalten (Tage seit 1970) und nicht als
 * formatierter Text. Ein leerer oder unlesbarer Textwert ist genau der Grund, warum
 * Datumsangaben beim Neustart plötzlich als 01.01.0001 erscheinen.
 * -1 bedeutet "kein Datum gesetzt".
 */
data class Hausaufgabe(
    val id: String = UUID.randomUUID().toString(),
    val fach: String,
    val text: String,
    val faelligEpochDay: Long = KEIN_DATUM,
    val erledigt: Boolean = false,
    val erstelltAm: Long = System.currentTimeMillis()
) {
    val faellig: LocalDate?
        get() = if (faelligEpochDay <= KEIN_DATUM) null else LocalDate.ofEpochDay(faelligEpochDay)

    /** Überfällig ist nur, was ein Datum hat, offen ist und dessen Tag vorbei ist. */
    fun istUeberfaellig(heute: LocalDate = LocalDate.now()): Boolean {
        val d = faellig ?: return false
        return !erledigt && d.isBefore(heute)
    }

    companion object {
        const val KEIN_DATUM = -1L
    }
}

enum class PruefungsArt(val anzeige: String) {
    KLAUSUR("Klausur"),
    TEST("Test");

    companion object {
        fun ausName(name: String?): PruefungsArt =
            entries.firstOrNull { it.name == name } ?: TEST
    }
}

/**
 * Ein Test oder eine Klausur. Auch hier steht das Datum als [datumEpochDay] in der
 * Ablage – so bleibt es über App-Neustarts hinweg exakt erhalten.
 */
data class Pruefung(
    val id: String = UUID.randomUUID().toString(),
    val fach: String,
    val titel: String,
    val datumEpochDay: Long,
    val art: PruefungsArt = PruefungsArt.KLAUSUR,
    val notiz: String = ""
) {
    val datum: LocalDate get() = LocalDate.ofEpochDay(datumEpochDay)

    fun tageBis(heute: LocalDate = LocalDate.now()): Long =
        java.time.temporal.ChronoUnit.DAYS.between(heute, datum)

    fun istVorbei(heute: LocalDate = LocalDate.now()): Boolean = datum.isBefore(heute)
}
