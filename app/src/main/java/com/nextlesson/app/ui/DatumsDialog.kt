package com.nextlesson.app.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Datumsauswahl.
 *
 * Der DatePicker liefert Millisekunden auf UTC-Mitternacht. Wird das mit der lokalen
 * Zeitzone umgerechnet, landet man je nach Zone einen Tag daneben – deshalb hier
 * konsequent über [ZoneOffset.UTC].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumsDialog(
    vorauswahl: LocalDate?,
    onAbbrechen: () -> Unit,
    onGewaehlt: (LocalDate) -> Unit
) {
    val startMillis = (vorauswahl ?: LocalDate.now())
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    val zustand = rememberDatePickerState(initialSelectedDateMillis = startMillis)

    DatePickerDialog(
        onDismissRequest = onAbbrechen,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = zustand.selectedDateMillis
                    if (millis != null) {
                        val datum = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onGewaehlt(datum)
                    } else {
                        onAbbrechen()
                    }
                }
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
    ) {
        DatePicker(state = zustand)
    }
}
