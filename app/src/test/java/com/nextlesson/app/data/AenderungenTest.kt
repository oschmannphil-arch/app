package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AenderungenTest {

    private val dienstag = LocalDate.of(2026, 10, 6)

    private fun stunde(
        nr: Int, fach: String = "DEU1", lehrer: String = "Got", raum: String = "033",
        entfaellt: Boolean = false, lehrerGeaendert: Boolean = false, raumGeaendert: Boolean = false,
        originalTeacher: String? = null, originalRoom: String? = null,
        istKlausur: Boolean = false, info: String = "", ende: String = "08:00"
    ) = Lesson(
        stunde = nr, beginn = LocalTime.parse(ende).minusMinutes(45), ende = LocalTime.parse(ende),
        fach = fach, fachGeaendert = false, raum = raum, raumGeaendert = raumGeaendert,
        originalRoom = originalRoom, lehrer = lehrer, lehrerGeaendert = lehrerGeaendert,
        originalTeacher = originalTeacher, info = info, entfaellt = entfaellt,
        kursKuerzel = fach, klasse = "12/5", istKlausur = istKlausur
    )

    @Test
    fun erkenntAlleDreiArten() {
        val a = Aenderung.von(
            listOf(
                stunde(1, entfaellt = true),
                stunde(2, lehrer = "Mei", lehrerGeaendert = true),
                stunde(3, raum = "235", raumGeaendert = true),
                stunde(4, lehrer = "Mei", lehrerGeaendert = true, raum = "235", raumGeaendert = true),
                stunde(5)
            )
        )
        assertEquals(
            listOf(
                1 to AenderungsArt.ENTFALL,
                2 to AenderungsArt.VERTRETUNG,
                3 to AenderungsArt.RAUM,
                4 to AenderungsArt.VERTRETUNG,
                4 to AenderungsArt.RAUM
            ),
            a.map { it.lesson.stunde to it.art }
        )
    }

    @Test
    fun klausurAufsichtIstKeineVertretung() {
        val a = Aenderung.von(listOf(stunde(1, lehrer = "Mei", lehrerGeaendert = true, istKlausur = true)))
        assertTrue(a.isEmpty())
    }

    @Test
    fun ersterAbrufMeldetNichts() {
        val aktuell = Aenderung.von(listOf(stunde(1, entfaellt = true)))
        assertTrue(Aenderung.neue(null, aktuell).isEmpty())
    }

    @Test
    fun nurNeuesWirdGemeldet_undErneuterRaumwechselZaehltNeu() {
        val vorher = Aenderung.von(listOf(stunde(3, raum = "235", raumGeaendert = true)))
        val bekannt = vorher.map { it.schluessel }.toSet()

        assertTrue(Aenderung.neue(bekannt, vorher).isEmpty())

        val nachher = Aenderung.von(listOf(stunde(3, raum = "240", raumGeaendert = true)))
        assertEquals(1, Aenderung.neue(bekannt, nachher).size)
    }

    @Test
    fun alterSpeicherstand_meldetNurNeuenAusfall() {
        // Ältere Versionen haben sich nur die Kennungen ausgefallener Stunden gemerkt.
        val alt = setOf(stunde(1, entfaellt = true).kennung())
        val aktuell = Aenderung.von(
            listOf(
                stunde(1, entfaellt = true),                          // schon bekannt
                stunde(2, lehrer = "Mei", lehrerGeaendert = true),    // gab es evtl. schon länger
                stunde(3, fach = "MAT2", entfaellt = true)            // wirklich neu
            )
        )
        val neu = Aenderung.neue(Aenderung.ausAltemStand(alt, aktuell), aktuell)
        assertEquals(listOf(3 to AenderungsArt.ENTFALL), neu.map { it.lesson.stunde to it.art })
    }

    @Test
    fun vorbeiIstNichtMehrRelevant() {
        val a = Aenderung.von(listOf(stunde(1, entfaellt = true, ende = "08:00"))).single()
        assertFalse(a.nochRelevant(dienstag, dienstag, LocalTime.of(9, 0)))
        assertTrue(a.nochRelevant(dienstag, dienstag, LocalTime.of(7, 30)))
        assertTrue(a.nochRelevant(dienstag.plusDays(1), dienstag, LocalTime.of(20, 0)))
        assertFalse(a.nochRelevant(dienstag.minusDays(1), dienstag, LocalTime.of(7, 0)))
    }

    @Test
    fun texte() {
        val ausfall = Aenderung.von(listOf(stunde(1, entfaellt = true))).single()
        val vertretung = Aenderung.von(
            listOf(stunde(2, lehrer = "Mei", lehrerGeaendert = true, originalTeacher = "Got"))
        ).single()
        val raum = Aenderung.von(
            listOf(stunde(5, fach = "PHY1", raum = "235", raumGeaendert = true, originalRoom = "226"))
        ).single()
        val montag = dienstag.minusDays(1)

        assertEquals("DEU1 fällt morgen aus", AenderungsText.titel(listOf(ausfall), dienstag, montag))
        assertEquals("DEU1 heute: Vertretung bei Mei", AenderungsText.titel(listOf(vertretung), dienstag, dienstag))
        assertEquals("PHY1 morgen in Raum 235", AenderungsText.titel(listOf(raum), dienstag, montag))
        assertEquals("2 Änderungen heute", AenderungsText.titel(listOf(ausfall, raum), dienstag, dienstag))
        assertEquals(
            "1. Std DEU1: fällt aus\n2. Std DEU1: Vertretung bei Mei statt Got\n5. Std PHY1: Raum 235 statt 226",
            AenderungsText.text(listOf(raum, ausfall, vertretung))
        )
    }

    @Test
    fun langerSammelhinweisLandetNichtImTitel() {
        val l = stunde(1, fach = "---", entfaellt = true, info = "Klausur!; BIO3 Herr Lonzer fällt aus; CHE1 Herr Gruß fällt aus")
            .copy(kursKuerzel = null)
        val a = Aenderung.von(listOf(l)).single()
        assertEquals("Unterricht fällt heute aus", AenderungsText.titel(listOf(a), dienstag, dienstag))
        assertEquals("1. Std: fällt aus", AenderungsText.zeile(a))
    }
}
