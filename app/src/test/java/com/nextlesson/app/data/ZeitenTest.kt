package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ZeitenTest {

    private val min = 60_000L

    @Test
    fun dauerMitEinheit() {
        assertEquals("12 Min", dauerText(12))
        assertEquals("2 Std", dauerText(120))
        assertEquals("1 Std 20 Min", dauerText(80))
    }

    @Test
    fun countdown() {
        // Läuft seit 10 Minuten, noch 35 Minuten.
        assertEquals("läuft noch 35 Min", countdownText(-10 * min, 35 * min))
        assertEquals("noch 35 Min", countdownText(-10 * min, 35 * min, laeuftPraefix = "noch"))
        assertEquals("läuft noch 1 Min", countdownText(-44 * min, 30_000L))
        // Aufrunden: 8 Min 20 s → 9 Min.
        assertEquals("in 9 Min", countdownText(8 * min + 20_000, 45 * min))
        assertEquals("in 1 Std 20 Min", countdownText(80 * min, null))
        assertEquals("jetzt", countdownText(0L, null))
        assertNull(countdownText(-1L, null))             // vorbei, Ende unbekannt
        assertNull(countdownText(-1L, -1L))              // vorbei
        assertNull(countdownText(601 * min, null))       // zu weit weg
    }

    @Test
    fun erinnerungAmTagDerZeitumstellung() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Sa 24.10.2026 17:00:01 → So 25.10. 17:00 sind 25 Stunden (Ende der Sommerzeit).
        val jetzt = ZonedDateTime.of(LocalDate.of(2026, 10, 24), LocalTime.of(17, 0, 1), berlin)
        assertEquals(25 * 3600 - 1L, verzoegerungBis(LocalTime.of(17, 0), jetzt).seconds)
        // Noch vor 17 Uhr: heute.
        val frueh = ZonedDateTime.of(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0), berlin)
        assertEquals(8 * 3600L, verzoegerungBis(LocalTime.of(17, 0), frueh).seconds)
    }
}
