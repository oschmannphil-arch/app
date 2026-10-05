package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.FreundeStore
import com.nextlesson.app.data.GesamtPlan
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.KursSelectionStore
import com.nextlesson.app.data.PlanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

/** Ein Schultag der Woche mit dem Plan der ganzen Schule (null + [fehler], wenn nicht ladbar). */
data class WocheTag(val datum: LocalDate, val gesamt: GesamtPlan?, val fehler: String? = null)

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

    fun eigeneKurse(): Set<String> = kursStore.laden()

    /** Freund aus einem geteilten Link, der noch bestätigt (und ggf. benannt) werden muss. */
    private val _importVorschlag = MutableStateFlow<Freund?>(null)
    val importVorschlag: StateFlow<Freund?> = _importVorschlag.asStateFlow()

    fun importVorschlagen(freund: Freund) {
        _importVorschlag.value = freund
    }

    fun importVerwerfen() {
        _importVorschlag.value = null
    }

    private val _woche = MutableStateFlow<FreundeWoche>(FreundeWoche.Laedt)
    val woche: StateFlow<FreundeWoche> = _woche.asStateFlow()

    private var wochenJob: Job? = null

    /** Lädt Montag–Freitag der aktuellen bzw. nächsten Woche (am Wochenende zählt "diese" schon als die kommende). */
    fun wocheLaden(naechste: Boolean) {
        wochenJob?.cancel()
        _woche.value = FreundeWoche.Laedt
        wochenJob = viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { credentialsStore.laden() }
            if (creds == null) {
                _woche.value = FreundeWoche.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
                return@launch
            }
            val heute = LocalDate.now()
            val basis = if (heute.dayOfWeek == DayOfWeek.SATURDAY || heute.dayOfWeek == DayOfWeek.SUNDAY) {
                heute.plusWeeks(1)
            } else {
                heute
            }
            val referenz = if (naechste) basis.plusWeeks(1) else basis
            val tage = repository.holeWoche(creds, referenz).map { (datum, ergebnis) ->
                when (ergebnis) {
                    is PlanResult.Success -> WocheTag(datum, ergebnis.plan)
                    is PlanResult.AuthFehler -> WocheTag(datum, null, "Login fehlgeschlagen")
                    is PlanResult.KeinPlanFuerTag -> WocheTag(datum, null, "Kein Plan veröffentlicht")
                    is PlanResult.NetzwerkFehler -> WocheTag(datum, null, "Keine Verbindung")
                }
            }
            _woche.value = FreundeWoche.Geladen(tage)
        }
    }
}
