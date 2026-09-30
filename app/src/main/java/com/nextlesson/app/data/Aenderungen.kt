package com.nextlesson.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Art einer Planänderung, über die die App benachrichtigen kann. */
enum class AenderungsArt(val anzeige: String) {
    ENTFALL("Ausfall"),
    VERTRETUNG("Vertretung"),
    RAUM("Raumänderung")
}

/**
 * Eine Änderung an einer deiner Stunden.
 *
 * Der [schluessel] enthält auch den neuen Stand (Vertretungslehrer, neuer Raum): Wechselt der
 * Raum ein zweites Mal, ist das eine neue Änderung und wird wieder gemeldet – dieselbe
 * Änderung bei jedem Abruf aber nur einmal.
 */
data class Aenderung(val lesson: Lesson, val art: AenderungsArt) {

    val schluessel: String
        get() = lesson.kennung() + TRENNER + when (art) {
            AenderungsArt.ENTFALL -> "E"
            AenderungsArt.VERTRETUNG -> "V:${lesson.lehrer}"
            AenderungsArt.RAUM -> "R:${lesson.raum}"
        }

    /**
     * Lohnt sich eine Meldung noch? Für heute nur, solange die Stunde nicht vorbei ist –
     * um 18 Uhr noch zu erfahren, dass die 3. Stunde ausgefallen ist, hilft niemandem.
     */
    fun nochRelevant(datum: LocalDate, heute: LocalDate, jetzt: LocalTime): Boolean = when {
        datum.isBefore(heute) -> false
        datum.isAfter(heute) -> true
        else -> (lesson.ende ?: lesson.beginn)?.isAfter(jetzt) ?: true
    }

    companion object {
        const val TRENNER = "#"

        /**
         * Alle aktuellen Änderungen eines Tages. Bei einer Klausur ist ein anderer Lehrer nur
         * die Aufsicht und keine Vertretung – ein anderer Raum zählt aber sehr wohl.
         */
        fun von(stunden: List<Lesson>): List<Aenderung> = stunden.flatMap { l ->
            if (l.entfaellt) {
                listOf(Aenderung(l, AenderungsArt.ENTFALL))
            } else {
                listOfNotNull(
                    Aenderung(l, AenderungsArt.VERTRETUNG)
                        .takeIf { l.lehrerGeaendert && !l.istKlausur && l.lehrer.isNotBlank() },
                    Aenderung(l, AenderungsArt.RAUM)
                        .takeIf { l.raumGeaendert && l.raum.isNotBlank() }
                )
            }
        }

        /**
         * Die Änderungen aus [aktuell], die im gespeicherten Stand [bekannt] noch fehlen.
         * [bekannt] == null heißt: erster Abruf dieses Tages – dann wird nichts gemeldet, sonst
         * käme direkt nach dem Einrichten eine Flut längst bekannter Änderungen.
         */
        fun neue(bekannt: Set<String>?, aktuell: List<Aenderung>): List<Aenderung> =
            if (bekannt == null) emptyList() else aktuell.filter { it.schluessel !in bekannt }

        /**
         * Übernimmt den Speicherstand älterer App-Versionen, die sich nur ausgefallene Stunden
         * gemerkt haben. Vertretungen und Raumänderungen gelten dabei als bekannt – sonst käme
         * nach dem Update eine Meldung für jede, die es schon länger gibt.
         */
        fun ausAltemStand(alt: Set<String>, aktuell: List<Aenderung>): Set<String> =
            alt.mapTo(HashSet()) { it + TRENNER + "E" } +
                aktuell.filter { it.art != AenderungsArt.ENTFALL }.map { it.schluessel }
    }
}

/** Stabile Kennung einer Stunde innerhalb eines Tages (Format wie in älteren App-Versionen). */
internal fun Lesson.kennung(): String =
    listOf(klasse, stunde.toString(), kursKuerzel.orEmpty(), unterrichtsNr.orEmpty()).joinToString("|")

/** Texte der Änderungs-Benachrichtigung – ohne Android-Abhängigkeit, damit sie testbar sind. */
object AenderungsText {

    private val tagFormat = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN)

    fun wann(datum: LocalDate, heute: LocalDate): String = when (datum) {
        heute -> "heute"
        heute.plusDays(1) -> "morgen"
        else -> datum.format(tagFormat)
    }

    /** Titel: nennt eine einzelne neue Änderung konkret, bei mehreren die Anzahl. */
    fun titel(neu: List<Aenderung>, datum: LocalDate, heute: LocalDate): String {
        val wann = wann(datum, heute)
        if (neu.size != 1) {
            return if (neu.all { it.art == AenderungsArt.ENTFALL }) {
                "${neu.size} Stunden fallen $wann aus"
            } else {
                "${neu.size} Änderungen $wann"
            }
        }
        val a = neu.first()
        val l = a.lesson
        val name = name(l)
        return when (a.art) {
            AenderungsArt.ENTFALL -> when {
                name != null -> "$name fällt $wann aus"
                // Beim Ausfall steht im Fach oft nur "---". Ein kurzer Hinweis nennt dann den
                // Kurs ("ENG2 Herr Niemietz fällt aus"); lange Sammel-Hinweise taugen nicht.
                kurzerHinweis(l) != null -> "${wann.replaceFirstChar { it.uppercase() }}: ${l.info}"
                else -> "Unterricht fällt $wann aus"
            }
            AenderungsArt.VERTRETUNG -> "${name ?: "Unterricht"} $wann: Vertretung bei ${l.lehrer}"
            AenderungsArt.RAUM -> "${name ?: "Unterricht"} $wann in Raum ${l.raum}"
        }
    }

    /** Eine Zeile je Änderung, nach Stunde sortiert. */
    fun text(alle: List<Aenderung>): String =
        alle.sortedWith(compareBy({ it.lesson.stunde }, { it.art.ordinal })).joinToString("\n") { zeile(it) }

    fun zeile(a: Aenderung): String {
        val l = a.lesson
        val kopf = buildString {
            append("${l.stunde}. Std")
            name(l)?.let { append(" $it") }
        }
        return when (a.art) {
            AenderungsArt.ENTFALL -> "$kopf: fällt aus" + (kurzerHinweis(l)?.let { " ($it)" } ?: "")
            AenderungsArt.VERTRETUNG ->
                "$kopf: Vertretung bei ${l.lehrer}" + (l.originalTeacher?.let { " statt $it" } ?: "")
            AenderungsArt.RAUM ->
                "$kopf: Raum ${l.raum}" + (l.originalRoom?.let { " statt $it" } ?: "")
        }
    }

    private fun name(l: Lesson): String? =
        l.fach.takeIf { f -> f.any { it.isLetterOrDigit() } } ?: l.kursKuerzel?.takeIf { it.isNotBlank() }

    private fun kurzerHinweis(l: Lesson): String? =
        l.info.takeIf { it.isNotBlank() && it.length <= 60 && ';' !in it }
}
