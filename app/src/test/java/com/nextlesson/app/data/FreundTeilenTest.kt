package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreundTeilenTest {

    @Test
    fun linkUeberstehtHinUndZurueck() {
        val kurse = setOf("12/5::DEU1", "12/5::*", "10/2::MA")
        val freund = FreundTeilen.lesen(FreundTeilen.link("Zoë Müller", kurse))!!
        assertEquals("Zoë Müller", freund.name)
        assertEquals(kurse, freund.kurse)
    }

    @Test
    fun linkSteckImNachrichtentext() {
        val text = FreundTeilen.nachricht("Anna", setOf("12/5::MAT2"))
        val freund = FreundTeilen.lesen("Hey!\n$text\nBis morgen")!!
        assertEquals("Anna", freund.name)
        assertEquals(setOf("12/5::MAT2"), freund.kurse)
    }

    @Test
    fun ohneNamenGehtAuch() {
        val freund = FreundTeilen.lesen(FreundTeilen.link("", setOf("12/5::MAT2")))!!
        assertEquals("", freund.name)
    }

    @Test
    fun ungueltigesWirdAbgelehnt() {
        assertNull(FreundTeilen.lesen(null))
        assertNull(FreundTeilen.lesen("https://example.com"))
        assertNull(FreundTeilen.lesen("nextlesson://freund?name=A&kurse="))
        // Einträge, die keine Kurs-ID sind, fliegen raus.
        assertNull(FreundTeilen.lesen("nextlesson://freund?name=A&kurse=quatsch"))
        assertEquals(
            setOf("12/5::MAT2"),
            FreundTeilen.lesen("nextlesson://freund?name=A&kurse=quatsch,12%2F5%3A%3AMAT2")!!.kurse
        )
    }
}
