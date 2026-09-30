package com.nextlesson.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()

    companion object {
        /** Aktualisiert alle auf dem Homescreen platzierten Widget-Instanzen mit den zuletzt
         * gespeicherten Daten aus [com.nextlesson.app.data.WidgetDataStore]. */
        suspend fun alleWidgetsAktualisieren(context: Context) {
            NextLessonWidget().updateAll(context)
            // Countdown weiter am Laufen halten (nächste volle Minute).
            WidgetTicker.planen(context)
        }
    }
}
