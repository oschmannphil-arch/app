package com.nextlesson.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuchVerlaufTest {
    @Test
    fun neuesterZuerstOhneDoppelteUndBegrenzt() {
        var v = emptyList<Treffer>()
        v = SuchVerlaufLogik.einfuegen(v, Treffer.Lehrer("Weis"))
        v = SuchVerlaufLogik.einfuegen(v, Treffer.Raum("204"))
        v = SuchVerlaufLogik.einfuegen(v, Treffer.Lehrer("Weis"))
        assertEquals(listOf(Treffer.Lehrer("Weis"), Treffer.Raum("204")), v)
        (1..20).forEach { v = SuchVerlaufLogik.einfuegen(v, Treffer.Raum("R$it")) }
        assertEquals(SuchVerlaufLogik.MAX, v.size)
        assertEquals(Treffer.Raum("R20"), v.first())
    }

    @Test
    fun schluesselWerdenGelesen() {
        assertEquals(Treffer.Lehrer("Weis"), SuchVerlaufLogik.ausSchluessel("L:Weis"))
        assertEquals(Treffer.Raum("204"), SuchVerlaufLogik.ausSchluessel("R:204"))
        assertNull(SuchVerlaufLogik.ausSchluessel("X:1"))
        assertNull(SuchVerlaufLogik.ausSchluessel("L:"))
    }
}
