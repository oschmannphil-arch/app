package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FerienTest {

    @Test
    fun countdownVorDenFerien() {
        // Gezählt wird bis Freitag, den letzten Schultag (16.10.), nicht bis zum Ferienbeginn am Montag
        assertEquals("Noch 5 Tage bis zu den Herbstferien (letzter Schultag: 16.10.)", Ferien.text(LocalDate.of(2026, 10, 11)))
        assertEquals("Morgen ist der letzte Schultag vor den Herbstferien", Ferien.text(LocalDate.of(2026, 10, 15)))
        assertEquals("Heute ist der letzte Schultag vor den Herbstferien", Ferien.text(LocalDate.of(2026, 10, 16)))
        // Am Wochenende davor
        assertEquals("Die Herbstferien beginnen am 19.10.", Ferien.text(LocalDate.of(2026, 10, 17)))
        assertEquals("Morgen beginnen die Herbstferien", Ferien.text(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun waehrendDerFerien() {
        assertEquals("Herbstferien: noch bis 30.10.", Ferien.text(LocalDate.of(2026, 10, 19)))
        assertEquals("Herbstferien: noch bis 30.10.", Ferien.text(LocalDate.of(2026, 10, 30)))
        assertEquals("Noch 48 Tage bis zu den Weihnachtsferien (letzter Schultag: 18.12.)", Ferien.text(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun zuWeitWegOderZuEnde() {
        // Mitte Mai 2027: Sommerferien (10.7.) sind 56 Tage entfernt, also noch im Vorlauf
        assertEquals("Noch 55 Tage bis zu den Sommerferien (letzter Schultag: 9.7.)", Ferien.text(LocalDate.of(2027, 5, 15)))
        // Nach der letzten Eintragung gibt es nichts mehr
        assertNull(Ferien.text(LocalDate.of(2027, 9, 1)))
        // Zu weit weg: Anfang April 2027 sind die Sommerferien mehr als 60 Tage entfernt
        assertNull(Ferien.text(LocalDate.of(2027, 4, 1)))
    }
}
