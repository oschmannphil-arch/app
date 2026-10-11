package com.nextlesson.app.data

import java.time.LocalTime

/** Reine Logik zu "neue gemeinsame Freistunde bei einem Freund" – ohne Android, damit testbar. */
object FreundFreiLogik {

    fun schluessel(b: Freiblock) = "${b.beginn}-${b.ende}"

    private fun intervall(schluessel: String): Pair<LocalTime, LocalTime>? {
        val teile = schluessel.split('-')
        if (teile.size != 2) return null
        return runCatching { LocalTime.parse(teile[0]) to LocalTime.parse(teile[1]) }.getOrNull()
    }

    /**
     * Blöcke, deren Zeit NEU ist: Was sich mit einem bekannten Block überlappt, zählt nicht – auch
     * nicht, wenn er größer, kleiner oder in zwei Teile geteilt wurde (die freie Zeit gab es schon).
     * Ohne bisherigen Stand ([alt] = null) wird nichts gemeldet.
     */
    fun neu(alt: Set<String>?, aktuell: List<Freiblock>): List<Freiblock> {
        if (alt == null) return emptyList()
        val bekannt = alt.mapNotNull { intervall(it) }
        return aktuell.filter { b -> bekannt.none { (von, bis) -> b.beginn.isBefore(bis) && von.isBefore(b.ende) } }
    }
}
