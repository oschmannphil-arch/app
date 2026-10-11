package com.nextlesson.app.ui

import com.nextlesson.app.data.NotenStore
import com.nextlesson.app.data.Abi
import androidx.compose.foundation.verticalScroll
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
import com.nextlesson.app.ui.theme.fachAnzeige
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
    ziele: Map<String, Int>,
    onZiel: (fach: String, wert: Int) -> Unit,
    onHinzufuegen: (fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) -> Unit,
    onBearbeiten: (id: String, fach: String, punkte: Int, art: NotenArt, datum: LocalDate, notiz: String) -> Unit,
    onLoeschen: (String) -> Unit
) {
    val dunkel = istDunkel()
    val heute = LocalDate.now()
    val jetzt by rememberJetzt()
    var entwurf by remember { mutableStateOf<NotenEntwurf?>(null) }
    var zeugnisOffen by remember { mutableStateOf(false) }
    var abiOffen by remember { mutableStateOf(false) }
    // "" = alle Halbjahre
    // Gibt es im aktuellen Halbjahr noch keine Noten, aber in einem früheren, starten wir dort.
    var halbjahr by rememberSaveable {
        val aktuell = Noten.halbjahr(heute)
        mutableStateOf(
            if (noten.isEmpty() || noten.any { Noten.halbjahr(it) == aktuell }) aktuell
            else Noten.halbjahr(noten.maxByOrNull { it.datumEpochDay }!!)
        )
    }

    val halbjahre = remember(noten, heute) {
        (noten.map { Noten.halbjahr(it) } + Noten.halbjahr(heute)).distinct().sortedDescending()
    }
    val sichtbar = remember(noten, halbjahr) {
        if (halbjahr.isEmpty()) noten else noten.filter { Noten.halbjahr(it) == halbjahr }
    }
    val faecher = remember(sichtbar, klausurAnteil) { Noten.nachFach(sichtbar, klausurAnteil) }
    val gesamt = remember(faecher) { Noten.gesamtSchnitt(faecher) }
    // Das Halbjahr davor, zum Vergleichen (nur wenn ein einzelnes Halbjahr gewählt ist).
    val vorher = remember(noten, halbjahr, klausurAnteil) {
        val davor = Noten.halbjahrDavor(halbjahr)
        if (davor == null) emptyMap()
        else Noten.nachFach(noten.filter { Noten.halbjahr(it) == davor }, klausurAnteil)
            .associateBy { Noten.fachSchluessel(it.fach) }
    }

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
            if (system == NotenSystem.NOTEN) {
                item {
                    LeerHinweis("Klasse 5–10: Klassenarbeiten zählen laut älterem Erlass etwa 25–40 % der Note (eine 45-Minuten-Arbeit ca. 20 %). Frag im Zweifel deine Lehrkraft.")
                }
            }

            if (faecher.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = { zeugnisOffen = true }, label = { Text("Zeugnis-Vorschau") })
                        if (system == NotenSystem.PUNKTE) {
                            AssistChip(onClick = { abiOffen = true }, label = { Text("Abi-Rechner") })
                        }
                    }
                }
            }

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
                    onLoeschen = onLoeschen,
                    vorher = vorher[Noten.fachSchluessel(f.fach)],
                    gespeichertesZiel = ziele[NotenStore.zielSchluessel(system, f.fach)],
                    onZiel = { onZiel(f.fach, it) }
                )
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (zeugnisOffen) {
        ZeugnisDialog(system, faecher, gesamt, if (halbjahr.isEmpty()) "Alle Halbjahre" else halbjahr) { zeugnisOffen = false }
    }
    if (abiOffen) {
        AbiDialog(
            schnitt = remember(noten, klausurAnteil) { Noten.durchschnittAllerHalbjahre(noten, klausurAnteil) },
            onSchliessen = { abiOffen = false }
        )
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
    onLoeschen: (String) -> Unit,
    vorher: FachSchnitt?,
    gespeichertesZiel: Int?,
    onZiel: (Int) -> Unit
) {
    val akzent = fachFarbe(schnitt.fach, dunkel)
    var prognoseOffen by remember { mutableStateOf(false) }
    // Ohne gespeichertes Ziel: einen Schritt besser als der aktuelle Zeugnisstand.
    val zielWert = if (gespeichertesZiel != null && gespeichertesZiel in system.min..system.max) gespeichertesZiel
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
                        Text(fachAnzeige(schnitt.fach), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

                if (vorher != null) {
                    // Besser/schlechter zum Vorhalbjahr – bei Noten ist weniger besser.
                    val diff = schnitt.gesamt - vorher.gesamt
                    val besser = if (system.hoeherIstBesser) diff > 0.05 else diff < -0.05
                    val schlechter = if (system.hoeherIstBesser) diff < -0.05 else diff > 0.05
                    Text(
                        text = "Vorhalbjahr Ø ${komma(vorher.gesamt)} · " + when {
                            besser -> "▲ ${komma(kotlin.math.abs(diff))} besser"
                            schlechter -> "▼ ${komma(kotlin.math.abs(diff))} schlechter"
                            else -> "gleich geblieben"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            besser -> MaterialTheme.colorScheme.primary
                            schlechter -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
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
                    TextButton(onClick = { onZiel(zielWert - 1) }, enabled = zielWert > system.min) { Text("−") }
                    Text(if (system == NotenSystem.PUNKTE) "$zielWert P" else "Note $zielWert", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onZiel(zielWert + 1) }, enabled = zielWert < system.max) { Text("+") }
                    if (Noten.zielErreicht(schnitt.zeugnis, zielWert, system)) {
                        Text("✓ erreicht", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
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
                TextButton(onClick = { prognoseOffen = true }) { Text("Was wäre wenn …") }
            }
        }
    }
    if (prognoseOffen) {
        PrognoseDialog(system, schnitt.fach, noten, klausurAnteil) { prognoseOffen = false }
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
                // Erst als Fehler zeigen, wenn die Eingabe nicht mehr zu einer gültigen Zahl werden kann ("3" → "35").
                isError = text.toIntOrNull().let { w ->
                    w == null || w > Noten.ANTEIL_MAX || (text.length >= 2 && w < Noten.ANTEIL_MIN)
                },
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

@Composable
private fun PrognoseDialog(
    system: NotenSystem,
    fach: String,
    noten: List<Note>,
    klausurAnteil: Int,
    onSchliessen: () -> Unit
) {
    var art by remember { mutableStateOf(NotenArt.KLAUSUR) }
    var wertText by remember { mutableStateOf("") }
    val wert = wertText.toIntOrNull()?.takeIf { it in system.min..system.max }
    val ergebnis = wert?.let { Noten.prognose(fach, noten, system, art, it, klausurAnteil) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Was wäre wenn … ($fach)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotenArt.entries.forEach { option ->
                        FilterChip(selected = art == option, onClick = { art = option }, label = { Text(option.anzeige) })
                    }
                }
                OutlinedTextField(
                    value = wertText,
                    onValueChange = { neu -> wertText = neu.filter { it.isDigit() }.take(2) },
                    label = { Text(if (system == NotenSystem.PUNKTE) "Angenommene Punkte (0 bis 15)" else "Angenommene Note (1 bis 6)") },
                    isError = wertText.isNotEmpty() && wert == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (ergebnis != null) {
                    Text(
                        text = "Dann steht das Fach bei Ø ${komma(ergebnis.gesamt)} – " +
                            if (system == NotenSystem.PUNKTE) "Zeugnis: ${ergebnis.zeugnis} Punkte" else "Zeugnis: Note ${ergebnis.zeugnis}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Schließen") } }
    )
}

@Composable
private fun ZeugnisDialog(
    system: NotenSystem,
    faecher: List<FachSchnitt>,
    gesamt: Double?,
    zeitraum: String,
    onSchliessen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Zeugnis-Vorschau") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(zeitraum, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                faecher.forEach { f ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fachAnzeige(f.fach), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (system == NotenSystem.PUNKTE) "${f.zeugnis} P" else "${f.zeugnis}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (gesamt != null) {
                    val zeugnisSchnitt = faecher.map { it.zeugnis }.average()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Durchschnitt der Zeugniswerte: ${komma(zeugnisSchnitt)}" +
                            if (system == NotenSystem.PUNKTE) " P (≈ Note ${komma(Noten.alsSchulnote(zeugnisSchnitt))})" else "",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    "Nur eine Vorschau aus deinen eingetragenen Noten – die Lehrkraft entscheidet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Schließen") } }
    )
}

@Composable
private fun AbiDialog(schnitt: Double?, onSchliessen: () -> Unit) {
    var pruefungen by remember {
        mutableStateOf(List(5) { schnitt?.let { s -> Noten.runden(s).toString() } ?: "10" })
    }
    val punkte = pruefungen.map { it.toIntOrNull()?.takeIf { p -> p in 0..Noten.MAX } }
    val blockI = schnitt?.let { Abi.blockI(it) }
    val blockII = if (punkte.all { it != null }) Abi.blockII(punkte.filterNotNull()) else null
    val gesamt = if (blockI != null && blockII != null) blockI + blockII else null

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Abi-Rechner") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (schnitt == null) "Trag zuerst Noten ein – daraus wird Block I geschätzt."
                    else "Block I aus deinem Durchschnitt aller Halbjahre (Ø ${komma(schnitt)} P). " +
                        "Trag unten ein, wie viele Punkte du in den fünf Prüfungen erwartest.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pruefungen.forEachIndexed { i, text ->
                        OutlinedTextField(
                            value = text,
                            onValueChange = { neu ->
                                pruefungen = pruefungen.toMutableList().also { it[i] = neu.filter { c -> c.isDigit() }.take(2) }
                            },
                            label = { Text("P${i + 1}") },
                            isError = punkte[i] == null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (blockI != null) Text("Block I: $blockI von ${Abi.BLOCK_I_MAX}", style = MaterialTheme.typography.bodyLarge)
                if (blockII != null) Text("Block II: $blockII von ${Abi.BLOCK_II_MAX}", style = MaterialTheme.typography.bodyLarge)
                if (gesamt != null && blockI != null && blockII != null) {
                    val note = Abi.note(gesamt)
                    val geschafft = Abi.bestanden(blockI, blockII)
                    Text(
                        text = "Gesamt: $gesamt von 900" + if (note != null && geschafft) " · Note ≈ ${komma(note)}" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (geschafft) "Bestehens-Grenzen erreicht." else
                            "Unter den Mindestwerten (Block I ${Abi.BLOCK_I_MIN}, Block II ${Abi.BLOCK_II_MIN}, gesamt ${Abi.GESAMT_MIN}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (geschafft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Block I = Durchschnitt × 40 (Summe der Punkte geteilt durch die Zahl der gewerteten Ergebnisse × 40). Laut Oberstufenverordnung zählen 36 bis 40 Kurshalbjahre, einige davon doppelt – diese Auswahl macht deine Schule. Das hier ist darum nur eine Schätzung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Schließen") } }
    )
}
