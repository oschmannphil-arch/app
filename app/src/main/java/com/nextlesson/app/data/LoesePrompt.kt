package com.nextlesson.app.data

/** Der Auftrag an die KI-App beim "Mit Claude lösen" – je nach Fach passend formuliert. */
object LoesePrompt {

    private const val ALLGEMEIN = "Löse diese Hausaufgabe Schritt für Schritt und erkläre den Weg verständlich."

    private const val SCHLUSS = " Wenn etwas auf dem Foto unleserlich ist, sag es, statt zu raten."

    private fun satz(fach: String, vararg namen: String) = namen.any { fach == it || (it.length >= 3 && fach.startsWith(it)) }

    /** Der Prompt für [fach] (Schreibweise egal: "MAT2", "Mathe", "mat"); [aufgabe] ist der Aufgabentext. */
    fun fuer(fach: String, aufgabe: String): String {
        val f = FachName.schluessel(fach)
        val text = when {
            satz(f, "mat", "math") ->
                "Löse diese Mathe-Hausaufgabe Schritt für Schritt. Zeige jeden Rechenschritt, nenne die verwendete Formel oder Regel und mach am Ende eine kurze Probe."
            satz(f, "phy") ->
                "Löse diese Physik-Aufgabe in dieser Reihenfolge: gegeben, gesucht, Formel, Einsetzen mit Einheiten, Ergebnis mit Einheit."
            satz(f, "che") ->
                "Löse diese Chemie-Aufgabe Schritt für Schritt. Stelle Reaktionsgleichungen ausgeglichen auf, rechne mit Einheiten und erkläre kurz die Chemie dahinter."
            satz(f, "bio") ->
                "Beantworte diese Biologie-Aufgabe in vollständigen Sätzen mit den richtigen Fachbegriffen und erkläre die Zusammenhänge kurz."
            satz(f, "inf", "info") ->
                "Löse diese Informatik-Aufgabe. Zeig die Lösung bzw. den Code, erkläre ihn Schritt für Schritt und gib ein kurzes Beispiel."
            satz(f, "deu") ->
                "Bearbeite diese Deutsch-Aufgabe in gutem, sachlichem Deutsch. Belege Aussagen mit Textstellen und erkläre kurz deine Überlegungen."
            satz(f, "eng") ->
                "Bearbeite diese Englisch-Aufgabe. Schreibe die Lösung auf Englisch und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln)."
            satz(f, "fra", "franz") ->
                "Bearbeite diese Französisch-Aufgabe. Schreibe die Lösung auf Französisch und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln)."
            satz(f, "spa", "span") ->
                "Bearbeite diese Spanisch-Aufgabe. Schreibe die Lösung auf Spanisch und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln)."
            satz(f, "rus") ->
                "Bearbeite diese Russisch-Aufgabe. Schreibe die Lösung auf Russisch und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln)."
            satz(f, "lat") ->
                "Übersetze bzw. bearbeite diese Latein-Aufgabe. Gib die Übersetzung Satz für Satz und erkläre die Grammatik (Kasus, Tempus, Konstruktionen)."
            satz(f, "ges") ->
                "Beantworte diese Geschichts-Aufgabe in klaren Sätzen. Nenne Zeitraum, Ursachen und Folgen und erkläre die Zusammenhänge."
            satz(f, "pol", "sozi", "sk", "gk", "wir") ->
                "Beantworte diese Aufgabe sachlich in klaren Sätzen. Erkläre die Begriffe, stelle verschiedene Sichtweisen dar und begründe."
            satz(f, "geo", "erd") ->
                "Beantworte diese Geografie-Aufgabe in klaren Sätzen. Nenne Ort, Ursachen und Zusammenhänge und erkläre die Fachbegriffe."
            satz(f, "rel", "eth", "phi") ->
                "Beantworte diese Aufgabe. Stelle die Positionen verständlich dar und begründe eine eigene Einschätzung nachvollziehbar."
            satz(f, "kun", "mus") ->
                "Bearbeite diese Aufgabe. Beschreibe und analysiere mit den passenden Fachbegriffen und begründe deine Deutung."
            satz(f, "spo") ->
                "Beantworte diese Sport-Aufgabe in klaren Sätzen und erkläre kurz die Regeln bzw. den Hintergrund."
            else -> ALLGEMEIN
        }
        return buildString {
            append(text).append(SCHLUSS)
            if (aufgabe.isNotBlank()) append("\n\nAufgabe: ").append(aufgabe.trim())
        }
    }
}
