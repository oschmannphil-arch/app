package com.nextlesson.app.work

import android.content.Context

/**
 * Merkt sich, wann der Hintergrund-Abruf zuletzt erfolgreich beim Server nachgesehen hat.
 * Die Einstellungen zeigen das an – so sieht man, ob Android die App im Hintergrund laufen
 * lässt (Voraussetzung für Ausfall-Benachrichtigungen).
 */
object HintergrundStatus {

    private const val DATEI = "hintergrund_status"
    private const val KEY_ZULETZT = "zuletzt_geprueft"

    fun erfolgreichGeprueft(context: Context, zeit: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit().putLong(KEY_ZULETZT, zeit).apply()
    }

    /** 0 = noch nie. */
    fun zuletztGeprueft(context: Context): Long =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).getLong(KEY_ZULETZT, 0L)
}
