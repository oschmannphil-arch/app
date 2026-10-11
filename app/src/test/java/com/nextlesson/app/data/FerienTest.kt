package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FerienTest {

    @Test
    fun countdownVorDenFerien() {
        assertEquals("Noch 8 Tage bis zu den Herbstferien (19.10.)", Ferien.text(LocalDate.of(2026, 10, 11)))
        assertEquals("Morgen beginnen die Herbstferien", Ferien.text(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun waehrendDerFerien() {
        assertEquals("Herbstferien: noch bis 30.10.", Ferien.text(LocalDate.of(2026, 10, 19)))
        assertEquals("Herbstferien: noch bis 30.10.", Ferien.text(LocalDate.of(2026, 10, 30)))
        assertEquals("Noch 51 Tage bis zu den Weihnachtsferien (21.12.)", Ferien.text(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun zuWeitWegOderZuEnde() {
        // Mitte Mai 2027: Sommerferien (10.7.) sind 56 Tage entfernt, also noch im Vorlauf
        assertEquals("Noch 56 Tage bis zu den Sommerferien (10.7.)", Ferien.text(LocalDate.of(2027, 5, 15)))
        // Nach der letzten Eintragung gibt es nichts mehr
        assertNull(Ferien.text(LocalDate.of(2027, 9, 1)))
        // Zu weit weg: Anfang April 2027 sind die Sommerferien mehr als 60 Tage entfernt
        assertNull(Ferien.text(LocalDate.of(2027, 4, 1)))
    }
}
