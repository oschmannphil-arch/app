package com.nextlesson.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nextlesson.app.R
import com.nextlesson.app.data.Aenderung
import com.nextlesson.app.data.AenderungsText
import com.nextlesson.app.ui.MainActivity
import java.time.LocalDate

/**
 * Push-Benachrichtigungen bei NEUEN Änderungen in den gewählten Kursen: Ausfall, Vertretung,
 * Raumänderung (welche davon, stellt man in der App ein). Je Tag gibt es genau eine Meldung,
 * die alle aktuellen Änderungen des Tages auflistet.
 *
 * Name aus der Zeit, als nur Ausfälle gemeldet wurden; der Kanal heißt intern weiter "entfall",
 * damit Einstellungen, die jemand in Android dafür getroffen hat, erhalten bleiben.
 */
object EntfallNotifier {

    private const val CHANNEL_ID = "entfall"
    private const val NOTIFICATION_ID_BASIS = 4200
    private const val TEST_ID = 4199

    fun kanalAnlegen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Name und Beschreibung eines bestehenden Kanals darf man ändern (die Wichtigkeit nicht).
        val kanal = NotificationChannel(
            CHANNEL_ID,
            "Stundenplan-Änderungen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Meldet, wenn eine deiner Stunden ausfällt, vertreten wird oder den Raum wechselt."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
    }

    fun darfBenachrichtigen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Meldet NEUE Änderungen eines Tages. Der Text listet aber ALLE aktuellen Änderungen des
     * Tages: Die Meldung eines Tages hat eine feste ID, eine zweite Änderung am selben Tag
     * ersetzt also die erste – und die erste darf dabei nicht verloren gehen.
     *
     * @param alle alle aktuell anzuzeigenden Änderungen des Tages.
     * @param neu nur die seit dem letzten Abruf neuen – bestimmen Titel und Ton.
     */
    fun melden(context: Context, datum: LocalDate, alle: List<Aenderung>, neu: List<Aenderung>) {
        if (neu.isEmpty() || !darfBenachrichtigen(context)) return
        zeigen(context, datum, alle, AenderungsText.titel(neu, datum, LocalDate.now()), leise = false)
    }

    /**
     * Stand einer bereits sichtbaren Meldung nachziehen, ohne erneut zu klingeln: Wurde eine
     * Änderung zurückgenommen oder ist die Stunde vorbei, verschwindet sie aus dem Text – bleibt
     * nichts übrig, verschwindet die Meldung ganz.
     */
    fun nachziehen(context: Context, datum: LocalDate, alle: List<Aenderung>) {
        val id = notificationId(datum)
        val sichtbar = runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.activeNotifications?.any { it.id == id } == true
        }.getOrDefault(false)
        if (!sichtbar) return
        if (alle.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(id)
            return
        }
        if (!darfBenachrichtigen(context)) return
        zeigen(context, datum, alle, AenderungsText.titel(alle, datum, LocalDate.now()), leise = true)
    }

    /** Probe-Meldung aus den Einstellungen – zeigt, ob Benachrichtigungen ankommen. */
    fun testen(context: Context): Boolean {
        if (!darfBenachrichtigen(context)) return false
        kanalAnlegen(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_entfall)
            .setContentTitle("Test: Benachrichtigungen funktionieren")
            .setContentText("So sieht eine Meldung aus, wenn sich eine deiner Stunden ändert.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(oeffnenIntent(context))
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_ID, notification)
        }.isSuccess
    }

    private fun zeigen(context: Context, datum: LocalDate, alle: List<Aenderung>, titel: String, leise: Boolean) {
        kanalAnlegen(context)
        val text = AenderungsText.text(alle)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_entfall)
            .setContentTitle(titel)
            .setContentText(text.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(leise)
            .setSilent(leise)
            .setAutoCancel(true)
            .setContentIntent(oeffnenIntent(context))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(datum), notification)
        }
    }

    private fun oeffnenIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // Die laufende App nach vorn holen statt sie zu beenden und neu zu starten
            // (das verwarf offene Eingaben und das Aufräumen der Hausaufgaben lief erneut).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationId(datum: LocalDate) = NOTIFICATION_ID_BASIS + datum.dayOfYear
}
