package com.nextlesson.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nextlesson.app.ui.theme.FarbThema

enum class DesignModus(val anzeige: String) {
    SYSTEM("System"), HELL("Hell"), DUNKEL("Dunkel")
}

/** Wie eng die Stundenliste gesetzt ist. */
enum class Listendichte(val anzeige: String, val abstandDp: Int) {
    KOMPAKT("Kompakt", 5), NORMAL("Normal", 10), GROSS("Groß", 16)
}

/**
 * Darstellung: hell/dunkel/System und eigene Fachfarben. Die Werte sind Compose-State, damit
 * sich die Oberfläche beim Umstellen sofort ändert – ohne ViewModel-Umweg.
 */
class DesignStore(context: Context) {

    private val prefs = context.getSharedPreferences("design", Context.MODE_PRIVATE)

    var modus by mutableStateOf(
        runCatching { DesignModus.valueOf(prefs.getString(KEY_MODUS, null) ?: "") }.getOrDefault(DesignModus.SYSTEM)
    )
        private set

    /** Stundenliste als Blöcke (Doppelstunden zusammengefasst) statt Stunde für Stunde. */
    var blockAnsicht by mutableStateOf(prefs.getBoolean(KEY_BLOECKE, false))
        private set

    fun blockAnsichtSetzen(an: Boolean) {
        blockAnsicht = an
        prefs.edit().putBoolean(KEY_BLOECKE, an).apply()
    }

    var farbThema by mutableStateOf(
        runCatching { FarbThema.valueOf(prefs.getString(KEY_THEMA, null) ?: "") }.getOrDefault(FarbThema.MOCHA)
    )
        private set

    fun farbThemaSetzen(neu: FarbThema) {
        farbThema = neu
        prefs.edit().putString(KEY_THEMA, neu.name).apply()
    }

    /** Symbol (Emoji) vor dem Fachnamen. */
    var fachSymbole by mutableStateOf(prefs.getBoolean(KEY_SYMBOLE, true))
        private set

    fun fachSymboleSetzen(an: Boolean) {
        fachSymbole = an
        prefs.edit().putBoolean(KEY_SYMBOLE, an).apply()
    }

    /** Farbton (0–360°) des eigenen Farbthemas. */
    var eigenerFarbton by mutableStateOf(prefs.getFloat(KEY_TON, 30f))
        private set

    fun eigenerFarbtonSetzen(grad: Float) {
        eigenerFarbton = grad.coerceIn(0f, 360f)
        prefs.edit().putFloat(KEY_TON, eigenerFarbton).apply()
    }

    var dichte by mutableStateOf(
        runCatching { Listendichte.valueOf(prefs.getString(KEY_DICHTE, null) ?: "") }.getOrDefault(Listendichte.NORMAL)
    )
        private set

    fun dichteSetzen(neu: Listendichte) {
        dichte = neu
        prefs.edit().putString(KEY_DICHTE, neu.name).apply()
    }

    /** Eigene Anzeigenamen: Fach (Schlüssel) → Name. */
    var fachNamen by mutableStateOf(namenLesen())
        private set

    /** [name] leer oder null = zurück zum Namen aus dem Plan. */
    fun fachNameSetzen(fach: String, name: String?) {
        val schluessel = FachName.schluessel(fach)
        if (schluessel.isEmpty()) return
        val sauber = name?.trim().orEmpty()
        fachNamen = if (sauber.isEmpty()) fachNamen - schluessel else fachNamen + (schluessel to sauber)
        prefs.edit().putStringSet(KEY_NAMEN, fachNamen.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private fun namenLesen(): Map<String, String> =
        (prefs.getStringSet(KEY_NAMEN, emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.indexOf('=')
            if (i > 0 && i < s.length - 1) s.substring(0, i) to s.substring(i + 1) else null
        }.toMap()

    /** Fach (klein geschrieben) → Farbe als ARGB-Int. */
    var fachFarben by mutableStateOf(lesen())
        private set

    fun modusSetzen(neu: DesignModus) {
        modus = neu
        prefs.edit().putString(KEY_MODUS, neu.name).apply()
    }

    /** [farbe] null = zurück zur automatischen Farbe. */
    fun fachFarbeSetzen(fach: String, farbe: Int?) {
        val schluessel = FachName.schluessel(fach)
        if (schluessel.isEmpty()) return
        // Zurück auf automatisch: auch eine ältere Einstellung mit der genauen Schreibweise entfernen.
        fachFarben = if (farbe == null) fachFarben - schluessel - fach.trim().lowercase() else fachFarben + (schluessel to farbe)
        prefs.edit().putStringSet(KEY_FARBEN, fachFarben.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private fun lesen(): Map<String, Int> =
        (prefs.getStringSet(KEY_FARBEN, emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.lastIndexOf('=')
            val farbe = if (i > 0) s.substring(i + 1).toIntOrNull() else null
            if (farbe == null) null else s.substring(0, i) to farbe
        }.toMap()

    private companion object {
        private const val KEY_MODUS = "modus"
        private const val KEY_BLOECKE = "block_ansicht"
        private const val KEY_FARBEN = "fach_farben"
        private const val KEY_SYMBOLE = "fach_symbole"
        private const val KEY_THEMA = "farb_thema"
        private const val KEY_TON = "eigener_farbton"
        private const val KEY_DICHTE = "listendichte"
        private const val KEY_NAMEN = "fach_namen"
    }
}
