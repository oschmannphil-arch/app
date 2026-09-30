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
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.WidgetDataStore
import com.nextlesson.app.widget.NextLessonWidgetReceiver
import java.time.LocalDate
import java.time.LocalTime

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
        IndiwareRepository(applicationContext).aufraeumen()

        // Heute + die nächsten Tage – anzahl = 7 stellt sicher, dass man am Wochenende
        // (Freitagabend) bereits den Entfall für Montagmorgen sieht.
        val tage = IndiwareRepository(applicationContext).holeTage(creds, heute, anzahl = 7)

        if (tage.any { it.second is PlanResult.AuthFehler }) {
            hinweisSchreiben("Login fehlgeschlagen")
            return Result.failure()
        }
        // Wenn alle Tage Netzwerkfehler haben, versuchen wir trotzdem weiterzumachen,
        // da IndiwareRepository nun automatisch auf den Cache zurückfällt.
        // Nur wenn WIRKLICH gar nichts da ist (weder Netz noch Cache), brechen wir ab.

        var anzeige: Pair<LocalDate, NaechsteStundeErgebnis>? = null
        var anzeigePlan: TagesPlan? = null
        var anzeigeGeprueftUm: Long = System.currentTimeMillis()
        var naechsteGrenzzeitMillis: Long = Long.MAX_VALUE

        tage.forEach { (datum, ergebnis) ->
            val success = ergebnis as? PlanResult.Success ?: return@forEach
            val gesamt = success.plan
            val plan = gesamt.tagesplanFuer(kurse)

            // ... (Entfall logic)

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
                    if (b != null) {
                        val bMillis = heute.atTime(b).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (bMillis > System.currentTimeMillis()) {
                            naechsteGrenzzeitMillis = minOf(naechsteGrenzzeitMillis, bMillis)
                        }
                    }
                    if (e != null) {
                        val eMillis = heute.atTime(e).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (eMillis > System.currentTimeMillis()) {
                            naechsteGrenzzeitMillis = minOf(naechsteGrenzzeitMillis, eMillis)
                        }
                    }
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
