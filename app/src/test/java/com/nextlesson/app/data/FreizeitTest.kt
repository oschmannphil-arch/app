package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class FreizeitTest {

    private fun t(s: String) = LocalTime.parse(s)

    private fun stunde(nr: Int, von: String, bis: String, kurs: String, entfaellt: Boolean = false) = Lesson(
        stunde = nr, beginn = t(von), ende = t(bis), fach = kurs, fachGeaendert = false,
        raum = "", raumGeaendert = false, lehrer = "", lehrerGeaendert = false, info = "",
        entfaellt = entfaellt, kursKuerzel = kurs, klasse = "12/5"
    )

    private fun plan(vararg s: Lesson) = TagesPlan(PlanKopf("", "", ""), "12/5", s.toList())

    // Raster wie an der Schule aus dem Screenshot: 1. 07:15, 2. 08:00, Pause, 3. 09:05, 4. 09:50 …
    private val ich = plan(
        stunde(1, "07:15", "08:00", "MAT2"),
        stunde(2, "08:00", "08:45", "MAT2"),
        stunde(5, "11:00", "11:45", "ges5"),
        stunde(6, "11:45", "12:30", "ges5")
    )

    @Test
    fun gemeinsameFreistunde() {
        val freund = plan(
            stunde(1, "07:15", "08:00", "MAT2"),
            stunde(2, "08:00", "08:45", "MAT2"),
            stunde(3, "09:05", "09:50", "DEU1"),
            stunde(6, "11:45", "12:30", "ENG2")
        )
        // Ich: frei 08:45–11:00; Freund: frei 09:50–11:45 → gemeinsam 09:50–11:00.
        assertEquals(listOf(t("09:50") to t("11:00")), Freizeit.gemeinsamFrei(ich, freund))
        assertEquals(listOf(1, 2), Freizeit.gemeinsameStunden(ich, freund).map { it.stunde })
    }

    @Test
    fun ausfallMachtFrei() {
        val freund = plan(
            stunde(1, "07:15", "08:00", "BIO3"),
            stunde(3, "09:05", "09:50", "BIO3", entfaellt = true),
            stunde(6, "11:45", "12:30", "BIO3")
        )
        // Freund: frei 08:00–11:45 (3. Std fällt aus); ich: frei 08:45–11:00.
        assertEquals(listOf(t("08:45") to t("11:00")), Freizeit.gemeinsamFrei(ich, freund))
    }

    @Test
    fun nurWennBeideInDerSchuleSind() {
        // Freund kommt erst zur 5. Stunde: Meine Freistunde davor zählt nicht als gemeinsam.
        val freund = plan(stunde(5, "11:00", "11:45", "SPO"), stunde(6, "11:45", "12:30", "SPO"))
        assertTrue(Freizeit.gemeinsamFrei(ich, freund).isEmpty())
    }

    @Test
    fun kurzePausenZaehlenNicht() {
        val a = plan(stunde(1, "07:15", "08:00", "A"), stunde(2, "08:20", "09:05", "A"))
        val b = plan(stunde(1, "07:15", "08:00", "B"), stunde(2, "08:20", "09:05", "B"))
        assertTrue(Freizeit.gemeinsamFrei(a, b).isEmpty())
    }

    @Test
    fun ohneUnterrichtKeineGemeinsameZeit() {
        assertTrue(Freizeit.gemeinsamFrei(ich, plan()).isEmpty())
        assertTrue(Freizeit.belegt(plan()).isEmpty())
        assertEquals(
            listOf(t("07:15") to t("08:45"), t("11:00") to t("12:30")),
            Freizeit.belegt(ich)
        )
    }
}
