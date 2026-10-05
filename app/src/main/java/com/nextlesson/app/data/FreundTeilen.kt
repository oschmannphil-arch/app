package com.nextlesson.app.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Kurse als Link verschicken: "nextlesson://freund?name=Anna&kurse=12%2F5%3A%3ADEU1,12%2F5%3A%3AMAT2".
 * Wer den Link bekommt, tippt ihn an (oder fügt ihn in den Einstellungen ein) und hat den
 * Freund samt Kursen, ohne etwas von Hand anzukreuzen.
 */
object FreundTeilen {

    const val SCHEMA = "nextlesson"
    private val LINK = Regex("nextlesson://freund\\?\\S+", RegexOption.IGNORE_CASE)

    fun link(name: String, kurse: Set<String>): String =
        "$SCHEMA://freund?name=${kodieren(name.trim())}&kurse=" + kurse.sorted().joinToString(",") { kodieren(it) }

    /** Text zum Verschicken (Messenger): kurze Erklärung plus Link. */
    fun nachricht(name: String, kurse: Set<String>): String =
        "Meine Kurse für die Stundenplan-App: ${link(name, kurse)}\n" +
            "(In der App: Einstellungen → Freunde → \"Link einfügen\")"

    /** Findet einen Link irgendwo in [text] (auch in einer ganzen Nachricht) und liest ihn. */
    fun lesen(text: String?): Freund? {
        val treffer = LINK.find(text.orEmpty())?.value ?: return null
        val query = treffer.substringAfter('?')
        val felder = query.split('&').mapNotNull { teil ->
            val i = teil.indexOf('=')
            if (i < 0) null else teil.substring(0, i).lowercase() to teil.substring(i + 1)
        }.toMap()
        val kurse = felder["kurse"].orEmpty().split(',')
            .filter { it.isNotBlank() }
            .mapNotNullTo(LinkedHashSet()) { dekodieren(it)?.takeIf { k -> k.contains("::") } }
        if (kurse.isEmpty()) return null
        return Freund(name = dekodieren(felder["name"].orEmpty()).orEmpty().trim(), kurse = kurse)
    }

    private fun kodieren(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun dekodieren(s: String): String? = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrNull()
}
