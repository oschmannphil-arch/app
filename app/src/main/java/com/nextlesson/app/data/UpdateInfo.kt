package com.nextlesson.app.data

/** Reine Logik des Update-Checks: Release-Tags ("build-31") und Versionsvergleich. */
object UpdateInfo {

    private val TAG = Regex("""^build-(\d+)$""", RegexOption.IGNORE_CASE)

    /** "build-31" → 31; alles andere → null. */
    fun buildAusTag(tag: String): Int? = TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Der höchste Build unter den Tags (Releases sind als "prerelease" markiert, "latest" greift nicht). */
    fun neuester(tags: List<String>): Int? = tags.mapNotNull(::buildAusTag).maxOrNull()

    fun istNeuer(installiert: Int, verfuegbar: Int): Boolean = verfuegbar > installiert
}
