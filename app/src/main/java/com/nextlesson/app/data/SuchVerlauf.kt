package com.nextlesson.app.data

import android.content.Context

/** Die zuletzt gewählten Lehrkräfte und Räume der Suche (neueste zuerst). */
class SuchVerlauf(context: Context) {

    private val prefs = context.getSharedPreferences("such_verlauf", Context.MODE_PRIVATE)

    fun laden(): List<Treffer> = (prefs.getString(KEY, "") ?: "")
        .split('\n').mapNotNull { SuchVerlaufLogik.ausSchluessel(it) }

    /** Setzt [t] an den Anfang (und entfernt ein früheres Vorkommen); gibt die neue Liste zurück. */
    fun merken(t: Treffer): List<Treffer> {
        val neu = SuchVerlaufLogik.einfuegen(laden(), t)
        prefs.edit().putString(KEY, neu.joinToString("\n") { it.schluessel }).apply()
        return neu
    }

    /** Löscht [t] aus dem Verlauf; gibt die neue Liste zurück. */
    fun entfernen(t: Treffer): List<Treffer> {
        val neu = SuchVerlaufLogik.entfernen(laden(), t)
        prefs.edit().putString(KEY, neu.joinToString("\n") { it.schluessel }).apply()
        return neu
    }

    fun leeren() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "verlauf"
    }
}
