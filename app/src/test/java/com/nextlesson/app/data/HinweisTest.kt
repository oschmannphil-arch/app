package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class HinweisTest {

    private fun std(fach: String, info: String) = Lesson(
        stunde = 1, beginn = LocalTime.of(7, 15), ende = LocalTime.of(8, 0), fach = fach, fachGeaendert = false,
        raum = "028", raumGeaendert = false, lehrer = "Ilgst", lehrerGeaendert = false, info = info,
        entfaellt = false, kursKuerzel = fach, klasse = "12/5"
    )

    private val liste = "Klausur!; BIO3 Herr Lonzer fällt aus; CHE1 Herr Gruß fällt aus; " +
        "DEU3 Frau Meier fällt aus; DEU4 Herr Wesenberg fällt aus; MAT2 Frau Weisheit fällt aus"

    @Test
    fun ausfaelleAndererKurseFliegenRaus() {
        assertEquals("Klausur!", std("deu1", liste).hinweisKurz())
    }

    @Test
    fun eigenerAusfallUndAllgemeinesBleiben() {
        assertEquals("Klausur!; DEU3 Frau Meier fällt aus", std("DEU3", liste).hinweisKurz())
        assertEquals("Aufgaben in Moodle", std("MAT2", "Aufgaben in Moodle").hinweisKurz())
        assertEquals("fällt aus", std("MAT2", "fällt aus").hinweisKurz())
        assertEquals("", std("MAT2", "").hinweisKurz())
    }
}
