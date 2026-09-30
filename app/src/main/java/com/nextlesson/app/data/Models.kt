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
    val klasse: String = "",
    /** Der Plan weist diese Stunde als Klausur/Klassenarbeit aus. */
    val istKlausur: Boolean = false
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
    val schulnummer: String,
    /** Tageshinweise der Schule (<ZusatzInfo>/<ZiZeile>), z.B. "Klausur!". */
    val zusatzInfo: List<String> = emptyList()
)

/** Der Block einer einzelnen Klasse/eines Jahrgangs aus der XML. */
data class KlassenPlan(
    val klasse: String,
    val stunden: List<Lesson>,
    val kurse: List<KursInfo>
)

/** Ganze Wörter (Buchstaben/Ziffern) – dient dem Erkennen von Kurskürzeln in Hinweistexten. */
private val WORT = Regex("[\\p{L}\\p{N}]+")

/** Sieht aus wie ein Kurskürzel ("DEU3", "MAT2"), auch wenn es in keiner Kursliste steht. */
private val KURS_MUSTER = Regex("(?<![\\p{L}\\p{N}])\\p{L}{2,5}\\d{1,2}(?![\\p{L}\\p{N}])")

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

        /**
         * Kurs einer Stunde. Fehlt die Zuordnung über <Nr> (z.B. bei ausfallenden oder
         * geänderten Stunden), steht der Kurs oft im Fach ("DEU1") – dann darüber zuordnen.
         * Ohne diesen Rückgriff gälte die Stunde als "Klassenunterricht" und würde für
         * ALLE Kurse angezeigt (DEU1 bis DEU4 gleichzeitig).
         */
        val kuerzelProKlasse = HashMap<String, List<String>>()
        fun kursVon(kp: KlassenPlan, l: Lesson): String? {
            l.kursKuerzel?.takeIf { it.isNotBlank() }?.let { return it }
            val fach = l.fach.trim()
            if (fach.isBlank()) return null
            val kuerzel = kuerzelProKlasse.getOrPut(kp.klasse) {
                kp.kurse.filterNot { it.istGanzeKlasse }.map { it.kuerzel }
            }
            kuerzel.firstOrNull { it == fach }?.let { return it }
            return kuerzel.filter { it.equals(fach, ignoreCase = true) }.singleOrNull()
        }

        val betroffene = klassen.filter { kp ->
            ganzeKlasseGewaehlt(kp.klasse) ||
                kp.kurse.any { it.id in gewaehlteKursIds } ||
                kp.stunden.any { l ->
                    val kurs = kursVon(kp, l)
                    kurs != null && "${kp.klasse}::$kurs" in gewaehlteKursIds
                }
        }

        val rohStunden = betroffene.flatMap { kp ->
            val alles = ganzeKlasseGewaehlt(kp.klasse)
            kp.stunden.mapNotNull { l ->
                val kurs = kursVon(kp, l)
                if (alles || kurs.isNullOrBlank() || "${kp.klasse}::$kurs" in gewaehlteKursIds) {
                    if (kurs != l.kursKuerzel) l.copy(kursKuerzel = kurs) else l
                } else null
            }
        }.sortedBy { it.stunde }

        // Tageshinweise der Schule ("Klausur!; BIO3 Herr X fällt aus; …"): Zeilen, die einen
        // fremden Kurs nennen, fliegen raus – allgemeine Zeilen und die eigenen bleiben.
        val alleKuerzel = klassen.flatMap { it.kurse }.filterNot { it.istGanzeKlasse }
            .map { it.kuerzel }.filter { it.isNotBlank() }.toSet()
        val eigeneKuerzel = gewaehlteKursIds.map { it.substringAfter("::") }
            .filter { it != KursInfo.GANZE_KLASSE }.toSet()
        val alleGewaehlt = betroffene.any { ganzeKlasseGewaehlt(it.klasse) }
        // Ganze Wörter einer Zeile als Menge: ein Regex-Durchlauf je Zeile statt eines neu
        // kompilierten Musters je Kürzel (bei einem Schulplan sonst tausende pro Tag).
        fun nennt(zeile: String, kuerzel: Set<String>): Boolean =
            WORT.findAll(zeile).any { it.value in kuerzel }
        val hinweise = kopf.zusatzInfo.filter { zeile ->
            alleGewaehlt || nennt(zeile, eigeneKuerzel) || !nennt(zeile, alleKuerzel)
        }

        // Klausurtage: Die Schule schreibt "Klausur!" und listet die ausfallenden Kurse
        // einzeln auf ("BIO3 Herr X fällt aus"). Die Kurse, die im Plan als ausgefallen
        // erscheinen, aber NICHT als Ausfall genannt werden, schreiben die Klausur.
        // Das gilt nur für Kurse desselben Fachs wie die genannten (DEU1 neben DEU3/DEU4),
        // damit ein anderes, wirklich ausgefallenes Fach nicht zur Klausur wird.
        // Quellen: Tageshinweise der Schule UND die Info-Texte der eigenen Stunden (die Schule
        // hängt "Klausur!; BIO3 … fällt aus; …" teils direkt an die Stunden).
        val segmente = (kopf.zusatzInfo + rohStunden.map { it.info })
            .flatMap { it.split(';') }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        fun istAusfallText(s: String) = listOf("fällt aus", "faellt aus", "entfällt", "entfaellt")
            .any { s.contains(it, ignoreCase = true) }
        val klausurSegmente = segmente.filter {
            it.contains("klausur", ignoreCase = true) || it.contains("klassenarbeit", ignoreCase = true)
        }
        val ausfallSegmente = segmente.filter { it !in klausurSegmente && istAusfallText(it) }
        fun praefix(kuerzel: String) = kuerzel.takeWhile { it.isLetter() }.lowercase()
        val klausurWoerter = klausurSegmente.flatMapTo(HashSet()) { s -> WORT.findAll(s).map { it.value }.toList() }
        val ausfallWoerter = ausfallSegmente.flatMapTo(HashSet()) { s -> WORT.findAll(s).map { it.value }.toList() }
        val genannteFaecher = if (klausurSegmente.isEmpty()) emptySet() else
            alleKuerzel.filter { it in klausurWoerter || it in ausfallWoerter }.map { praefix(it) }.toSet()
        val stunden = if (klausurSegmente.isEmpty()) rohStunden else rohStunden.map { l ->
            val kurs = l.kursKuerzel
            if (kurs == null || !(l.entfaellt || l.istKlausur)) return@map l
            // Sagt die Stunde selbst "… fällt aus", ohne einen anderen Kurs zu nennen, bleibt es Ausfall.
            val eigenerAusfall = l.info.split(';').map { it.trim() }.any {
                istAusfallText(it) && !nennt(it, alleKuerzel) && !KURS_MUSTER.containsMatchIn(it)
            }
            if (eigenerAusfall) return@map l
            val ausdruecklich = kurs in klausurWoerter
            val alsAusfallGenannt = kurs in ausfallWoerter
            if (ausdruecklich || (!alsAusfallGenannt && praefix(kurs) in genannteFaecher)) {
                l.copy(entfaellt = false, status = LessonStatus.NORMAL, istKlausur = true)
            } else if (l.istKlausur && l.entfaellt) {
                // Der Kurs wird ausdrücklich als Ausfall genannt: das ist keine Klausur.
                l.copy(istKlausur = false)
            } else l
        }

        return TagesPlan(
            kopf = kopf,
            klasse = betroffene.joinToString(" / ") { it.klasse },
            stunden = stunden,
            hinweise = hinweise
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
    val stunden: List<Lesson>,
    /** Tageshinweise der Schule, bereits auf die eigenen Kurse gefiltert. */
    val hinweise: List<String> = emptyList()
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
