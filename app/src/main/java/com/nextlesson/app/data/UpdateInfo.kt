package com.nextlesson.app.data

/** Reine Logik des Update-Checks: Release-Tags ("build-31") und Versionsvergleich. */
object UpdateInfo {

    private val TAG = Regex("""^build-(\d+)$""", RegexOption.IGNORE_CASE)

    /** "build-31" → 31; alles andere → null. */
    fun buildAusTag(tag: String): Int? = TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun istNeuer(installiert: Int, verfuegbar: Int): Boolean = verfuegbar > installiert
}
