package com.nextlesson.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nextlesson.app.data.CredentialsStore
import com.nextlesson.app.data.EntfallTracker
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.KursSelectionStore
import com.nextlesson.app.data.NaechsteStundeErgebnis
import com.nextlesson.app.data.PlanResult
import com.nextlesson.app.data.Quelle
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.WidgetDataStore
import com.nextlesson.app.widget.NextLessonWidgetReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Läuft regelmäßig im Hintergrund und erledigt zwei Dinge in einem Durchgang:
 *
 *  1. Entfall-Überwachung: vergleicht heute + die nächsten Tage mit dem zuletzt bekannten
 *     Stand und benachrichtigt NUR bei neuem Entfall in den gewählten Kursen.
 *  2. Widget-Daten: schreibt die nächste anstehende Stunde – nach Schulschluss die erste
 *     Stunde des nächsten Schultags.
 *
 * Jeder Tag wird dabei nur einmal vom Server geholt.
 */
class RefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val credentialsStore = CredentialsStore(applicationContext)
        val kursSelectionStore = KursSelectionStore(applicationContext)
        val widgetDataStore = WidgetDataStore(applicationContext)
        val entfallTracker = EntfallTracker(applicationContext)

        suspend fun hinweisSchreiben(nachricht: String) {
            widgetDataStore.speichern(null, "", null, nachricht)
            NextLessonWidgetReceiver.alleWidgetsAktualisieren(applicationContext)
        }

        val creds = credentialsStore.laden() ?: run {
            hinweisSchreiben("Zugangsdaten in der App eintragen")
            return Result.success()
        }

        val kurse = kursSelectionStore.laden()
        if (kurse.isEmpty()) {
            hinweisSchreiben("Kurse in der App auswählen")
            return Result.success()
        }

        val heute = LocalDate.now()
        val jetzt = LocalTime.now()
        entfallTracker.aufraeumen(heute)
        val repository = IndiwareRepository(applicationContext)
        repository.aufraeumen()

        // Heute + die nächsten Tage – anzahl = 7 stellt sicher, dass man am Wochenende
        // (Freitagabend) bereits den Entfall für Montagmorgen sieht.
        val tage = repository.holeTage(creds, heute, anzahl = 7)

        if (tage.any { it.second is PlanResult.AuthFehler }) {
            hinweisSchreiben("Login fehlgeschlagen")
            return Result.failure()
        }
        // Weder Netz noch Cache: Das Widget behält seinen letzten Stand (statt fälschlich
        // "Keine Stunden" zu zeigen), und WorkManager versucht es später erneut.
        if (tage.none { it.second is PlanResult.Success } &&
            tage.any { it.second is PlanResult.NetzwerkFehler }
        ) {
            return Result.retry()
        }

        var anzeige: Pair<LocalDate, NaechsteStundeErgebnis>? = null
        var anzeigePlan: TagesPlan? = null
        var anzeigeGeprueftUm: Long = System.currentTimeMillis()
        var naechsteGrenzzeitMillis: Long = Long.MAX_VALUE

        tage.forEach { (datum, ergebnis) ->
            val success = ergebnis as? PlanResult.Success ?: return@forEach
            val gesamt = success.plan
            val plan = gesamt.tagesplanFuer(kurse)

            // 1. Neuen Entfall in den gewählten Kursen melden (der Tracker merkt sich den
            //    Stand; beim ersten Abruf eines Tages wird nur gespeichert, nicht gemeldet).
            //    Nur bei frischen Serverdaten – ein Cache-Stand kann nichts Neues enthalten.
            if (success.aus == Quelle.NETZ) {
                val neu = entfallTracker.neueEntfaelle(datum, plan.stunden)
                if (neu.isNotEmpty()) EntfallNotifier.melden(applicationContext, datum, neu)
            }

            // 2. Erste passende Stunde für das Widget suchen.
            if (anzeige == null) {
                val treffer = if (datum == heute) {
                    plan.naechsteStunde(jetzt)
                } else {
                    plan.ersteStunde()?.let { NaechsteStundeErgebnis(it, istVorschau = false) }
                }
                if (treffer != null) {
                    anzeige = datum to treffer
                    anzeigePlan = plan
                    anzeigeGeprueftUm = success.geprueftUm
                }
            }

            // 3. Grenzzeiten für punktgenaue Widget-Updates berechnen (nur für heute).
            if (datum == heute) {
                plan.stunden.filter { !it.entfaellt }.forEach { lesson ->
                    val b = lesson.beginn
                    val e = lesson.ende
                    val jetztMillis = System.currentTimeMillis()
                    fun millis(zeit: LocalTime): Long =
                        heute.atTime(zeit).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    // Beginn, Ende UND der Moment, ab dem die nächste Stunde schon als
                    // Vorschau erscheint (Ende minus Vorlauf) – sonst hinkt das Widget 5 Min nach.
                    listOfNotNull(b, e, e?.minusMinutes(TagesPlan.VORLAUF_MINUTEN))
                        .map { millis(it) }
                        .filter { it > jetztMillis }
                        .forEach { naechsteGrenzzeitMillis = minOf(naechsteGrenzzeitMillis, it) }
                }
            }
        }

        val treffer = anzeige
        if (treffer == null) {
            widgetDataStore.speichern(null, "", null, "Keine Stunden in den nächsten Tagen")
        } else {
            widgetDataStore.speichern(
                ergebnis = treffer.second,
                klasse = anzeigePlan?.klasse.orEmpty(),
                datum = treffer.first,
                geprueftUm = anzeigeGeprueftUm
            )
        }
        NextLessonWidgetReceiver.alleWidgetsAktualisieren(applicationContext)

        // Punktgenaues Update planen, damit das Widget beim Stundenwechsel sofort umspringt.
        if (naechsteGrenzzeitMillis != Long.MAX_VALUE) {
            val verzoegerung = naechsteGrenzzeitMillis - System.currentTimeMillis()
            // 1 Sekunde Puffer, damit die Zeit auch sicher um ist.
            RefreshScheduler.planePunktgenaueAktualisierung(applicationContext, verzoegerung + 1000)
        }

        return Result.success()
    }
}
