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

    private val staende = HashMap<Triple<LocalDate, Any?, IndiwareCredentials?>, Pair<Long, List<T>>>()
    private var job: Job? = null
    private var laedtFuer: Pair<LocalDate, Any?>? = null
    private var frischLaden = false

    fun laden(referenz: LocalDate, extra: Any?, umwandeln: (LocalDate, PlanResult) -> T) {
        val anfrage = referenz to extra
        if (job?.isActive == true && laedtFuer == anfrage) return

        job?.cancel()
        laedtFuer = anfrage
        job = scope.launch {
            // Die Zugangsdaten gehören zum Schlüssel: Nach einem Wechsel von Zugang oder Schule darf
            // kein Stand (oder Fehler) des alten Zugangs übrig bleiben. Das Auslesen (verschlüsselter
            // Speicher) läuft nicht auf dem UI-Thread.
            val creds = withContext(Dispatchers.IO) { zugang() }
            if (creds == null) {
                _zustand.value = WochenDaten.Fehler("Bitte zuerst in den Einstellungen die Zugangsdaten eintragen.")
                return@launch
            }
            val schluessel = Triple(referenz, extra, creds)
            val jetzt = System.currentTimeMillis()
            staende.entries.removeAll { jetzt - it.value.first >= FRISCH_MILLIS }
            staende[schluessel]?.let { (_, tage) ->
                _zustand.value = WochenDaten.Geladen(referenz, tage)
                return@launch
            }

            _zustand.value = WochenDaten.Laedt
            val erzwingen = frischLaden
            frischLaden = false
            val ergebnisse = repository.holeWoche(creds, referenz, erzwingen)
            val tage = withContext(Dispatchers.Default) { ergebnisse.map { (datum, e) -> umwandeln(datum, e) } }
            // Nur einwandfreie Stände merken: Weder Netzfehler noch gespeicherter Ersatzstand
            // (Quelle.CACHE) noch falsche Zugangsdaten sollen 5 Minuten "frisch" bleiben.
            val vomServer = ergebnisse.none { (_, e) ->
                e is PlanResult.NetzwerkFehler || e is PlanResult.AuthFehler ||
                    (e is PlanResult.Success && e.aus == Quelle.CACHE)
            }
            if (vomServer) staende[schluessel] = System.currentTimeMillis() to tage
            _zustand.value = WochenDaten.Geladen(referenz, tage)
        }
    }

    /** Nach "Aktualisieren": alles beim nächsten Mal frisch laden. */
    fun vergessen() {
        staende.clear()
        frischLaden = true
    }

    private companion object {
        const val FRISCH_MILLIS = 5 * 60_000L
    }
}
