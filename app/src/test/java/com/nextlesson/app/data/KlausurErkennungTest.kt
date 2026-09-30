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
    fun platzhalterWerdenNichtAlsAlterRaumAngezeigt() {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>PHY1</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>PHY1</Fa><Le>Luth</Le><Ra RaAe="RaGeaendert">235</Ra><Nr></Nr></Std></Pl>
</Kl></Klassen></WplanVp>"""
        val l = IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!.klassen.first().stunden.first()
        assertEquals(null, l.originalRoom)
        assertEquals(LessonStatus.RAUMAENDERUNG, l.status)
    }
}
