package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.FavoritenStore
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.Quelle
import com.nextlesson.app.data.SchulTag
import com.nextlesson.app.data.Treffer
import com.nextlesson.app.data.ersterSchultag
import com.nextlesson.app.data.schultagVersetzt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

sealed class SucheZustand {
    object Laedt : SucheZustand()
    data class Geladen(val tag: SchulTag, val ausCache: Boolean) : SucheZustand()
    data class Fehler(val nachricht: String) : SucheZustand()
}

/** Ein Schultag der Wochenübersicht einer Lehrkraft (null + [fehler], wenn der Plan fehlt). */
data class SucheWochenTag(val datum: LocalDate, val tag: SchulTag?, val fehler: String? = null)

sealed class SucheWoche {
    object Laedt : SucheWoche()
    data class Geladen(val tage: List<SucheWochenTag>) : SucheWoche()
    data class Fehler(val nachricht: String) : SucheWoche()
}

/** Lädt den Plan der ganzen Schule für einen Tag und bereitet ihn für die Lehrer-/Raumsuche auf. */
class SucheViewModel(app: Application) : AndroidViewModel(app) {

    private val credentialsStore by lazy { CredentialsStore(app) }
    private val repository = IndiwareRepository(app)
    private val favoritenStore = FavoritenStore(app)

    val favoriten: StateFlow<List<Treffer>> = favoritenStore.favoriten

    fun favoritUmschalten(t: Treffer) = favoritenStore.umschalten(t)

    private val _woche = MutableStateFlow<SucheWoche>(SucheWoche.Laedt)
    val woche: StateFlow<SucheWoche> = _woche.asStateFlow()
    private var wochenJob: Job? = null

    /** Plan der ganzen Schule für Montag–Freitag – Grundlage für "Wann ist Herr X frei?". */
    fun wocheLaden(naechste: Boolean) {
        wochenJob?.cancel()
        _woche.value = SucheWoche.Laedt
        wochenJob = viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { credentialsStore.laden() }
            if (creds == null) {
                _woche.value = SucheWoche.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
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
                    is PlanResult.Success ->
                        SucheWochenTag(datum, withContext(Dispatchers.Default) { SchulTag(datum, ergebnis.plan) })
                    is PlanResult.AuthFehler -> SucheWochenTag(datum, null, "Login fehlgeschlagen")
                    is PlanResult.KeinPlanFuerTag -> SucheWochenTag(datum, null, "Kein Plan veröffentlicht")
                    is PlanResult.NetzwerkFehler -> SucheWochenTag(datum, null, "Keine Verbindung")
                }
            }
            _woche.value = SucheWoche.Geladen(tage)
        }
    }

    private val _datum = MutableStateFlow(ersterSchultag(LocalDate.now()))
    val datum: StateFlow<LocalDate> = _datum.asStateFlow()

    private val _zustand = MutableStateFlow<SucheZustand>(SucheZustand.Laedt)
    val zustand: StateFlow<SucheZustand> = _zustand.asStateFlow()

    private var ladeJob: Job? = null
    /** Für welchen Tag und wann zuletzt erfolgreich geladen wurde. */
    private var geladen: Pair<LocalDate, Long>? = null

    /** Beim Öffnen des Tabs: nur laden, wenn noch nichts, etwas Veraltetes oder ein Fehler da ist. */
    fun oeffnen() {
        // War die App über Nacht offen, wieder beim aktuellen Schultag anfangen.
        val heute = ersterSchultag(LocalDate.now())
        if (_datum.value.isBefore(heute)) _datum.value = heute
        val g = geladen
        val veraltet = g == null || g.first != _datum.value ||
            System.currentTimeMillis() - g.second > NEU_LADEN_NACH_MILLIS ||
            _zustand.value is SucheZustand.Fehler
        if (veraltet && ladeJob?.isActive != true) laden()
    }

    fun blaettern(richtung: Int) {
        _datum.value = schultagVersetzt(_datum.value, richtung)
        laden()
    }

    fun zuHeute() {
        _datum.value = ersterSchultag(LocalDate.now())
        laden()
    }

    fun aktualisieren() = laden(erzwingen = true)

    private fun laden(erzwingen: Boolean = false) {
        ladeJob?.cancel()
        val tag = _datum.value
        _zustand.value = SucheZustand.Laedt
        ladeJob = viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { credentialsStore.laden() }
            if (creds == null) {
                _zustand.value = SucheZustand.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
                return@launch
            }
            _zustand.value = when (val ergebnis = repository.holePlan(creds, tag, erzwingen)) {
                is PlanResult.Success -> {
                    geladen = tag to System.currentTimeMillis()
                    val schulTag = withContext(Dispatchers.Default) { SchulTag(tag, ergebnis.plan) }
                    SucheZustand.Geladen(schulTag, ausCache = ergebnis.aus == Quelle.CACHE)
                }
                is PlanResult.AuthFehler -> SucheZustand.Fehler("Benutzername oder Passwort falsch.")
                is PlanResult.KeinPlanFuerTag ->
                    SucheZustand.Fehler("Für diesen Tag gibt es keinen Plan – Ferien, Feiertag oder noch nicht veröffentlicht.")
                is PlanResult.NetzwerkFehler -> SucheZustand.Fehler("Keine Verbindung: ${ergebnis.nachricht}")
            }
        }
    }

    private companion object {
        const val NEU_LADEN_NACH_MILLIS = 5 * 60_000L
    }
}
