package com.nextlesson.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val leistenFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * Nach links wischen = nächster Tag, nach rechts = voriger. Das Wischen greift erst nach einer
 * deutlichen Bewegung zur Seite, damit normales Scrollen nach oben und unten nicht stört.
 */
@Composable
fun TagWischen(
    onVor: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var strecke by remember { mutableFloatStateOf(0f) }
    val schwelle = with(LocalDensity.current) { 90.dp.toPx() }
    Box(
        modifier = modifier.pointerInput(onVor, onZurueck) {
            detectHorizontalDragGestures(
                onDragStart = { strecke = 0f },
                onDragCancel = { strecke = 0f },
                onDragEnd = {
                    if (strecke <= -schwelle) onVor() else if (strecke >= schwelle) onZurueck()
                    strecke = 0f
                }
            ) { _, delta -> strecke += delta }
        }
    ) { content() }
}

/** Pfeile zum Blättern zwischen den Tagen; "Zurück" erscheint, wenn man nicht auf dem normalen Tag ist. */
@Composable
fun TagesLeiste(
    datum: LocalDate,
    abweichend: Boolean,
    basisText: String,
    onZurueck: () -> Unit,
    onVor: () -> Unit,
    onBasis: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onZurueck) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Tag")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = datum.format(leistenFormat),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            if (abweichend) {
                TextButton(onClick = onBasis) { Text(basisText) }
            }
        }
        IconButton(onClick = onVor) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Tag")
        }
    }
}

@Composable
fun TagLeerHinweis(grund: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(grund, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(
            "Wische weiter, um andere Tage zu sehen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
