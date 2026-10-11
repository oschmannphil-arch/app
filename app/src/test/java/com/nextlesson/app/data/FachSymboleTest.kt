package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FachSymboleTest {

    @Test
    fun kuerzelUndNamen() {
        assertEquals("🔢", FachSymbole.fuer("Ma"))
        assertEquals("🔢", FachSymbole.fuer("Mathematik"))
        assertEquals("📖", FachSymbole.fuer("Deutsch GK"))
        assertEquals("📖", FachSymbole.fuer("D1"))
        assertEquals("🇬🇧", FachSymbole.fuer("Englisch"))
        assertEquals("⚽", FachSymbole.fuer("Sport"))
        assertEquals("⚽", FachSymbole.fuer("Sp"))
        assertEquals("🌍", FachSymbole.fuer("Geografie"))
        assertEquals("🧬", FachSymbole.fuer("Biologie"))
    }

    @Test
    fun spanischUndSportBleibenGetrennt() {
        assertEquals("🇪🇸", FachSymbole.fuer("Spanisch"))
        assertEquals("🇪🇸", FachSymbole.fuer("Span"))
        assertEquals("⚽", FachSymbole.fuer("Spo"))
    }

    @Test
    fun unbekanntesBekommtNichts() {
        assertNull(FachSymbole.fuer("—"))
        assertNull(FachSymbole.fuer(""))
        assertNull(FachSymbole.fuer("Quatsch"))
        assertEquals("Quatsch", FachSymbole.mitSymbol("Quatsch"))
        assertEquals("🔢 Ma", FachSymbole.mitSymbol("Ma"))
    }

    @Test
    fun geschichteInAllenSchreibweisen() {
        listOf("Ge", "GE1", "Ges", "GES1", "Gesch", "Geschichte", "Gsch").forEach {
            assertEquals(it, "📜", FachSymbole.fuer(it))
        }
    }
}
