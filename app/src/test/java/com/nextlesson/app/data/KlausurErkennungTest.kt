package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Klausur-/Ausfall-Erkennung an einem nachgebauten Plan (Jahrgang 12/5, Deutsch-
 * Kurse DEU1–DEU4) nach dem Muster des Originals: "Klausur!; BIO3 … fällt aus; DEU3 … fällt aus".
 */
class KlausurErkennungTest {

    private val liste = "Klausur!; BIO3 Herr Lonzer fällt aus; CHE1 Herr Gruß fällt aus; " +
        "DEU3 Frau Meier fällt aus; DEU4 Herr Wesenberg fällt aus; MAT2 Frau Weisheit fällt aus"

    private val alleDeutsch = setOf("12/5::DEU1", "12/5::DEU2", "12/5::DEU3", "12/5::DEU4")

    private fun std(fach: String, info: String, ausfall: Boolean) =
        "<Std${if (ausfall) " Ausfall=\"1\"" else ""}><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende>" +
            "<Fa>$fach</Fa><Le>X</Le><Ra></Ra><Nr></Nr><If>$info</If></Std>"

    private fun plan(zusatz: String, info: String, ausfall: Boolean): GesamtPlan {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<WplanVp><Kopf><zeitstempel>30.09.2026, 08:45</zeitstempel></Kopf>
$zusatz
<Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU2</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>DEU4</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl>${listOf("DEU1", "DEU2", "DEU3", "DEU4").joinToString("") { std(it, info, ausfall) }}</Pl>
</Kl></Klassen></WplanVp>"""
        return IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
    }

    private fun zusatz(text: String) =
        "<ZusatzInfo>" + text.split("; ").joinToString("") { "<ZiZeile>$it</ZiZeile>" } + "</ZusatzInfo>"

    private fun TagesPlan.lesson(fach: String) = stunden.first { it.fach == fach }

    @Test
    fun listeAnJederStunde_eigenerKursIstKlausur_genannteSindAusfall() {
        val tp = plan("", liste, ausfall = false).tagesplanFuer(alleDeutsch)
        for (k in listOf("DEU1", "DEU2")) {
            assertTrue("$k soll Klausur sein", tp.lesson(k).istKlausur)
            assertFalse("$k darf nicht ausfallen", tp.lesson(k).entfaellt)
        }
        for (k in listOf("DEU3", "DEU4")) {
            assertTrue("$k soll Ausfall sein", tp.lesson(k).entfaellt)
            assertFalse("$k ist keine Klausur", tp.lesson(k).istKlausur)
        }
    }

    @Test
    fun listeAlsTageshinweis_ausfallendeStundenWerdenKlausur() {
        val tp = plan(zusatz(liste), "", ausfall = true).tagesplanFuer(alleDeutsch)
        for (k in listOf("DEU1", "DEU2")) {
            assertTrue("$k soll Klausur sein", tp.lesson(k).istKlausur)
            assertFalse("$k darf nicht ausfallen", tp.lesson(k).entfaellt)
        }
        for (k in listOf("DEU3", "DEU4")) {
            assertTrue("$k soll Ausfall bleiben", tp.lesson(k).entfaellt)
        }
    }

    @Test
    fun nurAllgemeinesKlausurWort_aendertNichts() {
        val tp = plan(zusatz("Klausur!"), "", ausfall = true).tagesplanFuer(alleDeutsch)
        assertTrue(tp.stunden.all { it.entfaellt && !it.istKlausur })
    }

    @Test
    fun ohneKlausurHinweis_bleibtAusfallAusfall() {
        val tp = plan("", "DEU1 Herr Got fällt aus", ausfall = false).tagesplanFuer(alleDeutsch)
        assertTrue(tp.lesson("DEU1").entfaellt)
        assertFalse(tp.lesson("DEU1").istKlausur)
    }

    @Test
    fun nurEigenerKurs_zeigtNurEigeneStundenUndHinweise() {
        val tp = plan(zusatz(liste), "", ausfall = true).tagesplanFuer(setOf("12/5::DEU1"))
        assertEquals(listOf("DEU1"), tp.stunden.map { it.fach })
        // "Klausur!" ist allgemein und bleibt, Ausfälle fremder Kurse fliegen raus.
        assertEquals(listOf("Klausur!"), tp.hinweise)
    }

    @Test
    fun fremdesFachMitListeImHinweis_istKeinAusfall() {
        // INF1 wird in der Liste nicht genannt und ist kein Deutsch-Kurs: Die Stunde fiel
        // nur wegen "BIO3 … fällt aus" usw. im eigenen Hinweis als Entfall auf.
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>INF1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>INF1</Fa><Le>X</Le><Ra>131</Ra><Nr></Nr><If>$liste</If></Std></Pl>
</Kl></Klassen></WplanVp>"""
        val l = IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
            .tagesplanFuer(setOf("12/5::INF1")).lesson("INF1")
        assertFalse("INF1 darf nicht als Entfall erscheinen", l.entfaellt)
        assertFalse("INF1 ist nicht als Klausur belegt", l.istKlausur)
        assertEquals(LessonStatus.NORMAL, l.status)
    }

    @Test
    fun klausurBehaeltVertretungsStatus() {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>DEU1</Fa><Le LeAe="Got">Mei</Le><Ra>033</Ra><Nr></Nr><If>$liste</If></Std></Pl>
</Kl></Klassen></WplanVp>"""
        val l = IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
            .tagesplanFuer(setOf("12/5::DEU1")).lesson("DEU1")
        assertTrue(l.istKlausur)
        assertFalse(l.entfaellt)
        assertEquals(LessonStatus.VERTRETUNG, l.status)
    }

    @Test
    fun platzhalterWerdenNichtAlsAlterRaumAngezeigt() {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>PHY1</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>PHY1</Fa><Le>Luth</Le><Ra RaAe="RaGeaendert">235</Ra><Nr></Nr></Std></Pl>
</Kl></Klassen></WplanVp>"""
        val l = IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!.klassen.first().stunden.first()
        assertEquals(null, l.originalRoom)
        assertEquals(LessonStatus.RAUMAENDERUNG, l.status)
    }

    /** Ein Sport-Kurs neben den Deutsch-Kursen – [fach], [info] und Ausfall-Flag frei wählbar. */
    private fun sportPlan(fach: String, info: String, zusatz: String = "", ausfall: Boolean = false): Lesson {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf>$zusatz<Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>SPO1</KKz></Ku><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl><Std${if (ausfall) " Ausfall=\"1\"" else ""}><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>$fach</Fa><Le>X</Le><Ra></Ra><Nr></Nr><If>$info</If></Std></Pl>
</Kl></Klassen></WplanVp>"""
        return IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
            .tagesplanFuer(setOf("12/5::SPO1")).stunden.single()
    }

    @Test
    fun sportFaelltAusWegenKlausur_istAusfall() {
        for (info in listOf(
            "SPO1 fällt aus wegen Klausur",
            "SPO1 Herr X fällt aus (Klausuraufsicht)",
            "Klausur DEU1; SPO1 fällt aus"
        )) {
            val l = sportPlan("SPO1", info)
            assertTrue("'$info': Sport muss ausfallen", l.entfaellt)
            assertFalse("'$info': Sport ist keine Klausur", l.istKlausur)
        }
    }

    @Test
    fun sportMitStrichenUndKlausurListe_istAusfall() {
        val l = sportPlan("---", liste)
        assertTrue(l.entfaellt)
        assertFalse(l.istKlausur)
    }

    @Test
    fun sportAlsAusfallGenanntAmKlausurTag_istAusfall() {
        val l = sportPlan("SPO1", "", zusatz(liste + "; SPO1 Frau Y fällt aus"), ausfall = true)
        assertTrue(l.entfaellt)
        assertFalse(l.istKlausur)
    }

    @Test
    fun klausurUndAusfallImSelbenSatz_mitStrich() {
        // Ein Hinweis, zwei Aussagen: DEU1 schreibt, BIO3 fällt aus.
        val tp = plan(zusatz("Klausur DEU1, DEU2 – BIO3 Herr Lonzer fällt aus"), "", ausfall = true)
            .tagesplanFuer(alleDeutsch)
        assertTrue("DEU1 soll Klausur sein", tp.lesson("DEU1").istKlausur)
        assertFalse("DEU1 findet statt", tp.lesson("DEU1").entfaellt)
    }
}

class KlausurListeTest {

    private val liste = "Klausur!; BIO4 Frau Dreier fällt aus; DEU2 Frau Gladow fällt aus; " +
        "ENG1 Herr Niemietz fällt aus; ENG2 Frau Däumer fällt aus; ENG3 Herr Bretschneider fällt aus"

    private fun std(nr: Int, von: String, bis: String, fach: String, info: String) =
        "<Std><St>$nr</St><Beginn>$von</Beginn><Ende>$bis</Ende><Fa>$fach</Fa><Le>Däu</Le><Ra>032</Ra><Nr></Nr><If>$info</If></Std>"

    private fun plan(klausuren: String): GesamtPlan {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>ENG2</KKz></Ku><Ku><KKz>MAT2</KKz></Ku></Kurse><Pl>
${std(1, "07:15", "08:00", "MAT2", "")}
${std(3, "09:05", "09:50", "ENG2", liste)}
${std(4, "09:50", "10:35", "ENG2", liste)}
${std(5, "11:00", "11:45", "ENG2", liste)}
${std(6, "11:45", "12:30", "ENG2", liste)}
${std(7, "12:55", "13:40", "ENG2", "")}
</Pl></Kl></Klassen>$klausuren</WplanVp>"""
        return IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
    }

    private val eng2 = setOf("12/5::ENG2", "12/5::MAT2")

    @Test
    fun klausurListeMachtAusfallZurKlausur() {
        val g = plan(
            "<Klausuren><Klausur><KlJahrgang>12</KlJahrgang><KlKurs>ENG2</KlKurs>" +
                "<KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur></Klausuren>"
        )
        assertEquals(listOf(KlausurTermin("12", "ENG2", java.time.LocalTime.of(9, 5), 180)), g.klausuren)
        val tp = g.tagesplanFuer(eng2)
        // 3.–6. Stunde liegen im Zeitraum 09:05–12:05: Klausur, nicht Ausfall.
        listOf(3, 4, 5, 6).forEach { nr ->
            val l = tp.stunden.first { it.stunde == nr }
            assertTrue("$nr. Std ist Klausur", l.istKlausur)
            assertFalse("$nr. Std fällt nicht aus", l.entfaellt)
        }
        // Die 7. Stunde liegt danach – nicht Teil der Klausur.
        assertFalse(tp.stunden.first { it.stunde == 7 }.istKlausur)
        // Der Hinweis zeigt keine "fällt aus"-Namen mehr.
        assertEquals("Klausur!", tp.stunden.first { it.stunde == 3 }.hinweisKurz())
    }

    @Test
    fun klausurAndererJahrgangOderKursBetrifftMichNicht() {
        val g = plan(
            "<Klausuren><Klausur><KlJahrgang>11</KlJahrgang><KlKurs>ENG2</KlKurs>" +
                "<KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur>" +
                "<Klausur><KlJahrgang>12</KlJahrgang><KlKurs>ENG3</KlKurs>" +
                "<KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur></Klausuren>"
        )
        val l = g.tagesplanFuer(eng2).stunden.first { it.stunde == 3 }
        assertFalse(l.istKlausur)   // wie bisher: genannt = Ausfall
        assertTrue(l.entfaellt)
    }

    @Test
    fun klausurListeInDerKlasseNimmtJahrgangVonDort() {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>ENG2</KKz></Ku></Kurse><Pl>
${std(3, "09:05", "09:50", "ENG2", liste)}
</Pl><Klausuren><Klausur><Kurs>ENG2</Kurs><Beginn>9:05</Beginn><Dauer>90</Dauer></Klausur></Klausuren></Kl></Klassen></WplanVp>"""
        val g = IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!
        assertEquals("12/5", g.klausuren.single().jahrgang)
        assertTrue(g.tagesplanFuer(setOf("12/5::ENG2")).stunden.single().istKlausur)
    }
}
