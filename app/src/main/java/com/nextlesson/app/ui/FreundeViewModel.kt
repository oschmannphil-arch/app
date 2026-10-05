package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

sealed class FreundeWoche {
    object Laedt : FreundeWoche()
    data class Geladen(val tage: List<WocheTag>) : FreundeWoche()
    data class Fehler(val nachricht: String) : FreundeWoche()
}

class FreundeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = FreundeStore(app)
    private val credentialsStore by lazy { CredentialsStore(app) }
    private val kursStore = KursSelectionStore(app)
    private val repository = IndiwareRepository(app)

    val freunde: StateFlow<List<Freund>> = store.freunde

    fun speichern(freund: Freund) = store.speichern(freund)

    fun loeschen(id: String) = store.loeschen(id)

    /** Freund aus einem geteilten Link, der noch bestätigt (und ggf. benannt) werden muss. */
    private val _importVorschlag = MutableStateFlow<Freund?>(null)
    val importVorschlag: StateFlow<Freund?> = _importVorschlag.asStateFlow()

    /** Schlägt [freund] zum Speichern vor – außer, genau diesen Freund gibt es schon. */
    fun importVorschlagen(freund: Freund) {
        val schonDa = store.freunde.value.any {
            it.kurse == freund.kurse && it.name.trim().equals(freund.name.trim(), ignoreCase = true)
        }
        if (!schonDa) _importVorschlag.value = freund
    }

    fun importVerwerfen() {
        _importVorschlag.value = null
    }

    private val _woche = MutableStateFlow<FreundeWoche>(FreundeWoche.Laedt)
    val woche: StateFlow<FreundeWoche> = _woche.asStateFlow()

    private class WochenStand(
        val referenz: LocalDate,
        val geladenUm: Long,
        val freunde: List<Freund>,
        val kurse: Set<String>,
        val tage: List<WocheTag>
    )

    private var wochenJob: Job? = null
    private var laedtFuer: LocalDate? = null
    private var stand: WochenStand? = null

    /**
     * Lädt Montag–Freitag der aktuellen bzw. nächsten Woche. Ein frischer Stand (gleiche Woche,
     * gleiche Freunde und Kurse, höchstens 5 Minuten alt) wird wiederverwendet – Drehen des
     * Handys oder erneutes Öffnen lädt dann nicht alles neu.
     */
    fun wocheLaden(naechste: Boolean) {
        val referenz = wochenReferenz(if (naechste) 1 else 0)
        val freunde = store.freunde.value
        val kurse = kursStore.laden()
        val s = stand
        if (s != null && s.referenz == referenz && s.freunde == freunde && s.kurse == kurse &&
            System.currentTimeMillis() - s.geladenUm < NEU_LADEN_NACH_MILLIS
        ) {
            wochenJob?.cancel()
            _woche.value = FreundeWoche.Geladen(s.tage)
            return
        }
        if (wochenJob?.isActive == true && laedtFuer == referenz) return

        wochenJob?.cancel()
        laedtFuer = referenz
        _woche.value = FreundeWoche.Laedt
        wochenJob = viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { credentialsStore.laden() }
            if (creds == null) {
                _woche.value = FreundeWoche.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
                return@launch
            }
            val ergebnisse = repository.holeWoche(creds, referenz)
            val tage = withContext(Dispatchers.Default) {
                ergebnisse.map { (datum, ergebnis) ->
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
            // Ohne Verbindung nicht merken – sonst bliebe der Fehler 5 Minuten stehen.
            if (ergebnisse.none { it.second is PlanResult.NetzwerkFehler }) {
                stand = WochenStand(referenz, System.currentTimeMillis(), freunde, kurse, tage)
            }
            _woche.value = FreundeWoche.Geladen(tage)
        }
    }

    private companion object {
        const val NEU_LADEN_NACH_MILLIS = 5 * 60_000L
    }
}
