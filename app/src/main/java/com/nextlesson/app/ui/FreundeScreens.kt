package com.nextlesson.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Freiblock
import com.nextlesson.app.data.Freizeit
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.KursInfo
import com.nextlesson.app.data.PersoenlicherPlan
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.Zeitfenster
import com.nextlesson.app.data.kennung
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val uhrzeit = DateTimeFormatter.ofPattern("HH:mm")
private val wochentag = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)

private fun zeitraum(von: LocalTime, bis: LocalTime) = "${von.format(uhrzeit)}–${bis.format(uhrzeit)}"

private fun tagLabel(persoenlich: PersoenlicherPlan): String =
    if (persoenlich.istHeute) "heute" else persoenlich.datum.format(wochentag)

/** "3.–4. Std (08:45–11:00)" bzw. nur die Uhrzeit, wenn der Plan keine Stundennummern hergibt. */
private fun blockText(b: Freiblock): String =
    b.stundenText?.let { "$it (${zeitraum(b.beginn, b.ende)})" } ?: zeitraum(b.beginn, b.ende)

/** Kurzfassung für die Karte auf der Startseite: Unterrichtszeit und gemeinsame Freistunden. */
private fun zusammenfassung(eigen: TagesPlan, freund: TagesPlan, raster: List<Zeitfenster>): String {
    val belegt = Freizeit.belegt(freund)
    if (belegt.isEmpty()) return "hat an diesem Tag keinen Unterricht"
    val frei = Freizeit.gemeinsamFrei(eigen, freund, raster)
    val unterricht = zeitraum(belegt.first().first, belegt.last().second)
    val gemeinsam = if (frei.isEmpty()) "keine gemeinsame Freistunde"
    else "gemeinsam frei: " + frei.joinToString(", ") { blockText(it) }
    return "$unterricht · $gemeinsam"
}

/** Karte auf der Startseite: je Freund die Unterrichtszeit und gemeinsame Freistunden. */
@Composable
fun FreundeKarte(freunde: List<Freund>, persoenlich: PersoenlicherPlan, onFreund: (Freund) -> Unit) {
    val tage = remember(freunde, persoenlich) {
        freunde.map { f -> f to persoenlich.gesamt.tagesplanFuer(f.kurse) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Freunde · ${tagLabel(persoenlich)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            tage.forEach { (freund, plan) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onFreund(freund) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(text = freund.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = zusammenfassung(persoenlich.plan, plan, persoenlich.gesamt.zeitraster),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Der Tag eines Freundes: gemeinsame Freistunden, gemeinsame Stunden und sein Plan. */
@Composable
fun FreundTagScreen(freund: Freund, persoenlich: PersoenlicherPlan, onBearbeiten: () -> Unit) {
    val jetzt by rememberJetzt()
    val dunkel = isSystemInDarkTheme()
    val plan = remember(freund, persoenlich) { persoenlich.gesamt.tagesplanFuer(freund.kurse) }
    val frei = remember(plan, persoenlich) {
        Freizeit.gemeinsamFrei(persoenlich.plan, plan, persoenlich.gesamt.zeitraster)
    }
    // Freistunden des Freundes in seiner Stundenliste, wie im eigenen Plan.
    val freiVor = remember(plan, persoenlich) {
        Freizeit.freiBloecke(plan, persoenlich.gesamt.zeitraster).mapNotNull { block ->
            val danach = plan.stunden.indexOfFirst { l -> l.beginn?.let { !it.isBefore(block.ende) } == true }
            if (danach >= 0) danach to block else null
        }.toMap()
    }
    val zusammen = remember(plan, persoenlich) {
        Freizeit.gemeinsameStunden(plan, persoenlich.plan).mapTo(HashSet()) { it.kennung() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            UebersichtKarte(
                titel = "Gemeinsam frei · ${tagLabel(persoenlich)}",
                text = if (frei.isEmpty()) "Keine gemeinsame Freistunde."
                else frei.joinToString("\n") { blockText(it) },
                hervorgehoben = false,
                onClick = null
            )
        }
        if (plan.stunden.isEmpty()) {
            item {
                Text(
                    text = "${freund.name} hat an diesem Tag keinen Unterricht in den gewählten Kursen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(plan.stunden) { index, lesson ->
            Column {
                freiVor[index]?.let { FreistundenZeile(it) }
                if (lesson.kennung() in zusammen) {
                    Text(
                        text = "zusammen mit dir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                StundenZeile(
                    lesson = lesson,
                    istNaechste = false,
                    laeuftGerade = persoenlich.istHeute && laeuft(lesson, jetzt),
                    dunkel = dunkel,
                    onClick = null
                )
            }
        }
        item {
            OutlinedButton(onClick = onBearbeiten, modifier = Modifier.fillMaxWidth()) {
                Text("Kurse von ${freund.name} bearbeiten")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Freund anlegen oder bearbeiten: Name und Kurse. */
@Composable
fun FreundBearbeitenScreen(
    freund: Freund,
    istNeu: Boolean,
    verfuegbareKurse: List<KursInfo>,
    onSpeichern: (Freund) -> Unit,
    onLoeschen: () -> Unit,
    onAbbrechen: () -> Unit
) {
    // Saveable: Beim Drehen des Handys (oder Wechsel zu WhatsApp, um nach den Kursen zu
    // fragen) gehen Name und angekreuzte Kurse nicht verloren.
    var name by rememberSaveable(freund.id) { mutableStateOf(freund.name) }
    var auswahl by rememberSaveable(freund.id, stateSaver = KursAuswahlSaver) { mutableStateOf(freund.kurse) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Kreuze die Kurse an. Die App zeigt dann den Plan und wann ihr gemeinsam frei habt.",
            style = MaterialTheme.typography.bodySmall
        )
        KursListe(
            verfuegbareKurse = verfuegbareKurse,
            auswahl = auswahl,
            onAuswahl = { auswahl = it },
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { onSpeichern(freund.copy(name = name.trim(), kurse = auswahl)) },
            enabled = name.isNotBlank() && auswahl.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    name.isBlank() -> "Namen eingeben"
                    auswahl.isEmpty() -> "Mindestens einen Kurs wählen"
                    else -> "Speichern"
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAbbrechen, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
            if (!istNeu) {
                OutlinedButton(
                    onClick = onLoeschen,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text("Löschen") }
            }
        }
    }
}

/** Abschnitt in den Einstellungen: Freunde verwalten. */
@Composable
fun FreundeEinstellungenKarte(freunde: List<Freund>, onBearbeiten: (Freund?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Freunde", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Trag die Kurse von Freunden ein – dann zeigt die Startseite, wann ihr gemeinsam frei habt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            freunde.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBearbeiten(f) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = if (f.kurse.size == 1) "1 Kurs" else "${f.kurse.size} Kurse",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = { onBearbeiten(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Freund hinzufügen")
            }
        }
    }
}
