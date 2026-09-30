package com.nextlesson.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RefreshScheduler {

    private const val PERIODIC_WORK_NAME = "naechste_stunde_periodisch"
    private const val EINMALIG_WORK_NAME = "naechste_stunde_sofort"
    private const val PRAEZISE_WORK_NAME = "naechste_stunde_praezise"

    /** 15 Minuten ist das kürzeste Intervall, das WorkManager für periodische Arbeit erlaubt. */
    private const val INTERVALL_MINUTEN = 15L

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Für Widget-Updates brauchen wir nicht zwingend Internet (Cache reicht). */
    private val constraintsWidget = Constraints.Builder().build()

    /**
     * Plant die regelmäßige Prüfung ein. [UPDATE] statt [KEEP], damit eine geänderte
     * Intervalllänge auch bei bestehenden Installationen greift.
     */
    fun periodischePruefungEinplanen(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(INTERVALL_MINUTEN, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Sofortige Prüfung – beim App-Start, nach Änderungen und beim Tippen aufs Widget.
     * Ohne Netz-Bedingung: Offline wartete der Auftrag sonst, und Tippen aufs Widget tat
     * nichts. Der Worker nimmt ohne Netz den gespeicherten Plan.
     */
    fun sofortAktualisieren(context: Context, verzoegerungSekunden: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraintsWidget)
            .apply { if (verzoegerungSekunden > 0) setInitialDelay(verzoegerungSekunden, TimeUnit.SECONDS) }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EINMALIG_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 
     * Plant eine einmalige Aktualisierung zu einem exakten Zeitpunkt (z.B. Stundenende).
     * Nutzt keine Netzwerk-Constraints, damit das Widget auch offline umspringt.
     */
    fun planePunktgenaueAktualisierung(context: Context, verzoegerungMillis: Long) {
        if (verzoegerungMillis <= 0) return

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(verzoegerungMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraintsWidget)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PRAEZISE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
