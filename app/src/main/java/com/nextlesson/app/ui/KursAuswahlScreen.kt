package com.nextlesson.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextlesson.app.data.KursInfo

/**
 * Der einzige Einrichtungsschritt nach dem Login: der Schüler kreuzt seine Kurse an.
 * Aus den Kursen leitet die App die Klasse/den Jahrgang selbst ab.
 */
@Composable
fun KursAuswahlScreen(
    verfuegbareKurse: List<KursInfo>,
    gewaehlteKurse: Set<String>,
    onSpeichern: (Set<String>) -> Unit,
    onAbbrechen: (() -> Unit)? = null
) {
    var auswahl by remember { mutableStateOf(gewaehlteKurse) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "Deine Kurse", style = MaterialTheme.typography.headlineSmall)

        if (verfuegbareKurse.isEmpty()) {
            Text(
                text = "Im Plan deiner Schule sind keine Kurse hinterlegt. Melde dich – dann " +
                    "bauen wir stattdessen eine einfache Klassenauswahl ein.",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Text(
            text = "Kreuze die Kurse an, die du belegst. Deine Klasse musst du nicht angeben – " +
                "die ergibt sich daraus. Gemeinsamer Klassenunterricht wird automatisch mit angezeigt.",
            style = MaterialTheme.typography.bodySmall
        )

        KursListe(
            verfuegbareKurse = verfuegbareKurse,
            auswahl = auswahl,
            onAuswahl = { auswahl = it },
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = { onSpeichern(auswahl) },
            enabled = auswahl.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (auswahl.isEmpty()) "Mindestens einen Kurs wählen" else "Stundenplan anzeigen")
        }
        if (onAbbrechen != null) {
            OutlinedButton(onClick = onAbbrechen, modifier = Modifier.fillMaxWidth()) {
                Text("Abbrechen")
            }
        }
    }
}

/** Durchsuchbare Kursliste zum Ankreuzen – für die eigenen Kurse und die von Freunden. */
@Composable
fun KursListe(
    verfuegbareKurse: List<KursInfo>,
    auswahl: Set<String>,
    onAuswahl: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var suche by remember { mutableStateOf("") }

    val gefiltert = remember(suche, verfuegbareKurse) {
        val q = suche.trim().lowercase()
        if (q.isEmpty()) verfuegbareKurse
        else verfuegbareKurse.filter { it.suchtext.contains(q) }
    }
    val gruppiert = remember(gefiltert) { gefiltert.groupBy { it.klasse } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = suche,
            onValueChange = { suche = it },
            label = { Text("Suchen (Kurs, Fach, Lehrer, Jahrgang)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${auswahl.size} ausgewählt",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            OutlinedButton(onClick = { onAuswahl(emptySet()) }) { Text("Zurücksetzen") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            gruppiert.forEach { (klasse, kurse) ->
                item(key = "kopf-$klasse") {
                    Text(
                        text = klasse,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }
                items(kurse, key = { it.id }) { kurs ->
                    val checked = kurs.id in auswahl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAuswahl(if (checked) auswahl - kurs.id else auswahl + kurs.id)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = kurs.anzeige,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
