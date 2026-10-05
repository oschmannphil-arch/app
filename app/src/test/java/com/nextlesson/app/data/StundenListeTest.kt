package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StundenListeTest {
    @Test
    fun faelltAufeinanderfolgendeZusammen() {
        assertEquals("1.–2., 5. Std", stundenListe(listOf(5, 1, 2)))
        assertEquals("3. Std", stundenListe(listOf(3)))
        assertEquals("1.–4. Std", stundenListe(listOf(1, 2, 3, 4, 4)))
    }
}
