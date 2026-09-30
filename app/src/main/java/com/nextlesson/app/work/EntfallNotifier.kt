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
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Schickt eine Push-Benachrichtigung – ausschließlich bei NEUEM Entfall in den Kursen,
 * die der Schüler ausgewählt hat. Raumwechsel oder Vertretungen lösen bewusst keine
 * Benachrichtigung aus.
 */
object EntfallNotifier {

    private const val CHANNEL_ID = "entfall"
    private const val NOTIFICATION_ID_BASIS = 4200
    private const val TEST_ID = 4199
    private val tagFormat = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN)

    fun kanalAnlegen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val kanal = NotificationChannel(
            CHANNEL_ID,
            "Stundenausfall",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Meldet, wenn eine deiner Stunden neu ausfällt."
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
     * Meldet NEUEN Entfall eines Tages. Der Text listet aber ALLE aktuellen Ausfälle des Tages:
     * Die Meldung eines Tages hat eine feste ID – vorher ersetzte ein zweiter Ausfall am selben
     * Tag die erste Meldung, und der erste Ausfall war aus der Leiste verschwunden.
     *
     * @param alle alle aktuell ausfallenden Stunden des Tages (eigene Kurse).
     * @param neu nur die seit dem letzten Abruf neu ausgefallenen – bestimmen Titel und Alarm.
     */
    fun melden(context: Context, datum: LocalDate, alle: List<Lesson>, neu: List<Lesson>) {
        if (neu.isEmpty() || !darfBenachrichtigen(context)) return
        zeigen(context, datum, alle, titel(datum, neu), leise = false)
    }

    /**
     * Stand einer bereits sichtbaren Meldung nachziehen, ohne erneut zu klingeln: Wurde ein
     * Ausfall zurückgenommen, verschwindet er aus dem Text – fällt nichts mehr aus, verschwindet
     * die Meldung ganz. Sonst stünde dort weiter ein Ausfall, der gar nicht mehr gilt.
     */
    fun nachziehen(context: Context, datum: LocalDate, alle: List<Lesson>) {
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
        zeigen(context, datum, alle, titel(datum, alle), leise = true)
    }

    /** Probe-Meldung aus den Einstellungen – zeigt, ob Benachrichtigungen ankommen. */
    fun testen(context: Context): Boolean {
        if (!darfBenachrichtigen(context)) return false
        kanalAnlegen(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_entfall)
            .setContentTitle("Test: Benachrichtigungen funktionieren")
            .setContentText("So sieht eine Meldung aus, wenn eine deiner Stunden ausfällt.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(oeffnenIntent(context))
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_ID, notification)
        }.isSuccess
    }

    private fun wann(datum: LocalDate): String {
        val heute = LocalDate.now()
        return when (datum) {
            heute -> "heute"
            heute.plusDays(1) -> "morgen"
            else -> datum.format(tagFormat)
        }
    }

    private fun titel(datum: LocalDate, stunden: List<Lesson>): String {
        val wann = wann(datum)
        if (stunden.size != 1) return "${stunden.size} Stunden fallen $wann aus"
        val l = stunden.first()
        // Beim Ausfall steht im Fach oft nur "---". Ein kurzer Info-Text nennt dann den echten
        // Kurs ("ENG2 Herr Niemietz fällt aus"); lange Sammel-Hinweise taugen nicht als Titel.
        return when {
            l.fach.any { it.isLetterOrDigit() } -> "${l.fach} fällt $wann aus"
            l.info.isNotBlank() && l.info.length <= 60 && ';' !in l.info ->
                "${wann.replaceFirstChar { it.uppercase() }}: ${l.info}"
            else -> "Unterricht fällt $wann aus"
        }
    }

    private fun zeigen(context: Context, datum: LocalDate, alle: List<Lesson>, titel: String, leise: Boolean) {
        kanalAnlegen(context)
        val text = alle.sortedBy { it.stunde }.joinToString("\n") { l ->
            buildString {
                append("${l.stunde}. Std")
                if (l.fach.any { it.isLetterOrDigit() }) append(" ${l.fach}")
                if (l.info.isNotBlank()) append(" – ${l.info}")
            }
        }

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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationId(datum: LocalDate) = NOTIFICATION_ID_BASIS + datum.dayOfYear
}
