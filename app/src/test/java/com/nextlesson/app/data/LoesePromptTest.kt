package com.nextlesson.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class LoesePromptTest {
    @Test
    fun mathe() = assertTrue(LoesePrompt.fuer("MAT2", "").startsWith("Löse diese Mathe-Hausaufgabe"))

    @Test
    fun physikAuchMitZiffernUndGrossschreibung() {
        assertTrue(LoesePrompt.fuer("PHY1", "").contains("gegeben, gesucht"))
        assertTrue(LoesePrompt.fuer("Physik", "").contains("gegeben, gesucht"))
    }

    @Test
    fun spracheVerlangtLoesungInDerSprache() = assertTrue(LoesePrompt.fuer("ENG2", "").contains("auf Englisch"))

    @Test
    fun unbekanntesFachBekommtAllgemeinenPrompt() = assertTrue(LoesePrompt.fuer("XYZ", "").startsWith("Löse diese Hausaufgabe Schritt für Schritt"))

    @Test
    fun aufgabentextWirdAngehaengt() {
        val p = LoesePrompt.fuer("DEU1", "  Aufgabe 3  ")
        assertTrue(p.endsWith("Aufgabe: Aufgabe 3"))
        assertTrue(p.contains("unleserlich"))
    }

    @Test
    fun gesundheitIstKeineGeschichte() = assertTrue(LoesePrompt.fuer("Gesundheit", "").startsWith("Löse diese Hausaufgabe Schritt für Schritt"))

    @Test
    fun geschichte() = assertTrue(LoesePrompt.fuer("GES1", "").startsWith("Beantworte diese Geschichts-Aufgabe"))

    @Test
    fun kurzeKuerzelAusDemPlan() {
        assertTrue(LoesePrompt.fuer("Ma", "").startsWith("Löse diese Mathe"))
        assertTrue(LoesePrompt.fuer("Ph", "").contains("gegeben, gesucht"))
        assertTrue(LoesePrompt.fuer("Ek", "").startsWith("Beantworte diese Geografie"))
        assertTrue(LoesePrompt.fuer("En", "").contains("auf Englisch"))
    }

    @Test
    fun polnischIstKeinePolitik() = assertTrue(LoesePrompt.fuer("Polnisch", "").startsWith("Löse diese Hausaufgabe Schritt für Schritt"))

    @Test
    fun ohneFachAllgemein() = assertTrue(LoesePrompt.fuer("", "").startsWith("Löse diese Hausaufgabe"))
}
