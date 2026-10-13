package com.nextlesson.app.data

/** Der Auftrag an die KI-App beim "Mit Claude lösen" – je nach Fach passend formuliert. */
object LoesePrompt {

    private const val ALLGEMEIN = "Löse diese Hausaufgabe Schritt für Schritt und erkläre den Weg verständlich."

    private const val SCHLUSS = " Wenn etwas auf dem Foto unleserlich ist, sag es, statt zu raten."

    /** Passt [fach] (Schlüssel, klein): genau eins der [kuerzel] oder beginnt mit einem der [anfaenge]. */
    private fun passt(fach: String, kuerzel: Set<String>, anfaenge: List<String> = emptyList()) =
        fach in kuerzel || anfaenge.any { fach.startsWith(it) }

    private fun sprache(name: String) =
        "Bearbeite diese $name-Aufgabe. Schreibe die Lösung auf $name und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln)."

    /** Der Prompt für [fach] (Schreibweise egal: "MAT2", "Ma", "Mathematik"); [aufgabe] ist der Aufgabentext. */
    fun fuer(fach: String, aufgabe: String): String {
        val f = FachName.schluessel(fach)
        // Kürzel wie bei den Fach-Symbolen, damit beides dieselben Fächer erkennt.
        val text = when {
            passt(f, setOf("m", "ma", "mat", "mathe"), listOf("mathem")) ->
                "Löse diese Mathe-Hausaufgabe Schritt für Schritt. Zeige jeden Rechenschritt, nenne die verwendete Formel oder Regel und mach am Ende eine kurze Probe."
            passt(f, setOf("ph", "phy"), listOf("physik")) ->
                "Löse diese Physik-Aufgabe in dieser Reihenfolge: gegeben, gesucht, Formel, Einsetzen mit Einheiten, Ergebnis mit Einheit."
            passt(f, setOf("ch", "che", "chem"), listOf("chemie")) ->
                "Löse diese Chemie-Aufgabe Schritt für Schritt. Stelle Reaktionsgleichungen ausgeglichen auf, rechne mit Einheiten und erkläre kurz die Chemie dahinter."
            passt(f, setOf("bi", "bio"), listOf("biolog")) ->
                "Beantworte diese Biologie-Aufgabe in vollständigen Sätzen mit den richtigen Fachbegriffen und erkläre die Zusammenhänge kurz."
            passt(f, setOf("if", "inf", "info"), listOf("informatik")) ->
                "Löse diese Informatik-Aufgabe. Zeig die Lösung bzw. den Code, erkläre ihn Schritt für Schritt und gib ein kurzes Beispiel."
            passt(f, setOf("d", "de", "deu"), listOf("deutsch")) ->
                "Bearbeite diese Deutsch-Aufgabe in gutem, sachlichem Deutsch. Belege Aussagen mit Textstellen und erkläre kurz deine Überlegungen."
            passt(f, setOf("e", "en", "eng", "engl"), listOf("englisch")) -> sprache("Englisch")
            passt(f, setOf("f", "fr", "fra", "frz", "franz"), listOf("franz")) -> sprache("Französisch")
            passt(f, setOf("es", "spa", "span"), listOf("spanisch")) -> sprache("Spanisch")
            passt(f, setOf("ru", "rus"), listOf("russisch")) -> sprache("Russisch")
            passt(f, setOf("l", "la", "lat"), listOf("latein")) ->
                "Übersetze bzw. bearbeite diese Latein-Aufgabe. Gib die Übersetzung Satz für Satz und erkläre die Grammatik (Kasus, Tempus, Konstruktionen)."
            passt(f, setOf("g", "ge", "gs", "ges", "gsch", "gesch", "geschi", "gschi"), listOf("gesch")) ->
                "Beantworte diese Geschichts-Aufgabe in klaren Sätzen. Nenne Zeitraum, Ursachen und Folgen und erkläre die Zusammenhänge."
            passt(f, setOf("sk", "gk", "pol", "soz", "sozi", "wi", "wirt", "wr"), listOf("sozial", "politik", "gemeinschaftskunde", "wirtschaft")) ->
                "Beantworte diese Aufgabe sachlich in klaren Sätzen. Erkläre die Begriffe, stelle verschiedene Sichtweisen dar und begründe."
            passt(f, setOf("ek", "geo", "erd"), listOf("geogr", "erdkunde")) ->
                "Beantworte diese Geografie-Aufgabe in klaren Sätzen. Nenne Ort, Ursachen und Zusammenhänge und erkläre die Fachbegriffe."
            passt(f, setOf("re", "rel", "reli", "et", "eth", "phil"), listOf("religion", "ethik", "philosoph")) ->
                "Beantworte diese Aufgabe. Stelle die Positionen verständlich dar und begründe eine eigene Einschätzung nachvollziehbar."
            passt(f, setOf("ku", "kun", "kue", "bk", "mu", "mus"), listOf("kunst", "musik")) ->
                "Bearbeite diese Aufgabe. Beschreibe und analysiere mit den passenden Fachbegriffen und begründe deine Deutung."
            passt(f, setOf("sp", "spo", "spt"), listOf("sport")) ->
                "Beantworte diese Sport-Aufgabe in klaren Sätzen und erkläre kurz die Regeln bzw. den Hintergrund."
            else -> ALLGEMEIN
        }
        return buildString {
            append(text).append(SCHLUSS)
            if (aufgabe.isNotBlank()) append("\n\nAufgabe: ").append(aufgabe.trim())
        }
    }
}
