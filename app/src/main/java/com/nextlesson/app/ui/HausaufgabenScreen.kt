package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Hausaufgabe
import com.nextlesson.app.ui.theme.fachFarbe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val datumFormat = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

@Composable
fun HausaufgabenScreen(
    aufgaben: List<Hausaufgabe>,
    onUmschalten: (String) -> Unit,
    onLoeschen: (String) -> Unit,
    onHinzufuegen: (fach: String, text: String, faellig: LocalDate?) -> Unit,
    onBearbeiten: (id: String, fach: String, text: String, faellig: LocalDate?) -> Unit
) {
    var dialogOffen by remember { mutableStateOf(false) }
    var bearbeitungsAufgabe by remember { mutableStateOf<Hausaufgabe?>(null) }
    val dunkel = isSystemInDarkTheme()
    val heute = LocalDate.now()

    val offen = aufgaben.filterNot { it.erledigt }
    val erledigt = aufgaben.filter { it.erledigt }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialogOffen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Aufgabe") }
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
                    titel = if (offen.isEmpty()) "Nichts offen" else "${offen.size} offen",
                    unterzeile = offen.count { it.istUeberfaellig(heute) }.let {
                        if (it > 0) "$it überfällig" else null
                    }
                )
            }

            if (aufgaben.isEmpty()) {
                item { LeerHinweis("Noch keine Hausaufgaben eingetragen. Unten rechts hinzufügen.") }
            }

            items(offen, key = { it.id }) { aufgabe ->
                AufgabenZeile(
                    aufgabe = aufgabe,
                    heute = heute,
                    dunkel = dunkel,
                    onUmschalten = onUmschalten,
                    onLoeschen = onLoeschen,
                    onBearbeitenClick = { bearbeitungsAufgabe = aufgabe }
                )
            }

            if (erledigt.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Erledigt",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Verschwindet beim nächsten Start der App.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(erledigt, key = { it.id }) { aufgabe ->
                    AufgabenZeile(
                        aufgabe = aufgabe,
                        heute = heute,
                        dunkel = dunkel,
                        onUmschalten = onUmschalten,
                        onLoeschen = onLoeschen,
                        onBearbeitenClick = { bearbeitungsAufgabe = aufgabe }
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (dialogOffen) {
        HausaufgabeDialog(
            onAbbrechen = { dialogOffen = false },
            onSpeichern = { fach, text, faellig ->
                onHinzufuegen(fach, text, faellig)
                dialogOffen = false
            }
        )
    }

    bearbeitungsAufgabe?.let { aufgabe ->
        HausaufgabeDialog(
            bestehendeAufgabe = aufgabe,
            onAbbrechen = { bearbeitungsAufgabe = null },
            onSpeichern = { fach, text, faellig ->
                onBearbeiten(aufgabe.id, fach, text, faellig)
                bearbeitungsAufgabe = null
            }
        )
    }
}

@Composable
private fun AufgabenZeile(
    aufgabe: Hausaufgabe,
    heute: LocalDate,
    dunkel: Boolean,
    onUmschalten: (String) -> Unit,
    onLoeschen: (String) -> Unit,
    onBearbeitenClick: () -> Unit
) {
    val ueberfaellig = aufgabe.istUeberfaellig(heute)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBearbeitenClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (aufgabe.erledigt) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = aufgabe.erledigt,
                onCheckedChange = { onUmschalten(aufgabe.id) }
            )

            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (aufgabe.fach.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(fachFarbe(aufgabe.fach, dunkel))
                        )
                        Text(
                            text = aufgabe.fach,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = aufgabe.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (aufgabe.erledigt) TextDecoration.LineThrough else null,
                    color = if (aufgabe.erledigt) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                aufgabe.faellig?.let { datum ->
                    Text(
                        text = faelligkeitText(datum, heute),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (ueberfaellig) FontWeight.Bold else FontWeight.Normal,
                        color = if (ueberfaellig) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { onLoeschen(aufgabe.id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun faelligkeitText(datum: LocalDate, heute: LocalDate): String = when {
    datum == heute -> "heute fällig"
    datum == heute.plusDays(1) -> "morgen fällig"
    datum.isBefore(heute) -> "war am ${datum.format(datumFormat)} fällig"
    else -> "bis ${datum.format(datumFormat)}"
}

@Composable
private fun HausaufgabeDialog(
    bestehendeAufgabe: Hausaufgabe? = null,
    onAbbrechen: () -> Unit,
    onSpeichern: (fach: String, text: String, faellig: LocalDate?) -> Unit
) {
    var fach by remember { mutableStateOf(bestehendeAufgabe?.fach ?: "") }
    var text by remember { mutableStateOf(bestehendeAufgabe?.text ?: "") }
    var faellig by remember { mutableStateOf<LocalDate?>(bestehendeAufgabe?.faellig ?: LocalDate.now().plusDays(1)) }
    var datumsDialog by remember { mutableStateOf(false) }
    val heute = LocalDate.now()

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (bestehendeAufgabe == null) "Neue Hausaufgabe" else "Hausaufgabe bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fach,
                    onValueChange = { fach = it },
                    label = { Text("Fach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Was ist zu tun?") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Fällig", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { faellig = heute },
                        label = { Text("Heute") }
                    )
                    AssistChip(
                        onClick = { faellig = heute.plusDays(1) },
                        label = { Text("Morgen") }
                    )
                    AssistChip(
                        onClick = { datumsDialog = true },
                        label = { Text("Datum") }
                    )
                }
                Text(
                    text = faellig?.let { "Gewählt: ${it.format(datumFormat)}" } ?: "Ohne Datum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeichern(fach, text, faellig) },
                enabled = text.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
    )

    if (datumsDialog) {
        DatumsDialog(
            vorauswahl = faellig,
            onAbbrechen = { datumsDialog = false },
            onGewaehlt = {
                faellig = it
                datumsDialog = false
            }
        )
    }
}

@Composable
fun UeberschriftZeile(titel: String, unterzeile: String?) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = titel, style = MaterialTheme.typography.headlineSmall)
        if (unterzeile != null) {
            Text(
                text = unterzeile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun LeerHinweis(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
