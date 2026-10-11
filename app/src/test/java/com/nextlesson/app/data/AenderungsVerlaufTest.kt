package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AenderungsVerlaufTest {
    private val tag1 = LocalDate.of(2026, 10, 12)
    private val tag2 = tag1.plusDays(1)

    @Test
    fun ersterAbrufHatKeineBasis() {
        val s = VerlaufsStand.fortschreiben(null, setOf("a"), tag1)
        assertNull(s.basis)
        assertEquals(setOf("a"), s.neu)
    }

    @Test
    fun amSelbenTagBleibtDieBasisUnveraendert() {
        val s1 = VerlaufsStand.fortschreiben(null, setOf("a"), tag1)
        val s2 = VerlaufsStand.fortschreiben(s1, setOf("a", "b"), tag1)
        assertNull(s2.basis)
        assertEquals(setOf("a", "b"), s2.neu)
    }

    @Test
    fun einNeuerTagMachtDenLetztenStandZurBasis() {
        val s1 = VerlaufsStand.fortschreiben(null, setOf("a"), tag1)
        val s2 = VerlaufsStand.fortschreiben(s1, setOf("a", "b"), tag2)
        assertEquals(setOf("a"), s2.basis)
        val s3 = VerlaufsStand.fortschreiben(s2, setOf("a", "b", "c"), tag2)
        assertEquals(setOf("a"), s3.basis)
        assertEquals(setOf("a", "b", "c"), s3.neu)
    }
}
