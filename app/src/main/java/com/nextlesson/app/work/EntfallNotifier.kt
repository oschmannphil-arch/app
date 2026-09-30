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
     * @param datum Tag, auf den sich die Entfälle beziehen.
     * @param neueEntfaelle nur die Stunden, die seit dem letzten Abruf neu ausgefallen sind.
     */
    fun melden(context: Context, datum: LocalDate, neueEntfaelle: List<Lesson>) {
        if (neueEntfaelle.isEmpty() || !darfBenachrichtigen(context)) return

        kanalAnlegen(context)

        val heute = LocalDate.now()
        val wann = when (datum) {
            heute -> "heute"
            heute.plusDays(1) -> "morgen"
            else -> datum.format(tagFormat)
        }

        val titel = if (neueEntfaelle.size == 1) {
            val l = neueEntfaelle.first()
            // Beim Ausfall steht im Fach nur "---". Der Info-Text nennt den echten Kurs
            // ("ENG2 Herr Niemietz fällt aus") und taugt deshalb weit besser als Titel.
            when {
                l.info.isNotBlank() -> "${wann.replaceFirstChar { it.uppercase() }}: ${l.info}"
                l.fach.any { it.isLetterOrDigit() } -> "${l.fach} fällt $wann aus"
                else -> "Unterricht fällt $wann aus"
            }
        } else {
            "${neueEntfaelle.size} Stunden fallen $wann aus"
        }

        val text = neueEntfaelle.sortedBy { it.stunde }.joinToString(", ") { l ->
            buildString {
                append("${l.stunde}. Std")
                if (l.fach.isNotBlank()) append(" ${l.fach}")
                if (l.info.isNotBlank()) append(" (${l.info})")
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_entfall)
            .setContentTitle(titel)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASIS + datum.dayOfYear, notification)
        }
    }
}
