package com.nextlesson.app.data

/** Wendungen, mit denen die Schule einen Ausfall meldet – eine Liste für Parser, Klausur-Logik und Hinweise. */
internal val AUSFALL_PHRASEN = listOf("fällt aus", "faellt aus", "entfällt", "entfaellt")

/** Meldet dieser Satz einen Ausfall ("BIO3 Herr X fällt aus")? */
internal fun nenntAusfall(satz: String) = AUSFALL_PHRASEN.any { satz.contains(it, ignoreCase = true) }

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

/** Name zum Anzeigen: das Fach, sonst das Kurskürzel; null, wenn der Plan beides leer lässt ("---"). */
fun Lesson.anzeigeName(): String? =
    fach.takeIf { f -> f.any { it.isLetterOrDigit() } } ?: kursKuerzel?.takeIf { it.isNotBlank() }
