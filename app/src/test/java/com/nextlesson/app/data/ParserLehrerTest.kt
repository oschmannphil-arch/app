package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParserLehrerTest {

    private fun lesson(lehrerEl: String, info: String = ""): Lesson {
        val xml = """<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>DEU1</KKz></Ku></Kurse><Pl>
<Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>DEU1</Fa>$lehrerEl<Ra>033</Ra><Nr></Nr><If>$info</If></Std>
</Pl></Kl></Klassen></WplanVp>"""
        return IndiwareXmlParser.parse(xml.byteInputStream(), "1")!!.klassen.single().stunden.single()
    }

    @Test
    fun lehrerMitAeUndFreiNamenBleibenErhalten() {
        // "Händel" enthält "änd", "Frei" und "Eva" sind Ausfall-Stichwörter – alles echte Namen.
        listOf("Händel", "Brändle", "Frei", "Eva").forEach { name ->
            val l = lesson("<Le>$name</Le>")
            assertEquals(name, l.lehrer)
            assertFalse("$name: kein Ausfall", l.entfaellt)
        }
    }

    @Test
    fun platzhalterWerdenLeer() {
        assertEquals("", lesson("""<Le LeAe="x">LeGeaendert</Le>""").lehrer)
        assertEquals("", lesson("<Le>LeAe</Le>").lehrer)
    }

    @Test
    fun stichwortImLehrerFeldBleibtAusfall() {
        assertEquals(true, lesson("<Le>Selbstständiges Arbeiten</Le>").entfaellt)
    }
}
