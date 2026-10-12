package com.nextlesson.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class BildAufbereitungTest {

    @Test
    fun tafelWirdZuDunklerSchriftAufHellemGrund() {
        // Dunkle Tafel (Grund 40) mit heller Kreide (220) an 10 von 100 Stellen.
        val grau = IntArray(100) { if (it % 10 == 0) 220 else 40 }
        val bg = IntArray(100) { 60 }
        val neu = TabelleHelfer.verbessern(grau, bg)
        val grund = neu[1]
        val schrift = neu[0]
        assertTrue("Grund soll hell werden: $grund", grund > 200)
        assertTrue("Schrift soll dunkel werden: $schrift", schrift < 80)
    }

    @Test
    fun helleSeiteBleibtDunkleSchriftAufHellemGrund() {
        val grau = IntArray(100) { if (it % 10 == 0) 30 else 230 }
        val bg = IntArray(100) { 210 }
        val neu = TabelleHelfer.verbessern(grau, bg)
        assertTrue(neu[0] < neu[1])
        assertTrue(neu[1] > 200)
    }

    @Test
    fun ungleichmaessigesLichtWirdAusgeglichen() {
        // Linke Hälfte hell beleuchtet (Grund 200), rechte im Schatten (Grund 100); Schrift jeweils halb so hell.
        val grau = IntArray(200) { i -> val links = i < 100; val schrift = i % 10 == 0; if (links) (if (schrift) 100 else 200) else (if (schrift) 50 else 100) }
        val bg = IntArray(200) { if (it < 100) 190 else 95 }
        val neu = TabelleHelfer.verbessern(grau, bg)
        val grundLinks = neu[1]
        val grundRechts = neu[101]
        assertTrue("Grund links/rechts ähnlich hell: $grundLinks / $grundRechts", kotlin.math.abs(grundLinks - grundRechts) < 40)
    }
}

private object TabelleHelfer {
    fun verbessern(grau: IntArray, bg: IntArray) = BildAufbereitung.verbessern(grau, bg)
}
