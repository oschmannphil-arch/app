package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchnotesTest {

    @Test
    fun idsSindEindeutigUndAufsteigend() {
        val ids = Patchnotes.alle.map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct(), ids)
        assertTrue(Patchnotes.alle.all { it.titel.isNotBlank() && it.punkte.isNotEmpty() })
    }

    @Test
    fun nurUngeseheneNeuesteZuerst() {
        val alle = (1..5).map { PatchEintrag(it, "t", listOf("p")) }
        fun ungesehen(bis: Int, max: Int) = Patchnotes.ungesehen(bis, max, alle).map { it.id }
        assertEquals(listOf(5, 4), ungesehen(3, 3))
        assertEquals(listOf(5, 4, 3), ungesehen(0, 3))
        assertEquals(emptyList<Int>(), ungesehen(5, 3))
        // Die echte Funktion: Wer alles gesehen hat, bekommt nichts; wer nichts kennt, den neuesten.
        assertTrue(Patchnotes.ungesehen(Patchnotes.neuesteId).isEmpty())
        assertEquals(Patchnotes.neuesteId, Patchnotes.ungesehen(0).first().id)
    }
}
