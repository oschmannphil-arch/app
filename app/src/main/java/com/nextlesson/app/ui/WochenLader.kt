package com.nextlesson.app.ui

import com.nextlesson.app.data.IndiwareCredentials
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.Quelle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Stand einer Wochenübersicht. [Geladen.referenz] sagt, welche Woche es ist. */
sealed class WochenDaten<out T> {
    object Laedt : WochenDaten<Nothing>()
    data class Geladen<T>(val referenz: LocalDate, val tage: List<T>) : WochenDaten<T>()
    data class Fehler(val nachricht: String) : WochenDaten<Nothing>()

    /** Die Tage, wenn genau die Woche [referenz] geladen ist – sonst null (z.B. noch die andere). */
    fun tageFuer(referenz: LocalDate): List<T>? = (this as? Geladen<T>)?.takeIf { it.referenz == referenz }?.tage
}

/**
 * Lädt Montag–Freitag einer Woche und wandelt jeden Tag um (außerhalb des UI-Threads).
 * Frische Stände (höchstens 5 Minuten alt, vom Server) werden wiederverwendet – Drehen des
 * Handys oder erneutes Öffnen lädt dann nicht alles neu. [extra] gehört mit zum Schlüssel:
 * Ändert sich etwas, wovon das Umwandeln abhängt (Freunde, Kurse), wird neu geladen.
 */
class WochenLader<T>(
    private val scope: CoroutineScope,
    private val repository: IndiwareRepository,
    private val zugang: () -> IndiwareCredentials?
) {
    private val _zustand = MutableStateFlow<WochenDaten<T>>(WochenDaten.Laedt)
    val zustand: StateFlow<WochenDaten<T>> = _zustand.asStateFlow()

    private val staende = HashMap<Pair<LocalDate, Any?>, Pair<Long, List<T>>>()
    private var job: Job? = null
    private var laedtFuer: Pair<LocalDate, Any?>? = null

    fun laden(referenz: LocalDate, extra: Any?, umwandeln: (LocalDate, PlanResult) -> T) {
        val schluessel = referenz to extra
        val jetzt = System.currentTimeMillis()
        staende.entries.removeAll { jetzt - it.value.first >= FRISCH_MILLIS }
        staende[schluessel]?.let { (_, tage) ->
            job?.cancel()
            laedtFuer = null
            _zustand.value = WochenDaten.Geladen(referenz, tage)
            return
        }
        if (job?.isActive == true && laedtFuer == schluessel) return

        job?.cancel()
        laedtFuer = schluessel
        _zustand.value = WochenDaten.Laedt
        job = scope.launch {
            val creds = withContext(Dispatchers.IO) { zugang() }
            if (creds == null) {
                _zustand.value = WochenDaten.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
                return@launch
            }
            val ergebnisse = repository.holeWoche(creds, referenz)
            val tage = withContext(Dispatchers.Default) { ergebnisse.map { (datum, e) -> umwandeln(datum, e) } }
            // Nur frisch vom Server merken: Ohne Verbindung liefert das Repository den
            // gespeicherten Stand (Quelle.CACHE) – der soll nicht 5 Minuten "frisch" bleiben.
            val vomServer = ergebnisse.none { (_, e) ->
                e is PlanResult.NetzwerkFehler || (e is PlanResult.Success && e.aus == Quelle.CACHE)
            }
            if (vomServer) staende[schluessel] = System.currentTimeMillis() to tage
            _zustand.value = WochenDaten.Geladen(referenz, tage)
        }
    }

    /** Nach "Aktualisieren": alles beim nächsten Mal frisch laden. */
    fun vergessen() {
        staende.clear()
    }

    private companion object {
        const val FRISCH_MILLIS = 5 * 60_000L
    }
}
