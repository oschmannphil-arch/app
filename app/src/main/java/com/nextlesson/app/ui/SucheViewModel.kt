package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.FavoritenStore
import com.nextlesson.app.data.SuchVerlauf
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.Quelle
import com.nextlesson.app.data.SchulTag
import com.nextlesson.app.data.Treffer
import com.nextlesson.app.data.ersterSchultag
import com.nextlesson.app.data.tagesFehler
import com.nextlesson.app.data.wochenReferenz
import com.nextlesson.app.data.schultagVersetzt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed class SucheZustand {
    object Laedt : SucheZustand()
    data class Geladen(val tag: SchulTag, val ausCache: Boolean) : SucheZustand()
    data class Fehler(val nachricht: String) : SucheZustand()
}

/** Ein Schultag der Wochenübersicht einer Lehrkraft (null + [fehler], wenn der Plan fehlt). */
data class SucheWochenTag(val datum: LocalDate, val tag: SchulTag?, val fehler: String? = null)

/** Lädt den Plan der ganzen Schule für einen Tag und bereitet ihn für die Lehrer-/Raumsuche auf. */
class SucheViewModel(app: Application) : AndroidViewModel(app) {

    private val credentialsStore by lazy { CredentialsStore(app) }
    private val repository = IndiwareRepository(app)
    private val favoritenStore = FavoritenStore(app)
    private val verlaufStore = SuchVerlauf(app)

    private val _verlauf = MutableStateFlow<List<Treffer>>(verlaufStore.laden())
    /** Die zuletzt gewählten Lehrkräfte und Räume, neueste zuerst. */
    val verlauf: StateFlow<List<Treffer>> = _verlauf.asStateFlow()

    fun gewaehlt(t: Treffer) {
        _verlauf.value = verlaufStore.merken(t)
    }

    val favoriten: StateFlow<List<Treffer>> = favoritenStore.favoriten

    fun favoritUmschalten(t: Treffer) = favoritenStore.umschalten(t)

    private val wochenLader = WochenLader<SucheWochenTag>(viewModelScope, repository) { credentialsStore.laden() }
    val woche: StateFlow<WochenDaten<SucheWochenTag>> = wochenLader.zustand

    /**
     * Plan der ganzen Schule für Montag–Freitag – Grundlage für "Wann ist Herr X frei?".
     * Eine geladene Woche gilt für jede Lehrkraft.
     */
    fun wocheLaden(naechste: Boolean) {
        wochenLader.laden(wochenReferenz(if (naechste) 1 else 0), null) { datum, ergebnis ->
            if (ergebnis is PlanResult.Success) SucheWochenTag(datum, SchulTag(datum, ergebnis.plan))
            else SucheWochenTag(datum, null, ergebnis.tagesFehler())
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

    fun aktualisieren() {
        wochenLader.vergessen()
        laden(erzwingen = true)
    }

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
