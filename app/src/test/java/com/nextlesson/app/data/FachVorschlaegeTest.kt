package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FachVorschlaegeTest {
    private val alle = listOf("GES", "GEO", "Deutsch", "ENG", "Englisch", "MAT", "Religion")

    @Test
    fun geZeigtGesUndGeo() = assertEquals(listOf("GEO", "GES"), FachVorschlaege.filtern(alle, "GE"))

    @Test
    fun grossKleinschreibungEgal() = assertEquals(listOf("GEO", "GES"), FachVorschlaege.filtern(alle, "ge"))

    @Test
    fun anfangVorMitte() =
        assertEquals(listOf("ENG", "Englisch", "Deutsch", "GEO", "GES", "Religion"), FachVorschlaege.filtern(alle, "e"))

    @Test
    fun leereEingabeOderGenauerTrefferZeigtNichts() {
        assertEquals(emptyList<String>(), FachVorschlaege.filtern(alle, " "))
        assertEquals(emptyList<String>(), FachVorschlaege.filtern(alle, "MAT"))
    }
}
