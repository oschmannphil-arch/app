package com.nextlesson.app.data

import android.content.Context

/** Welche Änderungsarten eine Benachrichtigung auslösen – in den Einstellungen einzeln schaltbar. */
class BenachrichtigungsEinstellungen(context: Context) {

    private val prefs = context.getSharedPreferences("benachrichtigungen", Context.MODE_PRIVATE)

    fun aktiv(art: AenderungsArt): Boolean = prefs.getBoolean(schluessel(art), true)

    fun setzen(art: AenderungsArt, aktiv: Boolean) {
        prefs.edit().putBoolean(schluessel(art), aktiv).apply()
    }

    fun aktiveArten(): Set<AenderungsArt> = AenderungsArt.entries.filterTo(HashSet()) { aktiv(it) }

    private fun schluessel(art: AenderungsArt) = "melden_" + art.name.lowercase()
}
