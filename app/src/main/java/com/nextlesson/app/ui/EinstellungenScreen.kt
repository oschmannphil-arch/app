package com.nextlesson.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import com.nextlesson.app.data.DesignModus
import com.nextlesson.app.ui.theme.FachFarbAuswahl
import com.nextlesson.app.ui.theme.fachFarbe
import com.nextlesson.app.ui.theme.istDunkel
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
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nextlesson.app.data.AenderungsArt
import com.nextlesson.app.data.BenachrichtigungsEinstellungen
import com.nextlesson.app.work.EntfallNotifier
import com.nextlesson.app.work.HintergrundStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Erinnerung
import com.nextlesson.app.data.Freund
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
    onKurseAendern: () -> Unit,
    freunde: List<Freund> = emptyList(),
    onFreundBearbeiten: (Freund?) -> Unit = {},
    onLinkImportieren: (Freund) -> Unit = {},
    onKurseTeilen: (() -> Unit)? = null,
    modus: DesignModus = DesignModus.SYSTEM,
    onModus: (DesignModus) -> Unit = {},
    fachNamen: List<String> = emptyList(),
    fachFarben: Map<String, Int> = emptyMap(),
    onFachFarbe: (String, Int?) -> Unit = { _, _ -> },
    update: UpdateZustand = UpdateZustand.Unbekannt,
    installierterBuild: Int = 0,
    onUpdatePruefen: () -> Unit = {},
    onUpdateLaden: () -> Unit = {},
    onUpdateInstallieren: () -> Unit = {}
) {
    var farbDialog by rememberSaveable { mutableStateOf(false) }
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

        BenachrichtigungenKarte()

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                if (onKurseTeilen != null) {
                    OutlinedButton(onClick = onKurseTeilen, modifier = Modifier.fillMaxWidth()) {
                        Text("Meine Kurse als Link teilen")
                    }
                }
            }
        }

        // --- Darstellung ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Darstellung", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesignModus.entries.forEach { m ->
                        FilterChip(selected = modus == m, onClick = { onModus(m) }, label = { Text(m.anzeige) })
                    }
                }
                OutlinedButton(onClick = { farbDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Farbe pro Fach anpassen")
                }
            }
        }

        // --- Freunde ---
        // Braucht die Kursliste der Schule – also erst, wenn ein Zugang gespeichert ist.
        if (credentials != null) {
            FreundeEinstellungenKarte(
                freunde = freunde,
                onBearbeiten = onFreundBearbeiten,
                onLinkImportieren = onLinkImportieren
            )
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

        UpdateKarte(update, installierterBuild, onUpdatePruefen, onUpdateLaden, onUpdateInstallieren)
        VersionsZeile()
        Spacer(Modifier.height(24.dp))
    }

    if (farbDialog) {
        FachFarbenDialog(
            faecher = fachNamen,
            farben = fachFarben,
            onFarbe = onFachFarbe,
            onFertig = { farbDialog = false }
        )
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

/**
 * Zeigt, ob Meldungen überhaupt ankommen können, welche Änderungen gemeldet werden – und bietet einen Test.
 * Der Stand wird bei jeder Rückkehr in die App neu geprüft (z.B. nach dem Ändern in den
 * Android-Einstellungen).
 */
@Composable
private fun BenachrichtigungenKarte() {
    val context = LocalContext.current
    var pruefung by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { pruefung++ }
    val einstellungen = remember { BenachrichtigungsEinstellungen(context) }
    var aktiveArten by remember { mutableStateOf(einstellungen.aktiveArten()) }

    val erlaubt = remember(pruefung) {
        EntfallNotifier.darfBenachrichtigen(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val akkuFrei = remember(pruefung) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val zuletzt = remember(pruefung) { HintergrundStatus.zuletztGeprueft(context) }
    var testErgebnis by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Benachrichtigungen", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Gemeldet werden neue Änderungen in deinen Kursen – je Tag eine Meldung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AenderungsArt.entries.forEach { art ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = art.anzeige,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = art in aktiveArten,
                        onCheckedChange = { an ->
                            einstellungen.setzen(art, an)
                            aktiveArten = einstellungen.aktiveArten()
                        }
                    )
                }
            }

            StatusZeile(
                ok = erlaubt,
                text = if (erlaubt) "Benachrichtigungen erlaubt" else "Benachrichtigungen sind aus",
                knopf = if (erlaubt) null else "Einschalten",
                onKnopf = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    runCatching { context.startActivity(intent) }
                }
            )
            StatusZeile(
                ok = akkuFrei,
                text = if (akkuFrei) "Keine Akku-Einschränkung" else
                    "Akku-Optimierung aktiv – Android kann Prüfungen im Hintergrund verzögern",
                knopf = if (akkuFrei) null else "Ändern",
                onKnopf = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            )
            Text(
                text = if (zuletzt <= 0L) "Noch keine Prüfung im Hintergrund"
                else "Zuletzt im Hintergrund geprüft: ${zeitpunkt(zuletzt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    testErgebnis = if (EntfallNotifier.testen(context)) {
                        "Test gesendet – schau in die Benachrichtigungsleiste."
                    } else {
                        "Konnte nicht gesendet werden: Benachrichtigungen sind nicht erlaubt."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test-Benachrichtigung senden") }
            testErgebnis?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusZeile(ok: Boolean, text: String, knopf: String?, onKnopf: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (ok) "✓" else "!",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (knopf != null) {
            TextButton(onClick = onKnopf) { Text(knopf) }
        }
    }
}

private fun zeitpunkt(millis: Long): String {
    val zeit = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val heute = LocalDate.now()
    val tag = when (zeit.toLocalDate()) {
        heute -> "heute"
        heute.minusDays(1) -> "gestern"
        else -> "%02d.%02d.".format(zeit.dayOfMonth, zeit.monthValue)
    }
    return "$tag, %02d:%02d".format(zeit.hour, zeit.minute)
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

/** Pro Fach eine eigene Farbe wählen (oder zurück zur automatischen). */
@Composable
private fun FachFarbenDialog(
    faecher: List<String>,
    farben: Map<String, Int>,
    onFarbe: (String, Int?) -> Unit,
    onFertig: () -> Unit
) {
    val dunkel = istDunkel()
    AlertDialog(
        onDismissRequest = onFertig,
        title = { Text("Farbe pro Fach") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (faecher.isEmpty()) {
                    Text(
                        text = "Sobald dein Plan geladen ist, erscheinen hier deine Fächer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                faecher.forEach { fach ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(fachFarbe(fach, dunkel))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fach, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (farben.containsKey(fach.trim().lowercase())) {
                                TextButton(onClick = { onFarbe(fach, null) }) { Text("Automatisch") }
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FachFarbAuswahl.forEach { farbe ->
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(farbe)
                                        .clickable { onFarbe(fach, farbe.toArgb()) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFertig) { Text("Fertig") } }
    )
}

/** Zeigt, welcher Build installiert ist – so sieht man, ob ein Update angekommen ist. */
@Composable
private fun VersionsZeile() {
    val context = LocalContext.current
    val info = remember {
        runCatching {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            val build = if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
            "Build $build · Version ${p.versionName}"
        }.getOrDefault("Build unbekannt")
    }
    Text(
        text = info,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** App-Update: neuen Build suchen, herunterladen und installieren. */
@Composable
private fun UpdateKarte(
    update: UpdateZustand,
    installiert: Int,
    onPruefen: () -> Unit,
    onLaden: () -> Unit,
    onInstallieren: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App-Update", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (update) {
                    is UpdateZustand.Unbekannt -> "Du hast Build $installiert."
                    is UpdateZustand.Sucht -> "Suche nach Updates …"
                    is UpdateZustand.Aktuell -> "Build $installiert ist der neueste."
                    is UpdateZustand.Verfuegbar -> "Build ${update.build} ist verfügbar (du hast $installiert)."
                    is UpdateZustand.Laedt -> "Lade Build ${update.build} … ${update.prozent} %"
                    is UpdateZustand.Bereit -> "Build ${update.build} ist heruntergeladen."
                    is UpdateZustand.Fehler -> update.nachricht
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (update is UpdateZustand.Fehler) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (update) {
                is UpdateZustand.Verfuegbar -> Button(onClick = onLaden, modifier = Modifier.fillMaxWidth()) {
                    Text("Herunterladen")
                }
                is UpdateZustand.Bereit -> Button(onClick = onInstallieren, modifier = Modifier.fillMaxWidth()) {
                    Text("Installieren")
                }
                is UpdateZustand.Sucht, is UpdateZustand.Laedt -> Unit
                else -> OutlinedButton(onClick = onPruefen, modifier = Modifier.fillMaxWidth()) {
                    Text("Nach Update suchen")
                }
            }
        }
    }
}
