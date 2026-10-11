package com.nextlesson.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nextlesson.app.R
import com.nextlesson.app.data.AenderungsText
import com.nextlesson.app.data.Freiblock
import com.nextlesson.app.data.Freund
import com.nextlesson.app.ui.MainActivity
import java.time.LocalDate

/** Meldung: "Anna hat morgen 3.–4. Std frei – wie du." – wenn eine gemeinsame Freistunde dazukommt. */
object FreundNotifier {

    private const val CHANNEL_ID = "freunde_frei"
    private const val NOTIFICATION_ID = 5000

    private fun kanalAnlegen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val kanal = NotificationChannel(CHANNEL_ID, "Freunde: gemeinsame Freistunden", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Meldet, wenn du und ein Freund plötzlich gleichzeitig frei habt, z. B. weil bei ihm eine Stunde ausfällt."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
    }

    fun melden(context: Context, datum: LocalDate, freund: Freund, neu: List<Freiblock>) {
        if (neu.isEmpty() || !EntfallNotifier.darfBenachrichtigen(context)) return
        kanalAnlegen(context)
        val wann = AenderungsText.wann(datum, LocalDate.now())
        val zeiten = neu.joinToString("\n") { b ->
            val von = b.beginn.toString()
            val bis = b.ende.toString()
            (b.stundenText?.let { "$it · " }.orEmpty()) + "$von–$bis"
        }
        val titel = "${freund.name} hat $wann auch frei"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lernen)
            .setContentTitle(titel)
            .setContentText("Gemeinsam frei: " + zeiten.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText("Gemeinsam frei:\n$zeiten"))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // Eigene Meldung je Freund und Tag: feste ID, Unterscheidung über das Tag (keine Hash-Kollisionen).
        runCatching { NotificationManagerCompat.from(context).notify("frei|$datum|${freund.id}", NOTIFICATION_ID, notification) }
    }
}
