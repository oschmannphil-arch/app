package com.nextlesson.app.data

/** Reine Logik des Such-Verlaufs (ohne Android, damit testbar). */
object SuchVerlaufLogik {
    const val MAX = 8

    fun ausSchluessel(s: String): Treffer? {
        val name = s.drop(2)
        return when {
            name.isBlank() -> null
            s.startsWith("L:") -> Treffer.Lehrer(name)
            s.startsWith("R:") -> Treffer.Raum(name)
            else -> null
        }
    }

    /** [t] nach vorn, ein früheres Vorkommen entfällt, höchstens [MAX] Einträge. */
    fun einfuegen(liste: List<Treffer>, t: Treffer): List<Treffer> =
        (listOf(t) + liste.filter { it != t }).take(MAX)
}
