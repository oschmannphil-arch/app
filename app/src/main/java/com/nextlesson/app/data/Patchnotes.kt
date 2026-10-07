package com.nextlesson.app.data

/**
 * Ein Eintrag der Patchnotes. [id] zählt einfach hoch (unabhängig von der Build-Nummer der CI) –
 * so zeigt die App nach einem Update genau die Einträge, die der Nutzer noch nicht gesehen hat.
 */
data class PatchEintrag(val id: Int, val titel: String, val punkte: List<String>)

object Patchnotes {

    /** Neue Einträge immer mit der nächsten [PatchEintrag.id] ans Ende hängen. */
    val alle: List<PatchEintrag> = listOf(
        PatchEintrag(
            id = 1,
            titel = "Neu in dieser Version",
            punkte = listOf(
                "Klausuren: Die Klausurliste deines Plans wird ausgewertet. Klausurstunden erscheinen als Klausur statt als Ausfall.",
                "Eine Klausur gilt nach dem Ende ihrer Stunde als geschrieben, nicht erst um Mitternacht.",
                "App-Update direkt in der App: Einstellungen → App-Update, außerdem ein Hinweis beim Öffnen.",
                "Der installierte Build steht ganz unten in den Einstellungen.",
                "Nach einem Update siehst du hier die Neuerungen – einmalig."
            )
        ),
        PatchEintrag(
            id = 2,
            titel = "Fehler behoben",
            punkte = listOf(
                "Lehrer mit ä im Namen (z. B. Händel) werden wieder angezeigt; ein Lehrer namens \"Frei\" macht keinen Unterricht mehr zum Ausfall.",
                "Hinweise wie \"keine Pause\" oder \"Hausaufgaben\" lösen keinen falschen Ausfall mehr aus.",
                "Klausurliste: Kurse, die dort nicht stehen, gelten nicht mehr als Klausur.",
                "Update-Download: Bei einem Fehler kannst du es direkt noch einmal versuchen; die Datei wird vor dem Installieren geprüft.",
                "Eine Lehrkraft, die in mehreren Klassen steht, wird in der Suche nicht mehr doppelt gezählt."
            )
        ),
        PatchEintrag(
            id = 3,
            titel = "Neue Funktionen",
            punkte = listOf(
                "Ferien: Statt einer Fehlermeldung siehst du, wann der nächste Plan bereitsteht.",
                "Freunde: Die Karte zeigt, wer in der Schule ist, wer noch kommt und wer schon fertig ist. Neu: Meldung, wenn ein Freund eine gemeinsame Freistunde mit dir bekommt (abschaltbar in den Einstellungen).",
                "Suche: \"Zuletzt gesucht\", freie Räume zu einer oder mehreren Stunden und der Hinweis, wenn eine Lehrkraft nicht im Plan steht oder alle Stunden ausfallen.",
                "Widget: Neuer Knopf \"Suche\" öffnet direkt die Suche mit Tastatur.",
                "Updates: Die App prüft einmal täglich im Hintergrund und meldet neue Versionen – ohne dass der Start länger dauert."
            )
        ),
        PatchEintrag(
            id = 4,
            titel = "Fehler behoben",
            punkte = listOf(
                "Ein vom Hintergrund-Check gefundenes Update erscheint auch dann, wenn die App schon offen im Hintergrund lag.",
                "Ferien-Hinweis: Ein fehlgeschlagener Abruf wird nicht mehr als \"nichts eingestellt\" gemerkt. Sind Pläne da, aber ohne deine Kurse, führt die App zur Kurswahl.",
                "\"Freund hat auch frei\": keine Meldung mehr, wenn eine bekannte freie Zeit nur kleiner oder geteilt wird; Meldungen verschiedener Freunde überschreiben sich nicht.",
                "Widget: Der Suche-Knopf steht nur in den breiteren Layouts, nach dem Wählen eines Treffers schließt sich die Tastatur."
            )
        )
    )

    val neuesteId: Int get() = alle.maxOfOrNull { it.id } ?: 0

    /** Die Einträge, die nach [gesehenBis] dazugekommen sind – neueste zuerst, höchstens [max]. */
    fun ungesehen(gesehenBis: Int, max: Int = 3, liste: List<PatchEintrag> = alle): List<PatchEintrag> =
        liste.filter { it.id > gesehenBis }.sortedByDescending { it.id }.take(max)
}
