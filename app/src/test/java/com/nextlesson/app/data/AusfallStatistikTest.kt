package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AusfallStatistikTest {

    private fun l(stunde: Int, fach: String, aus: Boolean = false, klausur: Boolean = false) = Lesson(
        stunde = stunde, beginn = null, ende = null, fach = fach, fachGeaendert = false, raum = "", raumGeaendert = false,
        lehrer = "", lehrerGeaendert = false, info = "", entfaellt = aus, istKlausur = klausur
    )

    @Test
    fun kodierenZaehltAusfaelleOhneKlausur() {
        val s = AusfallLogik.kodieren(listOf(l(1, "Ma"), l(2, "De", aus = true), l(3, "En", aus = true, klausur = true), l(4, "Sp", aus = true)))
        assertEquals("4;2|De,4|Sp", s)
    }

    @Test
    fun fachNameAuchBeiLeeremFach() {
        // Ausfall: im Fach steht nichts, aber das Kurskürzel ist bekannt
        assertEquals("PHY1", AusfallLogik.ausfallName(l(1, "", aus = true).copy(kursKuerzel = "PHY1")))
        // Weder Fach noch Kürzel: aus dem Hinweis
        assertEquals("MAT2", AusfallLogik.ausfallName(l(1, "---", aus = true).copy(info = "MAT2 fällt aus")))
        assertEquals("", AusfallLogik.ausfallName(l(1, "", aus = true)))
        assertEquals("2;2|PHY1", AusfallLogik.kodieren(listOf(l(1, "Ma"), l(2, "", aus = true).copy(kursKuerzel = "PHY1"))))
    }

    @Test
    fun geoUndGeo2ZaehlenAlsEinFach() {
        val e = mapOf("2026-10-12" to "6;1|GEO2,2|Geo", "2026-10-13" to "6;1|De")
        val r = AusfallLogik.auswerten(e, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 31), LocalDate.of(2026, 10, 14))
        assertEquals(listOf("Geo" to 2, "De" to 1), r.proFach)
    }

    @Test
    fun auswertenVergangenheitUndZukunft() {
        val heute = LocalDate.of(2026, 10, 14)
        val e = mapOf(
            "2026-10-12" to "6;2|De,4|Sp",
            "2026-10-13" to "5;",
            "2026-10-14" to "6;1|De",
            "2026-10-16" to "6;3|Ma,4|Ma",
            "2026-02-01" to "6;1|Ma"
        )
        val r = AusfallLogik.auswerten(e, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 31), heute)
        assertEquals(3, r.tage)
        assertEquals(17, r.stundenGesamt)
        assertEquals(3, r.ausgefallen)
        assertEquals(2, r.kommend)
        assertEquals(18, r.anteilProzent)
        assertEquals(listOf("De" to 2, "Sp" to 1), r.proFach)
    }
}
