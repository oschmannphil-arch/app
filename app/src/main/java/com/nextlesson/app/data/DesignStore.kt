package com.nextlesson.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DesignModus(val anzeige: String) {
    SYSTEM("System"), HELL("Hell"), DUNKEL("Dunkel")
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

    /** Fach (klein geschrieben) → Farbe als ARGB-Int. */
    var fachFarben by mutableStateOf(lesen())
        private set

    fun modusSetzen(neu: DesignModus) {
        modus = neu
        prefs.edit().putString(KEY_MODUS, neu.name).apply()
    }

    /** [farbe] null = zurück zur automatischen Farbe. */
    fun fachFarbeSetzen(fach: String, farbe: Int?) {
        val schluessel = fach.trim().lowercase()
        if (schluessel.isEmpty()) return
        fachFarben = if (farbe == null) fachFarben - schluessel else fachFarben + (schluessel to farbe)
        prefs.edit().putStringSet(KEY_FARBEN, fachFarben.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private fun lesen(): Map<String, Int> =
        (prefs.getStringSet(KEY_FARBEN, emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.lastIndexOf('=')
            val farbe = if (i > 0) s.substring(i + 1).toIntOrNull() else null
            if (farbe == null) null else s.substring(0, i) to farbe
        }.toMap()

    companion object {
        /** Die laufende Instanz, damit [com.nextlesson.app.ui.theme.fachFarbe] die eigenen Farben kennt. */
        var aktuell: DesignStore? = null

        private const val KEY_MODUS = "modus"
        private const val KEY_FARBEN = "fach_farben"
    }
}
