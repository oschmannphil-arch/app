package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TagesPlanFunktionenTest {

    private fun stunde(
        nr: Int, von: String, bis: String, fach: String = "MAT2",
        kurs: String? = "MAT2", entfaellt: Boolean = false
    ) = Lesson(
        stunde = nr, beginn = LocalTime.parse(von), ende = LocalTime.parse(bis),
        fach = fach, fachGeaendert = false, raum = "226", raumGeaendert = false,
        lehrer = "Weis", lehrerGeaendert = false, info = "", entfaellt = entfaellt,
        kursKuerzel = kurs, klasse = "12/5"
    )

    private fun plan(vararg s: Lesson) = TagesPlan(PlanKopf("", "", ""), "12/5", s.toList())

    @Test
    fun freistundeZwischenZweiStunden() {
        val p = plan(
            stunde(1, "07:15", "08:00"),
            stunde(2, "08:00", "08:45"),
            stunde(4, "09:50", "10:35", fach = "GES5", kurs = "ges5")
        )
        assertEquals(listOf(Triple(2, LocalTime.of(8, 45), LocalTime.of(9, 50))), p.freistunden())
    }

    @Test
    fun normalePauseIstKeineFreistunde() {
        val p = plan(stunde(1, "07:15", "08:00"), stunde(2, "08:20", "09:05"))
        assertTrue(p.freistunden().isEmpty())
    }

    @Test
    fun schlussIgnoriertAusgefalleneLetzteStunde() {
        val p = plan(
            stunde(1, "07:15", "08:00"),
            stunde(7, "13:40", "14:25", fach = "---", kurs = null, entfaellt = true)
        )
        assertEquals(LocalTime.of(8, 0), p.schluss())
        assertNull(plan().schluss())
    }

    @Test
    fun hatStundeVonVergleichtKurs() {
        val vorbild = stunde(1, "07:15", "08:00")
        assertTrue(plan(stunde(3, "09:05", "09:50")).hatStundeVon(vorbild))
        assertFalse(plan(stunde(3, "09:05", "09:50", fach = "DEU1", kurs = "DEU1")).hatStundeVon(vorbild))
        // Fällt die Stunde aus, ist das nicht "die nächste Stunde".
        assertFalse(plan(stunde(3, "09:05", "09:50", entfaellt = true)).hatStundeVon(vorbild))
    }
}
