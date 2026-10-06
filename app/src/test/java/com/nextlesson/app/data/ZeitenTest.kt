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

class PruefungVorbeiTest {

    private val heute = LocalDate.of(2026, 10, 5)
    private fun klausur(tag: LocalDate, ende: LocalTime?) = Pruefung(
        fach = "DEU1", titel = "", datumEpochDay = tag.toEpochDay(),
        endeSekunden = ende?.toSecondOfDay() ?: Pruefung.KEIN_ENDE
    )

    @Test
    fun klausurIstNachIhremEndeVorbei() {
        val k = klausur(heute, LocalTime.of(8, 45))
        assertEquals(false, k.istVorbei(heute, LocalTime.of(8, 0)))
        assertEquals(true, k.istVorbei(heute, LocalTime.of(16, 6)))
        assertEquals(false, klausur(heute.plusDays(1), LocalTime.of(8, 45)).istVorbei(heute, LocalTime.of(23, 0)))
        assertEquals(true, klausur(heute.minusDays(1), null).istVorbei(heute, LocalTime.of(0, 1)))
    }

    @Test
    fun ohneEndeBleibtDerTagBisMitternacht() {
        assertEquals(false, klausur(heute, null).istVorbei(heute, LocalTime.of(23, 59)))
    }

    @Test
    fun planKlausurMerktSichDasEnde() {
        val l = Lesson(
            stunde = 1, beginn = LocalTime.of(7, 15), ende = LocalTime.of(8, 45), fach = "DEU1", fachGeaendert = false,
            raum = "", raumGeaendert = false, lehrer = "", lehrerGeaendert = false, info = "Klausur!",
            entfaellt = false, kursKuerzel = "DEU1", klasse = "12/5", istKlausur = true
        )
        val p = PlanKlausur.ausStunden(heute, listOf(l)).single().alsPruefung()
        assertEquals(LocalTime.of(8, 45).toSecondOfDay(), p.endeSekunden)
    }
}
