package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KursMigrationTest {

    private fun k(klasse: String, kuerzel: String) = KursInfo(klasse, kuerzel, kuerzel, "X")

    private val liste = listOf(k("12/5", "MAT3"), k("12/5", "ENG1"), k("12/5", "DEU1"), k("12/5", "PHY2"), k("12/5", "PHY3"))

    @Test
    fun allesVorhandenKeineAenderung() {
        val e = KursMigration.pruefen(setOf("12/5::MAT3", "12/5::ENG1"), liste)
        assertFalse(e.hatAenderung)
    }

    @Test
    fun eindeutigerNachfolgerWirdVorgeschlagen() {
        val e = KursMigration.pruefen(setOf("12/5::MAT2", "12/5::ENG1"), liste)
        assertEquals(mapOf("12/5::MAT2" to "12/5::MAT3"), e.ersetzt)
        assertTrue(e.fehlend.isEmpty())
        assertEquals(setOf("12/5::MAT3", "12/5::ENG1"), e.neueAuswahl(setOf("12/5::MAT2", "12/5::ENG1")))
    }

    @Test
    fun nurNachfolgerEinsetzenFehlendeBleiben() {
        val gewaehlt = setOf("12/5::MAT2", "12/5::PHY1", "12/5::ENG1")
        val e = KursMigration.pruefen(gewaehlt, liste)
        assertEquals(setOf("12/5::MAT3", "12/5::PHY1", "12/5::ENG1"), e.mitNachfolgern(gewaehlt))
    }

    @Test
    fun mehrdeutigOderGanzFortWirdGemeldet() {
        val e = KursMigration.pruefen(setOf("12/5::PHY1", "12/5::KUN1"), liste)
        assertTrue(e.ersetzt.isEmpty())
        assertEquals(listOf("12/5::KUN1", "12/5::PHY1"), e.fehlend)
        assertEquals(emptySet<String>(), e.neueAuswahl(setOf("12/5::PHY1", "12/5::KUN1")))
    }

    @Test
    fun unbekannteKlasseUndGanzeKlasseBleibenUnberuehrt() {
        val e = KursMigration.pruefen(setOf("7a::*", "9b::MA1"), liste)
        assertFalse(e.hatAenderung)
        assertFalse(KursMigration.pruefen(setOf("12/5::MAT1"), emptyList()).hatAenderung)
    }
}
