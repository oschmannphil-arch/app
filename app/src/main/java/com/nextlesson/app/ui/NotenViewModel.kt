package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nextlesson.app.data.Note
import com.nextlesson.app.data.NotenArt
import com.nextlesson.app.data.NotenStore
import com.nextlesson.app.data.NotenSystem
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class NotenViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NotenStore(app)

    val noten: StateFlow<List<Note>> = store.noten
    val klausurAnteile: StateFlow<Map<NotenSystem, Int>> = store.klausurAnteile

    fun anteilSetzen(system: NotenSystem, prozent: Int) = store.anteilSetzen(system, prozent)

    fun hinzufuegen(system: NotenSystem, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) =
        store.hinzufuegen(system, fach, punkte, art, datum, notiz)

    fun bearbeiten(id: String, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) =
        store.bearbeiten(id, fach, punkte, art, datum, notiz)

    fun loeschen(id: String) = store.loeschen(id)
}
