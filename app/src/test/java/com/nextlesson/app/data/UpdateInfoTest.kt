package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {
    @Test
    fun tagsWerdenGelesen() {
        assertEquals(31, UpdateInfo.buildAusTag("build-31"))
        assertEquals(7, UpdateInfo.buildAusTag(" Build-7 "))
        assertNull(UpdateInfo.buildAusTag("v1.0.3"))
        assertNull(UpdateInfo.buildAusTag("build-"))
    }

    @Test
    fun zahlenWerdenNumerischVerglichen() {
        // "build-9" ist kleiner als "build-100", auch wenn es als Text größer wäre.
        assertTrue(UpdateInfo.istNeuer(UpdateInfo.buildAusTag("build-9")!!, UpdateInfo.buildAusTag("build-100")!!))
    }

    @Test
    fun nurNeuereGelten() {
        assertTrue(UpdateInfo.istNeuer(30, 31))
        assertFalse(UpdateInfo.istNeuer(31, 31))
        assertFalse(UpdateInfo.istNeuer(32, 31))
    }
}
