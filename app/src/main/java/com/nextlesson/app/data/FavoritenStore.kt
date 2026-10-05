package com.nextlesson.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Gemerkte Lehrkräfte und Räume der Suche ("L:Weis", "R:204"). */
class FavoritenStore(context: Context) {

    private val prefs = context.getSharedPreferences("favoriten", Context.MODE_PRIVATE)

    private val _favoriten = MutableStateFlow(lesen())
    val favoriten: StateFlow<List<Treffer>> = _favoriten.asStateFlow()

    fun umschalten(t: Treffer) {
        val liste = _favoriten.value
        val neu = if (t in liste) liste - t else liste + t
        _favoriten.value = neu
        prefs.edit().putStringSet(KEY, neu.map(::schluessel).toSet()).apply()
    }

    private fun lesen(): List<Treffer> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            .mapNotNull { s ->
                val name = s.drop(2)
                when {
                    name.isBlank() -> null
                    s.startsWith("L:") -> Treffer.Lehrer(name)
                    s.startsWith("R:") -> Treffer.Raum(name)
                    else -> null
                }
            }
            .sortedWith(compareBy({ it is Treffer.Raum }, { it.name.lowercase() }))

    private fun schluessel(t: Treffer) = (if (t is Treffer.Lehrer) "L:" else "R:") + t.name

    private companion object {
        const val KEY = "favoriten"
    }
}
