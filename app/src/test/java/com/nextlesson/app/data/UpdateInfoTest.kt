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
    fun hoechsterBuildGewinnt_nichtDieReihenfolge() {
        assertEquals(100, UpdateInfo.neuester(listOf("build-9", "build-100", "build-30", "irgendwas")))
        assertNull(UpdateInfo.neuester(listOf("v1", "x")))
    }

    @Test
    fun nurNeuereGelten() {
        assertTrue(UpdateInfo.istNeuer(30, 31))
        assertFalse(UpdateInfo.istNeuer(31, 31))
        assertFalse(UpdateInfo.istNeuer(32, 31))
    }
}
