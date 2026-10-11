package com.nextlesson.app.data

/** Reine Logik des Such-Verlaufs (ohne Android, damit testbar). */
object SuchVerlaufLogik {
    const val MAX = 8

    fun ausSchluessel(s: String): Treffer? = Treffer.ausSchluessel(s)

    /** [t] nach vorn, ein früheres Vorkommen entfällt, höchstens [MAX] Einträge. */
    fun einfuegen(liste: List<Treffer>, t: Treffer): List<Treffer> =
        (listOf(t) + liste.filter { it != t }).take(MAX)

    fun entfernen(liste: List<Treffer>, t: Treffer): List<Treffer> = liste.filter { it != t }
}
