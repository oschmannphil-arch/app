package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SucheTest {

    private val dienstag = LocalDate.of(2026, 10, 6)

    private fun std(nr: Int, von: String, bis: String, fach: String, le: String, ra: String, info: String = "") =
        "<Std><St>$nr</St><Beginn>$von</Beginn><Ende>$bis</Ende><Fa>$fach</Fa><Le>$le</Le>" +
            "<Ra>$ra</Ra><Nr></Nr><If>$info</If></Std>"

    /** Zwei Klassen; Weis unterrichtet in beiden, Raum 226 ist in der 3. Stunde frei. */
    private fun schultag(): SchulTag {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>MAT2</KKz></Ku><Ku><KKz>PHY1</KKz></Ku></Kurse><Pl>
${std(1, "07:15", "08:00", "MAT2", "Weis", "226")}
${std(2, "08:00", "08:45", "MAT2", "Weis", "226")}
${std(3, "09:05", "09:50", "PHY1", "Luth", "235")}
${std(4, "09:50", "10:35", "---", "---", "", "PHY1 fällt aus")}
</Pl></Kl>
<Kl><Kurz>10/2</Kurz><Kurse><Ku><KKz>MA</KKz></Ku></Kurse><Pl>
${std(4, "09:50", "10:35", "MA", "Weis", "121")}
${std(5, "10:55", "11:40", "DE", "Schö", "204 205")}
</Pl></Kl>
</Klassen></WplanVp>"""
        return SchulTag(dienstag, IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!)
    }

    @Test
    fun sucheFindetLehrerUndRaeume_exaktZuerst() {
        val t = schultag()
        assertEquals(listOf(Treffer.Lehrer("Weis")), t.suche("weis"))
        assertEquals(listOf(Treffer.Raum("226")), t.suche("226"))
        // "2" passt auf mehrere Räume; die mit "2" anfangen, kommen vor denen, die "2" nur enthalten.
        assertEquals(listOf("204", "205", "226", "235", "121"), t.suche("2").map { it.name })
        assertTrue(t.suche("   ").isEmpty())
    }

    @Test
    fun mehrereRaeumeMitLeerzeichenWerdenGetrennt() {
        assertEquals(listOf("204", "205"), SchulTag.raeumeVon("204 205"))
        assertEquals(listOf("SH 1"), SchulTag.raeumeVon("SH 1"))
        assertEquals(listOf("TH1", "TH2"), SchulTag.raeumeVon("TH1, TH2"))
        assertTrue(SchulTag.raeumeVon("---").isEmpty())
        assertEquals(listOf("Got", "Mei"), SchulTag.lehrerVon("Got Mei"))
        assertTrue(SchulTag.lehrerVon("---").isEmpty())
    }

    @Test
    fun wasMachtWeisGerade() {
        val t = schultag()
        val weis = Treffer.Lehrer("Weis")
        val jetzt = t.belegung(weis, LocalTime.of(8, 10)) as Belegung.Jetzt
        assertEquals("226", jetzt.lesson.raum)

        // In der Pause nach der 2. Stunde: frei, als Nächstes 4. Stunde in 121 (andere Klasse).
        val frei = t.belegung(weis, LocalTime.of(8, 50)) as Belegung.Frei
        assertEquals("121", frei.naechste?.raum)

        assertNull((t.belegung(weis, LocalTime.of(12, 0)) as Belegung.Frei).naechste)
    }

    @Test
    fun freieRaeume_amLaengstenFreiZuerst() {
        val t = schultag()
        // 09:10 (3. Stunde): 235 belegt; 226 heute nicht mehr belegt; 121 ab 09:50; 204/205 ab 10:55.
        val frei = t.freieRaeume(LocalTime.of(9, 10))!!
        assertEquals(listOf("226", "204", "205", "121"), frei.map { it.first })
        assertNull(frei.first().second)
        // Vor Unterrichtsbeginn und danach gibt es keine sinnvolle Aussage.
        assertNull(t.freieRaeume(LocalTime.of(6, 0)))
        assertNull(t.freieRaeume(LocalTime.of(15, 0)))
    }

    @Test
    fun tagesablaufRaumMitFreienStunden() {
        val t = schultag()
        val zeilen = t.tagesablauf(Treffer.Raum("226"))
        assertEquals(listOf(1, 2, 3, 4, 5), zeilen.map { it.first.stunde })
        assertEquals(listOf(1, 1, 0, 0, 0), zeilen.map { it.second.size })
        assertEquals(LocalTime.of(9, 5), zeilen[2].first.beginn)
    }

    @Test
    fun tagesablaufLehrerOhneLeereRaender() {
        val zeilen = schultag().tagesablauf(Treffer.Lehrer("Weis"))
        assertEquals(listOf(1, 2, 3, 4), zeilen.map { it.first.stunde })
    }

    @Test
    fun klausurAufsichtIstNichtFrei() {
        val liste = "Klausur!; DEU3 Frau Meier fällt aus"
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku></Kurse><Pl>
${std(1, "07:15", "08:00", "DEU1", "Got", "033", liste)}
${std(1, "07:15", "08:00", "DEU3", "Mei", "031", liste)}
</Pl></Kl></Klassen></WplanVp>"""
        val t = SchulTag(dienstag, IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!)
        // Got beaufsichtigt die Klausur in 033 …
        val got = t.belegung(Treffer.Lehrer("Got"), LocalTime.of(7, 30)) as Belegung.Jetzt
        assertTrue(got.lesson.istKlausur)
        // … Meiers Kurs fällt dagegen wirklich aus.
        assertTrue(t.belegung(Treffer.Lehrer("Mei"), LocalTime.of(7, 30)) is Belegung.Frei)
    }

    @Test
    fun schultageUeberspringenDasWochenende() {
        val freitag = LocalDate.of(2026, 10, 9)
        assertEquals(LocalDate.of(2026, 10, 12), schultagVersetzt(freitag, 1))
        assertEquals(freitag, schultagVersetzt(LocalDate.of(2026, 10, 12), -1))
        assertEquals(LocalDate.of(2026, 10, 12), ersterSchultag(LocalDate.of(2026, 10, 10)))
        assertEquals(dienstag, ersterSchultag(dienstag))
    }

    @Test
    fun lehrerWocheLueckenUndUnterricht() {
        val t = schultag()
        val weis = Treffer.Lehrer("Weis")
        // Weis unterrichtet 1., 2. (12/5) und 4. (10/2); dazwischen ist die 3. Stunde frei.
        assertEquals(listOf(1, 2, 4), t.unterrichtsStunden(weis))
        assertEquals(listOf("3. Std"), t.luecken(weis).map { it.stundenText })
        // Luth: nur die 3. Stunde – davor ist nicht "Lücke", sondern noch nicht da.
        assertTrue(t.luecken(Treffer.Lehrer("Luth")).isEmpty())
        assertTrue(t.luecken(Treffer.Lehrer("gibtEsNicht")).isEmpty())
    }

    @Test
    fun raumGruppenZumFiltern() {
        assertEquals("Etage 2", raumGruppe("204"))
        assertEquals("EG", raumGruppe("033"))
        assertEquals("SH", raumGruppe("SH 1"))
        assertEquals("A · Etage 1", raumGruppe("A101"))
        assertEquals("Sonstige", raumGruppe("12"))
    }

    @Test
    fun wochenReferenzSpringtAmWochenende() {
        val samstag = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 12), wochenReferenz(0, samstag))
        assertEquals(LocalDate.of(2026, 10, 19), wochenReferenz(1, samstag))
        assertEquals(LocalDate.of(2026, 10, 5), wochenReferenz(-1, samstag))
        assertEquals(dienstag, wochenReferenz(0, dienstag))
    }
}
