package com.nextlesson.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.Belegung
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.data.LessonStatus
import com.nextlesson.app.data.SchulTag
import com.nextlesson.app.data.Treffer
import com.nextlesson.app.data.Zeitfenster
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import com.nextlesson.app.data.raumGruppe
import com.nextlesson.app.data.stundenListe
import com.nextlesson.app.data.wochenReferenz
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val uhrzeitFormat = DateTimeFormatter.ofPattern("HH:mm")
private val tagFormat = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

/**
 * Lehrer- und Raumsuche: "Wo ist Frau X gerade?" und "Ist Raum 121 frei?" – für heute oder
 * einen anderen Schultag, aus dem Plan der ganzen Schule.
 */
@Composable
fun SucheScreen(
    zustand: SucheZustand,
    datum: LocalDate,
    onBlaettern: (Int) -> Unit,
    onHeute: () -> Unit,
    onNeuLaden: () -> Unit,
    favoriten: List<Treffer> = emptyList(),
    onFavorit: (Treffer) -> Unit = {},
    woche: WochenDaten<SucheWochenTag> = WochenDaten.Laedt,
    onWocheLaden: (naechste: Boolean) -> Unit = {}
) {
    var anfrage by rememberSaveable { mutableStateOf("") }
    var auswahl by rememberSaveable { mutableStateOf<Treffer?>(null) }
    val jetzt by rememberJetzt()
    val istHeute = datum == LocalDate.now()
    val fokus = LocalFocusManager.current

    // Zurück aus der Detailansicht führt erst zur Trefferliste, nicht aus der App.
    BackHandler(enabled = auswahl != null) { auswahl = null }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = anfrage,
            onValueChange = {
                anfrage = it
                auswahl = null
            },
            placeholder = { Text("Lehrerkürzel oder Raum") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (anfrage.isNotEmpty()) {
                    IconButton(onClick = {
                        anfrage = ""
                        auswahl = null
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Suche leeren")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { fokus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )

        TagesLeiste(datum = datum, istHeute = istHeute, onBlaettern = onBlaettern, onHeute = onHeute)

        when (zustand) {
            is SucheZustand.Laedt -> LadeScreen()
            is SucheZustand.Fehler -> FehlerScreen(
                nachricht = zustand.nachricht,
                zugangsproblem = false,
                onErneutVersuchen = onNeuLaden,
                onEinstellungen = {}
            )
            is SucheZustand.Geladen -> {
                val tag = zustand.tag
                val gewaehlt = auswahl
                when {
                    gewaehlt != null -> Detail(
                        tag, gewaehlt, istHeute, jetzt,
                        istFavorit = gewaehlt in favoriten,
                        onFavorit = { onFavorit(gewaehlt) },
                        woche = woche,
                        onWocheLaden = onWocheLaden
                    )
                    anfrage.isBlank() -> Startansicht(tag, istHeute, jetzt, zustand.ausCache, favoriten) { auswahl = it }
                    else -> Trefferliste(tag, tag.suche(anfrage), istHeute, jetzt) {
                        fokus.clearFocus()
                        auswahl = it
                    }
                }
            }
        }
    }
}

@Composable
private fun TagesLeiste(datum: LocalDate, istHeute: Boolean, onBlaettern: (Int) -> Unit, onHeute: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onBlaettern(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Schultag")
        }
        Text(
            text = tagesLabel(datum),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onBlaettern(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Schultag")
        }
        if (!istHeute) {
            TextButton(onClick = onHeute) { Text("Heute") }
        }
    }
}

private fun tagesLabel(datum: LocalDate): String {
    val heute = LocalDate.now()
    return when (datum) {
        heute -> "Heute"
        heute.plusDays(1) -> "Morgen"
        else -> datum.format(tagFormat)
    }
}

@Composable
private fun Startansicht(
    tag: SchulTag,
    istHeute: Boolean,
    jetzt: LocalTime,
    ausCache: Boolean,
    favoriten: List<Treffer>,
    onWahl: (Treffer) -> Unit
) {
    val frei = remember(tag, istHeute, jetzt) { if (istHeute) tag.freieRaeume(jetzt) else null }
    // Raum → Gruppe (Haus/Etage) einmal berechnen; daraus Filter-Chips und gefilterte Liste.
    val mitGruppe = remember(frei) { frei?.map { it to raumGruppe(it.first) } }
    var gruppe by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (ausCache) {
            item { Hinweistext("Gespeicherter Stand – gerade keine Verbindung.") }
        }
        if (favoriten.isNotEmpty()) {
            item {
                Text(text = "Favoriten", style = MaterialTheme.typography.titleSmall)
            }
            items(favoriten, key = { "F:" + it.schluessel }) { t ->
                TrefferZeile(tag, t, istHeute, jetzt, onWahl)
            }
        }
        item {
            Text(
                text = "Finde heraus, wo eine Lehrkraft gerade Unterricht hat oder ob ein Raum frei ist: " +
                    "einfach das Kürzel oder die Raumnummer eingeben.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (istHeute) {
            item {
                if (frei == null) {
                    Hinweistext("Gerade ist kein Unterricht – freie Räume gibt es hier während der Schulzeit.")
                } else {
                    // Filter nach Haus bzw. Etage – nur, wenn es überhaupt etwas zu unterscheiden gibt.
                    val paare = mitGruppe.orEmpty()
                    val gruppen = paare.map { it.second }.distinct().sorted()
                    val aktiv = gruppe?.takeIf { it in gruppen }
                    val sichtbar = paare.filter { aktiv == null || it.second == aktiv }.map { it.first }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Jetzt frei (${sichtbar.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (gruppen.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(selected = aktiv == null, onClick = { gruppe = null }, label = { Text("Alle") })
                                }
                                items(gruppen, key = { it }) { g ->
                                    FilterChip(selected = aktiv == g, onClick = { gruppe = g }, label = { Text(g) })
                                }
                            }
                        }
                        if (frei.isEmpty()) {
                            Hinweistext("Laut Plan ist gerade jeder Raum belegt.")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sichtbar, key = { it.first }) { (raum, bis) ->
                                AssistChip(
                                    onClick = { onWahl(Treffer.Raum(raum)) },
                                    label = { Text(if (bis == null) raum else "$raum · bis ${bis.format(uhrzeitFormat)}") }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Hinweistext("${tag.anzahlLehrer} Lehrkräfte und ${tag.raeume.size} Räume im Plan dieses Tages.")
        }
        item { QuellenHinweis() }
    }
}

@Composable
private fun Trefferliste(
    tag: SchulTag,
    treffer: List<Treffer>,
    istHeute: Boolean,
    jetzt: LocalTime,
    onWahl: (Treffer) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (treffer.isEmpty()) {
            item {
                Hinweistext("Nichts gefunden. An diesem Tag steht dieses Kürzel bzw. dieser Raum nicht im Plan.")
            }
        }
        items(treffer, key = { it.schluessel }) { t ->
            TrefferZeile(tag, t, istHeute, jetzt, onWahl)
        }
        item { QuellenHinweis() }
    }
}

@Composable
private fun TrefferZeile(tag: SchulTag, t: Treffer, istHeute: Boolean, jetzt: LocalTime, onWahl: (Treffer) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onWahl(t) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrefferSymbol(t)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = t.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = statusText(tag, t, istHeute, jetzt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Detail(
    tag: SchulTag,
    t: Treffer,
    istHeute: Boolean,
    jetzt: LocalTime,
    istFavorit: Boolean,
    onFavorit: () -> Unit,
    woche: WochenDaten<SucheWochenTag>,
    onWocheLaden: (naechste: Boolean) -> Unit
) {
    val zeilen = remember(tag, t) { tag.tagesablauf(t) }
    var wocheOffen by rememberSaveable(t.name) { mutableStateOf(false) }
    var naechste by rememberSaveable(t.name) { mutableStateOf(false) }
    LaunchedEffect(wocheOffen, naechste) { if (wocheOffen) onWocheLaden(naechste) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                TrefferSymbol(t)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (t is Treffer.Lehrer) "Lehrkraft" else "Raum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = t.name, style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onFavorit) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = if (istFavorit) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                        tint = if (istFavorit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText(tag, t, istHeute, jetzt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (t is Treffer.Lehrer) {
            item {
                OutlinedButton(onClick = { wocheOffen = !wocheOffen }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (wocheOffen) "Zurück zum Tag" else "Wann ist ${t.name} diese Woche frei?")
                }
            }
        }
        if (wocheOffen && t is Treffer.Lehrer) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !naechste, onClick = { naechste = false }, label = { Text("Diese Woche") })
                    FilterChip(selected = naechste, onClick = { naechste = true }, label = { Text("Nächste Woche") })
                }
            }
            // Nur die gewählte Woche zeigen – nicht kurz noch die zuvor geladene.
            val tage = woche.tageFuer(wochenReferenz(if (naechste) 1 else 0))
            when {
                woche is WochenDaten.Fehler -> item { Hinweistext(woche.nachricht) }
                tage == null -> item { LadeZeile() }
                else -> items(tage, key = { it.datum.toString() }) { wt -> LehrerWochenKarte(wt, t) }
            }
        } else {
            if (zeilen.isEmpty()) {
                item { Hinweistext("An diesem Tag steht dazu nichts im Plan.") }
            }
            items(zeilen) { (fenster, stunden) ->
                ZeitfensterZeile(fenster, stunden, t, laeuft = istHeute && laeuftIn(fenster, jetzt))
            }
        }
        item { QuellenHinweis() }
    }
}

/** Ein Tag der Wochenübersicht: wann unterrichtet die Lehrkraft, wann hat sie Lücken? */
@Composable
private fun LehrerWochenKarte(wt: SucheWochenTag, t: Treffer.Lehrer) {
    val tag = wt.tag
    val stunden = remember(tag, t) { tag?.unterrichtsStunden(t).orEmpty() }
    val luecken = remember(tag, t) { tag?.luecken(t).orEmpty() }
    val schluss = remember(tag, t) {
        tag?.stundenVon(t)?.filter { !it.entfaellt }?.mapNotNull { it.ende }?.maxOrNull()
    }
    TagesKarte(wt.datum) {
        val grau = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            tag == null -> Text(wt.fehler ?: "Kein Plan", style = MaterialTheme.typography.bodyMedium, color = grau)
            stunden.isEmpty() -> Text("kein Unterricht laut Plan", style = MaterialTheme.typography.bodyMedium, color = grau)
            else -> {
                Text("Unterricht: ${stundenListe(stunden)}", style = MaterialTheme.typography.bodyMedium)
                if (luecken.isEmpty()) {
                    Text("Keine Lücke dazwischen", style = MaterialTheme.typography.bodySmall, color = grau)
                }
                luecken.forEach { b ->
                    Text(
                        text = "Frei: " + blockText(b),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (schluss != null) {
                    Text(
                        "Letzte Stunde endet ${schluss.format(uhrzeitFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = grau
                    )
                }
            }
        }
    }
}

@Composable
private fun ZeitfensterZeile(fenster: Zeitfenster, stunden: List<Lesson>, t: Treffer, laeuft: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (laeuft) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.widthIn(min = 76.dp)) {
            Text(text = "${fenster.stunde}. Std", style = MaterialTheme.typography.titleSmall)
            val beginn = fenster.beginn
            val ende = fenster.ende
            if (beginn != null && ende != null) {
                Text(
                    text = "${beginn.format(uhrzeitFormat)}–${ende.format(uhrzeitFormat)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (stunden.isEmpty()) {
                Text(
                    text = if (t is Treffer.Raum) "frei" else "keine Stunde",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            stunden.forEach { l -> BelegungsZeile(l, t) }
        }
    }
}

@Composable
private fun BelegungsZeile(l: Lesson, t: Treffer) {
    val zusatz = when (t) {
        is Treffer.Lehrer -> l.raum.takeIf { it.isNotBlank() }?.let { "Raum $it" }
        is Treffer.Raum -> l.lehrer.takeIf { it.isNotBlank() }
    }
    Column {
        Text(
            text = listOfNotNull(l.klasse, fachName(l), zusatz).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (l.entfaellt) TextDecoration.LineThrough else null,
            color = if (l.entfaellt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        val etikett = when {
            l.entfaellt -> "entfällt"
            l.istKlausur -> "Klausur"
            l.status == LessonStatus.VERTRETUNG -> "Vertretung"
            l.raumGeaendert -> "Raumänderung"
            else -> null
        }
        if (etikett != null) {
            StatusBadge(
                text = etikett,
                hintergrund = if (l.entfaellt || l.istKlausur) MaterialTheme.colorScheme.error else Color(0xFFFF9800),
                vordergrund = if (l.entfaellt || l.istKlausur) MaterialTheme.colorScheme.onError else Color.White
            )
        }
    }
}

@Composable
private fun TrefferSymbol(t: Treffer) {
    Icon(
        imageVector = if (t is Treffer.Lehrer) Icons.Filled.Person else Icons.Filled.Place,
        contentDescription = if (t is Treffer.Lehrer) "Lehrkraft" else "Raum",
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Hinweistext(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun QuellenHinweis() {
    Text(
        text = "Grundlage ist der Schülerplan aller Klassen. Aufsichten, Sprechstunden und Räume " +
            "ohne Unterricht stehen darin nicht – \"frei\" heißt: laut Plan kein Unterricht.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
}

/** Eine Zeile Status: wo die Lehrkraft gerade ist bzw. ob der Raum frei ist. */
private fun statusText(tag: SchulTag, t: Treffer, istHeute: Boolean, jetzt: LocalTime): String {
    if (!istHeute) {
        val anzahl = tag.stundenVon(t).count { !it.entfaellt }
        return when (anzahl) {
            0 -> "an diesem Tag kein Unterricht"
            1 -> "1 Stunde an diesem Tag"
            else -> "$anzahl Stunden an diesem Tag"
        }
    }
    return when (val b = tag.belegung(t, jetzt)) {
        is Belegung.Jetzt -> {
            val bis = b.lesson.ende?.let { " bis ${it.format(uhrzeitFormat)}" }.orEmpty()
            when (t) {
                is Treffer.Lehrer -> "jetzt: ${ort(b.lesson)}$bis"
                is Treffer.Raum -> "belegt: ${klasseUndFach(b.lesson)}" +
                    (b.lesson.lehrer.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + bis
            }
        }
        is Belegung.Frei -> {
            val n = b.naechste
            val ab = n?.beginn?.format(uhrzeitFormat)
            when (t) {
                is Treffer.Lehrer ->
                    if (n == null) "heute keine Stunde mehr"
                    else "gerade keine Stunde · ab ${ab ?: "später"}: ${ort(n)}"
                is Treffer.Raum ->
                    if (n == null) "frei für den Rest des Tages"
                    else "frei bis ${ab ?: "später"}"
            }
        }
    }
}

private fun ort(l: Lesson): String =
    (l.raum.takeIf { it.isNotBlank() }?.let { "Raum $it, " } ?: "") + klasseUndFach(l)

private fun klasseUndFach(l: Lesson): String = listOfNotNull(l.klasse, fachName(l)).joinToString(" ")

private fun fachName(l: Lesson): String? =
    l.fach.takeIf { f -> f.any { it.isLetterOrDigit() } } ?: l.kursKuerzel?.takeIf { it.isNotBlank() }

private fun laeuftIn(fenster: Zeitfenster, jetzt: LocalTime): Boolean {
    val beginn = fenster.beginn ?: return false
    val ende = fenster.ende ?: return false
    return !jetzt.isBefore(beginn) && jetzt.isBefore(ende)
}
