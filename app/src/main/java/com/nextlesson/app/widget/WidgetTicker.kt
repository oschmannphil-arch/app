package com.nextlesson.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextlesson.app.data.WidgetDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hält den Countdown im Widget aktuell.
 *
 * Ein Widget zeichnet sich nicht von selbst neu – ohne Anstoß bliebe "in 8 Min" stundenlang
 * stehen. Solange ein Countdown angezeigt wird, stößt dieser Ticker deshalb zur nächsten
 * vollen Minute eine Aktualisierung an – aber nur in den letzten 90 Minuten vor Beginn bzw.
 * Ende der Stunde (Akku!). Davor weckt er nur einmal, um den Takt zu starten. Nach der
 * Stunde übernimmt der RefreshWorker.
 */
object WidgetTicker {

    private const val VORLAUF_MILLIS = 90 * 60_000L

    /** Nächster Tick oder null, wenn gerade kein Countdown läuft. */
    fun naechsterTick(inhalt: WidgetDataStore.WidgetInhalt, jetzt: Long): Long? {
        val ziel = when {
            inhalt.beginnMillis <= 0L -> return null
            inhalt.endeMillis > 0L && jetzt in inhalt.beginnMillis until inhalt.endeMillis -> inhalt.endeMillis
            inhalt.beginnMillis > jetzt -> inhalt.beginnMillis
            else -> return null
        }
        val rest = ziel - jetzt
        return if (rest <= VORLAUF_MILLIS) {
            (jetzt / 60_000 + 1) * 60_000
        } else {
            // Noch weit weg: einmal aufwachen, wenn der Minuten-Takt beginnen soll.
            ziel - VORLAUF_MILLIS
        }
    }

    fun planen(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app)
        val jetzt = System.currentTimeMillis()
        val tick = naechsterTick(WidgetDataStore(app).laden(), jetzt)
        if (tick == null) {
            alarm.cancel(pending)
            return
        }
        // Bewusst nicht "exakt": braucht keine Sonderberechtigung und schont den Akku.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, tick, pending)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Zeichnet neu und plant dabei den nächsten Tick.
                NextLessonWidgetReceiver.alleWidgetsAktualisieren(app)
            } finally {
                pending.finish()
            }
        }
    }
}
