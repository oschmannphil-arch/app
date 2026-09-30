package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Die Tagesbeschriftung im Widget wird beim Zeichnen berechnet, nicht beim Speichern. */
class WidgetTagLabelTest {

    private val dienstag = LocalDate.of(2026, 10, 6)

    @Test
    fun heuteOhneBeschriftung() {
        assertNull(WidgetDataStore.tagLabel(dienstag, heute = dienstag))
    }

    @Test
    fun morgen() {
        assertEquals("morgen", WidgetDataStore.tagLabel(dienstag, heute = dienstag.minusDays(1)))
    }

    @Test
    fun nachMitternachtWirdAusMorgenHeute() {
        // Am Montagabend gespeichert ("morgen"), am Dienstagmorgen gezeichnet: keine Beschriftung.
        val gespeichertFuer = dienstag
        assertEquals("morgen", WidgetDataStore.tagLabel(gespeichertFuer, heute = dienstag.minusDays(1)))
        assertNull(WidgetDataStore.tagLabel(gespeichertFuer, heute = dienstag))
    }
}
