package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class BloeckeTest {

    private fun t(s: String) = LocalTime.parse(s)

    private fun std(nr: Int, von: String, bis: String, fach: String, lehrer: String = "Got", raum: String = "033") = Lesson(
        stunde = nr, beginn = t(von), ende = t(bis), fach = fach, fachGeaendert = false,
        raum = raum, raumGeaendert = false, lehrer = lehrer, lehrerGeaendert = false, info = "",
        entfaellt = false, kursKuerzel = fach, klasse = "12/5"
    )

    @Test
    fun doppelstundeWirdEinBlock() {
        val b = listOf(
            std(1, "07:15", "08:00", "MAT2"), std(2, "08:00", "08:45", "MAT2"),
            std(3, "09:05", "09:50", "DEU1")
        ).alsBloecke()
        assertEquals(listOf("1.–2. Std", "3. Std"), b.map { it.stundenText })
        assertEquals(t("07:15") to t("08:45"), b[0].zusammengefasst.beginn to b[0].zusammengefasst.ende)
        assertEquals(listOf(0, 2), b.map { it.ersteIndex })
        assertEquals("1.–2.", b[0].stundenKurz)
    }

    @Test
    fun grossePauseOderAndereDetailsTrennen() {
        // 2. → 3. Stunde mit 20 Minuten Pause: kein Block.
        assertEquals(
            2,
            listOf(std(2, "08:00", "08:45", "MAT2"), std(3, "09:05", "09:50", "MAT2")).alsBloecke().size
        )
        // Anderer Raum oder andere Lehrkraft: getrennt.
        assertEquals(2, listOf(std(1, "07:15", "08:00", "A"), std(2, "08:00", "08:45", "A", raum = "034")).alsBloecke().size)
        assertEquals(2, listOf(std(1, "07:15", "08:00", "A"), std(2, "08:00", "08:45", "A", lehrer = "Mei")).alsBloecke().size)
        // Lücke in den Nummern: getrennt.
        assertEquals(2, listOf(std(1, "07:15", "08:00", "A"), std(3, "08:00", "08:45", "A")).alsBloecke().size)
    }

    @Test
    fun paralleleKurseWerdenEinzelnVerfolgt() {
        val b = listOf(
            std(1, "07:15", "08:00", "DEU1"), std(1, "07:15", "08:00", "MAT2"),
            std(2, "08:00", "08:45", "DEU1"), std(2, "08:00", "08:45", "MAT2")
        ).alsBloecke()
        assertEquals(listOf("1.–2. Std", "1.–2. Std"), b.map { it.stundenText })
        assertEquals(listOf("DEU1", "MAT2"), b.map { it.erste.fach })
    }

    @Test
    fun ausfallUndNormalWerdenNichtVermischt() {
        val b = listOf(std(1, "07:15", "08:00", "A"), std(2, "08:00", "08:45", "A").copy(entfaellt = true)).alsBloecke()
        assertEquals(2, b.size)
    }
}
