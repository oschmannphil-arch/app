package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.data.LessonStatus
import com.nextlesson.app.data.NaechsteStundeErgebnis
import com.nextlesson.app.data.PersoenlicherPlan
import com.nextlesson.app.ui.theme.fachFarbe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zeitFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val tagFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val kurzTagFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)

@Composable
fun LadeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun FehlerScreen(nachricht: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = nachricht,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeScreen(persoenlich: PersoenlicherPlan) {
    val jetzt by rememberJetzt()
    val plan = persoenlich.plan
    val naechste = persoenlich.naechste
    val dunkel = isSystemInDarkTheme()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            if (naechste != null) {
                HeroKarte(naechste, persoenlich.istHeute, persoenlich.datum, jetzt, dunkel)
            } else {
                LeerKarte()
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (persoenlich.istHeute) "Heute" else {
                        persoenlich.datum.format(kurzTagFormat).replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = persoenlich.datum.format(tagFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bewusst ohne key: bei mehreren gewählten Kursblöcken können zwei Stunden
        // dieselbe Nummer und dasselbe Fach haben, und doppelte Keys lassen LazyColumn abstürzen.
        items(plan.stunden) { lesson ->
            StundenZeile(
                lesson = lesson,
                istNaechste = lesson.stunde == naechste?.lesson?.stunde,
                laeuftGerade = persoenlich.istHeute && laeuft(lesson, jetzt),
                dunkel = dunkel
            )
        }

        item {
            Fusszeile(persoenlich)
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun laeuft(lesson: Lesson, jetzt: LocalTime): Boolean {
    val b = lesson.beginn ?: return false
    val e = lesson.ende ?: return false
    return !jetzt.isBefore(b) && jetzt.isBefore(e)
}

@Composable
private fun HeroKarte(
    ergebnis: NaechsteStundeErgebnis,
    istHeute: Boolean,
    datum: LocalDate,
    jetzt: LocalTime,
    dunkel: Boolean
) {
    val lesson = ergebnis.lesson
    val status = lesson.status
    val istEntfall = status == LessonStatus.ENTFALL
    val istVertretung = status == LessonStatus.VERTRETUNG
    val istRaum = status == LessonStatus.RAUMAENDERUNG
    val gestoert = status != LessonStatus.NORMAL

    val akzent = when (status) {
        LessonStatus.ENTFALL -> MaterialTheme.colorScheme.outline
        LessonStatus.VERTRETUNG -> Color(0xFFFF9800)
        LessonStatus.RAUMAENDERUNG -> Color(0xFFFF9800)
        else -> fachFarbe(lesson.fach, dunkel)
    }

    val kopf = when {
        !istHeute -> {
            val label = if (datum == LocalDate.now().plusDays(1)) "morgen" else
                datum.format(kurzTagFormat).replaceFirstChar { it.uppercase() }
            "Als Nächstes · $label"
        }
        ergebnis.istVorschau -> "Gleich vorbei · als Nächstes"
        else -> "Nächste Stunde"
    }
    val countdown = countdownText(lesson, jetzt, istHeute)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (gestoert) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Farbschiene links: gibt der Karte auf einen Blick ein Fach.
            // IntrinsicSize.Min sorgt dafür, dass sie exakt so hoch wird wie der Inhalt.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(akzent)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = kopf,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (gestoert) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (countdown != null) {
                        StatusBadge(
                            text = countdown,
                            hintergrund = akzent,
                            // Der Akzent ist im Dunkelmodus hell und im Hellmodus dunkel –
                            // die Grundfläche ist jeweils genau der Gegenpol dazu.
                            vordergrund = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = lesson.fach.ifBlank { "—" },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (gestoert) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                // Raum und Zeit sind das, wofür man die App wirklich aufmacht. Responsive per Weight.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoBlock(
                        titel = "Raum",
                        wert = if (istEntfall) "—" else lesson.raum.ifBlank { "—" },
                        gestoert = gestoert,
                        originalWert = if (istRaum) lesson.originalRoom else null,
                        modifier = Modifier.weight(1f)
                    )
                    val beginn = lesson.beginn
                    val ende = lesson.ende
                    if (beginn != null) {
                        InfoBlock(
                            titel = "${lesson.stunde}. Stunde",
                            wert = if (ende != null) {
                                "${beginn.format(zeitFormat)}–${ende.format(zeitFormat)}"
                            } else {
                                beginn.format(zeitFormat)
                            },
                            gestoert = gestoert,
                            modifier = Modifier.weight(1.2f) // Etwas mehr Platz für die Zeitspanne
                        )
                    }
                    if (lesson.lehrer.isNotBlank()) {
                        InfoBlock(
                            titel = "Lehrer",
                            wert = lesson.lehrer,
                            gestoert = gestoert,
                            originalWert = if (istVertretung) lesson.originalTeacher else null,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Spacer mit Weight, damit das Layout auch ohne Lehrer symmetrisch bleibt
                        Spacer(Modifier.weight(1f))
                    }
                }

                if (gestoert) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = lesson.info.ifBlank { "Änderung zum Regelplan" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (lesson.hatAufgaben) {
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(
                        text = "Aufgaben erteilt",
                        hintergrund = Color(0xFF455A64),
                        vordergrund = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(
    titel: String,
    wert: String,
    gestoert: Boolean,
    modifier: Modifier = Modifier,
    originalWert: String? = null
) {
    val gedimmt = if (gestoert) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    val voll = if (gestoert) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    Column(modifier = modifier) {
        Text(text = titel, style = MaterialTheme.typography.labelSmall, color = gedimmt, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = wert,
                style = MaterialTheme.typography.titleMedium,
                color = voll,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (originalWert != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = originalWert,
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = gedimmt,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LeerKarte() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Frei", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "In den nächsten Tagen ist in deinen Kursen kein Unterricht eingetragen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StundenZeile(
    lesson: Lesson,
    istNaechste: Boolean,
    laeuftGerade: Boolean,
    dunkel: Boolean
) {
    val status = lesson.status
    val istEntfall = status == LessonStatus.ENTFALL
    val istVertretung = status == LessonStatus.VERTRETUNG
    val istRaum = status == LessonStatus.RAUMAENDERUNG

    val akzent = when (status) {
        LessonStatus.ENTFALL -> Color.Gray
        LessonStatus.VERTRETUNG -> Color(0xFFFF9800)
        LessonStatus.RAUMAENDERUNG -> Color(0xFFFF9800)
        else -> fachFarbe(lesson.fach, dunkel)
    }

    // Jede Zeile bekommt eine Fläche; die laufende bzw. nächste hebt sich davon ab.
    val hervorgehoben = laeuftGerade || istNaechste
    val hintergrund = if (hervorgehoben) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    val textHaupt = if (hervorgehoben) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val textNeben = if (hervorgehoben) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    val alpha = if (istEntfall) 0.4f else 1.0f

    val desc = buildString {
        append("${lesson.stunde}. Stunde: ${lesson.fach.ifBlank { "Unbekannt" }}")
        when (status) {
            LessonStatus.ENTFALL -> append(", fällt aus")
            LessonStatus.VERTRETUNG -> append(", Vertretung durch ${lesson.lehrer}")
            LessonStatus.RAUMAENDERUNG -> append(", Raumänderung nach ${lesson.raum}")
            else -> append(", bei ${lesson.lehrer}")
        }
        if (lesson.hatAufgaben) append(", Aufgaben erteilt")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(hintergrund)
            .alpha(alpha)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            Spacer(Modifier.width(8.dp))
        }

        // Zeitspalte - Flexibel bei großer Schrift
        Column(
            modifier = Modifier.widthIn(min = 52.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = lesson.beginn?.format(zeitFormat) ?: "${lesson.stunde}.",
                style = MaterialTheme.typography.titleSmall,
                color = textHaupt
            )
            Text(
                text = lesson.ende?.format(zeitFormat) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = textNeben
            )
        }

        // Farbpunkt als Fachkennung
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(akzent)
        )

        // Fach + Details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lesson.fach.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (istEntfall) TextDecoration.LineThrough else null,
                    color = if (istEntfall) textNeben else textHaupt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (laeuftGerade && !istEntfall) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        text = "jetzt",
                        hintergrund = MaterialTheme.colorScheme.secondary,
                        vordergrund = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lesson.lehrer.isNotBlank()) {
                    Text(
                        text = lesson.lehrer,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = if (istEntfall) TextDecoration.LineThrough else null,
                        color = textNeben,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (istVertretung && lesson.originalTeacher != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lesson.originalTeacher,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        textDecoration = TextDecoration.LineThrough,
                        color = textNeben,
                        maxLines = 1
                    )
                }
            }
        }

        // Raum bzw. Statusetikett
        Column(horizontalAlignment = Alignment.End) {
            if (lesson.hatAufgaben) {
                StatusBadge(
                    text = "Aufgaben erteilt",
                    hintergrund = Color(0xFF455A64),
                    vordergrund = Color.White
                )
                Spacer(Modifier.height(2.dp))
            }
            when {
                istEntfall -> StatusBadge(
                    text = "entfällt",
                    hintergrund = MaterialTheme.colorScheme.error,
                    vordergrund = MaterialTheme.colorScheme.onError
                )
                istVertretung -> StatusBadge(
                    text = "Vertretung",
                    hintergrund = Color(0xFFFF9800),
                    vordergrund = Color.White
                )
                istRaum -> Column(horizontalAlignment = Alignment.End) {
                    if (lesson.originalRoom != null) {
                        Text(
                            text = lesson.originalRoom,
                            style = MaterialTheme.typography.labelSmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = textNeben
                        )
                    }
                    Text(
                        text = lesson.raum,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE65100)
                    )
                }
                lesson.raum.isNotBlank() -> Text(
                    text = lesson.raum,
                    style = MaterialTheme.typography.titleSmall,
                    color = textHaupt
                )
            }
        }
    }
}

@Composable
private fun Fusszeile(persoenlich: PersoenlicherPlan) {
    val plan = persoenlich.plan
    Column(modifier = Modifier.padding(top = 12.dp, start = 4.dp)) {
        if (plan.klasse.isNotBlank()) {
            Text(
                text = plan.klasse,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (plan.kopf.zeitstempel.isNotBlank()) {
            Text(
                text = "Plan der Schule: ${plan.kopf.zeitstempel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "App zuletzt geprüft: ${uhrzeit(persoenlich.geprueftUm)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun uhrzeit(millis: Long): String {
    if (millis <= 0L) return "–"
    val zeit = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(zeit.hour, zeit.minute)
}
