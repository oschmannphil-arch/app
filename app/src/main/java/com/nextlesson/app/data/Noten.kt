package com.nextlesson.app.data

import java.time.LocalDate
import java.util.UUID
import kotlin.math.floor

enum class NotenArt(val anzeige: String) {
    KLAUSUR("Klausur"),
    MUENDLICH("Mündlich");

    companion object {
        fun ausName(name: String?): NotenArt = entries.firstOrNull { it.name == name } ?: MUENDLICH
    }
}

/**
 * Bewertungssystem: Klasse 5–10 mit Noten 1 (beste) bis 6, ab Klasse 11 (Qualifikationsphase)
 * mit Punkten 0 bis 15 (15 = beste).
 */
enum class NotenSystem(val min: Int, val max: Int, val hoeherIstBesser: Boolean) {
    PUNKTE(0, 15, true),
    NOTEN(1, 6, false);

    /** Beste zuletzt: von der schlechtesten bis zur besten Wertung. */
    val schlechtZuGut: List<Int>
        get() = if (hoeherIstBesser) (min..max).toList() else (min..max).toList().reversed()

    companion object {
        /** Klasse 11 und höher → Punkte, sonst Noten. Ohne erkennbare Klasse: Punkte. */
        const val ERSTE_PUNKTE_KLASSE = 11

        fun ausName(name: String?): NotenSystem = entries.firstOrNull { it.name == name } ?: PUNKTE

        /** Kurs-IDs haben die Form "Klasse::Kürzel", z.B. "12/5::D1" oder "7a::*". Die höchste Klasse entscheidet. */
        fun ausKursIds(ids: Collection<String>): NotenSystem {
            val klassen = ids.mapNotNull { id ->
                id.substringBefore("::").trim().takeWhile { it.isDigit() }.toIntOrNull()
            }
            val hoechste = klassen.maxOrNull() ?: return PUNKTE
            return if (hoechste >= ERSTE_PUNKTE_KLASSE) PUNKTE else NOTEN
        }
    }
}

/** Eine Note: je nach [system] Punkte (0–15) oder Schulnote (1–6) in [punkte]. */
data class Note(
    val system: NotenSystem = NotenSystem.PUNKTE,
    val id: String = UUID.randomUUID().toString(),
    val fach: String,
    val punkte: Int,
    val art: NotenArt,
    val datumEpochDay: Long,
    val notiz: String = ""
) {
    val datum: LocalDate get() = LocalDate.ofEpochDay(datumEpochDay)
}

/** Durchschnitt eines Fachs: Klausur- und mündlicher Schnitt sowie das gewichtete Ergebnis. */
data class FachSchnitt(
    val fach: String,
    val anzahl: Int,
    val klausur: Double?,
    val muendlich: Double?,
    val gesamt: Double,
    val system: NotenSystem = NotenSystem.PUNKTE
) {
    /** Zeugniswert (Punkte bzw. Note): ab ,5 wird zur höheren Zahl gerundet. */
    val zeugnis: Int get() = Noten.runden(gesamt, system)
}

object Noten {

    const val MAX = 15
    const val STANDARD_KLAUSUR_ANTEIL = 50
    const val ANTEIL_MIN = 20
    const val ANTEIL_MAX = 50

    /** Kaufmännisch runden (x,5 → aufwärts), begrenzt auf 0..15. */
    fun runden(wert: Double, system: NotenSystem = NotenSystem.PUNKTE): Int =
        floor(wert + 0.5).toInt().coerceIn(system.min, system.max)

    /** Punkte als Schulnote: 15 → 1,0 · 12 → 2,0 · 9 → 3,0 · 0 → 6,0. */
    fun alsSchulnote(punkte: Double): Double = 6.0 - punkte / 3.0

    /**
     * Halbjahr eines Datums: August bis Januar = 1. Halbjahr, Februar bis Juli = 2. Halbjahr.
     * Beispiel: "2025/26 · 1. HJ".
     */
    fun halbjahr(datum: LocalDate): String {
        val m = datum.monthValue
        val schuljahrStart = if (m >= 8) datum.year else datum.year - 1
        val hj = if (m >= 8 || m == 1) 1 else 2
        return "$schuljahrStart/${(schuljahrStart + 1) % 100} · $hj. HJ"
    }

    fun halbjahr(note: Note): String = halbjahr(note.datum)

    private fun schnitt(werte: List<Note>): Double? =
        if (werte.isEmpty()) null else werte.sumOf { it.punkte }.toDouble() / werte.size

    /**
     * Fachschnitt mit [klausurAnteil] Prozent Gewicht für Klausuren. Gibt es nur eine Art von Noten,
     * zählt allein diese.
     */
    fun fachSchnitt(noten: List<Note>, klausurAnteil: Int = STANDARD_KLAUSUR_ANTEIL): FachSchnitt? {
        if (noten.isEmpty()) return null
        val k = schnitt(noten.filter { it.art == NotenArt.KLAUSUR })
        val m = schnitt(noten.filter { it.art == NotenArt.MUENDLICH })
        val w = klausurAnteil.coerceIn(0, 100) / 100.0
        val gesamt = when {
            k != null && m != null -> k * w + m * (1 - w)
            k != null -> k
            else -> m ?: return null
        }
        return FachSchnitt(noten.first().fach, noten.size, k, m, gesamt, noten.first().system)
    }

    /** Gleiche Fächer unabhängig von Groß-/Kleinschreibung zusammenfassen. */
    fun fachSchluessel(fach: String): String = fach.trim().lowercase()

    fun nachFach(noten: List<Note>, klausurAnteil: Int = STANDARD_KLAUSUR_ANTEIL): List<FachSchnitt> =
        noten.groupBy { fachSchluessel(it.fach) }
            .mapNotNull { (_, liste) -> fachSchnitt(liste, klausurAnteil) }
            .sortedBy { it.fach.lowercase() }

    /** Gesamtschnitt: Mittel der Fachschnitte (jedes Fach zählt gleich). */
    fun gesamtSchnitt(faecher: List<FachSchnitt>): Double? =
        if (faecher.isEmpty()) null else faecher.sumOf { it.gesamt } / faecher.size

    /** Ergebnis der Frage "Was brauche ich noch?". */
    sealed interface Benoetigt {
        /** Mindestens [punkte] Punkte – bzw. bei Noten höchstens die Note [punkte] – erreicht das Ziel. */
        data class Punkte(val punkte: Int) : Benoetigt
        /** Das Ziel ist schon sicher, egal was kommt. */
        data object Sicher : Benoetigt
        /** Selbst mit 15 Punkten reicht es nicht. */
        data object Unmoeglich : Benoetigt
    }

    /**
     * Welche Punktzahl braucht die nächste Note der [art], damit das Fach auf [ziel] Zeugnispunkte
     * kommt (Durchschnitt ab ziel − 0,5)?
     */
    fun benoetigt(
        fachNoten: List<Note>,
        art: NotenArt,
        ziel: Int,
        klausurAnteil: Int = STANDARD_KLAUSUR_ANTEIL,
        system: NotenSystem = NotenSystem.PUNKTE
    ): Benoetigt {
        val w = klausurAnteil.coerceIn(0, 100) / 100.0
        val dieseArt = fachNoten.filter { it.art == art }
        val andere = fachNoten.filter { it.art != art }
        val summe = dieseArt.sumOf { it.punkte }.toDouble()
        val n = dieseArt.size
        val andererSchnitt = schnitt(andere)
        val gewichtDiese = if (art == NotenArt.KLAUSUR) w else 1 - w
        val gewichtAndere = 1 - gewichtDiese

        fun gesamtMit(x: Int): Double {
            val neu = (summe + x) / (n + 1)
            return if (andererSchnitt == null || gewichtDiese >= 1.0) neu
            else neu * gewichtDiese + andererSchnitt * gewichtAndere
        }

        // Punkte: Durchschnitt ab ziel − 0,5. Noten: Durchschnitt unter ziel + 0,5.
        fun erreicht(schnitt: Double) =
            if (system.hoeherIstBesser) schnitt >= ziel - 0.5 else schnitt < ziel + 0.5

        // Anteil 0: Diese Art zählt gar nicht, das Ergebnis hängt nicht von der Note ab.
        if (gewichtDiese <= 0.0 && andererSchnitt != null) {
            return if (erreicht(andererSchnitt)) Benoetigt.Sicher else Benoetigt.Unmoeglich
        }
        // Von der schlechtesten Wertung aufwärts: die erste, die reicht, ist die geforderte Mindestleistung.
        val kandidaten = system.schlechtZuGut
        kandidaten.forEachIndexed { i, x ->
            if (erreicht(gesamtMit(x))) return if (i == 0) Benoetigt.Sicher else Benoetigt.Punkte(x)
        }
        return Benoetigt.Unmoeglich
    }
}
