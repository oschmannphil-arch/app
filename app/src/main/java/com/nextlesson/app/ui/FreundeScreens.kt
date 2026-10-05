package com.nextlesson.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Freiblock
import com.nextlesson.app.data.alsEintraege
import com.nextlesson.app.data.wochenReferenz
import com.nextlesson.app.data.FreundTeilen
import com.nextlesson.app.data.Freizeit
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.KursInfo
import com.nextlesson.app.data.PersoenlicherPlan
import com.nextlesson.app.data.TagesPlan
import com.nextlesson.app.data.Zeitfenster
import com.nextlesson.app.ui.theme.istDunkel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val uhrzeit = DateTimeFormatter.ofPattern("HH:mm")
private val wochentag = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)

private fun zeitraum(von: LocalTime, bis: LocalTime) = "${von.format(uhrzeit)}–${bis.format(uhrzeit)}"

private fun tagLabel(persoenlich: PersoenlicherPlan): String =
    if (persoenlich.istHeute) "heute" else persoenlich.datum.format(wochentag)

/** "3.–4. Std (08:45–11:00)" bzw. nur die Uhrzeit, wenn der Plan keine Stundennummern hergibt. */
internal fun blockText(b: Freiblock): String =
    b.stundenText?.let { "$it (${zeitraum(b.beginn, b.ende)})" } ?: zeitraum(b.beginn, b.ende)

/** Was an einem Tag über einen Freund zu sagen ist: wann er in der Schule ist, wann ihr beide frei seid. */
private class FreundHeute(val unterricht: String?, val frei: List<Freiblock>)

private fun freundHeute(eigen: TagesPlan, freund: TagesPlan, raster: List<Zeitfenster>): FreundHeute {
    val belegt = Freizeit.belegt(freund)
    if (belegt.isEmpty()) return FreundHeute(null, emptyList())
    return FreundHeute(
        unterricht = zeitraum(belegt.first().first, belegt.last().second),
        frei = Freizeit.gemeinsamFrei(eigen, freund, raster)
    )
}

/** Karte auf der Startseite: je Freund die Unterrichtszeit und gemeinsame Freistunden. */
@Composable
fun FreundeKarte(
    freunde: List<Freund>,
    persoenlich: PersoenlicherPlan,
    onFreund: (Freund) -> Unit,
    onWoche: () -> Unit = {}
) {
    val jetzt by rememberJetzt()
    val tage = remember(freunde, persoenlich) {
        freunde.map { f -> f to persoenlich.gesamt.tagesplanFuer(f.kurse) }
    }
    val heute = remember(tage, persoenlich) {
        tage.map { (_, plan) -> freundHeute(persoenlich.plan, plan, persoenlich.gesamt.zeitraster) }
    }
    // "Wer ist gerade frei?" – nur für den heutigen Tag sinnvoll.
    val gerade = remember(tage, persoenlich, jetzt) {
        if (!persoenlich.istHeute) emptyList()
        else tage.mapNotNull { (f, p) ->
            Freizeit.jetztFrei(p, persoenlich.gesamt.zeitraster, jetzt)?.let { f to it }
        }
    }
    // Vor Schulbeginn und nach Schulschluss aller Freunde wäre "niemand frei" irreführend.
    val jemandDa = remember(tage, persoenlich, jetzt) {
        persoenlich.istHeute && tage.any { (_, p) -> Freizeit.inDerSchule(p, jetzt) }
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
            if (gerade.isNotEmpty() || jemandDa) {
                Text(
                    text = if (gerade.isEmpty()) "Gerade hat niemand eine Freistunde."
                    else "Gerade frei: " + gerade.joinToString(", ") { (f, b) -> "${f.name} (bis ${b.ende.format(uhrzeit)})" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (gerade.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            tage.forEachIndexed { index, (freund, _) ->
                val h = heute[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onFreund(freund) }
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = freund.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (h.unterricht != null) {
                            Text(
                                text = "in der Schule ${h.unterricht}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    when {
                        h.unterricht == null -> Text(
                            text = "Kein Unterricht an diesem Tag",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        h.frei.isEmpty() -> Text(
                            text = "Keine gemeinsame Freistunde",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            Text(
                                text = "Gemeinsam frei",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            h.frei.forEach { b ->
                                Text(
                                    text = blockText(b),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onWoche) { Text("Wann haben wir diese Woche frei?") }
        }
    }
}

/** Der Tag eines Freundes: gemeinsame Freistunden, gemeinsame Stunden und sein Plan. */
@Composable
fun FreundTagScreen(
    freund: Freund,
    persoenlich: PersoenlicherPlan,
    onWoche: () -> Unit,
    onBearbeiten: () -> Unit,
    blockAnsicht: Boolean = false,
    onBlockAnsicht: (Boolean) -> Unit = {}
) {
    val jetzt by rememberJetzt()
    val dunkel = istDunkel()
    val plan = remember(freund, persoenlich) { persoenlich.gesamt.tagesplanFuer(freund.kurse) }
    val frei = remember(plan, persoenlich) {
        Freizeit.gemeinsamFrei(persoenlich.plan, plan, persoenlich.gesamt.zeitraster)
    }
    // Freistunden des Freundes in seiner Stundenliste, wie im eigenen Plan.
    val freiVor = remember(plan, persoenlich) { Freizeit.freiVor(plan, persoenlich.gesamt.zeitraster) }
    val eintraege = remember(plan, blockAnsicht) { plan.stunden.alsEintraege(blockAnsicht) }
    val zusammen = remember(plan, persoenlich) {
        Freizeit.gemeinsameStunden(plan, persoenlich.plan).mapTo(HashSet()) { Freizeit.zusammenKey(it) }
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
        if (plan.stunden.isNotEmpty()) {
            item { AnsichtUmschalter(blockAnsicht, onBlockAnsicht) }
        }
        itemsIndexed(eintraege) { _, block ->
            val lesson = block.zusammengefasst
            Column {
                freiVor[block.ersteIndex]?.let { FreistundenZeile(it) }
                if (block.stunden.any { Freizeit.zusammenKey(it) in zusammen }) ZusammenHinweis()
                StundenZeile(
                    lesson = lesson,
                    istNaechste = false,
                    laeuftGerade = persoenlich.istHeute && laeuft(lesson, jetzt),
                    dunkel = dunkel,
                    onClick = null,
                    stundenText = if (blockAnsicht) block.stundenText else null
                )
            }
        }
        item {
            Button(onClick = onWoche, modifier = Modifier.fillMaxWidth()) {
                Text("Wann haben wir diese Woche frei?")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBearbeiten, modifier = Modifier.fillMaxWidth()) {
                Text("Kurse von ${freund.name} bearbeiten")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ZusammenHinweis() {
    Text(
        text = "zusammen mit dir",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
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
        if (!istNeu) {
            val context = LocalContext.current
            OutlinedButton(
                onClick = { teilen(context, FreundTeilen.nachricht(name, auswahl, eigene = false), "Kurse teilen") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kurse als Link teilen") }
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
fun FreundeEinstellungenKarte(
    freunde: List<Freund>,
    onBearbeiten: (Freund?) -> Unit,
    onLinkImportieren: (Freund) -> Unit = {}
) {
    var linkDialog by rememberSaveable { mutableStateOf(false) }
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
            OutlinedButton(onClick = { linkDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Link von einem Freund einfügen")
            }
        }
    }

    if (linkDialog) {
        LinkEinfuegenDialog(
            onAbbrechen = { linkDialog = false },
            onImportieren = {
                linkDialog = false
                onLinkImportieren(it)
            }
        )
    }
}

@Composable
private fun LinkEinfuegenDialog(onAbbrechen: () -> Unit, onImportieren: (Freund) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val freund = remember(text) { FreundTeilen.lesen(text) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Link einfügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Füge den Link oder die ganze Nachricht ein, die dein Freund dir geschickt hat.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("nextlesson://freund?…") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank() && freund == null) {
                    Text(
                        text = "Darin steckt kein gültiger Link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { freund?.let(onImportieren) }, enabled = freund != null) { Text("Weiter") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )
}

private class TagErgebnis(val frei: List<Freiblock>, val ohneUnterricht: List<String>)

/** Gemeinsame Freistunden von dir und den gewählten Freunden an einem Tag (Pläne schon gefiltert). */
private fun gemeinsamAm(tag: WocheTag, freunde: List<Freund>): TagErgebnis? {
    val ich = tag.eigen ?: return null
    val plaene = freunde.mapNotNull { f -> tag.freunde[f.id]?.let { f to it } }
    val ohne = buildList {
        if (ich.stunden.none { !it.entfaellt }) add("Du")
        plaene.filter { (_, p) -> p.stunden.none { !it.entfaellt } }.forEach { add(it.first.name) }
    }
    val frei = Freizeit.gemeinsamFreiAlle(listOf(ich) + plaene.map { it.second }, tag.raster)
    return TagErgebnis(frei, ohne)
}

/** "Du hast" / "Anna hat" / "Du und Anna haben" … keinen Unterricht. */
private fun ohneUnterrichtText(namen: List<String>): String {
    val verb = when {
        namen == listOf("Du") -> "hast"
        namen.size == 1 -> "hat"
        else -> "haben"
    }
    return "${namen.joinToString(" und ")} $verb an diesem Tag keinen Unterricht"
}

/**
 * Wann haben du und (mehrere) Freunde in der ganzen Woche gleichzeitig frei? Freunde lassen
 * sich einzeln an- und abwählen; gezeigt werden nur Zeiten, in denen ALLE Gewählten frei sind.
 */
@Composable
fun GemeinsamFreiScreen(
    freunde: List<Freund>,
    startAuswahl: Set<String>,
    woche: WochenDaten<WocheTag>,
    onWocheLaden: (naechste: Boolean) -> Unit
) {
    var auswahl by rememberSaveable(stateSaver = KursAuswahlSaver) { mutableStateOf(startAuswahl) }
    var naechste by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(naechste) { onWocheLaden(naechste) }

    val gewaehlte = remember(freunde, auswahl) { freunde.filter { it.id in auswahl } }
    // Nur die gewählte Woche zeigen – nicht kurz noch die zuvor geladene.
    val tage = woche.tageFuer(wochenReferenz(if (naechste) 1 else 0))
    val ergebnisse = remember(tage, gewaehlte) {
        tage?.map { tag -> tag to gemeinsamAm(tag, gewaehlte) }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !naechste, onClick = { naechste = false }, label = { Text("Diese Woche") })
                    FilterChip(selected = naechste, onClick = { naechste = true }, label = { Text("Nächste Woche") })
                }
                Text(
                    text = "Mit wem willst du dich treffen? Pro Tag siehst du, welche Stunden ihr alle " +
                        "gleichzeitig frei habt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(freunde, key = { it.id }) { f ->
                        FilterChip(
                            selected = f.id in auswahl,
                            onClick = { auswahl = if (f.id in auswahl) auswahl - f.id else auswahl + f.id },
                            label = { Text(f.name) }
                        )
                    }
                }
            }
        }
        when {
            gewaehlte.isEmpty() -> item {
                Text(
                    text = "Wähle mindestens einen Freund aus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            woche is WochenDaten.Fehler -> item {
                Text(text = woche.nachricht, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onWocheLaden(naechste) }) { Text("Erneut versuchen") }
            }
            tage == null -> item { LadeZeile() }
            else -> {
                item {
                    val mit = ergebnisse.count { (_, e) -> e != null && e.frei.isNotEmpty() }
                    val moeglich = ergebnisse.count { (_, e) -> e != null }
                    val wer = if (gewaehlte.size == 1) "ihr" else "ihr alle"
                    Text(
                        text = when {
                            moeglich == 0 -> "Für diese Woche gibt es noch keine Pläne."
                            mit == 0 -> "In dieser Woche habt $wer keine gemeinsame Freistunde."
                            else -> "An $mit von $moeglich Tagen habt $wer gemeinsam frei."
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(ergebnisse, key = { it.first.datum.toString() }) { (tag, ergebnis) ->
                    GemeinsamTagKarte(tag.datum, ergebnis, tag.fehler)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun GemeinsamTagKarte(datum: LocalDate, ergebnis: TagErgebnis?, fehler: String?) {
    TagesKarte(datum) {
        val grau = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            ergebnis == null -> Text(fehler ?: "Kein Plan", style = MaterialTheme.typography.bodyMedium, color = grau)
            ergebnis.ohneUnterricht.isNotEmpty() -> Text(
                text = ohneUnterrichtText(ergebnis.ohneUnterricht),
                style = MaterialTheme.typography.bodyMedium,
                color = grau
            )
            ergebnis.frei.isEmpty() -> Text("Keine gemeinsame Freistunde", style = MaterialTheme.typography.bodyMedium, color = grau)
            else -> ergebnis.frei.forEach { b ->
                Text(
                    text = "Frei: " + blockText(b),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
