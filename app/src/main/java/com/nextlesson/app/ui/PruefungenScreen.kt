package com.nextlesson.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Pruefung
import com.nextlesson.app.data.PruefungsArt
import com.nextlesson.app.ui.theme.fachFarbe
import com.nextlesson.app.ui.theme.istDunkel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val langesDatum = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
private val kurzesDatum = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

@Composable
fun PruefungenScreen(
    pruefungen: List<Pruefung>,
    onLoeschen: (String) -> Unit,
    onHinzufuegen: (fach: String, titel: String, datum: LocalDate, art: PruefungsArt, notiz: String) -> Unit,
    onBearbeiten: (id: String, fach: String, titel: String, datum: LocalDate, art: PruefungsArt, notiz: String) -> Unit
) {
    var dialogOffen by remember { mutableStateOf(false) }
    var bearbeitungsPruefung by remember { mutableStateOf<Pruefung?>(null) }
    val dunkel = istDunkel()
    val heute = LocalDate.now()

    val kommend = pruefungen.filterNot { it.istVorbei(heute) }
    val vorbei = pruefungen.filter { it.istVorbei(heute) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialogOffen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Termin") }
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
                UeberschriftZeile(
                    titel = when {
                        kommend.isEmpty() -> "Nichts angesetzt"
                        kommend.size == 1 -> "1 Termin"
                        else -> "${kommend.size} Termine"
                    },
                    unterzeile = kommend.firstOrNull()?.let { naechster ->
                        val tage = naechster.tageBis(heute)
                        when {
                            tage <= 0L -> "Heute: ${naechster.fach}"
                            tage == 1L -> "Morgen: ${naechster.fach}"
                            tage <= 7L -> "In $tage Tagen: ${naechster.fach}"
                            else -> null
                        }
                    }
                )
            }

            if (pruefungen.isEmpty()) {
                item { LeerHinweis("Noch keine Klausuren. Klausuren deiner Kurse erscheinen hier automatisch, sobald sie im Plan stehen. Eigene Tests kannst du unten rechts hinzufügen.") }
            }

            items(kommend, key = { it.id }) { pruefung ->
                PruefungsKarte(pruefung, heute, dunkel, onLoeschen, onBearbeitenClick = { bearbeitungsPruefung = pruefung })
            }

            if (vorbei.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Vorbei",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Wird eine Woche nach dem Termin automatisch entfernt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(vorbei, key = { it.id }) { pruefung ->
                    PruefungsKarte(pruefung, heute, dunkel, onLoeschen, onBearbeitenClick = { bearbeitungsPruefung = pruefung })
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (dialogOffen) {
        PruefungDialog(
            onAbbrechen = { dialogOffen = false },
            onSpeichern = { fach, titel, datum, art, notiz ->
                onHinzufuegen(fach, titel, datum, art, notiz)
                dialogOffen = false
            }
        )
    }

    bearbeitungsPruefung?.let { pruefung ->
        PruefungDialog(
            bestehendePruefung = pruefung,
            onAbbrechen = { bearbeitungsPruefung = null },
            onSpeichern = { fach, titel, datum, art, notiz ->
                onBearbeiten(pruefung.id, fach, titel, datum, art, notiz)
                bearbeitungsPruefung = null
            }
        )
    }
}

@Composable
private fun PruefungsKarte(
    pruefung: Pruefung,
    heute: LocalDate,
    dunkel: Boolean,
    onLoeschen: (String) -> Unit,
    onBearbeitenClick: () -> Unit
) {
    val tage = pruefung.tageBis(heute)
    val vorbei = pruefung.istVorbei(heute)
    val dringend = !vorbei && tage <= 3
    val akzent = when {
        vorbei -> MaterialTheme.colorScheme.outline
        dringend -> MaterialTheme.colorScheme.error
        else -> fachFarbe(pruefung.fach, dunkel)
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (vorbei) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onBearbeitenClick
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(akzent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            text = pruefung.art.anzeige,
                            hintergrund = akzent,
                            vordergrund = MaterialTheme.colorScheme.surface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = pruefung.fach,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (pruefung.titel.isNotBlank()) {
                        Text(
                            text = pruefung.titel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Das Datum steht ausgeschrieben da – so sieht man sofort, ob es stimmt.
                    Text(
                        text = pruefung.datum.format(langesDatum),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pruefung.notiz.isNotBlank()) {
                        Text(
                            text = pruefung.notiz,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            vorbei -> "vorbei"
                            tage == 0L -> "heute"
                            tage == 1L -> "morgen"
                            else -> "in $tage T."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (dringend) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { onLoeschen(pruefung.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PruefungDialog(
    bestehendePruefung: Pruefung? = null,
    onAbbrechen: () -> Unit,
    onSpeichern: (fach: String, titel: String, datum: LocalDate, art: PruefungsArt, notiz: String) -> Unit
) {
    var fach by remember { mutableStateOf(bestehendePruefung?.fach ?: "") }
    var titel by remember { mutableStateOf(bestehendePruefung?.titel ?: "") }
    var notiz by remember { mutableStateOf(bestehendePruefung?.notiz ?: "") }
    var art by remember { mutableStateOf(bestehendePruefung?.art ?: PruefungsArt.KLAUSUR) }
    var datum by remember { mutableStateOf(bestehendePruefung?.datum ?: LocalDate.now().plusDays(7)) }
    var datumsDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (bestehendePruefung == null) "Test oder Klausur" else "Termin bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PruefungsArt.entries.forEach { option ->
                        FilterChip(
                            selected = art == option,
                            onClick = { art = option },
                            label = { Text(option.anzeige) }
                        )
                    }
                }
                OutlinedTextField(
                    value = fach,
                    onValueChange = { fach = it },
                    label = { Text("Fach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = titel,
                    onValueChange = { titel = it },
                    label = { Text("Thema (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    label = { Text("Notiz (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                AssistChip(
                    onClick = { datumsDialog = true },
                    label = { Text("Datum: ${datum.format(kurzesDatum)}") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeichern(fach, titel, datum, art, notiz) },
                enabled = fach.isNotBlank() || titel.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
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
