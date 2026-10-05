package com.nextlesson.app.data

private fun nenntAusfall(s: String) =
    listOf("fällt aus", "faellt aus", "entfällt", "entfaellt").any { s.contains(it, ignoreCase = true) }

/**
 * Der Hinweis der Schule zu dieser Stunde, aufs Wesentliche gekürzt. An Klausurtagen hängt
 * die Schule an jede Stunde die ganze Liste ("Klausur!; BIO3 … fällt aus; CHE1 … fällt aus; …").
 * Ausfälle anderer Kurse gehen dich nichts an und fliegen raus; was dich betrifft
 * ("Klausur!", Ausfall deines eigenen Kurses, allgemeine Hinweise) bleibt.
 */
fun Lesson.hinweisKurz(): String {
    val eigene = listOfNotNull(kursKuerzel, fach)
        .map { it.trim().lowercase() }
        .filter { k -> k.any { it.isLetterOrDigit() } }
        .toSet()
    return info.split(SATZ_GRENZE).map { it.trim() }.filter { it.isNotBlank() }.filter { satz ->
        val woerter = WORT.findAll(satz).map { it.value.lowercase() }.toSet()
        !nenntAusfall(satz) || woerter.any { it in eigene } || !KURS_MUSTER.containsMatchIn(satz)
    }.distinct().joinToString("; ")
}
