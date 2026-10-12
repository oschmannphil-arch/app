package com.nextlesson.app.data

import com.nextlesson.app.data.TabellenText.Wort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabellenTextTest {

    private fun w(t: String, x: Int, y: Int, breite: Int = t.length * 10) = Wort(t, x, x + breite, y, y + 20)

    @Test
    fun einfacherTextBleibtZeilenText() {
        val text = TabellenText.aufbereiten(listOf(w("Aufgabe", 0, 0), w("3", 90, 0), w("lösen", 0, 40)))
        assertEquals("Aufgabe 3\nlösen", text)
    }

    @Test
    fun tabelleWirdMitTrennernGeschrieben() {
        val text = TabellenText.aufbereiten(
            listOf(
                w("Tag", 0, 0), w("Fach", 200, 0), w("Seite", 400, 0),
                w("Mo", 0, 40), w("Mathe", 200, 40), w("12", 400, 40),
                w("Di", 0, 80), w("Englisch", 200, 80), w("7", 400, 80)
            )
        )
        assertEquals("Tag | Fach | Seite\nMo | Mathe | 12\nDi | Englisch | 7", text)
    }

    @Test
    fun einzelneZeileMitLueckeIstNochKeineTabelle() {
        val text = TabellenText.aufbereiten(listOf(w("Links", 0, 0), w("Rechts", 300, 0), w("darunter", 0, 40)))
        assertEquals("Links Rechts\ndarunter", text)
    }

    @Test
    fun woerterInEinerZelleBleibenZusammen() {
        val text = TabellenText.aufbereiten(
            listOf(
                w("Fach", 0, 0), w("Thema", 300, 0),
                w("Deutsch", 0, 40), w("Gedicht", 300, 40), w("Analyse", 380, 40)
            )
        )
        assertEquals("Fach | Thema\nDeutsch | Gedicht Analyse", text)
    }

    @Test
    fun abschnitteTrennenTabelleVonText() {
        val a = TabellenText.abschnitte("Hallo\nA | B\nC | D\nEnde")
        assertEquals(3, a.size)
        assertTrue(a[1] is TabellenText.Abschnitt.Tabelle)
        assertEquals(listOf(listOf("A", "B"), listOf("C", "D")), (a[1] as TabellenText.Abschnitt.Tabelle).zeilen)
    }
}
