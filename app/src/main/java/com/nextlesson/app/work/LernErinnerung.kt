package com.nextlesson.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nextlesson.app.R
import com.nextlesson.app.data.AufgabenStore
import com.nextlesson.app.data.ErinnerungsStore
import com.nextlesson.app.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Tägliche Erinnerung ans Lernen bzw. an die Hausaufgaben, zu einer in der App
 * eingestellten Uhrzeit.
 *
 * Statt eines periodischen Jobs plant sich diese Erinnerung nach jedem Auslösen selbst
 * neu ein. Ein periodischer WorkManager-Job driftet über die Tage von der Wunschzeit weg;
 * dieser Weg trifft die eingestellte Uhrzeit jeden Tag neu.
 */
object LernErinnerung {

    private const val WORK_NAME = "lern_erinnerung"
    private const val CHANNEL_ID = "lernen"
    // Nicht 4300: Der EntfallNotifier nutzt 4200 + Tag im Jahr (4201–4566) und hätte
    // am 100. Tag des Jahres dieselbe ID.
    private const val NOTIFICATION_ID = 4100

    fun kanalAnlegen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val kanal = NotificationChannel(
            CHANNEL_ID,
            "Lern-Erinnerung",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tägliche Erinnerung an Hausaufgaben und anstehende Klausuren."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
    }

    /** Plant die nächste Erinnerung ein bzw. sagt sie ab, wenn sie ausgeschaltet wurde. */
    fun neuPlanen(context: Context) {
        val einstellung = ErinnerungsStore(context).laden()
        val workManager = WorkManager.getInstance(context)

        if (!einstellung.aktiv) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val verzoegerung = verzoegerungBis(einstellung.uhrzeit)
        val request = OneTimeWorkRequestBuilder<LernErinnerungWorker>()
            .setInitialDelay(verzoegerung.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Zeit bis zum nächsten Auftreten dieser Uhrzeit – heute, sonst morgen. */
    internal fun verzoegerungBis(ziel: LocalTime, jetzt: LocalDateTime = LocalDateTime.now()): Duration {
        var zielZeitpunkt = jetzt.toLocalDate().atTime(ziel)
        if (!zielZeitpunkt.isAfter(jetzt)) {
            zielZeitpunkt = zielZeitpunkt.plusDays(1)
        }
        return Duration.between(jetzt, zielZeitpunkt)
    }

    fun melden(context: Context, offeneAufgaben: Int, naechstePruefungInTagen: Long?, pruefungFach: String?) {
        if (!EntfallNotifier.darfBenachrichtigen(context)) return
        kanalAnlegen(context)

        val titel = when {
            offeneAufgaben > 0 -> if (offeneAufgaben == 1) {
                "1 Hausaufgabe offen"
            } else {
                "$offeneAufgaben Hausaufgaben offen"
            }
            naechstePruefungInTagen != null -> "Zeit zum Lernen"
            else -> "Zeit zum Lernen"
        }

        val text = buildString {
            if (offeneAufgaben > 0) append("Erledige sie, bevor der Tag vorbei ist. ")
            if (naechstePruefungInTagen != null && pruefungFach != null) {
                append(
                    when {
                        naechstePruefungInTagen <= 0L -> "$pruefungFach ist heute."
                        naechstePruefungInTagen == 1L -> "$pruefungFach ist morgen."
                        else -> "$pruefungFach in $naechstePruefungInTagen Tagen."
                    }
                )
            }
            if (isEmpty()) append("Nichts Offenes eingetragen – kurz nachschauen schadet trotzdem nie.")
        }.trim()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lernen)
            .setContentTitle(titel)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

class LernErinnerungWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val einstellung = ErinnerungsStore(applicationContext).laden()
        if (!einstellung.aktiv) return Result.success()

        // Kein beimStartAufraeumen() hier: Das löschte abgehakte Hausaufgaben mitten am Tag
        // (statt beim nächsten App-Start) und an der laufenden App vorbei. Zählen reicht.
        val store = AufgabenStore(applicationContext)

        val offen = store.offeneHausaufgaben().size
        val naechste = store.kommendePruefungen().minByOrNull { it.datumEpochDay }

        LernErinnerung.melden(
            context = applicationContext,
            offeneAufgaben = offen,
            naechstePruefungInTagen = naechste?.tageBis(LocalDate.now()),
            pruefungFach = naechste?.let { "${it.art.anzeige} ${it.fach}".trim() }
        )

        // Direkt den Termin für morgen setzen.
        LernErinnerung.neuPlanen(applicationContext)
        return Result.success()
    }
}
