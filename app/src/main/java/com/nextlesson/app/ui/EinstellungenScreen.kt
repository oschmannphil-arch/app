package com.nextlesson.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Erinnerung
import com.nextlesson.app.data.IndiwareCredentials

/**
 * Zugangsdaten und die Einstellung für die tägliche Lern-Erinnerung.
 */
@Composable
fun EinstellungenScreen(
    credentials: IndiwareCredentials?,
    erinnerung: Erinnerung,
    grosserText: Boolean,
    onZugangSpeichern: (IndiwareCredentials) -> Unit,
    onErinnerungSetzen: (aktiv: Boolean, stunde: Int, minute: Int) -> Unit,
    onGrosserTextSetzen: (Boolean) -> Unit,
    onKurseAendern: () -> Unit
) {
    var schulnummer by remember { mutableStateOf(credentials?.schulnummer.orEmpty()) }
    var benutzer by remember { mutableStateOf(credentials?.benutzername.orEmpty()) }
    var passwort by remember { mutableStateOf(credentials?.passwort.orEmpty()) }
    var zeitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // --- Erinnerung ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tägliche Erinnerung", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Erinnert dich an Hausaufgaben und anstehende Klausuren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = erinnerung.aktiv,
                        onCheckedChange = {
                            onErinnerungSetzen(it, erinnerung.stunde, erinnerung.minute)
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Uhrzeit", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = erinnerung.anzeige,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    OutlinedButton(onClick = { zeitDialog = true }) {
                        Text("Ändern")
                    }
                }
            }
        }

        // --- Kurse ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deine Kurse", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Legt fest, welche Stunden angezeigt werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onKurseAendern) { Text("Ändern") }
            }
        }

        // --- Widget ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Großer Widget-Text", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Vergrößert die Schriftart im Homescreen-Widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = grosserText,
                    onCheckedChange = onGrosserTextSetzen
                )
            }
        }

        // --- Zugangsdaten ---
        Text(
            text = "Zugang zu Indiware mobil",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
        Text(
            text = "Wird verschlüsselt auf diesem Gerät gespeichert und nur an stundenplan24.de gesendet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        OutlinedTextField(
            value = schulnummer,
            onValueChange = { schulnummer = it },
            label = { Text("Schulnummer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = benutzer,
            onValueChange = { benutzer = it },
            label = { Text("Benutzername") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = passwort,
            onValueChange = { passwort = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onZugangSpeichern(
                    IndiwareCredentials(schulnummer.trim(), benutzer.trim(), passwort)
                )
            },
            enabled = schulnummer.isNotBlank() && benutzer.isNotBlank() && passwort.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zugang speichern")
        }

        Spacer(Modifier.height(24.dp))
    }

    if (zeitDialog) {
        ZeitDialog(
            stunde = erinnerung.stunde,
            minute = erinnerung.minute,
            onAbbrechen = { zeitDialog = false },
            onGewaehlt = { std, min ->
                // Uhrzeit ändern schaltet die Erinnerung gleich mit ein.
                onErinnerungSetzen(true, std, min)
                zeitDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeitDialog(
    stunde: Int,
    minute: Int,
    onAbbrechen: () -> Unit,
    onGewaehlt: (Int, Int) -> Unit
) {
    val zustand = rememberTimePickerState(
        initialHour = stunde,
        initialMinute = minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Wann erinnern?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = zustand)
            }
        },
        confirmButton = {
            TextButton(onClick = { onGewaehlt(zustand.hour, zustand.minute) }) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
    )
}
