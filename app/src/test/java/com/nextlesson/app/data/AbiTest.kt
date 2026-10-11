package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbiTest {

    @Test
    fun bloecke() {
        assertEquals(600, Abi.blockI(15.0))
        assertEquals(400, Abi.blockI(10.0))
        assertEquals(0, Abi.blockI(-3.0))
        assertEquals(300, Abi.blockII(listOf(15, 15, 15, 15, 15)))
        assertEquals(200, Abi.blockII(listOf(10, 10, 10, 10, 10)))
    }

    @Test
    fun noteAusPunkten() {
        assertEquals(1.0, Abi.note(900)!!, 0.001)
        assertEquals(1.0, Abi.note(840)!!, 0.001)
        assertEquals(1.7, Abi.note(720)!!, 0.001)
        assertEquals(2.0, Abi.note(660)!!, 0.001)
        assertEquals(4.0, Abi.note(300)!!, 0.001)
        assertNull(Abi.note(299))
    }

    @Test
    fun bestehen() {
        assertTrue(Abi.bestanden(400, 200))
        assertFalse(Abi.bestanden(199, 300))
        assertFalse(Abi.bestanden(500, 99))
        assertFalse(Abi.bestanden(250, 40))
    }
}
