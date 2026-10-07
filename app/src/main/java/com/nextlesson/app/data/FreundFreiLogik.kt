package com.nextlesson.app.data

/** Reine Logik zu "neue gemeinsame Freistunde bei einem Freund" – ohne Android, damit testbar. */
object FreundFreiLogik {

    fun schluessel(b: Freiblock) = "${b.beginn}-${b.ende}"

    /** Blöcke, die [alt] noch nicht kannte; ohne bisherigen Stand ([alt] = null) keine. */
    fun neu(alt: Set<String>?, aktuell: List<Freiblock>): List<Freiblock> =
        if (alt == null) emptyList() else aktuell.filter { schluessel(it) !in alt }
}
