package com.nextlesson.app.data

/**
 * Macht aus erkannten Wörtern (mit Position im Foto) Text – Tabellen bleiben dabei Tabellen:
 * Zeilen mit mehreren Spalten werden als "Zelle | Zelle | Zelle" geschrieben, die App zeigt
 * zusammenhängende Zeilen dieser Art als Tabelle an.
 */
object TabellenText {

    const val TRENNER = " | "

    /** Ein erkanntes Wort mit seinem Rahmen im Foto (Pixel). */
    data class Wort(val text: String, val links: Int, val rechts: Int, val oben: Int, val unten: Int) {
        val mitteY: Double get() = (oben + unten) / 2.0
        val hoehe: Int get() = (unten - oben).coerceAtLeast(1)
    }

    /** Spaltenabstand, ab dem zwei Wörter in verschiedene Zellen gehören (in Zeilenhöhen). */
    private const val LUECKE = 1.2

    /** Wie weit die Mitte eines Worts von der Zeilenmitte abweichen darf (in Zeilenhöhen). */
    private const val ZEILENTOLERANZ = 0.6

    fun aufbereiten(woerter: List<Wort>): String {
        val sauber = woerter.filter { it.text.isNotBlank() }
        if (sauber.isEmpty()) return ""
        val hoehe = sauber.map { it.hoehe }.sorted()[sauber.size / 2].toDouble()

        // Zeilen bilden: von oben nach unten, Wörter mit ähnlicher Mitte gehören zusammen.
        val zeilen = ArrayList<MutableList<Wort>>()
        for (w in sauber.sortedBy { it.mitteY }) {
            val letzte = zeilen.lastOrNull()
            val mitte = letzte?.map { it.mitteY }?.average()
            if (letzte != null && mitte != null && kotlin.math.abs(w.mitteY - mitte) <= hoehe * ZEILENTOLERANZ) letzte.add(w)
            else zeilen.add(mutableListOf(w))
        }

        // Je Zeile in Zellen teilen, wo die Lücke zwischen zwei Wörtern groß ist.
        val zellenProZeile = zeilen.map { zeile ->
            val sortiert = zeile.sortedBy { it.links }
            val zellen = ArrayList<MutableList<String>>()
            var vorher: Wort? = null
            for (w in sortiert) {
                if (vorher == null || w.links - vorher.rechts > hoehe * LUECKE) zellen.add(mutableListOf(w.text))
                else zellen.last().add(w.text)
                vorher = w
            }
            zellen.map { it.joinToString(" ") }
        }

        // Eine Tabelle gibt es erst, wenn mindestens zwei Zeilen mehrere Spalten haben.
        val mehrspaltig = zellenProZeile.count { it.size >= 2 }
        return zellenProZeile.joinToString("\n") { zellen ->
            if (mehrspaltig >= 2 && zellen.size >= 2) zellen.joinToString(TRENNER) else zellen.joinToString(" ")
        }
    }

    fun istTabellenZeile(zeile: String): Boolean = TRENNER in zeile

    fun zellen(zeile: String): List<String> = zeile.split("|").map { it.trim() }

    /** Teilt Text in Abschnitte: normaler Text bzw. zusammenhängende Tabellenzeilen (je Zeile die Zellen). */
    sealed class Abschnitt {
        data class Normal(val text: String) : Abschnitt()
        data class Tabelle(val zeilen: List<List<String>>) : Abschnitt()
    }

    fun abschnitte(text: String): List<Abschnitt> {
        val out = ArrayList<Abschnitt>()
        val normal = ArrayList<String>()
        val tabelle = ArrayList<List<String>>()
        fun normalAbschliessen() {
            if (normal.isNotEmpty()) out.add(Abschnitt.Normal(normal.joinToString("\n")))
            normal.clear()
        }
        fun tabelleAbschliessen() {
            if (tabelle.isNotEmpty()) out.add(Abschnitt.Tabelle(tabelle.toList()))
            tabelle.clear()
        }
        for (zeile in text.split("\n")) {
            if (istTabellenZeile(zeile)) {
                normalAbschliessen()
                tabelle.add(zellen(zeile))
            } else {
                tabelleAbschliessen()
                normal.add(zeile)
            }
        }
        normalAbschliessen()
        tabelleAbschliessen()
        return out
    }
}
