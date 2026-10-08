package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.FachSchnitt
import com.nextlesson.app.data.Note
import com.nextlesson.app.data.Noten
import com.nextlesson.app.data.NotenArt
import com.nextlesson.app.data.NotenSystem
import com.nextlesson.app.data.Pruefung
import com.nextlesson.app.data.PruefungsArt
import com.nextlesson.app.ui.theme.fachFarbe
import com.nextlesson.app.ui.theme.istDunkel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val notenDatum = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
private val kurzDatum = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)

private fun komma(wert: Double): String = String.format(Locale.GERMAN, "%.1f", wert)

/** Entwurf für den Dialog: bestehende Note bearbeiten oder eine neue (ggf. vorbelegt) anlegen. */
private data class NotenEntwurf(
    val bestehend: Note? = null,
    val fach: String = "",
    val art: NotenArt = NotenArt.KLAUSUR,
    val datum: LocalDate = LocalDate.now()
)

@Composable
fun NotenScreen(
    system: NotenSystem,
    noten: List<Note>,
    klausurAnteil: Int,
    pruefungen: List<Pruefung>,
    fachVorschlaege: List<String>,
    onAnteil: (Int) -> Unit,
    onHinzufuegen: (fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) -> Unit,
    onBearbeiten: (id: String, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) -> Unit,
    onLoeschen: (String) -> Unit
) {
    val dunkel = istDunkel()
    val heute = LocalDate.now()
    val jetzt by rememberJetzt()
    var entwurf by remember { mutableStateOf<NotenEntwurf?>(null) }
    // "" = alle Halbjahre
    var halbjahr by rememberSaveable { mutableStateOf(Noten.halbjahr(heute)) }

    val halbjahre = remember(noten, heute) {
        (noten.map { Noten.halbjahr(it) } + Noten.halbjahr(heute)).distinct().sortedDescending()
    }
    val sichtbar = remember(noten, halbjahr) {
        if (halbjahr.isEmpty()) noten else noten.filter { Noten.halbjahr(it) == halbjahr }
    }
    val faecher = remember(sichtbar, klausurAnteil) { Noten.nachFach(sichtbar, klausurAnteil) }
    val gesamt = remember(faecher) { Noten.gesamtSchnitt(faecher) }

    // Geschriebene Klausuren, zu denen noch keine Note eingetragen ist.
    val offeneKlausuren = remember(pruefungen, noten, heute, jetzt) {
        pruefungen
            .filter { it.art == PruefungsArt.KLAUSUR && it.istVorbei(heute, jetzt) && it.fach.isNotBlank() }
            .filter { p ->
                noten.none { n ->
                    n.art == NotenArt.KLAUSUR && n.datumEpochDay == p.datumEpochDay &&
                        Noten.fachSchluessel(n.fach) == Noten.fachSchluessel(p.fach)
                }
            }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { entwurf = NotenEntwurf(art = NotenArt.MUENDLICH) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Note") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                GesamtKarte(system, gesamt, faecher.size)
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = halbjahr.isEmpty(), onClick = { halbjahr = "" }, label = { Text("Alle") })
                    halbjahre.forEach { hj ->
                        FilterChip(selected = halbjahr == hj, onClick = { halbjahr = hj }, label = { Text(hj) })
                    }
                }
            }

            item { AnteilRegler(klausurAnteil, onAnteil) }

            if (offeneKlausuren.isNotEmpty()) {
                item {
                    Text(
                        text = "Klausur geschrieben – Note eintragen?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(offeneKlausuren, key = { "offen-${it.id}" }) { p ->
                    AssistChip(
                        onClick = { entwurf = NotenEntwurf(fach = p.fach, art = NotenArt.KLAUSUR, datum = p.datum) },
                        label = { Text("${p.fach} · ${p.datum.format(kurzDatum)}") }
                    )
                }
            }

            if (noten.isEmpty()) {
                item {
                    LeerHinweis(
                        if (system == NotenSystem.PUNKTE) "Noch keine Noten. Trag sie unten rechts ein (0 bis 15 Punkte) – die App rechnet Durchschnitt und Zeugnispunkte aus. Alles bleibt nur auf deinem Handy."
                        else "Noch keine Noten. Trag sie unten rechts ein (1 bis 6) – die App rechnet Durchschnitt und Zeugnisnote aus. Alles bleibt nur auf deinem Handy."
                    )
                }
            } else if (faecher.isEmpty()) {
                item { LeerHinweis("In diesem Halbjahr gibt es noch keine Noten.") }
            }

            items(faecher, key = { Noten.fachSchluessel(it.fach) }) { f ->
                val fachNoten = sichtbar.filter { Noten.fachSchluessel(it.fach) == Noten.fachSchluessel(f.fach) }
                FachKarte(
                    system = system,
                    schnitt = f,
                    noten = fachNoten,
                    klausurAnteil = klausurAnteil,
                    dunkel = dunkel,
                    onNote = { entwurf = NotenEntwurf(bestehend = it) },
                    onLoeschen = onLoeschen
                )
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    entwurf?.let { e ->
        NoteDialog(
            system = system,
            entwurf = e,
            vorschlaege = fachVorschlaege,
            onAbbrechen = { entwurf = null },
            onSpeichern = { fach, punkte, art, datum, notiz ->
                val alt = e.bestehend
                if (alt == null) onHinzufuegen(fach, punkte, art, datum, notiz)
                else onBearbeiten(alt.id, fach, punkte, art, datum, notiz)
                entwurf = null
            }
        )
    }
}

@Composable
private fun GesamtKarte(system: NotenSystem, gesamt: Double?, anzahlFaecher: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Gesamtdurchschnitt",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (gesamt == null) {
                Text(
                    text = "–",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = if (system == NotenSystem.PUNKTE) "${komma(gesamt)} Punkte" else "Note ${komma(gesamt)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = (if (system == NotenSystem.PUNKTE) "≈ Note ${komma(Noten.alsSchulnote(gesamt))} · " else "") +
                        "$anzahlFaecher ${if (anzahlFaecher == 1) "Fach" else "Fächer"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FachKarte(
    system: NotenSystem,
    schnitt: FachSchnitt,
    noten: List<Note>,
    klausurAnteil: Int,
    dunkel: Boolean,
    onNote: (Note) -> Unit,
    onLoeschen: (String) -> Unit
) {
    val akzent = fachFarbe(schnitt.fach, dunkel)
    var ziel by rememberSaveable(schnitt.fach) { mutableStateOf(-1) }
    // Standardziel: einen Punkt über dem aktuellen Zeugnisstand.
    val zielWert = if (ziel in system.min..system.max) ziel
    else (if (system.hoeherIstBesser) schnitt.zeugnis + 1 else schnitt.zeugnis - 1).coerceIn(system.min, system.max)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(akzent))
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(schnitt.fach, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val teile = listOfNotNull(
                            schnitt.klausur?.let { "Klausuren Ø ${komma(it)}" },
                            schnitt.muendlich?.let { "Mündlich Ø ${komma(it)}" }
                        )
                        Text(
                            text = teile.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${schnitt.zeugnis}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = akzent)
                        Text("Ø ${komma(schnitt.gesamt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(6.dp))
                noten.forEach { n ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onNote(n) }, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (system == NotenSystem.PUNKTE) "${n.punkte} P" else "Note ${n.punkte}"} · ${n.art.anzeige} · ${n.datum.format(notenDatum)}" +
                                    if (n.notiz.isNotBlank()) " · ${n.notiz}" else "",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onLoeschen(n.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Rechner: Was brauche ich noch für mein Ziel?
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ziel", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { ziel = zielWert - 1 }, enabled = zielWert > system.min) { Text("−") }
                    Text(if (system == NotenSystem.PUNKTE) "$zielWert P" else "Note $zielWert", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { ziel = zielWert + 1 }, enabled = zielWert < system.max) { Text("+") }
                }
                NotenArt.entries.forEach { art ->
                    Text(
                        text = "Nächste ${if (art == NotenArt.KLAUSUR) "Klausur" else "mündliche Note"}: " +
                            when (val b = Noten.benoetigt(noten, art, zielWert, klausurAnteil, system)) {
                                is Noten.Benoetigt.Punkte ->
                                    if (system == NotenSystem.PUNKTE) "mindestens ${b.punkte} P" else "höchstens Note ${b.punkte}"
                                Noten.Benoetigt.Sicher -> "Ziel schon sicher"
                                Noten.Benoetigt.Unmoeglich -> "reicht allein nicht"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(
    system: NotenSystem,
    entwurf: NotenEntwurf,
    vorschlaege: List<String>,
    onAbbrechen: () -> Unit,
    onSpeichern: (fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) -> Unit
) {
    val alt = entwurf.bestehend
    var fach by remember { mutableStateOf(alt?.fach ?: entwurf.fach) }
    var punkteText by remember { mutableStateOf(alt?.punkte?.toString() ?: "") }
    var art by remember { mutableStateOf(alt?.art ?: entwurf.art) }
    var datum by remember { mutableStateOf(alt?.datum ?: entwurf.datum) }
    var notiz by remember { mutableStateOf(alt?.notiz ?: "") }
    var datumsDialog by remember { mutableStateOf(false) }
    val punkte = punkteText.toIntOrNull()?.takeIf { it in system.min..system.max }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (alt == null) "Note eintragen" else "Note bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotenArt.entries.forEach { option ->
                        FilterChip(selected = art == option, onClick = { art = option }, label = { Text(option.anzeige) })
                    }
                }
                OutlinedTextField(
                    value = fach,
                    onValueChange = { fach = it },
                    label = { Text("Fach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (vorschlaege.isNotEmpty() && alt == null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vorschlaege.forEach { v ->
                            AssistChip(onClick = { fach = v }, label = { Text(v) })
                        }
                    }
                }
                OutlinedTextField(
                    value = punkteText,
                    onValueChange = { neu -> punkteText = neu.filter { it.isDigit() }.take(2) },
                    label = { Text(if (system == NotenSystem.PUNKTE) "Punkte (0 bis 15)" else "Note (1 bis 6)") },
                    isError = punkteText.isNotEmpty() && punkte == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    label = { Text("Notiz (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AssistChip(
                    onClick = { datumsDialog = true },
                    label = { Text("Datum: ${datum.format(notenDatum)}") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (punkte != null) onSpeichern(fach, punkte, art, datum, notiz) },
                enabled = fach.isNotBlank() && punkte != null
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )

    if (datumsDialog) {
        DatumsDialog(
            vorauswahl = datum,
            onAbbrechen = { datumsDialog = false },
            onGewaehlt = {
                datum = it
                datumsDialog = false
            }
        )
    }
}

/** Gewicht der Klausuren: Schieberegler von [Noten.ANTEIL_MIN] bis [Noten.ANTEIL_MAX] % und ein Zahlenfeld. */
@Composable
private fun AnteilRegler(anteil: Int, onAnteil: (Int) -> Unit) {
    var text by remember(anteil) { mutableStateOf(anteil.toString()) }
    Column {
        Text(
            text = "Klausuren zählen",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = anteil.toFloat(),
                onValueChange = { onAnteil(it.roundToInt().coerceIn(Noten.ANTEIL_MIN, Noten.ANTEIL_MAX)) },
                valueRange = Noten.ANTEIL_MIN.toFloat()..Noten.ANTEIL_MAX.toFloat(),
                steps = Noten.ANTEIL_MAX - Noten.ANTEIL_MIN - 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { neu ->
                    val ziffern = neu.filter { it.isDigit() }.take(3)
                    text = ziffern
                    // Nur gültige Werte übernehmen; bei zu kleinen/großen Zahlen bleibt der alte stehen.
                    ziffern.toIntOrNull()?.takeIf { it in Noten.ANTEIL_MIN..Noten.ANTEIL_MAX }?.let(onAnteil)
                },
                suffix = { Text("%") },
                isError = text.toIntOrNull()?.let { it !in Noten.ANTEIL_MIN..Noten.ANTEIL_MAX } ?: true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
        }
        Text(
            text = "Mündlich zählt ${100 - anteil} %. Erlaubt: ${Noten.ANTEIL_MIN} bis ${Noten.ANTEIL_MAX} %.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
