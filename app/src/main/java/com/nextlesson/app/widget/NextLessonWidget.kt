package com.nextlesson.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.nextlesson.app.R
import com.nextlesson.app.data.WidgetDataStore
import com.nextlesson.app.work.RefreshScheduler

/**
 * Homescreen-Widget: zeigt groß, was als Nächstes ansteht.
 *
 * Es gibt zwei Layouts. Ab 2x2 (schmal) eine gekürzte Fassung, in der Raum und Zeit
 * untereinander stehen und der Kopf nur ein kurzes Wort trägt – sonst brechen die
 * Zeilen mitten im Text um. Ab 3 Zellen Breite die ausführliche Fassung mit Lehrer
 * und Prüfzeit.
 */
class NextLessonWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(KLEIN, BREIT, GROSS)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val inhalt = WidgetDataStore(context).laden()
        provideContent { WidgetInhalt(inhalt) }
    }

    @Composable
    private fun WidgetInhalt(inhalt: WidgetDataStore.WidgetInhalt) {
        val groesse = LocalSize.current
        val kompakt = groesse.width < 170.dp
        val niedrig = groesse.height < 130.dp

        val alarm = inhalt.hatAenderung || inhalt.entfaellt
        val hintergrund = if (alarm) R.drawable.widget_bg_alarm else R.drawable.widget_bg

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(hintergrund))
                .padding(horizontal = if (kompakt) 12.dp else 16.dp, vertical = 12.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            horizontalAlignment = Alignment.Start
        ) {
            val scaling = if (inhalt.grosserText) 1.4f else 1.0f
            fun scaled(size: Int): TextUnit = (size * scaling).sp

            Kopfzeile(inhalt, kompakt, alarm, ::scaled)

            when {
                inhalt.fehlermeldung != null -> {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = inhalt.fehlermeldung,
                        style = TextStyle(color = TextStark, fontSize = scaled(if (kompakt) 13 else 15)),
                        maxLines = 3
                    )
                }

                inhalt.fach == null -> {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = "Nichts mehr",
                        style = TextStyle(
                            color = TextStark,
                            fontSize = scaled(if (kompakt) 20 else 24),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }

                else -> Stunde(inhalt, kompakt, niedrig, alarm, ::scaled)
            }

            // Die Prüfzeit ist Beiwerk – auf kleinen Widgets fehlt dafür schlicht der Platz.
            if (!kompakt && !niedrig) {
                Spacer(GlanceModifier.defaultWeight())
                inhalt.geprueftText?.let {
                    Text(text = it, style = TextStyle(color = TextSchwach, fontSize = scaled(10)), maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Kopfzeile(
        inhalt: WidgetDataStore.WidgetInhalt,
        kompakt: Boolean,
        alarm: Boolean,
        scaled: (Int) -> TextUnit
    ) {
        val countdown = inhalt.countdown()

        if (kompakt) {
            // Auf 2x2 nur ein kurzes Wort, sonst rutscht der Kopf über zwei Zeilen.
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = kurzerKopf(inhalt),
                    style = TextStyle(
                        color = if (alarm) AlarmFarbe else TextSchwach,
                        fontSize = scaled(11),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (countdown != null) {
                    Text(
                        text = countdown,
                        style = TextStyle(color = TextStark, fontSize = scaled(11), fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = langerKopf(inhalt),
                    style = TextStyle(
                        color = if (alarm) AlarmFarbe else TextSchwach,
                        fontSize = scaled(12),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (countdown != null) {
                    Text(
                        text = countdown,
                        style = TextStyle(color = TextStark, fontSize = scaled(12), fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_chip))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun Stunde(
        inhalt: WidgetDataStore.WidgetInhalt,
        kompakt: Boolean,
        niedrig: Boolean,
        alarm: Boolean,
        scaled: (Int) -> TextUnit
    ) {
        Spacer(GlanceModifier.height(if (kompakt) 3.dp else 6.dp))

        // Das Fach muss aus der Entfernung lesbar sein – es bekommt den meisten Platz.
        Text(
            text = inhalt.fach.orEmpty(),
            style = TextStyle(
                color = if (inhalt.entfaellt) AlarmFarbe else TextStark,
                fontSize = scaled(if (kompakt) 24 else 28),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        if (inhalt.entfaellt) {
            Text(
                text = "entfällt",
                style = TextStyle(color = AlarmFarbe, fontSize = scaled(if (kompakt) 13 else 15), fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            return
        }

        Spacer(GlanceModifier.height(if (kompakt) 2.dp else 5.dp))

        val zeit = buildString {
            if (!inhalt.beginn.isNullOrBlank()) {
                append(inhalt.beginn)
                if (!inhalt.ende.isNullOrBlank() && !kompakt) append("–${inhalt.ende}")
            }
        }

        if (kompakt) {
            // Untereinander: nebeneinander passt "Raum 226 · 07:15–08:00" nicht in 2x2.
            if (!inhalt.raum.isNullOrBlank()) {
                Text(
                    text = inhalt.raum,
                    style = TextStyle(color = TextStark, fontSize = scaled(16), fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            if (zeit.isNotEmpty()) {
                Text(
                    text = zeit,
                    style = TextStyle(color = TextSchwach, fontSize = scaled(14)),
                    maxLines = 1
                )
            }
            if (!niedrig && !inhalt.lehrer.isNullOrBlank()) {
                Text(
                    text = inhalt.lehrer,
                    style = TextStyle(color = TextSchwach, fontSize = scaled(12)),
                    maxLines = 1
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!inhalt.raum.isNullOrBlank()) {
                    Text(
                        text = inhalt.raum,
                        style = TextStyle(color = TextStark, fontSize = scaled(17), fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(10.dp))
                }
                if (zeit.isNotEmpty()) {
                    Text(
                        text = zeit,
                        style = TextStyle(color = TextSchwach, fontSize = scaled(15)),
                        maxLines = 1
                    )
                }
            }
            if (!inhalt.lehrer.isNullOrBlank()) {
                Text(
                    text = inhalt.lehrer,
                    style = TextStyle(color = TextSchwach, fontSize = scaled(13)),
                    maxLines = 1
                )
            }
        }
    }

    /** Ein Wort für schmale Widgets: "morgen", "Mi 27.08.", "jetzt" oder die Stundennummer. */
    private fun kurzerKopf(inhalt: WidgetDataStore.WidgetInhalt): String = when {
        inhalt.tag != null -> inhalt.tag
        inhalt.istVorschau -> "danach"
        inhalt.stunde > 0 -> "${inhalt.stunde}. Stunde"
        else -> "als Nächstes"
    }

    private fun langerKopf(inhalt: WidgetDataStore.WidgetInhalt): String = when {
        inhalt.tag != null -> "Als Nächstes · ${inhalt.tag}"
        inhalt.istVorschau -> "Gleich vorbei · als Nächstes"
        inhalt.stunde > 0 -> "Nächste Stunde · ${inhalt.stunde}."
        else -> "Nächste Stunde"
    }

    companion object {
        /** 2x2 – die kleinste unterstützte Größe. */
        private val KLEIN = DpSize(120.dp, 120.dp)
        /** 3x2 */
        private val BREIT = DpSize(200.dp, 120.dp)
        /** 4x2 und größer */
        private val GROSS = DpSize(260.dp, 180.dp)

        // Farben getrennt für hell und dunkel, damit der Text in beiden Fällen sitzt.
        private val TextStark = ColorProvider(day = Color(0xFF1B1B21), night = Color(0xFFE4E1E9))
        private val TextSchwach = ColorProvider(day = Color(0xFF6B6B75), night = Color(0xFFA8A6B0))
        private val AlarmFarbe = ColorProvider(day = Color(0xFF8C1D18), night = Color(0xFFFFB4AB))
    }
}

/** Tippen auf das Widget stößt sofort eine Aktualisierung an. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        RefreshScheduler.sofortAktualisieren(context)
    }
}
