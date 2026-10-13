package com.nextlesson.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.nextlesson.app.data.FotoAenderung
import com.nextlesson.app.data.TafelFotos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Hausaufgabe
import com.nextlesson.app.ui.theme.fachAnzeige
import com.nextlesson.app.ui.theme.fachFarbe
import com.nextlesson.app.ui.theme.istDunkel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val datumFormat = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

@Composable
fun HausaufgabenScreen(
    aufgaben: List<Hausaufgabe>,
    onUmschalten: (String) -> Unit,
    onLoeschen: (String) -> Unit,
    onHinzufuegen: (fach: String, text: String, faellig: LocalDate?, foto: FotoAenderung) -> Unit,
    onBearbeiten: (id: String, fach: String, text: String, faellig: LocalDate?, foto: FotoAenderung) -> Unit,
    fotoVersion: Int = 0,
    faecher: List<String> = emptyList()
) {
    var dialogOffen by remember { mutableStateOf(false) }
    var galerieOffen by remember { mutableStateOf(false) }
    val kontext = LocalContext.current
    val mitFoto = remember(aufgaben, fotoVersion) { aufgaben.filter { TafelFotos.vorhanden(kontext, it.id) } }
    var bearbeitungsAufgabe by remember { mutableStateOf<Hausaufgabe?>(null) }
    val dunkel = istDunkel()
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
                if (mitFoto.isNotEmpty()) {
                    TextButton(onClick = { galerieOffen = true }) { Text("📷 Tafelfotos (${mitFoto.size})") }
                }
                if (aufgaben.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { erledigt.size.toFloat() / aufgaben.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${erledigt.size} von ${aufgaben.size} erledigt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    onBearbeitenClick = { bearbeitungsAufgabe = aufgabe },
                    fotoVersion = fotoVersion
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
                        onBearbeitenClick = { bearbeitungsAufgabe = aufgabe },
                    fotoVersion = fotoVersion
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (galerieOffen) {
        FotoGalerie(mitFoto, onSchliessen = { galerieOffen = false })
    }

    if (dialogOffen) {
        HausaufgabeDialog(
            faecher = faecher,
            onAbbrechen = { dialogOffen = false },
            onSpeichern = { fach, text, faellig, foto ->
                onHinzufuegen(fach, text, faellig, foto)
                dialogOffen = false
            }
        )
    }

    bearbeitungsAufgabe?.let { aufgabe ->
        HausaufgabeDialog(
            bestehendeAufgabe = aufgabe,
            faecher = faecher,
            onAbbrechen = { bearbeitungsAufgabe = null },
            onSpeichern = { fach, text, faellig, foto ->
                onBearbeiten(aufgabe.id, fach, text, faellig, foto)
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
    onBearbeitenClick: () -> Unit,
    fotoVersion: Int
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
                            text = fachAnzeige(aufgabe.fach),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextMitTabelle(
                    text = aufgabe.text,
                    durchgestrichen = aufgabe.erledigt,
                    farbe = if (aufgabe.erledigt) MaterialTheme.colorScheme.onSurfaceVariant
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

            val kontext = LocalContext.current
            var fotoOffen by remember { mutableStateOf(false) }
            // Nach dem Speichern entsteht die Datei kurz verzögert – darum an der Aufgabe festmachen.
            val fotoDa = remember(aufgabe.id, fotoVersion) { TafelFotos.vorhanden(kontext, aufgabe.id) }
            if (fotoDa) {
                TextButton(onClick = { fotoOffen = true }) { Text("📷") }
            }
            if (fotoOffen) {
                FotoDialog(aufgabe.id, aufgabe.fach, aufgabe.text) { fotoOffen = false }
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

/**
 * @param vorschlagFach Fach vorbelegen (Aufgabe direkt aus einer Stunde anlegen).
 * @param naechsteStunde Datum der nächsten Stunde dieses Kurses – wird als Fälligkeit
 *        übernommen, sobald es ermittelt ist, solange man selbst noch kein Datum gewählt hat.
 */
@Composable
internal fun HausaufgabeDialog(
    bestehendeAufgabe: Hausaufgabe? = null,
    vorschlagFach: String? = null,
    naechsteStunde: LocalDate? = null,
    faecher: List<String> = emptyList(),
    onAbbrechen: () -> Unit,
    onSpeichern: (fach: String, text: String, faellig: LocalDate?, foto: FotoAenderung) -> Unit
) {
    val context = LocalContext.current
    // Ein frisches Kamerabild liegt in der Zwischendatei; ein früheres gehört schon zur Aufgabe.
    var neuesFoto by remember { mutableStateOf(false) }
    var fotoEntfernen by remember { mutableStateOf(false) }
    val hatAltesFoto = remember { bestehendeAufgabe != null && TafelFotos.vorhanden(context, bestehendeAufgabe.id) }
    DisposableEffect(Unit) { onDispose { TafelFotos.zwischendateiLoeschen(context) } }
    // Bricht man die Kamera ab, bleibt ein schon gemachtes Foto erhalten.
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { neuesFoto = true; fotoEntfernen = false } }
    // Ein Bild aus der Galerie landet in derselben Zwischendatei wie ein Kamerabild. Kopiert wird im
    // Hintergrund (Bilder aus der Cloud werden dabei erst geladen) und über eine Hilfsdatei, damit ein
    // abgebrochenes Kopieren kein schon aufgenommenes Foto zerstört.
    var galerieLaedt by remember { mutableStateOf(false) }
    val haupt = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Wird der Dialog geschlossen, während ein Bild noch lädt, soll das Laden nichts mehr ablegen.
    val dialogOffen = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { dialogOffen.set(false) } }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            galerieLaedt = true
            Thread {
                val ziel = TafelFotos.zwischendatei(context)
                val hilfe = java.io.File(ziel.parentFile, "galerie.tmp")
                val kopiert = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { ein ->
                        hilfe.outputStream().use { aus -> ein.copyTo(aus) }
                        true
                    } ?: false
                }.getOrDefault(false) && dialogOffen.get() && hilfe.length() > 0 && (hilfe.renameTo(ziel) || runCatching { hilfe.copyTo(ziel, overwrite = true); true }.getOrDefault(false))
                hilfe.delete()
                haupt.post {
                    galerieLaedt = false
                    if (kopiert) { neuesFoto = true; fotoEntfernen = false }
                }
            }.start()
        }
    }
    var fach by remember { mutableStateOf(bestehendeAufgabe?.fach ?: vorschlagFach ?: "") }
    var text by remember { mutableStateOf(bestehendeAufgabe?.text ?: "") }
    // Diktieren: die Spracherkennung des Geräts liefert Text, der ans Feld angehängt wird.
    var sprachFehler by remember { mutableStateOf(false) }
    val diktat = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val gesagt = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == android.app.Activity.RESULT_OK && !gesagt.isNullOrBlank()) {
            text = if (text.isBlank()) gesagt else text.trimEnd() + " " + gesagt
        }
    }
    // Text aus dem Foto lesen (frisches Kamerabild oder das gespeicherte Foto der Aufgabe).
    var ocrStatus by remember { mutableStateOf<String?>(null) }
    // Neue Aufgabe: ab morgen; bestehende behält ihr Datum – auch "ohne Datum".
    var faellig by remember {
        mutableStateOf<LocalDate?>(if (bestehendeAufgabe != null) bestehendeAufgabe.faellig else LocalDate.now().plusDays(1))
    }
    var datumSelbstGewaehlt by remember { mutableStateOf(bestehendeAufgabe != null) }
    var datumsDialog by remember { mutableStateOf(false) }
    val heute = LocalDate.now()

    LaunchedEffect(naechsteStunde) {
        if (naechsteStunde != null && !datumSelbstGewaehlt) faellig = naechsteStunde
    }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (bestehendeAufgabe == null) "Neue Hausaufgabe" else "Hausaufgabe bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FachFeld(wert = fach, onWert = { fach = it }, faecher = faecher, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Was ist zu tun?") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {
                            sprachFehler = false
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Was ist zu tun?")
                            }
                            runCatching { diktat.launch(intent) }.onFailure { sprachFehler = true }
                        },
                        label = { Text("🎤 Diktieren") }
                    )
                    if (neuesFoto || (hatAltesFoto && !fotoEntfernen)) {
                        AssistChip(
                            onClick = {
                                ocrStatus = "Text wird gelesen …"
                                val datei = if (neuesFoto) TafelFotos.zwischendatei(context)
                                else TafelFotos.datei(context, bestehendeAufgabe!!.id)
                                com.nextlesson.app.data.TextErkennung.aus(context, datei) { erkannt ->
                                    if (erkannt == null) {
                                        ocrStatus = "Kein Text erkannt."
                                    } else {
                                        text = if (text.isBlank()) erkannt else text.trimEnd() + "\n" + erkannt
                                        ocrStatus = "Text übernommen – bitte prüfen."
                                    }
                                }
                            },
                            label = { Text("🔍 Text aus Foto") }
                        )
                    }
                }
                if (sprachFehler) {
                    Text("Auf diesem Gerät ist keine Spracheingabe verfügbar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                ocrStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                AssistChip(
                    onClick = { galerie.launch("image/*") },
                    label = { Text(if (galerieLaedt) "🖼️ Bild wird geladen …" else "🖼️ Bild aus Galerie") }
                )
                AssistChip(
                    onClick = { kamera.launch(TafelFotos.uriFuerKamera(context)) },
                    label = {
                        Text(
                            when {
                                neuesFoto -> "📷 Neues Foto bereit"
                                hatAltesFoto -> "📷 Foto ersetzen"
                                else -> "📷 Tafel fotografieren"
                            }
                        )
                    }
                )

                if (hatAltesFoto && !neuesFoto) {
                    AssistChip(
                        onClick = { fotoEntfernen = !fotoEntfernen },
                        label = { Text(if (fotoEntfernen) "Foto wird entfernt – rückgängig" else "Foto entfernen") }
                    )
                }

                Text("Fällig", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (naechsteStunde != null) {
                        AssistChip(
                            onClick = { faellig = naechsteStunde; datumSelbstGewaehlt = true },
                            label = { Text("Nächste Stunde") }
                        )
                    } else {
                        AssistChip(
                            onClick = { faellig = heute; datumSelbstGewaehlt = true },
                            label = { Text("Heute") }
                        )
                    }
                    AssistChip(
                        onClick = { faellig = heute.plusDays(1); datumSelbstGewaehlt = true },
                        label = { Text("Morgen") }
                    )
                    AssistChip(
                        onClick = { datumsDialog = true },
                        label = { Text("Datum") }
                    )
                    AssistChip(
                        onClick = { faellig = null; datumSelbstGewaehlt = true },
                        label = { Text("Ohne") }
                    )
                }
                Text(
                    text = faellig?.let {
                        "Gewählt: ${it.format(datumFormat)}" +
                            if (it == naechsteStunde) " (nächste Stunde)" else ""
                    } ?: "Ohne Datum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSpeichern(
                        fach, text, faellig,
                        when {
                            neuesFoto -> FotoAenderung.NEU
                            fotoEntfernen -> FotoAenderung.ENTFERNEN
                            else -> FotoAenderung.KEINE
                        }
                    )
                },
                // Erst speichern, wenn ein Galerie-Bild fertig geladen ist – sonst ginge es verloren.
                enabled = text.isNotBlank() && !galerieLaedt
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
                datumSelbstGewaehlt = true
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


/** Aufgabentext; Zeilen der Form "A | B | C" erscheinen als Tabelle. */
@Composable
private fun TextMitTabelle(text: String, durchgestrichen: Boolean, farbe: androidx.compose.ui.graphics.Color) {
    val deko = if (durchgestrichen) TextDecoration.LineThrough else null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        com.nextlesson.app.data.TabellenText.abschnitte(text).forEach { abschnitt ->
            when (abschnitt) {
                is com.nextlesson.app.data.TabellenText.Abschnitt.Normal ->
                    Text(abschnitt.text, style = MaterialTheme.typography.bodyLarge, textDecoration = deko, color = farbe)
                is com.nextlesson.app.data.TabellenText.Abschnitt.Tabelle -> {
                    val spalten = abschnitt.zeilen.maxOf { it.size }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    ) {
                        abschnitt.zeilen.forEachIndexed { i, zeile ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                for (c in 0 until spalten) {
                                    Text(
                                        text = zeile.getOrElse(c) { "" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                                        textDecoration = deko,
                                        color = farbe,
                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                    )
                                }
                            }
                            if (i < abschnitt.zeilen.size - 1) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Alle Tafelfotos, nach Fach gruppiert; ein Tipp öffnet das Foto groß. */
@Composable
private fun FotoGalerie(aufgaben: List<Hausaufgabe>, onSchliessen: () -> Unit) {
    var gross by remember { mutableStateOf<String?>(null) }
    val gruppen = remember(aufgaben) {
        aufgaben.groupBy { com.nextlesson.app.data.FachName.schluessel(it.fach) }
            .values.sortedBy { it.first().fach.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Tafelfotos") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gruppen.forEach { liste ->
                    Text(
                        com.nextlesson.app.ui.theme.fachAnzeige(liste.first().fach.ifBlank { "Ohne Fach" }),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        liste.sortedByDescending { it.erstelltAm }.forEach { a ->
                            val context = LocalContext.current
                            val vorschau by produceState<ImageBitmap?>(null, a.id) {
                                value = withContext(Dispatchers.IO) { TafelFotos.laden(context, a.id, maxKante = 256)?.asImageBitmap() }
                            }
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { gross = a.id }
                            ) {
                                vorschau?.let { Image(bitmap = it, contentDescription = "Foto: ${a.text}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Schließen") } }
    )
    gross?.let { id -> FotoDialog(id, aufgaben.firstOrNull { it.id == id }?.fach.orEmpty(), aufgaben.firstOrNull { it.id == id }?.text.orEmpty()) { gross = null } }
}

@Composable
private fun FotoDialog(id: String, fach: String, aufgabenText: String, onSchliessen: () -> Unit) {
    val context = LocalContext.current
    val bild by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { TafelFotos.laden(context, id)?.asImageBitmap() }
    }
    AlertDialog(
        onDismissRequest = onSchliessen,
        text = {
            val b = bild
            if (b == null) {
                Text("Foto wird geladen …")
            } else {
                Image(bitmap = b, contentDescription = "Foto zur Hausaufgabe", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Schließen") } },
        dismissButton = {
            // Das Foto geht an die Claude-App; dort läuft die Lösung, nicht in dieser App.
            TextButton(onClick = { claudeLoesen(context, id, fach, aufgabenText) }) { Text("🤖 Mit Claude lösen") }
        }
    )
}

/**
 * Schickt das Foto samt Aufgabentext an die Claude-App (oder, wenn sie fehlt, an eine andere App aus
 * dem Teilen-Menü). Die App selbst ruft keine KI auf und speichert keinen Schlüssel.
 */
private fun claudeLoesen(context: android.content.Context, id: String, fach: String, aufgabenText: String) {
    val quelle = TafelFotos.datei(context, id)
    if (!quelle.exists() || quelle.length() == 0L) {
        android.widget.Toast.makeText(context, "Kein Foto gefunden", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val ordner = java.io.File(context.cacheDir, "teilen").apply { mkdirs() }
        val kopie = java.io.File(ordner, "hausaufgabe.jpg")
        quelle.copyTo(kopie, overwrite = true)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", kopie)
        val frage = com.nextlesson.app.data.LoesePrompt.fuer(fach, aufgabenText)
        val senden = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            // Bewusst ohne EXTRA_TEXT: Die Claude-App nimmt sonst nur den Text und lässt das Bild fallen.
            // Die Frage steht stattdessen in der Zwischenablage.
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        senden.clipData = android.content.ClipData.newUri(context.contentResolver, "Foto", uri)
        // Viele KI-Apps übernehmen beim Teilen nur das Foto, nicht den Text. Darum liegt die Frage zusätzlich
        // in der Zwischenablage und lässt sich dort ins Feld einfügen.
        runCatching {
            val zwischenablage = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            zwischenablage.setPrimaryClip(android.content.ClipData.newPlainText("Hausaufgabe", frage))
        }
        // Erst direkt an die Claude-App; gibt es sie nicht, zeigt Android das normale Teilen-Menü.
        val direkt = android.content.Intent(senden).setPackage("com.anthropic.claude")
        val ziel = try {
            context.startActivity(direkt)
            "Claude"
        } catch (e: android.content.ActivityNotFoundException) {
            context.startActivity(android.content.Intent.createChooser(senden, "Mit KI-App lösen"))
            "der KI-App"
        }
        android.widget.Toast.makeText(context, "Prompt kopiert – in $ziel ins Feld einfügen", android.widget.Toast.LENGTH_LONG).show()
    }
}
