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

/** Eine Note im 15-Punkte-System (0 bis 15). */
data class Note(
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
    val gesamt: Double
) {
    /** Zeugnispunkte: ab ,5 wird aufgerundet. */
    val zeugnis: Int get() = Noten.runden(gesamt)
}

object Noten {

    const val MAX = 15
    const val STANDARD_KLAUSUR_ANTEIL = 50
    const val ANTEIL_MIN = 20
    const val ANTEIL_MAX = 50

    /** Kaufmännisch runden (x,5 → aufwärts), begrenzt auf 0..15. */
    fun runden(wert: Double): Int = floor(wert + 0.5).toInt().coerceIn(0, MAX)

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
        return FachSchnitt(noten.first().fach, noten.size, k, m, gesamt)
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
        /** Mit mindestens [punkte] in der nächsten Note wird das Ziel erreicht. */
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
        klausurAnteil: Int = STANDARD_KLAUSUR_ANTEIL
    ): Benoetigt {
        val schwelle = ziel - 0.5
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

        // Anteil 0: Diese Art zählt gar nicht, das Ergebnis hängt nicht von der Note ab.
        if (gewichtDiese <= 0.0 && andererSchnitt != null) {
            return if (andererSchnitt >= schwelle) Benoetigt.Sicher else Benoetigt.Unmoeglich
        }
        if (gesamtMit(0) >= schwelle) return Benoetigt.Sicher
        for (x in 1..MAX) if (gesamtMit(x) >= schwelle) return Benoetigt.Punkte(x)
        return Benoetigt.Unmoeglich
    }
}
