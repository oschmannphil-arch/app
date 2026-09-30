package com.nextlesson.app.data

import android.content.Context
import java.time.LocalTime

/** Einstellungen für die tägliche Lern-Erinnerung. */
data class Erinnerung(
    val aktiv: Boolean,
    val stunde: Int,
    val minute: Int
) {
    val uhrzeit: LocalTime get() = LocalTime.of(stunde, minute)
    val anzeige: String get() = "%02d:%02d".format(stunde, minute)
}

class ErinnerungsStore(context: Context) {

    private val prefs = context.getSharedPreferences("erinnerung", Context.MODE_PRIVATE)

    fun laden(): Erinnerung = Erinnerung(
        aktiv = prefs.getBoolean(KEY_AKTIV, false),
        stunde = prefs.getInt(KEY_STUNDE, 17).coerceIn(0, 23),
        minute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
    )

    fun speichern(erinnerung: Erinnerung) {
        prefs.edit()
            .putBoolean(KEY_AKTIV, erinnerung.aktiv)
            .putInt(KEY_STUNDE, erinnerung.stunde.coerceIn(0, 23))
            .putInt(KEY_MINUTE, erinnerung.minute.coerceIn(0, 59))
            .apply()
    }

    companion object {
        private const val KEY_AKTIV = "aktiv"
        private const val KEY_STUNDE = "stunde"
        private const val KEY_MINUTE = "minute"
    }
}
