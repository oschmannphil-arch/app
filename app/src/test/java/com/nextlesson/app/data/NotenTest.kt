package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NotenTest {

    private fun n(fach: String, p: Int, art: NotenArt) =
        Note(fach = fach, punkte = p, art = art, datumEpochDay = LocalDate.of(2025, 10, 1).toEpochDay())

    @Test
    fun rundenKaufmaennisch() {
        assertEquals(10, Noten.runden(9.5))
        assertEquals(9, Noten.runden(9.49))
        assertEquals(15, Noten.runden(15.4))
        assertEquals(0, Noten.runden(-1.0))
    }

    @Test
    fun schulnote() {
        assertEquals(1.0, Noten.alsSchulnote(15.0), 0.001)
        assertEquals(2.0, Noten.alsSchulnote(12.0), 0.001)
        assertEquals(6.0, Noten.alsSchulnote(0.0), 0.001)
    }

    @Test
    fun halbjahre() {
        assertEquals("2025/26 · 1. HJ", Noten.halbjahr(LocalDate.of(2025, 9, 3)))
        assertEquals("2025/26 · 1. HJ", Noten.halbjahr(LocalDate.of(2026, 1, 20)))
        assertEquals("2025/26 · 2. HJ", Noten.halbjahr(LocalDate.of(2026, 3, 3)))
        assertEquals("2025/26 · 2. HJ", Noten.halbjahr(LocalDate.of(2026, 7, 10)))
        assertEquals("2026/27 · 1. HJ", Noten.halbjahr(LocalDate.of(2026, 8, 20)))
    }

    @Test
    fun schnittGewichtet() {
        val s = Noten.fachSchnitt(
            listOf(n("Mathe", 12, NotenArt.KLAUSUR), n("Mathe", 8, NotenArt.KLAUSUR), n("Mathe", 14, NotenArt.MUENDLICH))
        )!!
        assertEquals(10.0, s.klausur!!, 0.001)
        assertEquals(14.0, s.muendlich!!, 0.001)
        assertEquals(12.0, s.gesamt, 0.001)
        assertEquals(12, s.zeugnis)
    }

    @Test
    fun schnittMitAnderemAnteil() {
        val liste = listOf(n("Mathe", 10, NotenArt.KLAUSUR), n("Mathe", 14, NotenArt.MUENDLICH))
        assertEquals(12.8, Noten.fachSchnitt(liste, 30)!!.gesamt, 0.001)
        assertEquals(10.0, Noten.fachSchnitt(liste, 100)!!.gesamt, 0.001)
    }

    @Test
    fun nurEineArtZaehltAllein() {
        val s = Noten.fachSchnitt(listOf(n("Kunst", 13, NotenArt.MUENDLICH), n("Kunst", 11, NotenArt.MUENDLICH)))!!
        assertNull(s.klausur)
        assertEquals(12.0, s.gesamt, 0.001)
        assertNull(Noten.fachSchnitt(emptyList()))
    }

    @Test
    fun faecherZusammenfassenUndGesamt() {
        val liste = listOf(
            n("Mathe", 10, NotenArt.KLAUSUR), n("mathe ", 12, NotenArt.KLAUSUR), n("Deutsch", 14, NotenArt.MUENDLICH)
        )
        val faecher = Noten.nachFach(liste)
        assertEquals(2, faecher.size)
        assertEquals("Deutsch", faecher[0].fach)
        assertEquals(2, faecher[1].anzahl)
        assertEquals(12.5, Noten.gesamtSchnitt(faecher)!!, 0.001)
        assertNull(Noten.gesamtSchnitt(emptyList()))
    }

    @Test
    fun benoetigtPunkte() {
        // Klausuren 10, mündlich 10 → Ziel 12 (Schnitt ≥ 11,5). Neue Klausur x: (10+x)/2*0,5 + 5 ≥ 11,5 → x ≥ 16 → unmöglich
        val liste = listOf(n("M", 10, NotenArt.KLAUSUR), n("M", 10, NotenArt.MUENDLICH))
        assertEquals(Noten.Benoetigt.Unmoeglich, Noten.benoetigt(liste, NotenArt.KLAUSUR, 12))
        // Ziel 11 (≥ 10,5): (10+x)/2*0,5+5 ≥ 10,5 → x ≥ 12
        assertEquals(Noten.Benoetigt.Punkte(12), Noten.benoetigt(liste, NotenArt.KLAUSUR, 11))
        // Ziel 10 (≥ 9,5): (10+x)/2*0,5+5 ≥ 9,5 → x ≥ 8
        assertEquals(Noten.Benoetigt.Punkte(8), Noten.benoetigt(liste, NotenArt.KLAUSUR, 10))
        // Ziel 7 (≥ 6,5) ist selbst mit 0 Punkten sicher (7,5)
        assertEquals(Noten.Benoetigt.Sicher, Noten.benoetigt(liste, NotenArt.KLAUSUR, 7))
    }

    @Test
    fun benoetigtOhneAndereArt() {
        val liste = listOf(n("M", 8, NotenArt.KLAUSUR))
        // Ziel 10 (≥ 9,5): (8+x)/2 ≥ 9,5 → x ≥ 11
        assertEquals(Noten.Benoetigt.Punkte(11), Noten.benoetigt(liste, NotenArt.KLAUSUR, 10))
        assertNotNull(Noten.benoetigt(emptyList(), NotenArt.KLAUSUR, 12))
        assertEquals(Noten.Benoetigt.Punkte(12), Noten.benoetigt(emptyList(), NotenArt.KLAUSUR, 12))
    }

    @Test
    fun benoetigtBeiAnteilNull() {
        val liste = listOf(n("M", 13, NotenArt.MUENDLICH))
        assertEquals(Noten.Benoetigt.Sicher, Noten.benoetigt(liste, NotenArt.KLAUSUR, 13, 0))
        assertEquals(Noten.Benoetigt.Unmoeglich, Noten.benoetigt(liste, NotenArt.KLAUSUR, 14, 0))
    }

    private fun sn(fach: String, p: Int, art: NotenArt) =
        Note(system = NotenSystem.NOTEN, fach = fach, punkte = p, art = art, datumEpochDay = LocalDate.of(2025, 10, 1).toEpochDay())

    @Test
    fun systemAusKursen() {
        assertEquals(NotenSystem.PUNKTE, NotenSystem.ausKursIds(setOf("12/5::D1", "12/5::M2")))
        assertEquals(NotenSystem.PUNKTE, NotenSystem.ausKursIds(setOf("11/3::E1")))
        assertEquals(NotenSystem.NOTEN, NotenSystem.ausKursIds(setOf("7a::*")))
        assertEquals(NotenSystem.NOTEN, NotenSystem.ausKursIds(setOf("10b::*", "5c::Ku")))
        // Gemischt: die höchste Klasse entscheidet.
        assertEquals(NotenSystem.PUNKTE, NotenSystem.ausKursIds(setOf("10b::*", "12/5::D1")))
        // Nichts erkennbar → Punkte.
        assertEquals(NotenSystem.PUNKTE, NotenSystem.ausKursIds(emptySet()))
        assertEquals(NotenSystem.PUNKTE, NotenSystem.ausKursIds(setOf("Q1::D1")))
    }

    @Test
    fun schnittUndZeugnisBeiNoten() {
        val s = Noten.fachSchnitt(
            listOf(sn("Mathe", 2, NotenArt.KLAUSUR), sn("Mathe", 3, NotenArt.KLAUSUR), sn("Mathe", 2, NotenArt.MUENDLICH)), 50
        )!!
        assertEquals(2.25, s.gesamt, 0.001)
        assertEquals(2, s.zeugnis)
        assertEquals(NotenSystem.NOTEN, s.system)
        // Rundung bleibt in 1..6
        assertEquals(6, Noten.runden(6.4, NotenSystem.NOTEN))
        assertEquals(1, Noten.runden(0.2, NotenSystem.NOTEN))
        assertEquals(3, Noten.runden(2.5, NotenSystem.NOTEN))
    }

    @Test
    fun benoetigtBeiNoten() {
        // Klausur 3, mündlich 3, Anteil 50. Ziel 3 (Schnitt < 3,5): neue Klausur x → (3+x)/2*0,5 + 1,5 < 3,5 → x < 5 → höchstens 4
        val liste = listOf(sn("M", 3, NotenArt.KLAUSUR), sn("M", 3, NotenArt.MUENDLICH))
        assertEquals(Noten.Benoetigt.Punkte(4), Noten.benoetigt(liste, NotenArt.KLAUSUR, 3, 50, NotenSystem.NOTEN))
        // Ziel 4 (< 4,5) ist selbst mit einer 6 sicher: (3+6)/2*0,5+1,5 = 3,75
        assertEquals(Noten.Benoetigt.Sicher, Noten.benoetigt(liste, NotenArt.KLAUSUR, 4, 50, NotenSystem.NOTEN))
        // Ziel 2 (< 2,5) geht nicht: selbst mit einer 1 ergibt sich (3+1)/2*0,5+1,5 = 2,5
        assertEquals(Noten.Benoetigt.Unmoeglich, Noten.benoetigt(liste, NotenArt.KLAUSUR, 2, 50, NotenSystem.NOTEN))
    }
}
