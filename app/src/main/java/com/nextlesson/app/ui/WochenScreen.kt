package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.data.LessonStatus
import com.nextlesson.app.ui.theme.fachFarbe
import com.nextlesson.app.ui.theme.istDunkel
import java.time.LocalDate
import com.nextlesson.app.data.alsEintraege
import com.nextlesson.app.data.hinweisKurz
import java.time.format.DateTimeFormatter
import java.util.Locale

private val wochentagFormat = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
private val datumFormat = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
private val zeitFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun WochenScreen(
    zustand: WochenZustand,
    auswahl: WochenAuswahl,
    onAuswahlChange: (WochenAuswahl) -> Unit,
    blockAnsicht: Boolean = false,
    onBlockAnsicht: (Boolean) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WochenAuswahlLeiste(auswahl, onAuswahlChange)
        AnsichtUmschalter(blockAnsicht, onBlockAnsicht, Modifier.padding(horizontal = 16.dp))

        when (zustand) {
            is WochenZustand.NichtGeladen, is WochenZustand.Laedt -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is WochenZustand.Geladen -> {
                val dunkel = istDunkel()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(zustand.tage, key = { it.datum.toString() }) { tag ->
                        TagKarte(tag, dunkel, blockAnsicht)
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WochenAuswahlLeiste(
    auswahl: WochenAuswahl,
    onAuswahlChange: (WochenAuswahl) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WochenAuswahl.entries.forEach { eintrag ->
            FilterChip(
                selected = auswahl == eintrag,
                onClick = { onAuswahlChange(eintrag) },
                label = { Text(eintrag.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = null
            )
        }
    }
}

@Composable
private fun TagKarte(tag: WochenTag, dunkel: Boolean, blockAnsicht: Boolean) {
    val heute = tag.datum == LocalDate.now()
    val stunden = tag.plan?.stunden.orEmpty()
    
    // Wir zählen nur echte Änderungen für die Badges oben rechts.
    val entfaelle = stunden.count { it.status == LessonStatus.ENTFALL }
    val aenderungen = stunden.count { 
        it.status != LessonStatus.NORMAL && 
        it.status != LessonStatus.ENTFALL
    }
    val klausur = stunden.any { it.istKlausur && !it.entfaellt }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (heute) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Kopfzeile flexibel gestaltet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Oben bündig für Flow-Verhalten
            ) {
                // Linke Seite: Wochentag & Datum (in einer Spalte)
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tag.datum.format(wochentagFormat).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip // Nicht abschneiden, sondern Platz lassen
                        )
                        if (heute) {
                            Spacer(Modifier.width(6.dp))
                            StatusBadge(
                                text = "heute",
                                hintergrund = MaterialTheme.colorScheme.primary,
                                vordergrund = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Text(
                        text = tag.datum.format(datumFormat),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                
                // Rechte Seite: Badges – wenn es zu viele werden, rutschen sie untereinander
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (klausur) {
                        StatusBadge(
                            text = "Klausur",
                            hintergrund = MaterialTheme.colorScheme.error,
                            vordergrund = MaterialTheme.colorScheme.onError
                        )
                    }
                    if (entfaelle > 0) {
                        StatusBadge(
                            text = if (entfaelle == 1) "1 Entfall" else "$entfaelle Entfälle",
                            hintergrund = MaterialTheme.colorScheme.errorContainer,
                            vordergrund = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (aenderungen > 0) {
                        StatusBadge(
                            text = if (aenderungen == 1) "1 Änderung" else "$aenderungen Änderungen",
                            hintergrund = Color(0xFFFFF3E0),
                            vordergrund = Color(0xFFE65100)
                        )
                    }
                }
            }

            when {
                tag.fehlermeldung != null -> Hinweis(tag.fehlermeldung)
                stunden.isEmpty() -> Hinweis("Kein Unterricht in deinen Kursen.")
                else -> {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    val eintraege = remember(stunden, blockAnsicht) { stunden.alsEintraege(blockAnsicht) }
                    eintraege.forEachIndexed { index, block ->
                        WochenStundenZeile(block.zusammengefasst, dunkel, if (blockAnsicht) block.stundenKurz else null)
                        if (index < eintraege.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun WochenStundenZeile(lesson: Lesson, dunkel: Boolean, stundenKurz: String? = null) {
    val status = lesson.status
    val istEntfall = status == LessonStatus.ENTFALL
    val istVertretung = status == LessonStatus.VERTRETUNG

    val akzent = when (status) {
        LessonStatus.ENTFALL -> MaterialTheme.colorScheme.outline
        LessonStatus.VERTRETUNG -> Color(0xFFFF9800)
        LessonStatus.RAUMAENDERUNG -> Color(0xFFFF9800)
        else -> fachFarbe(lesson.fach, dunkel)
    }

    val haupt = LocalContentColor.current
    val neben = haupt.copy(alpha = 0.7f)
    val alpha = if (istEntfall) 0.4f else 1.0f

    val desc = buildString {
        append("${stundenKurz?.let { "$it Stunde" } ?: "${lesson.stunde}. Stunde"}: ${lesson.fach.ifBlank { "Unbekannt" }}")
        when (status) {
            LessonStatus.ENTFALL -> append(", fällt aus")
            LessonStatus.VERTRETUNG -> append(", Vertretung durch ${lesson.lehrer}")
            LessonStatus.RAUMAENDERUNG -> append(", Raumänderung nach ${lesson.raum}")
            else -> append(", bei ${lesson.lehrer}")
        }
        if (lesson.istKlausur && !istEntfall) append(", Klausur")
        if (lesson.hatAufgaben) append(", Aufgaben erteilt")
        if (lesson.raum.isNotBlank() && !istEntfall) append(" in Raum ${lesson.raum}")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .alpha(alpha)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .background(
                if (istVertretung) Color(0xFFFF9800).copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertretungs-Balken links
        if (istVertretung) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF9800))
            )
            Spacer(Modifier.width(6.dp))
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stundenKurz ?: "${lesson.stunde}.",
                style = MaterialTheme.typography.labelMedium,
                color = neben,
                modifier = Modifier.widthIn(min = 22.dp).padding(end = if (stundenKurz != null) 4.dp else 0.dp)
            )
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (istEntfall) Color.Gray else akzent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.fach.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (lesson.hatAenderung) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (istEntfall) TextDecoration.LineThrough else null,
                    color = if (istEntfall) neben else haupt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lesson.lehrer.isNotBlank()) {
                    Text(
                        text = lesson.lehrer,
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = if (istEntfall) TextDecoration.LineThrough else null,
                        color = neben,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (istVertretung && lesson.originalTeacher != null) {
                    Text(
                        text = lesson.originalTeacher,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        textDecoration = TextDecoration.LineThrough,
                        color = neben,
                        maxLines = 1
                    )
                }
                val hinweisText = lesson.hinweisKurz()
                if (hinweisText.isNotBlank()) {
                    Text(
                        text = hinweisText,
                        style = MaterialTheme.typography.labelSmall,
                        color = neben,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val beginn = lesson.beginn
            if (beginn != null) {
                Text(
                    text = beginn.format(zeitFormat),
                    style = MaterialTheme.typography.labelMedium,
                    color = neben
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Raumänderung auch markieren, wenn zugleich vertreten wird.
                if (lesson.raumGeaendert && lesson.originalRoom != null) {
                    Text(
                        text = lesson.originalRoom,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = neben,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (lesson.raum.isNotBlank() && !istEntfall) {
                    Text(
                        text = lesson.raum,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (lesson.raumGeaendert) Color(0xFFE65100) else haupt
                    )
                }
            }
        }

        // An Klausurtagen soll man die Klausur auch in der Woche sehen (fehlte vorher).
        if (lesson.istKlausur && !istEntfall) {
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = "Klausur",
                hintergrund = MaterialTheme.colorScheme.error,
                vordergrund = MaterialTheme.colorScheme.onError
            )
        }
        if (lesson.hatAufgaben) {
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = "Aufgaben erteilt",
                hintergrund = Color(0xFF455A64),
                vordergrund = Color.White
            )
        }
        if (istEntfall) {
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = "entfällt",
                hintergrund = MaterialTheme.colorScheme.error,
                vordergrund = MaterialTheme.colorScheme.onError
            )
        }
    }
}
