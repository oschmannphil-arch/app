package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.EntfallTracker
import com.nextlesson.app.data.GesamtPlan
import com.nextlesson.app.data.IndiwareCredentials
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.KursInfo
import com.nextlesson.app.data.KursSelectionStore
import com.nextlesson.app.data.PersoenlicherPlan
import com.nextlesson.app.data.PersoenlicherResult
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.WidgetDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

sealed class UiZustand {
    object LoginNoetig : UiZustand()
    object Laedt : UiZustand()
    /** Zugang steht – jetzt nur noch die eigenen Kurse ankreuzen. */
    data class KurseWaehlen(val kurse: List<KursInfo>) : UiZustand()
    data class Angezeigt(val plan: PersoenlicherPlan) : UiZustand()
    data class Fehler(val nachricht: String) : UiZustand()
}

/** Ein einzelner Tag der Wochenansicht. */
data class WochenTag(
    val datum: LocalDate,
    val plan: TagesPlan?,
    val fehlermeldung: String? = null
)

sealed class WochenZustand {
    object NichtGeladen : WochenZustand()
    object Laedt : WochenZustand()
    data class Geladen(val tage: List<WochenTag>) : WochenZustand()
}

/** Auswahl für die Wochenansicht. */
enum class WochenAuswahl(val label: String) {
    LETZTE("Vorherige"),
    AKTUELL("Diese Woche"),
    NAECHSTE("Nächste Woche")
}

class PlanViewModel(app: Application) : AndroidViewModel(app) {

    private val credentialsStore = CredentialsStore(app)
    private val kursSelectionStore = KursSelectionStore(app)
    private val entfallTracker = EntfallTracker(app)
    private val widgetDataStore = WidgetDataStore(app)
    private val repository = IndiwareRepository(app)

    private val _zustand = MutableStateFlow<UiZustand>(UiZustand.Laedt)
    val zustand: StateFlow<UiZustand> = _zustand.asStateFlow()

    private val _wochenZustand = MutableStateFlow<WochenZustand>(WochenZustand.NichtGeladen)
    val wochenZustand: StateFlow<WochenZustand> = _wochenZustand.asStateFlow()

    private val _wochenAuswahl = MutableStateFlow(WochenAuswahl.AKTUELL)
    val wochenAuswahl: StateFlow<WochenAuswahl> = _wochenAuswahl.asStateFlow()

    private val _grosserText = MutableStateFlow(widgetDataStore.laden().grosserText)
    val grosserText: StateFlow<Boolean> = _grosserText.asStateFlow()

    /** Zuletzt geladener Gesamtplan – Grundlage für die Kursliste. */
    private var letzterGesamtPlan: GesamtPlan? = null

    init {
        ladeGespeichertUndAktualisiere()
    }

    fun ladeGespeichertUndAktualisiere() {
        val creds = credentialsStore.laden()
        if (creds == null) _zustand.value = UiZustand.LoginNoetig else aktualisiere(creds)
    }

    fun anmelden(creds: IndiwareCredentials) {
        credentialsStore.speichern(creds)
        entfallTracker.zuruecksetzen()
        aktualisiere(creds)
    }

    fun abmelden() {
        credentialsStore.loeschen()
        kursSelectionStore.speichern(emptySet())
        entfallTracker.zuruecksetzen()
        letzterGesamtPlan = null
        _wochenZustand.value = WochenZustand.NichtGeladen
        _zustand.value = UiZustand.LoginNoetig
    }

    fun aktuelleCredentials(): IndiwareCredentials? = credentialsStore.laden()

    fun verfuegbareKurse(): List<KursInfo> = letzterGesamtPlan?.alleKurse ?: emptyList()

    fun aktuelleKursAuswahl(): Set<String> = kursSelectionStore.laden()

    /** Speichert die Kurswahl und lädt den persönlichen Plan neu. */
    fun kursAuswahlSpeichern(kursIds: Set<String>) {
        kursSelectionStore.speichern(kursIds)
        // Der alte Entfall-Stand bezog sich auf andere Kurse und würde sonst Fehlalarme geben.
        entfallTracker.zuruecksetzen()
        _wochenZustand.value = WochenZustand.NichtGeladen

        if (kursIds.isEmpty()) {
            _zustand.value = UiZustand.KurseWaehlen(letzterGesamtPlan?.alleKurse ?: emptyList())
            return
        }
        credentialsStore.laden()?.let { aktualisiere(it) }
    }

    fun aktualisieren() {
        val creds = credentialsStore.laden() ?: run {
            _zustand.value = UiZustand.LoginNoetig
            return
        }
        _wochenZustand.value = WochenZustand.NichtGeladen
        aktualisiere(creds)
    }

    fun grosserTextSetzen(aktiv: Boolean) {
        widgetDataStore.grosserTextSetzen(aktiv)
        _grosserText.value = aktiv
    }

    fun setWochenAuswahl(auswahl: WochenAuswahl) {
        if (_wochenAuswahl.value == auswahl) return
        _wochenAuswahl.value = auswahl
        wocheLaden()
    }

    /** Lädt Montag–Freitag für die Wochenansicht. */
    fun wocheLaden() {
        val creds = credentialsStore.laden() ?: return
        val kurse = kursSelectionStore.laden()
        if (kurse.isEmpty()) return
        
        if (_wochenZustand.value !is WochenZustand.Geladen) {
            _wochenZustand.value = WochenZustand.Laedt
        }
        
        viewModelScope.launch {
            val heute = LocalDate.now()
            
            // Basis-Woche bestimmen: ab Samstag springen wir standardmäßig in die neue Woche.
            val base = if (heute.dayOfWeek == DayOfWeek.SATURDAY || heute.dayOfWeek == DayOfWeek.SUNDAY) {
                heute.plusWeeks(1)
            } else {
                heute
            }
            
            val referenz = when (_wochenAuswahl.value) {
                WochenAuswahl.LETZTE -> base.minusWeeks(1)
                WochenAuswahl.AKTUELL -> base
                WochenAuswahl.NAECHSTE -> base.plusWeeks(1)
            }
            
            val tage = repository.holeWoche(creds, referenz).map { (datum, ergebnis) ->
                when (ergebnis) {
                    is PlanResult.Success -> WochenTag(datum, ergebnis.plan.tagesplanFuer(kurse))
                    is PlanResult.AuthFehler -> WochenTag(datum, null, "Login fehlgeschlagen")
                    is PlanResult.KeinPlanFuerTag -> WochenTag(datum, null, "Kein Plan veröffentlicht")
                    is PlanResult.NetzwerkFehler -> WochenTag(datum, null, "Netzwerkfehler")
                }
            }
            _wochenZustand.value = WochenZustand.Geladen(tage)
        }
    }

    private fun aktualisiere(creds: IndiwareCredentials) {
        val auswahl = kursSelectionStore.laden()
        // Nur wenn wir noch nichts anzeigen, schalten wir auf den Lade-Screen um.
        // Bei einem Hintergrund-Update bleibt der aktuelle Plan sichtbar.
        if (_zustand.value !is UiZustand.Angezeigt) {
            _zustand.value = UiZustand.Laedt
        }

        viewModelScope.launch {
            // Ohne Kurswahl brauchen wir nur die Kursliste.
            if (auswahl.isEmpty()) {
                when (val ergebnis = repository.holeNaechstenVerfuegbarenPlan(creds)) {
                    is PlanResult.Success -> {
                        letzterGesamtPlan = ergebnis.plan
                        _zustand.value = UiZustand.KurseWaehlen(ergebnis.plan.alleKurse)
                    }
                    is PlanResult.AuthFehler -> _zustand.value = fehlerAuth()
                    is PlanResult.KeinPlanFuerTag -> _zustand.value = fehlerKeinPlan()
                    is PlanResult.NetzwerkFehler -> _zustand.value = fehlerNetz(ergebnis.nachricht)
                }
                return@launch
            }

            when (val ergebnis = repository.holePersoenlichenPlan(creds, auswahl)) {
                is PersoenlicherResult.Erfolg -> {
                    letzterGesamtPlan = ergebnis.plan.gesamt
                    _zustand.value = UiZustand.Angezeigt(ergebnis.plan)
                }
                is PersoenlicherResult.AuthFehler -> _zustand.value = fehlerAuth()
                is PersoenlicherResult.KeinPlan -> _zustand.value = fehlerKeinPlan()
                is PersoenlicherResult.NetzwerkFehler -> _zustand.value = fehlerNetz(ergebnis.nachricht)
            }
        }
    }

    private fun fehlerAuth() =
        UiZustand.Fehler("Benutzername oder Passwort falsch. Bitte in den Einstellungen prüfen.")

    private fun fehlerKeinPlan() =
        UiZustand.Fehler("Für die nächsten Tage wurde kein Plan gefunden (Ferien?).")

    private fun fehlerNetz(nachricht: String) =
        UiZustand.Fehler("Keine Verbindung: $nachricht")
}
