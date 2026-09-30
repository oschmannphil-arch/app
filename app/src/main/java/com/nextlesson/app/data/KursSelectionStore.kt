package com.nextlesson.app.data

import android.content.Context

/**
 * Speichert, welche Kurse (aus dem Oberstufen-Kurssystem) der Schüler belegt hat.
 * Nicht sicherheitsrelevant, daher normale (unverschlüsselte) SharedPreferences.
 */
class KursSelectionStore(context: Context) {

    private val prefs = context.getSharedPreferences("kurs_auswahl", Context.MODE_PRIVATE)

    fun speichern(kurse: Set<String>) {
        prefs.edit().putStringSet(KEY, kurse).apply()
    }

    fun laden(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    companion object {
        private const val KEY = "gewaehlte_kurse"
    }
}
