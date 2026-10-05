package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.nextlesson.app.data.Freiblock
import com.nextlesson.app.data.Freizeit
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.Hausaufgabe
import com.nextlesson.app.data.Pruefung
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nextlesson.app.ui.theme.istDunkel
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

/**
 * Fehleranzeige mit Ausweg: "Erneut versuchen" und – bei falschen Zugangsdaten –
 * ein direkter Weg in die Einstellungen. Scrollbar, damit Pull-to-Refresh auch hier geht.
 */
@Composable
fun FehlerScreen(
    nachricht: String,
    zugangsproblem: Boolean,
    onErneutVersuchen: () -> Unit,
    onEinstellungen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = nachricht,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (zugangsproblem) {
            Button(onClick = onEinstellungen) { Text("Zugangsdaten prüfen") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onErneutVersuchen) { Text("Erneut versuchen") }
        } else {
            Button(onClick = onErneutVersuchen) { Text("Erneut versuchen") }
        }
    }
}

/** Zusammenfassung von Hausaufgaben und nächster Klausur für die Startseite. */
data class Uebersicht(
    val offeneAufgaben: Int,
    val ueberfaellig: Int,
    val bisMorgen: Int,
    val naechstePruefung: Pruefung?
) {
    companion object {
        fun berechne(
            hausaufgaben: List<Hausaufgabe>,
            pruefungen: List<Pruefung>,
            heute: LocalDate = LocalDate.now()
        ): Uebersicht {
            val offen = hausaufgaben.filter { !it.erledigt }
            return Uebersicht(
                offeneAufgaben = offen.size,
                ueberfaellig = offen.count { it.istUeberfaellig(heute) },
                bisMorgen = offen.count {
                    val f = it.faellig
                    f != null && !f.isBefore(heute) && !f.isAfter(heute.plusDays(1))
                },
                // Nur was in den nächsten zwei Wochen ansteht – alles Weitere wäre Rauschen.
                naechstePruefung = pruefungen
                    .filter { !it.istVorbei(heute) && it.tageBis(heute) <= 14 }
                    .minByOrNull { it.datumEpochDay }
            )
        }
    }
}

@Composable
fun HomeScreen(
    persoenlich: PersoenlicherPlan,
    uebersicht: Uebersicht,
    aktualisiertGerade: Boolean,
    onOeffneAufgaben: () -> Unit,
    onOeffnePruefungen: () -> Unit,
    onStundeAntippen: (Lesson) -> Unit = {},
    freunde: List<Freund> = emptyList(),
    onFreund: (Freund) -> Unit = {},
    onFreundeWoche: () -> Unit = {},
    onTagVorbei: () -> Unit = {}
) {
    val jetzt by rememberJetzt(aktualisiertGerade)
    val plan = persoenlich.plan
    // Heute wird "nächste Stunde" mit der tickenden Uhr neu bestimmt. Sonst bliebe bei
    // geöffneter App die beim Laden ermittelte Stunde stehen, obwohl sie längst vorbei ist.
    val naechste = if (persoenlich.istHeute) plan.naechsteStunde(jetzt) else persoenlich.naechste
    val dunkel = istDunkel()
    // Freistunden im Zeitraster der Schule (auch vor der ersten Stunde), je an der Stelle der
    // Stunde, die danach kommt.
    val freiVor = remember(persoenlich) {
        Freizeit.freiBloecke(plan, persoenlich.gesamt.zeitraster).mapNotNull { block ->
            val danach = plan.stunden.indexOfFirst { l -> l.beginn?.let { !it.isBefore(block.ende) } == true }
            if (danach >= 0) danach to block else null
        }.toMap()
    }

    // Endet die letzte Stunde, während die App offen ist, einmal neu laden: Dann springt die
    // Ansicht auf den nächsten Schultag, statt "kein Unterricht in den nächsten Tagen" zu zeigen.
    // Höchstens einmal je Tag, damit es keine Endlosschleife gibt, falls wirklich nichts kommt.
    val tagVorbei = persoenlich.istHeute && naechste == null
    var nachgeladenFuer by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(tagVorbei, persoenlich.datum) {
        if (tagVorbei && nachgeladenFuer != persoenlich.datum) {
            nachgeladenFuer = persoenlich.datum
            onTagVorbei()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (persoenlich.ausCache) {
            item {
                Spacer(Modifier.height(4.dp))
                OfflineHinweis(persoenlich.geprueftUm, aktualisiertGerade)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            if (naechste != null) {
                HeroKarte(naechste, persoenlich.istHeute, persoenlich.datum, jetzt, dunkel)
            } else {
                LeerKarte(tagVorbei = tagVorbei, laedt = aktualisiertGerade)
            }
        }

        if (plan.hinweise.isNotEmpty()) {
            item {
                UebersichtKarte(
                    titel = "Hinweise für ${if (persoenlich.istHeute) "heute" else "diesen Tag"}",
                    text = plan.hinweise.joinToString("\n"),
                    hervorgehoben = plan.hinweise.any { it.contains("klausur", ignoreCase = true) },
                    onClick = null
                )
            }
        }

        if (uebersicht.offeneAufgaben > 0) {
            item {
                UebersichtKarte(
                    titel = "Hausaufgaben",
                    text = aufgabenText(uebersicht),
                    hervorgehoben = uebersicht.ueberfaellig > 0,
                    onClick = onOeffneAufgaben
                )
            }
        }
        uebersicht.naechstePruefung?.let { pruefung ->
            item {
                UebersichtKarte(
                    titel = pruefung.art.anzeige,
                    text = pruefungText(pruefung),
                    hervorgehoben = pruefung.tageBis() <= 1,
                    onClick = onOeffnePruefungen
                )
            }
        }

        if (freunde.isNotEmpty()) {
            item { FreundeKarte(freunde, persoenlich, onFreund, onFreundeWoche) }
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
                    text = buildString {
                        append(
                            if (persoenlich.istHeute) "Heute" else
                                persoenlich.datum.format(kurzTagFormat).replaceFirstChar { it.uppercase() }
                        )
                        plan.schluss()?.let { append(" · Schluss ${it.format(zeitFormat)}") }
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
        itemsIndexed(plan.stunden) { index, lesson ->
            Column {
                freiVor[index]?.let { FreistundenZeile(it) }
                StundenZeile(
                    lesson = lesson,
                    istNaechste = lesson.stunde == naechste?.lesson?.stunde,
                    laeuftGerade = persoenlich.istHeute && laeuft(lesson, jetzt),
                    dunkel = dunkel,
                    onClick = { onStundeAntippen(lesson) }
                )
            }
        }

        item {
            Fusszeile(persoenlich)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OfflineHinweis(geprueftUm: Long, aktualisiertGerade: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = buildString {
                append("Gespeicherter Stand von ${uhrzeit(geprueftUm)}")
                append(if (aktualisiertGerade) " · wird aktualisiert …" else " · keine Verbindung, Plan kann veraltet sein")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun UebersichtKarte(
    titel: String,
    text: String,
    hervorgehoben: Boolean,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // Ohne Aktion auch kein Klick-Effekt (vorher: Welle, aber nichts passierte).
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hervorgehoben) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = titel,
                style = MaterialTheme.typography.labelLarge,
                color = if (hervorgehoben) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hervorgehoben) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun aufgabenText(u: Uebersicht): String = buildString {
    append(if (u.offeneAufgaben == 1) "1 offene Aufgabe" else "${u.offeneAufgaben} offene Aufgaben")
    val teile = buildList {
        if (u.ueberfaellig > 0) add("${u.ueberfaellig} überfällig")
        if (u.bisMorgen > 0) add("${u.bisMorgen} bis morgen fällig")
    }
    if (teile.isNotEmpty()) append(" · ").append(teile.joinToString(", "))
}

private fun pruefungText(p: Pruefung): String {
    val name = listOf(p.fach, p.titel).filter { it.isNotBlank() }.joinToString(" – ")
    val wann = when (val tage = p.tageBis()) {
        0L -> "heute"
        1L -> "morgen"
        else -> "in $tage Tagen"
    }
    return if (name.isBlank()) wann.replaceFirstChar { it.uppercase() } else "$name · $wann"
}

/** Dezente Zeile für eine Freistunde, damit man freie Zeit auf einen Blick sieht. */
@Composable
internal fun FreistundenZeile(block: Freiblock) {
    val zeit = "${block.beginn.format(zeitFormat)}–${block.ende.format(zeitFormat)}"
    Text(
        text = listOfNotNull("Freistunde", block.stundenText, zeit).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, bottom = 10.dp)
    )
}

/** Tagesplan als Text zum Teilen (z.B. per Messenger an Mitschüler). */
fun planAlsText(persoenlich: PersoenlicherPlan): String = buildString {
    append(persoenlich.datum.format(tagFormat))
    persoenlich.plan.stunden.forEach { l ->
        append("\n")
        append(l.beginn?.format(zeitFormat) ?: "${l.stunde}.")
        append("  ")
        append(l.fach.ifBlank { l.kursKuerzel ?: "—" })
        when {
            l.entfaellt -> append(" – fällt aus")
            l.istKlausur -> append(" – Klausur")
            l.raum.isNotBlank() -> append(" (${l.raum})")
        }
        if (!l.entfaellt && l.status == LessonStatus.VERTRETUNG && l.lehrer.isNotBlank()) {
            append(", Vertretung: ${l.lehrer}")
        }
    }
    persoenlich.plan.schluss()?.let { append("\nSchluss: ${it.format(zeitFormat)}") }
}

internal fun laeuft(lesson: Lesson, jetzt: LocalTime): Boolean {
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
                        originalWert = if (lesson.raumGeaendert) lesson.originalRoom else null,
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
private fun LeerKarte(tagVorbei: Boolean, laedt: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = if (tagVorbei) "Schluss für heute" else "Frei",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = when {
                    tagVorbei && laedt -> "Der nächste Schultag wird geladen …"
                    tagVorbei -> "Für die nächsten Tage ist in deinen Kursen noch kein Unterricht eingetragen."
                    else -> "In den nächsten Tagen ist in deinen Kursen kein Unterricht eingetragen."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StundenZeile(
    lesson: Lesson,
    istNaechste: Boolean,
    laeuftGerade: Boolean,
    dunkel: Boolean,
    onClick: (() -> Unit)?
) {
    val status = lesson.status
    val istEntfall = status == LessonStatus.ENTFALL
    val istVertretung = status == LessonStatus.VERTRETUNG

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
        if (!lesson.entfaellt && lesson.raum.isNotBlank() && status != LessonStatus.RAUMAENDERUNG) {
            append(", Raum ${lesson.raum}")
        }
        if (lesson.istKlausur && !lesson.entfaellt) append(", Klausur")
        if (lesson.hatAufgaben) append(", Aufgaben erteilt")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(hintergrund)
            .alpha(alpha)
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = "Hausaufgabe eintragen", onClick = onClick)
                else Modifier
            )
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
            // Hinweis des Plans (z.B. "Klausur!", "Aufgaben in Moodle") – so sieht man, warum.
            if (lesson.info.isNotBlank()) {
                Text(
                    text = lesson.info,
                    style = MaterialTheme.typography.labelSmall,
                    color = textNeben,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Raum bzw. Statusetikett
        Column(horizontalAlignment = Alignment.End) {
            if (lesson.istKlausur && !istEntfall) {
                StatusBadge(
                    text = "Klausur",
                    hintergrund = MaterialTheme.colorScheme.error,
                    vordergrund = MaterialTheme.colorScheme.onError
                )
                Spacer(Modifier.height(2.dp))
            }
            if (lesson.hatAufgaben) {
                StatusBadge(
                    text = "Aufgaben erteilt",
                    hintergrund = Color(0xFF455A64),
                    vordergrund = Color.White
                )
                Spacer(Modifier.height(2.dp))
            }
            if (istEntfall) {
                StatusBadge(
                    text = "entfällt",
                    hintergrund = MaterialTheme.colorScheme.error,
                    vordergrund = MaterialTheme.colorScheme.onError
                )
            } else {
                if (istVertretung) {
                    StatusBadge(
                        text = "Vertretung",
                        hintergrund = Color(0xFFFF9800),
                        vordergrund = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                }
                // Den Raum immer zeigen – auch bei einer Vertretung (dort fehlte er vorher ganz),
                // und eine Raumänderung auch dann markieren, wenn zugleich vertreten wird.
                if (lesson.raumGeaendert && lesson.originalRoom != null) {
                    Text(
                        text = lesson.originalRoom,
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = textNeben
                    )
                }
                if (lesson.raum.isNotBlank()) {
                    Text(
                        text = lesson.raum,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (lesson.raumGeaendert) Color(0xFFE65100) else textHaupt
                    )
                }
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
            text = "Tipp: Stunde antippen, um eine Hausaufgabe bis zur nächsten Stunde einzutragen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
