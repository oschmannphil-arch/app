package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nextlesson.app.data.AufgabenStore
import com.nextlesson.app.data.Erinnerung
import com.nextlesson.app.data.ErinnerungsStore
import com.nextlesson.app.data.Hausaufgabe
import com.nextlesson.app.data.Pruefung
import com.nextlesson.app.data.PruefungsArt
import com.nextlesson.app.data.TafelFotos
import com.nextlesson.app.work.LernErinnerung
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        TafelFotos.aufraeumen(app, store.hausaufgaben.value.map { it.id }.toSet())
        // Falls die App länger zu war: Termin wieder scharf stellen.
        LernErinnerung.neuPlanen(app)
    }

    // ---------- Hausaufgaben ----------

    /** [foto]: Es liegt ein neues Kamerabild bereit, das zur Aufgabe gehören soll. */
    fun hausaufgabeHinzufuegen(fach: String, text: String, faellig: LocalDate?, foto: Boolean = false) {
        if (text.isBlank()) return
        val id = store.hausaufgabeHinzufuegen(fach, text, faellig)
        if (foto) fotoUebernehmen(id)
    }

    fun hausaufgabeBearbeiten(id: String, fach: String, text: String, faellig: LocalDate?, foto: Boolean = false) {
        if (text.isBlank()) return
        store.hausaufgabeBearbeiten(id, fach, text, faellig)
        if (foto) fotoUebernehmen(id)
    }

    // Verkleinern kostet etwas – nicht auf dem Hauptthread.
    private fun fotoUebernehmen(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            TafelFotos.uebernehmen(kontext, id)
            _fotoVersion.value += 1
        }
    }

    private val _fotoVersion = MutableStateFlow(0)
    /** Zählt hoch, wenn ein Foto fertig gespeichert ist – damit die Liste das 📷 zeigt. */
    val fotoVersion: StateFlow<Int> = _fotoVersion.asStateFlow()

    fun hausaufgabeUmschalten(id: String) = store.hausaufgabeUmschalten(id)

    fun hausaufgabeLoeschen(id: String) {
        store.hausaufgabeLoeschen(id)
        TafelFotos.loeschen(kontext, id)
    }

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
