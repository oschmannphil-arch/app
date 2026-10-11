package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FachNameTest {

    @Test
    fun oberstufenKursUndNormalesFachSindDasselbe() {
        assertEquals(FachName.schluessel("Geo"), FachName.schluessel("GEO2"))
        assertEquals("geo", FachName.schluessel("Geo 2"))
        assertEquals("spo", FachName.schluessel("spo2_3JU"))
        assertEquals("mathe", FachName.schluessel("Mathe LK"))
    }

    @Test
    fun zusammenfassenBevorzugtNamenOhneZiffer() {
        assertEquals(listOf("Geo", "MAT2"), FachName.zusammen(listOf("GEO2", "Geo", "MAT2", "", "—")))
        assertEquals(listOf("ENG2"), FachName.zusammen(listOf("ENG2", "eng1")))
    }

    @Test
    fun farbtonBleibtInSeinerFamilie() {
        // Braun (28°) → Grün (125°), Schieferblau (197°) → Blaugrün (150°)
        assertEquals(125f, Farbton.neu(28f, 125f, 150f), 0.01f)
        assertEquals(150f, Farbton.neu(197f, 125f, 150f), 0.01f)
        // Kleine Abweichung bleibt erhalten: Karamell 33° → 5° über dem Ziel
        assertEquals(130f, Farbton.neu(33f, 125f, 150f), 0.01f)
        // Rot-Seite (350°) bleibt warm und läuft nicht über
        assertEquals(((125f + (350f - 28f - 360f)) + 360f) % 360f, Farbton.neu(350f, 125f, 150f), 0.01f)
    }
}
