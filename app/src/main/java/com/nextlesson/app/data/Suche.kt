package com.nextlesson.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Eine Unterrichtsstunde im Zeitraster der Schule. */
data class Zeitfenster(val stunde: Int, val beginn: LocalTime?, val ende: LocalTime?)

/** Suchergebnis: ein Lehrer (Kürzel wie im Plan) oder ein Raum. */
sealed class Treffer {
    abstract val name: String

    data class Lehrer(override val name: String) : Treffer()
    data class Raum(override val name: String) : Treffer()
}

/** Was ein Lehrer gerade macht bzw. was in einem Raum gerade los ist. */
sealed class Belegung {
    /** Diese Stunde läuft gerade. */
    data class Jetzt(val lesson: Lesson) : Belegung()

    /** Gerade keine Stunde; [naechste] = nächste Stunde des Tages, null = keine mehr. */
    data class Frei(val naechste: Lesson?) : Belegung()
}

/**
 * Der Plan der ganzen Schule für einen Tag, aufbereitet für die Suche nach Lehrern und Räumen.
 *
 * Grundlage ist der Schülerplan aller Klassen. Was dort nicht steht – Aufsichten,
 * Sprechstunden, Räume ohne Unterricht – kennt die App nicht; "frei" heißt also nur:
 * laut Plan kein Unterricht.
 */
class SchulTag(val datum: LocalDate, gesamt: GesamtPlan) {

    /**
     * Alle Stunden aller Klassen – je Klasse so aufbereitet wie der persönliche Plan. Sonst
     * zählte an Klausurtagen die Klausur als Ausfall, und die Aufsicht wäre angeblich frei.
     */
    val stunden: List<Lesson> = gesamt.klassen.flatMap { kp ->
        gesamt.tagesplanFuer(setOf("${kp.klasse}::${KursInfo.GANZE_KLASSE}")).stunden
    }

    /** Zeitraster: je Stundennummer die häufigste Beginn/Ende-Kombination. */
    val raster: List<Zeitfenster> = stunden.filter { it.stunde > 0 }
        .groupBy { it.stunde }
        .map { (nr, ls) ->
            val zeit = ls.filter { it.beginn != null && it.ende != null }
                .groupingBy { it.beginn to it.ende }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            Zeitfenster(nr, zeit?.first, zeit?.second)
        }
        .sortedBy { it.stunde }

    private val nachLehrer: Map<String, List<Lesson>> = index { lehrerVon(it.lehrer) }
    private val nachRaum: Map<String, List<Lesson>> = index { raeumeVon(it.raum) }

    /** Alle Räume, die an diesem Tag im Plan vorkommen – in natürlicher Reihenfolge (2 vor 10). */
    val raeume: List<String> = nachRaum.keys.sortedWith(RAUM_REIHENFOLGE)

    val anzahlLehrer: Int get() = nachLehrer.size

    private fun index(schluessel: (Lesson) -> List<String>): Map<String, List<Lesson>> {
        val out = HashMap<String, MutableList<Lesson>>()
        stunden.forEach { l -> schluessel(l).distinct().forEach { k -> out.getOrPut(k) { ArrayList() } += l } }
        return out.mapValues { (_, ls) -> ls.sortedWith(compareBy({ it.stunde }, { it.klasse })) }
    }

    /**
     * Lehrer und Räume, deren Name [anfrage] enthält: exakte Treffer zuerst, dann solche, die
     * damit anfangen, dann der Rest; bei Gleichstand Lehrer vor Räumen.
     */
    fun suche(anfrage: String, max: Int = 30): List<Treffer> {
        val q = anfrage.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun rang(name: String): Int? {
            val n = name.lowercase()
            return when {
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                else -> null
            }
        }
        val kandidaten: List<Treffer> =
            nachLehrer.keys.map { Treffer.Lehrer(it) } + nachRaum.keys.map { Treffer.Raum(it) }
        return kandidaten
            .mapNotNull { t -> rang(t.name)?.let { it to t } }
            .sortedWith(
                compareBy(
                    { it.first },
                    { if (it.second is Treffer.Lehrer) 0 else 1 },
                    { it.second.name.lowercase() }
                )
            )
            .take(max)
            .map { it.second }
    }

    /** Alle Stunden eines Lehrers bzw. in einem Raum, nach Stunde sortiert (auch ausgefallene). */
    fun stundenVon(t: Treffer): List<Lesson> = when (t) {
        is Treffer.Lehrer -> nachLehrer[t.name]
        is Treffer.Raum -> nachRaum[t.name]
    }.orEmpty()

    /** Stand um [jetzt]. Ausgefallene Stunden zählen nicht – dann ist der Raum ja frei. */
    fun belegung(t: Treffer, jetzt: LocalTime): Belegung {
        val aktiv = stundenVon(t).filter { !it.entfaellt }
        aktiv.firstOrNull { laeuft(it, jetzt) }?.let { return Belegung.Jetzt(it) }
        return Belegung.Frei(
            aktiv.filter { it.beginn?.isAfter(jetzt) == true }.minByOrNull { it.beginn ?: LocalTime.MAX }
        )
    }

    /**
     * Räume ohne Unterricht um [jetzt] – die am längsten frei bleiben zuerst – jeweils mit der
     * Uhrzeit, ab der sie wieder belegt sind (null = heute nicht mehr). Außerhalb der
     * Unterrichtszeit null: Da wäre "frei" bei allen Räumen nichtssagend.
     */
    fun freieRaeume(jetzt: LocalTime): List<Pair<String, LocalTime?>>? {
        val aktiv = stunden.filter { !it.entfaellt }
        val beginn = aktiv.mapNotNull { it.beginn }.minOrNull() ?: return null
        val ende = aktiv.mapNotNull { it.ende }.maxOrNull() ?: return null
        if (jetzt.isBefore(beginn) || !jetzt.isBefore(ende)) return null
        return raeume
            .mapNotNull { r -> (belegung(Treffer.Raum(r), jetzt) as? Belegung.Frei)?.let { r to it.naechste?.beginn } }
            .sortedWith(compareBy<Pair<String, LocalTime?>, LocalTime?>(nullsFirst(reverseOrder())) { it.second })
    }

    /**
     * Der Tag eines Lehrers oder Raums im Zeitraster: je Stunde die Belegung (leer = frei).
     * Beim Lehrer ohne die leeren Stunden vor der ersten und nach der letzten.
     */
    fun tagesablauf(t: Treffer): List<Pair<Zeitfenster, List<Lesson>>> {
        val eigene = stundenVon(t)
        val nummern = (raster.map { it.stunde } + eigene.map { it.stunde }).distinct().sorted()
        val zeilen = nummern.map { nr ->
            val fenster = raster.firstOrNull { it.stunde == nr }
                ?: eigene.first { it.stunde == nr }.let { Zeitfenster(nr, it.beginn, it.ende) }
            fenster to eigene.filter { it.stunde == nr }
        }
        return if (t is Treffer.Lehrer) {
            zeilen.dropWhile { it.second.isEmpty() }.dropLastWhile { it.second.isEmpty() }
        } else {
            zeilen
        }
    }

    companion object {
        /** Lehrerkürzel eines Eintrags; mehrere (Team-Teaching) stehen durch Leerzeichen o.ä. getrennt. */
        internal fun lehrerVon(feld: String): List<String> =
            feld.split(Regex("[\\s,;/+]+"))
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { k -> k.any { it.isLetter() } }

        /**
         * Räume eines Eintrags. Mehrere Räume stehen durch Komma o.ä. getrennt oder als Nummern
         * mit Leerzeichen ("204 205"); ein Name wie "SH 1" bleibt dagegen zusammen.
         */
        internal fun raeumeVon(feld: String): List<String> =
            feld.split(',', ';', '/')
                .flatMap { teil ->
                    val t = teil.trim()
                    val woerter = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (woerter.size > 1 && woerter.all { w -> w.any { it.isDigit() } }) woerter else listOf(t)
                }
                .filter { r -> r.any { it.isLetterOrDigit() } }

        private val RAUM_REIHENFOLGE =
            compareBy<String>({ it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it })

        private fun laeuft(l: Lesson, jetzt: LocalTime): Boolean {
            val b = l.beginn
            val e = l.ende
            return b != null && e != null && !jetzt.isBefore(b) && jetzt.isBefore(e)
        }
    }
}

/** Nächster Schultag (Mo–Fr) in Richtung [richtung]; Feiertage und Ferien kennt die App nicht. */
fun schultagVersetzt(datum: LocalDate, richtung: Int): LocalDate {
    val schritt = if (richtung < 0) -1L else 1L
    var d = datum.plusDays(schritt)
    while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(schritt)
    return d
}

/** Heute – oder am Wochenende der kommende Montag. */
fun ersterSchultag(heute: LocalDate): LocalDate =
    if (heute.dayOfWeek == DayOfWeek.SATURDAY || heute.dayOfWeek == DayOfWeek.SUNDAY) schultagVersetzt(heute, 1) else heute
