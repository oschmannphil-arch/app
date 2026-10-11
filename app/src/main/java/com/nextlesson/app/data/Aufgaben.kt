package com.nextlesson.app.data

import java.time.LocalDate
import java.time.LocalTime
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

/**
 * Eine Klausur, die im Schulplan steht (Infotext "Klausur"). Nur Stunden der eigenen
 * Kurse landen hier, weil der Plan vorher auf die Kurswahl gefiltert wird.
 */
data class PlanKlausur(
    val datum: LocalDate,
    val fach: String,
    val kurs: String,
    val klasse: String,
    val stundenText: String,
    /** Ende der letzten Klausur-Stunde – danach ist die Klausur vorbei, auch am selben Tag. */
    val ende: LocalTime? = null
) {
    /** Stabile Kennung: derselbe Plan-Eintrag ergibt bei jedem Abruf dieselbe ID. */
    val id: String get() = "$PLAN_PREFIX$datum|$klasse|$kurs|$fach"

    fun alsPruefung() = Pruefung(
        id = id,
        fach = fach.ifBlank { kurs },
        titel = if (kurs.isNotBlank() && !kurs.equals(fach, ignoreCase = true)) kurs else "",
        datumEpochDay = datum.toEpochDay(),
        art = PruefungsArt.KLAUSUR,
        notiz = "Aus dem Plan · $stundenText",
        endeSekunden = ende?.toSecondOfDay() ?: Pruefung.KEIN_ENDE
    )

    companion object {
        const val PLAN_PREFIX = "plan|"

        /** Fasst die Klausur-Stunden eines Tages zusammen (eine Klausur über mehrere Stunden = ein Eintrag). */
        fun ausStunden(datum: LocalDate, stunden: List<Lesson>): List<PlanKlausur> =
            stunden.filter { it.istKlausur && !it.entfaellt }
                .groupBy { Triple(it.klasse, it.kursKuerzel.orEmpty(), it.fach) }
                .map { (schluessel, gruppe) ->
                    val sortiert = gruppe.sortedBy { it.stunde }
                    val erste = sortiert.first()
                    val letzte = sortiert.last()
                    val std = if (erste.stunde == letzte.stunde) "${erste.stunde}. Std"
                    else "${erste.stunde}.–${letzte.stunde}. Std"
                    val zeit = if (erste.beginn != null && letzte.ende != null) " · ${erste.beginn}–${letzte.ende}" else ""
                    PlanKlausur(datum, schluessel.third, schluessel.second, schluessel.first, std + zeit, letzte.ende)
                }
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
    val notiz: String = "",
    /** Uhrzeit (Sekunden seit Mitternacht), zu der der Termin endet; [KEIN_ENDE] = ganztägig. */
    val endeSekunden: Int = KEIN_ENDE
) {
    val datum: LocalDate get() = LocalDate.ofEpochDay(datumEpochDay)

    fun tageBis(heute: LocalDate = LocalDate.now()): Long =
        java.time.temporal.ChronoUnit.DAYS.between(heute, datum)

    /**
     * Vorbei ist ein Termin ab dem nächsten Tag – und am selben Tag, sobald er zu Ende ist
     * (eine Klausur um 07:15 ist um 16:00 nicht mehr "heute", sondern geschrieben).
     */
    fun istVorbei(heute: LocalDate = LocalDate.now(), jetzt: LocalTime = LocalTime.now()): Boolean =
        datum.isBefore(heute) ||
            (datum == heute && endeSekunden != KEIN_ENDE && jetzt.toSecondOfDay() >= endeSekunden)

    companion object {
        const val KEIN_ENDE = -1
    }
}
