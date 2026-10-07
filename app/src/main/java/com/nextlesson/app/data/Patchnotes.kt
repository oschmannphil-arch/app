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
        )
    )

    val neuesteId: Int get() = alle.maxOfOrNull { it.id } ?: 0

    /** Die Einträge, die nach [gesehenBis] dazugekommen sind – neueste zuerst, höchstens [max]. */
    fun ungesehen(gesehenBis: Int, max: Int = 3): List<PatchEintrag> =
        alle.filter { it.id > gesehenBis }.sortedByDescending { it.id }.take(max)
}
