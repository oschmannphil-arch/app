package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Lesson
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime

/** Kleines farbiges Etikett, z.B. "Entfällt", "Raum neu", "läuft". */
@Composable
fun StatusBadge(
    text: String,
    hintergrund: Color,
    vordergrund: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(hintergrund, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = vordergrund,
            maxLines = 1
        )
    }
}

/**
 * Tickende Uhrzeit: aktualisiert sich zu jeder vollen Minute, damit der Countdown
 * auf dem Bildschirm nicht einfriert, während man draufschaut.
 */
@Composable
fun rememberJetzt(neuStart: Any? = null): State<LocalTime> {
    val zustand = remember { mutableStateOf(LocalTime.now()) }
    // Bei jedem neuen [neuStart] (z.B. Aktualisierung nach dem Öffnen der App) sofort die
    // echte Uhrzeit nehmen, statt bis zum nächsten Tick mit einem alten Wert zu arbeiten.
    LaunchedEffect(neuStart) {
        zustand.value = LocalTime.now()
        while (true) {
            // Genau zur nächsten vollen Minute ticken – dann wechseln Stunden und Countdown
            // im Gleichtakt mit der Uhr (vorher: alle 30 s ab beliebigem Zeitpunkt = bis zu 30 s Versatz).
            delay(60_000 - System.currentTimeMillis() % 60_000 + 100)
            zustand.value = LocalTime.now()
        }
    }
    return zustand
}

/**
 * Menschlicher Countdown zur nächsten Stunde – nur für heute sinnvoll.
 *
 * "läuft noch 12 Min" · "in 8 Min" · "in 1 Std 20 Min"
 */
fun countdownText(lesson: Lesson, jetzt: LocalTime, istHeute: Boolean): String? {
    if (!istHeute) return null
    val beginn = lesson.beginn ?: return null
    val ende = lesson.ende

    // Aufgerundet auf volle Minuten: bei 8:20 Restzeit steht "9 Min" statt "8 Min", und die
    // Anzeige springt genau zur vollen Minute – wie die Uhr.
    if (ende != null && !jetzt.isBefore(beginn) && jetzt.isBefore(ende)) {
        val rest = (Duration.between(jetzt, ende).seconds + 59) / 60
        return if (rest <= 0) "endet gleich" else "läuft noch ${dauer(rest)}"
    }

    val bisSekunden = Duration.between(jetzt, beginn).seconds
    val bis = (bisSekunden + 59) / 60
    return when {
        bisSekunden < 0 -> null
        bis == 0L -> "jetzt"
        bis > 600 -> null // mehr als 10 Stunden: Uhrzeit sagt mehr als ein Countdown
        else -> "in ${dauer(bis)}"
    }
}

private fun dauer(minuten: Long): String {
    if (minuten < 60) return "$minuten Min"
    val std = minuten / 60
    val min = minuten % 60
    return if (min == 0L) "$std Std" else "$std Std $min Min"
}
