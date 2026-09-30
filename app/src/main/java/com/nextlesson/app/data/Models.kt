package com.nextlesson.app.data

import java.time.Duration
import java.time.LocalTime

/**
 * Der Status einer Stunde: normal oder eine Art der Änderung.
 */
enum class LessonStatus {
    NORMAL, ENTFALL, VERTRETUNG, RAUMAENDERUNG
}

/**
 * Eine einzelne Unterrichtsstunde (<Std> innerhalb von <Pl>).
 */
data class Lesson(
    val stunde: Int,
    val beginn: LocalTime?,
    val ende: LocalTime?,
    val fach: String,
    val fachGeaendert: Boolean,
    val raum: String,
    val raumGeaendert: Boolean,
    val originalRoom: String? = null,
    val lehrer: String,
    val lehrerGeaendert: Boolean,
    val originalTeacher: String? = null,
    val info: String,
    val entfaellt: Boolean,
    val hatAufgaben: Boolean = false,
    val status: LessonStatus = LessonStatus.NORMAL,
    /** Unterrichtsnummer aus <Nr> – Schlüssel in die <Unterricht>-Tabelle. */
    val unterrichtsNr: String? = null,
    /** Kurskürzel, aufgelöst über <Nr> → <UeNr UeGr="…">. Null = gemeinsamer Klassenunterricht. */
    val kursKuerzel: String? = null,
    /** Klasse/Jahrgang, aus dessen Block diese Stunde stammt. */
    val klasse: String = ""
) {
    val hatAenderung: Boolean
        get() = status != LessonStatus.NORMAL || fachGeaendert || info.isNotBlank()
}

/**
 * Ein wählbarer Kurs. [id] ist eindeutig über die ganze Schule hinweg, weil dasselbe
 * Kürzel in mehreren Jahrgängen vorkommen kann.
 */
data class KursInfo(
    val klasse: String,
    val kuerzel: String,
    val fach: String,
    val lehrer: String
) {
    val id: String get() = "$klasse::$kuerzel"

    /** True für den Sammel-Eintrag "alle Stunden dieser Klasse". */
    val istGanzeKlasse: Boolean get() = kuerzel == GANZE_KLASSE

    /** Anzeigetext, z.B. "D1 · De (Müller)". */
    val anzeige: String
        get() = if (istGanzeKlasse) "Ganze Klasse – alle Stunden" else buildString {
            append(kuerzel)
            if (fach.isNotBlank() && !fach.equals(kuerzel, ignoreCase = true)) append(" · $fach")
            if (lehrer.isNotBlank()) append(" ($lehrer)")
        }

    /** Text, gegen den die Suche in der Kursauswahl läuft. */
    val suchtext: String get() = "$klasse $kuerzel $fach $lehrer".lowercase()

    companion object {
        /** Pseudo-Kürzel für "die ganze Klasse", damit auch Klassen ohne Kurssystem wählbar sind. */
        const val GANZE_KLASSE = "*"
    }
}

/** Kopfdaten eines Plans. */
data class PlanKopf(
    val datumPlan: String,
    val zeitstempel: String,
    val schulnummer: String
)

/** Der Block einer einzelnen Klasse/eines Jahrgangs aus der XML. */
data class KlassenPlan(
    val klasse: String,
    val stunden: List<Lesson>,
    val kurse: List<KursInfo>
)

/** Alles, was in einer PlanKl-Datei steht – alle Klassen der Schule für diesen Tag. */
data class GesamtPlan(
    val kopf: PlanKopf,
    val klassen: List<KlassenPlan>
) {
    /** Alle Kurse der ganzen Schule, für die Auswahlliste ("ganze Klasse" jeweils zuerst). */
    val alleKurse: List<KursInfo>
        get() = klassen.flatMap { it.kurse }.sortedWith(
            compareBy({ it.klasse }, { !it.istGanzeKlasse }, { it.kuerzel })
        )

    /**
     * Baut aus den gewählten Kurs-IDs den persönlichen Tagesplan.
     *
     * Die Klasse muss der Schüler nicht angeben: Es zählen die Klassen-Blöcke, in denen
     * mindestens einer seiner Kurse liegt. Aus diesen Blöcken werden die Stunden seiner
     * Kurse übernommen – plus die Stunden ohne Kurskürzel (gemeinsamer Klassenunterricht).
     */
    fun tagesplanFuer(gewaehlteKursIds: Set<String>): TagesPlan {
        fun ganzeKlasseGewaehlt(klasse: String) =
            "$klasse::${KursInfo.GANZE_KLASSE}" in gewaehlteKursIds

        val betroffene = klassen.filter { kp ->
            ganzeKlasseGewaehlt(kp.klasse) ||
                kp.kurse.any { it.id in gewaehlteKursIds } ||
                kp.stunden.any { l ->
                    l.kursKuerzel != null && "${kp.klasse}::${l.kursKuerzel}" in gewaehlteKursIds
                }
        }

        val stunden = betroffene.flatMap { kp ->
            val alles = ganzeKlasseGewaehlt(kp.klasse)
            kp.stunden.filter { l ->
                val kurs = l.kursKuerzel
                alles || kurs.isNullOrBlank() || "${kp.klasse}::$kurs" in gewaehlteKursIds
            }
        }.sortedBy { it.stunde }

        return TagesPlan(
            kopf = kopf,
            klasse = betroffene.joinToString(" / ") { it.klasse },
            stunden = stunden
        )
    }
}

/**
 * Ergebnis von [TagesPlan.naechsteStunde]: die Stunde plus Info, ob es sich schon um eine
 * Vorschau auf die kommende Stunde handelt (weil die laufende gleich endet).
 */
data class NaechsteStundeErgebnis(
    val lesson: Lesson,
    val istVorschau: Boolean
)

/** Der fertig gefilterte, persönliche Plan für einen Tag. */
data class TagesPlan(
    val kopf: PlanKopf,
    val klasse: String,
    val stunden: List<Lesson>
) {
    /**
     * Liefert die laufende oder als nächstes anstehende Stunde relativ zu [jetzt].
     *
     * Endet die laufende Stunde in [vorlaufMinuten] oder weniger, wird schon die
     * darauffolgende zurückgegeben (istVorschau = true).
     */
    fun naechsteStunde(jetzt: LocalTime, vorlaufMinuten: Long = VORLAUF_MINUTEN): NaechsteStundeErgebnis? {
        val sortiert = stunden.filter { !it.entfaellt }.sortedBy { it.stunde }

        val laufende = sortiert.firstOrNull { l ->
            val b = l.beginn
            val e = l.ende
            b != null && e != null && !jetzt.isBefore(b) && jetzt.isBefore(e)
        }

        if (laufende != null) {
            val ende = laufende.ende
            val rest = if (ende != null) Duration.between(jetzt, ende).toMinutes() else Long.MAX_VALUE
            if (rest > vorlaufMinuten) return NaechsteStundeErgebnis(laufende, false)
            val danach = sortiert.firstOrNull { it.stunde > laufende.stunde && it.beginn != null }
            return NaechsteStundeErgebnis(danach ?: laufende, istVorschau = danach != null)
        }

        val kommende = sortiert.firstOrNull { l -> l.beginn != null && jetzt.isBefore(l.beginn) }
        return kommende?.let { NaechsteStundeErgebnis(it, false) }
    }

    /** Erste nicht ausgefallene Stunde des Tages – für die Vorschau auf den nächsten Schultag. */
    fun ersteStunde(): Lesson? =
        stunden.filter { !it.entfaellt }.minByOrNull { it.stunde }

    /** Alle ausgefallenen Stunden dieses Tages. */
    fun entfaelle(): List<Lesson> = stunden.filter { it.entfaellt }

    companion object {
        /** So viele Minuten vor Stundenende wird schon die nächste Stunde gezeigt. */
        const val VORLAUF_MINUTEN = 5L
    }
}

/** Zugangsdaten, wie sie lokal auf dem Gerät gespeichert werden. Keine Klasse nötig. */
data class IndiwareCredentials(
    val schulnummer: String,
    val benutzername: String,
    val passwort: String
) {
    fun istVollstaendig(): Boolean =
        schulnummer.isNotBlank() && benutzername.isNotBlank() && passwort.isNotBlank()
}
