package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.FreundeStore
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.KursSelectionStore
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.Zeitfenster
import com.nextlesson.app.data.tagesFehler
import com.nextlesson.app.data.wochenReferenz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Ein Schultag der Woche: dein Plan und die deiner Freunde (nach Freund-ID), schon gefiltert –
 * das Filtern des Schulplans ist teuer und soll nicht beim Antippen eines Freundes passieren.
 */
data class WocheTag(
    val datum: LocalDate,
    val raster: List<Zeitfenster> = emptyList(),
    val eigen: TagesPlan? = null,
    val freunde: Map<String, TagesPlan> = emptyMap(),
    val fehler: String? = null
)

class FreundeViewModel(app: Application, private val zustand: SavedStateHandle) : AndroidViewModel(app) {

    private val store = FreundeStore(app)
    private val credentialsStore by lazy { CredentialsStore(app) }
    private val kursStore = KursSelectionStore(app)
    private val repository = IndiwareRepository(app)

    val freunde: StateFlow<List<Freund>> = store.freunde

    fun speichern(freund: Freund) = store.speichern(freund)

    fun loeschen(id: String) = store.loeschen(id)

    /**
     * Freund aus einem geteilten Link, der noch bestätigt (und ggf. benannt) werden muss.
     * Im SavedStateHandle, damit er auch übersteht, dass Android die App im Hintergrund beendet.
     */
    private val _importVorschlag = MutableStateFlow(gespeicherterVorschlag())
    val importVorschlag: StateFlow<Freund?> = _importVorschlag.asStateFlow()

    /** Kurze Meldung für den Nutzer (z.B. "Anna ist schon gespeichert"); null = keine. */
    private val _hinweis = MutableStateFlow<String?>(null)
    val hinweis: StateFlow<String?> = _hinweis.asStateFlow()

    fun hinweisGezeigt() {
        _hinweis.value = null
    }

    /** Schlägt [freund] zum Speichern vor – außer, genau diesen Freund gibt es schon. */
    fun importVorschlagen(freund: Freund) {
        val vorhanden = store.freunde.value.firstOrNull {
            it.kurse == freund.kurse && it.name.trim().equals(freund.name.trim(), ignoreCase = true)
        }
        if (vorhanden != null) {
            _hinweis.value = "${vorhanden.name.ifBlank { "Dieser Freund" }} ist schon gespeichert."
            return
        }
        vorschlagSetzen(freund)
    }

    fun importVerwerfen() = vorschlagSetzen(null)

    private fun vorschlagSetzen(freund: Freund?) {
        _importVorschlag.value = freund
        zustand[KEY_ID] = freund?.id
        zustand[KEY_NAME] = freund?.name
        zustand[KEY_KURSE] = freund?.kurse?.let { ArrayList(it) }
    }

    private fun gespeicherterVorschlag(): Freund? {
        val id = zustand.get<String>(KEY_ID) ?: return null
        val kurse = zustand.get<ArrayList<String>>(KEY_KURSE) ?: return null
        return Freund(id = id, name = zustand.get<String>(KEY_NAME).orEmpty(), kurse = kurse.toSet())
    }

    private val lader = WochenLader<WocheTag>(viewModelScope, repository) { credentialsStore.laden() }
    val woche: StateFlow<WochenDaten<WocheTag>> = lader.zustand

    /** Lädt Montag–Freitag der aktuellen bzw. nächsten Woche, mit deinem Plan und denen deiner Freunde. */
    fun wocheLaden(naechste: Boolean) {
        val freunde = store.freunde.value
        val kurse = kursStore.laden()
        lader.laden(wochenReferenz(if (naechste) 1 else 0), freunde to kurse) { datum, ergebnis ->
            if (ergebnis is PlanResult.Success) {
                val gesamt = ergebnis.plan
                WocheTag(
                    datum = datum,
                    raster = gesamt.zeitraster,
                    eigen = gesamt.tagesplanFuer(kurse),
                    freunde = freunde.associate { it.id to gesamt.tagesplanFuer(it.kurse) }
                )
            } else {
                WocheTag(datum, fehler = ergebnis.tagesFehler())
            }
        }
    }

    private companion object {
        const val KEY_ID = "import_id"
        const val KEY_NAME = "import_name"
        const val KEY_KURSE = "import_kurse"
    }
}
