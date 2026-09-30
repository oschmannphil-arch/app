package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nextlesson.app.data.AufgabenStore
import com.nextlesson.app.data.Erinnerung
import com.nextlesson.app.data.ErinnerungsStore
import com.nextlesson.app.data.Hausaufgabe
import com.nextlesson.app.data.Pruefung
import com.nextlesson.app.data.PruefungsArt
import com.nextlesson.app.work.LernErinnerung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Hält Hausaufgaben, Prüfungen und die Erinnerungseinstellung.
 *
 * Das Aufräumen (abgehakte Hausaufgaben entfernen) passiert genau einmal hier im
 * Konstruktor – also beim App-Start, nicht beim Wechseln zwischen Tabs.
 */
class AufgabenViewModel(app: Application) : AndroidViewModel(app) {

    // Als Feld gehalten: getApplication() ist generisch und braucht sonst an jeder
    // Aufrufstelle eine Typangabe.
    private val kontext: Application = app

    private val store = AufgabenStore(app)
    private val erinnerungsStore = ErinnerungsStore(app)

    val hausaufgaben: StateFlow<List<Hausaufgabe>> = store.hausaufgaben
    val pruefungen: StateFlow<List<Pruefung>> = store.pruefungen

    private val _erinnerung = MutableStateFlow(erinnerungsStore.laden())
    val erinnerung: StateFlow<Erinnerung> = _erinnerung.asStateFlow()

    init {
        store.beimStartAufraeumen()
        // Falls die App länger zu war: Termin wieder scharf stellen.
        LernErinnerung.neuPlanen(app)
    }

    // ---------- Hausaufgaben ----------

    fun hausaufgabeHinzufuegen(fach: String, text: String, faellig: LocalDate?) {
        if (text.isBlank()) return
        store.hausaufgabeHinzufuegen(fach, text, faellig)
    }

    fun hausaufgabeBearbeiten(id: String, fach: String, text: String, faellig: LocalDate?) {
        if (text.isBlank()) return
        store.hausaufgabeBearbeiten(id, fach, text, faellig)
    }

    fun hausaufgabeUmschalten(id: String) = store.hausaufgabeUmschalten(id)

    fun hausaufgabeLoeschen(id: String) = store.hausaufgabeLoeschen(id)

    // ---------- Prüfungen ----------

    fun pruefungHinzufuegen(
        fach: String,
        titel: String,
        datum: LocalDate,
        art: PruefungsArt,
        notiz: String
    ) {
        if (fach.isBlank() && titel.isBlank()) return
        store.pruefungHinzufuegen(fach, titel, datum, art, notiz)
    }

    fun pruefungBearbeiten(
        id: String,
        fach: String,
        titel: String,
        datum: LocalDate,
        art: PruefungsArt,
        notiz: String
    ) {
        if (fach.isBlank() && titel.isBlank()) return
        store.pruefungBearbeiten(id, fach, titel, datum, art, notiz)
    }

    fun pruefungLoeschen(id: String) = store.pruefungLoeschen(id)

    // ---------- Erinnerung ----------

    fun erinnerungSetzen(aktiv: Boolean, stunde: Int, minute: Int) {
        val neu = Erinnerung(aktiv, stunde, minute)
        erinnerungsStore.speichern(neu)
        _erinnerung.value = neu
        LernErinnerung.neuPlanen(kontext)
    }
}
