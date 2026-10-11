package com.nextlesson.app.data

/**
 * Ein passendes Symbol (Emoji) zu einem Fach – erkannt am Namen oder am Kürzel aus dem Plan
 * ("Ma", "Mathematik", "Deutsch GK", "D1"). Unbekannte Fächer bekommen keins.
 */
object FachSymbole {

    private class Regel(val symbol: String, val kuerzel: Set<String>, val anfaenge: List<String>)

    private val REGELN = listOf(
        Regel("🔢", setOf("m", "ma", "mat", "mathe"), listOf("mathem")),
        Regel("📖", setOf("d", "de", "deu"), listOf("deutsch")),
        Regel("🇬🇧", setOf("e", "en", "eng", "engl"), listOf("englisch")),
        Regel("🇫🇷", setOf("f", "fr", "fra", "frz", "franz"), listOf("franz")),
        Regel("🇪🇸", setOf("es", "spa", "span"), listOf("spanisch")),
        Regel("🇮🇹", setOf("it", "ital"), listOf("italien")),
        Regel("🇷🇺", setOf("ru", "rus"), listOf("russisch")),
        Regel("🏛️", setOf("l", "la", "lat"), listOf("latein")),
        Regel("⚛️", setOf("ph", "phy"), listOf("physik")),
        Regel("🧪", setOf("ch", "che", "chem"), listOf("chemie")),
        Regel("🧬", setOf("bi", "bio"), listOf("biolog")),
        Regel("📜", setOf("g", "ge", "gs", "ges", "gsch", "gesch", "geschi", "gschi"), listOf("gesch")),
        Regel("🌍", setOf("ek", "geo", "erd"), listOf("geogr", "erdkunde")),
        Regel("⚽", setOf("sp", "spo", "spt"), listOf("sport")),
        Regel("🎵", setOf("mu", "mus"), listOf("musik")),
        Regel("🎨", setOf("ku", "kun", "kue", "bk"), listOf("kunst")),
        Regel("💻", setOf("if", "inf", "info"), listOf("informatik")),
        Regel("✝️", setOf("re", "rel", "reli"), listOf("religion")),
        Regel("🤔", setOf("et", "eth"), listOf("ethik")),
        Regel("🗳️", setOf("sk", "pol", "soz", "sozi"), listOf("sozial", "politik", "gemeinschaftskunde")),
        Regel("💶", setOf("wi", "wirt", "wr"), listOf("wirtschaft")),
        Regel("🧠", setOf("psy", "psych"), listOf("psycholog")),
        Regel("💭", setOf("phil"), listOf("philosoph")),
        Regel("🔭", setOf("astr"), listOf("astro")),
        Regel("🔧", setOf("wtk", "tec"), listOf("technik", "werken")),
        Regel("📝", setOf("sem"), listOf("seminar"))
    )

    private val WORT = Regex("[a-zäöüß]+")

    /** Das Symbol für [fach] oder null, wenn keins passt. */
    fun fuer(fach: String): String? {
        val wort = WORT.find(fach.lowercase())?.value ?: return null
        return REGELN.firstOrNull { wort in it.kuerzel || it.anfaenge.any { a -> wort.startsWith(a) } }?.symbol
    }

    /** "🔢 Mathe" – oder nur der Name, wenn es kein Symbol gibt. */
    fun mitSymbol(fach: String): String = fuer(fach)?.let { "$it $fach" } ?: fach
}
