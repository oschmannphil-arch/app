package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf(t("09:50") to t("11:00")), Freizeit.gemeinsamFrei(ich, freund).map { it.beginn to it.ende })
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
        assertEquals(listOf(t("08:45") to t("11:00")), Freizeit.gemeinsamFrei(ich, freund).map { it.beginn to it.ende })
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

    // --- Mit Zeitraster der Schule ---

    private val raster = listOf(
        Zeitfenster(1, t("07:15"), t("08:00")), Zeitfenster(2, t("08:00"), t("08:45")),
        Zeitfenster(3, t("09:05"), t("09:50")), Zeitfenster(4, t("09:50"), t("10:35")),
        Zeitfenster(5, t("11:00"), t("11:45")), Zeitfenster(6, t("11:45"), t("12:30")),
        Zeitfenster(7, t("12:45"), t("13:30"))
    )

    @Test
    fun eigeneFreistundenMitStundennummer() {
        val spaet = plan(stunde(3, "09:05", "09:50", "DEU1"), stunde(6, "11:45", "12:30", "ENG2"))
        val b = Freizeit.freiBloecke(spaet, raster)
        // 1.–2. Std vor Unterrichtsbeginn, 4.–5. Std Lücke; nach der 6. nichts mehr.
        assertEquals(listOf("1.–2. Std", "4.–5. Std"), b.map { it.stundenText })
        assertEquals(t("07:15") to t("09:05"), b[0].beginn to b[0].ende)
        assertEquals(t("09:50") to t("11:45"), b[1].beginn to b[1].ende)
    }

    @Test
    fun ausfallStehtInDerListe_istKeineFreistunde() {
        val p = plan(stunde(1, "07:15", "08:00", "A", entfaellt = true), stunde(2, "08:00", "08:45", "A"))
        assertTrue(Freizeit.freiBloecke(p, raster).isEmpty())
        assertEquals(listOf("1. Std"), Freizeit.freiBloecke(p, raster, ausfallIstFrei = true).map { it.stundenText })
    }

    @Test
    fun gemeinsamFreiAmTagesanfang() {
        // Beide haben die 1. Stunde frei und danach Unterricht → zählt.
        val a = plan(stunde(2, "08:00", "08:45", "A"), stunde(3, "09:05", "09:50", "A"))
        val b = plan(stunde(2, "08:00", "08:45", "B"), stunde(5, "11:00", "11:45", "B"))
        assertEquals(listOf("1. Std"), Freizeit.gemeinsamFrei(a, b, raster).map { it.stundenText })
    }

    @Test
    fun nachSchlussZaehltNicht() {
        // a hat nach der 2. Schluss: die 3.–4. Stunde ist nicht mehr gemeinsam frei.
        val a = plan(stunde(1, "07:15", "08:00", "A"), stunde(2, "08:00", "08:45", "A"))
        val b = plan(stunde(1, "07:15", "08:00", "B"), stunde(5, "11:00", "11:45", "B"))
        assertTrue(Freizeit.gemeinsamFrei(a, b, raster).isEmpty())
    }

    @Test
    fun gemeinsamFreiMitRaster_mitteUndAusfall() {
        val freund = plan(
            stunde(1, "07:15", "08:00", "BIO3"),
            stunde(3, "09:05", "09:50", "BIO3", entfaellt = true),
            stunde(6, "11:45", "12:30", "BIO3")
        )
        // Ich frei 3.–4., Freund frei 2.–5. → gemeinsam 3.–4. inkl. Pausen 08:45–11:00.
        val b = Freizeit.gemeinsamFrei(ich, freund, raster)
        assertEquals(listOf("3.–4. Std"), b.map { it.stundenText })
        assertEquals(t("08:45") to t("11:00"), b[0].beginn to b[0].ende)
    }

    @Test
    fun jetztFrei_nurInDerLueckeUndNichtNachSchluss() {
        // Ich: Unterricht 1., 2., 5., 6. Std – frei 08:45–11:00 (Pausen eingerechnet).
        assertEquals("3.–4. Std", Freizeit.jetztFrei(ich, raster, t("09:30"))?.stundenText)
        assertEquals("3.–4. Std", Freizeit.jetztFrei(ich, raster, t("08:50"))?.stundenText)
        assertNull(Freizeit.jetztFrei(ich, raster, t("08:30")))   // 2. Std läuft
        assertNull(Freizeit.jetztFrei(ich, raster, t("11:00")))   // 5. Std beginnt
        assertNull(Freizeit.jetztFrei(ich, raster, t("12:40")))   // Schule aus
        assertNull(Freizeit.jetztFrei(plan(), raster, t("09:30")))  // kein Unterricht = nicht da
    }

    @Test
    fun jetztFrei_ausfallZaehltAlsFrei() {
        val p = plan(
            stunde(1, "07:15", "08:00", "A"),
            stunde(3, "09:05", "09:50", "A", entfaellt = true),
            stunde(5, "11:00", "11:45", "A")
        )
        assertEquals(t("11:00"), Freizeit.jetztFrei(p, raster, t("09:20"))?.ende)
    }

    @Test
    fun gemeinsamFreiAlle_mitDreiLeuten() {
        val b = plan(
            stunde(1, "07:15", "08:00", "MAT2"), stunde(2, "08:00", "08:45", "MAT2"),
            stunde(3, "09:05", "09:50", "DEU1"), stunde(6, "11:45", "12:30", "ENG2")
        )
        val c = plan(
            stunde(1, "07:15", "08:00", "X"), stunde(2, "08:00", "08:45", "X"),
            stunde(5, "11:00", "11:45", "Y"), stunde(6, "11:45", "12:30", "Y")
        )
        // ich frei 3.–4., b frei 4.–5., c frei 3.–4. → alle drei nur in der 4. Stunde.
        val mitRaster = Freizeit.gemeinsamFreiAlle(listOf(ich, b, c), raster)
        assertEquals(listOf("4. Std"), mitRaster.map { it.stundenText })
        assertEquals(t("09:50") to t("11:00"), mitRaster[0].beginn to mitRaster[0].ende)
        // Ohne Raster dasselbe Fenster als reine Uhrzeit.
        assertEquals(
            listOf(t("09:50") to t("11:00")),
            Freizeit.gemeinsamFreiAlle(listOf(ich, b, c)).map { it.beginn to it.ende }
        )
        // Wer nicht in der Schule ist, nimmt allen die gemeinsame Zeit.
        assertTrue(Freizeit.gemeinsamFreiAlle(listOf(ich, b, plan()), raster).isEmpty())
        assertTrue(Freizeit.gemeinsamFreiAlle(emptyList(), raster).isEmpty())
    }

    @Test
    fun gleicherKursInAnderenKlassenIstZusammen() {
        val meins = plan(stunde(3, "09:05", "09:50", "DEU1").copy(lehrer = "Got", unterrichtsNr = "11"))
        // Derselbe Kurs, von einem Freund aus Klasse 12/6 gewählt: andere Klasse, andere Unterrichtsnummer.
        val seins = plan(stunde(3, "09:05", "09:50", "DEU1").copy(klasse = "12/6", lehrer = "Got", unterrichtsNr = "42"))
        assertEquals(listOf(3), Freizeit.gemeinsameStunden(meins, seins).map { it.stunde })
        assertEquals(Freizeit.zusammenKey(meins.stunden[0]), Freizeit.zusammenKey(seins.stunden[0]))
    }

    @Test
    fun anderesLehrerOderAndereZeitIstNichtZusammen() {
        val meins = plan(stunde(3, "09:05", "09:50", "DEU1").copy(lehrer = "Got"))
        val andererLehrer = plan(stunde(3, "09:05", "09:50", "DEU1").copy(klasse = "12/6", lehrer = "Mei"))
        val andereZeit = plan(stunde(4, "09:50", "10:35", "DEU1").copy(klasse = "12/6", lehrer = "Got"))
        assertTrue(Freizeit.gemeinsameStunden(meins, andererLehrer).isEmpty())
        assertTrue(Freizeit.gemeinsameStunden(meins, andereZeit).isEmpty())
    }

    @Test
    fun jetztFrei_vorDerErstenStundeIstManNochNichtDa() {
        val spaet = plan(stunde(3, "09:05", "09:50", "DEU1"), stunde(5, "11:00", "11:45", "ENG2"))
        assertNull(Freizeit.jetztFrei(spaet, raster, t("07:20")))
        assertEquals("4. Std", Freizeit.jetztFrei(spaet, raster, t("10:00"))?.stundenText)
        // 1.–2. fällt aus: Unterricht beginnt erst mit der 3. – davor nicht "frei".
        val ausfallZuerst = plan(
            stunde(1, "07:15", "08:00", "A", entfaellt = true),
            stunde(2, "08:00", "08:45", "A", entfaellt = true),
            stunde(3, "09:05", "09:50", "A"),
            stunde(5, "11:00", "11:45", "A")
        )
        assertNull(Freizeit.jetztFrei(ausfallZuerst, raster, t("07:30")))
        assertTrue(Freizeit.inDerSchule(spaet, t("10:00")))
        assertTrue(!Freizeit.inDerSchule(spaet, t("08:00")))
        assertTrue(!Freizeit.inDerSchule(spaet, t("11:45")))
    }

    @Test
    fun ohneRaster_ausfallZaehltAlsFreiWennGewuenscht() {
        val p = plan(
            stunde(1, "07:15", "08:00", "A"),
            stunde(2, "08:00", "08:45", "A", entfaellt = true),
            stunde(3, "08:45", "09:30", "A")
        )
        assertTrue(Freizeit.freiBloecke(p, emptyList()).isEmpty())
        assertEquals(
            listOf(t("08:00") to t("08:45")),
            Freizeit.freiBloecke(p, emptyList(), ausfallIstFrei = true).map { it.beginn to it.ende }
        )
        // Lehrkraft mit 1. und (ausgefallener) 5. Stunde: keine Lücke, der Tag endet nach der 1.
        val q = plan(stunde(1, "07:15", "08:00", "A"), stunde(5, "11:00", "11:45", "A", entfaellt = true))
        assertTrue(Freizeit.freiBloecke(q, emptyList(), ausfallIstFrei = true).isEmpty())
    }

    @Test
    fun gleichesKuerzelAnderesJahrgangIstNichtZusammen() {
        val elf = plan(stunde(5, "11:00", "11:45", "SPO1").copy(klasse = "11"))
        val zwoelf = plan(stunde(5, "11:00", "11:45", "SPO1").copy(klasse = "12"))
        assertTrue(Freizeit.gemeinsameStunden(elf, zwoelf).isEmpty())
        assertEquals("12", Freizeit.jahrgang("12/5"))
        assertEquals("12", Freizeit.jahrgang("12a"))
        assertEquals("Q1", Freizeit.jahrgang("Q1/2"))
    }

    @Test
    fun abweichendeKlassenzeiten_echteStundenZaehlenStattRaster() {
        // Raster: 4. 09:50–10:35, 5. 11:00–11:45. Die Klasse hat die 5. Stunde aber 10:40–11:25.
        val p = plan(stunde(3, "09:05", "09:50", "A"), stunde(5, "10:40", "11:25", "A"))
        val block = Freizeit.freiBloecke(p, raster).last()
        assertEquals("4. Std", block.stundenText)
        assertEquals(t("09:50") to t("10:40"), block.beginn to block.ende)
        // Um 10:50 sitzt man im Unterricht – nicht "gerade frei bis 11:00".
        assertNull(Freizeit.jetztFrei(p, raster, t("10:50")))
    }

    @Test
    fun freistundeStehtVorDerRichtigenStunde() {
        // Raster sagt 3. Stunde ab 09:05, die Klasse beginnt sie schon um 09:00.
        val p = plan(stunde(3, "09:00", "09:45", "A"), stunde(4, "09:50", "10:35", "A"))
        val vor = Freizeit.freiVor(p, raster)
        assertEquals(setOf(0), vor.keys)
        assertEquals("1.–2. Std", vor.getValue(0).stundenText)
        assertEquals(t("09:00"), vor.getValue(0).ende)
    }
}
