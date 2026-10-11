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
                "Updates: Die App prüft im Hintergrund auf neue Versionen und meldet sie – ohne dass der Start länger dauert."
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
        ),
        PatchEintrag(
            id = 5,
            titel = "Update-Prüfung",
            punkte = listOf(
                "Die App sucht jetzt um 7, 15 und 20 Uhr im Hintergrund nach neuen Versionen (statt einmal täglich).",
                "Gibt es eine neue Version, zeigt die App beim Öffnen sofort den Hinweis zum Updaten."
            )
        )        ,
        PatchEintrag(
            id = 6,
            titel = "Notentracker",
            punkte = listOf(
                "Neu: Noten (0 bis 15 Punkte) mit dem Stern oben rechts auf \"Heute\" und \"Klausuren\".",
                "Durchschnitt pro Fach und gesamt, Zeugnispunkte, nach Halbjahr gefiltert; Klausuren und mündliche Noten mit einstellbarem Gewicht.",
                "Rechner: Zeigt, wie viele Punkte du in der nächsten Klausur oder mündlich für dein Ziel brauchst.",
                "Geschriebene Klausuren aus dem Plan bieten dir direkt das Eintragen der Note an. Alles bleibt nur auf deinem Handy.",
                "Klasse 5–10 rechnet mit Noten 1–6, ab Klasse 11 mit Punkten 0–15 – die App erkennt das an deinen gewählten Kursen."
            )
        )        ,
        PatchEintrag(
            id = 7,
            titel = "Symbole und Sperrbildschirm",
            punkte = listOf(
                "Fach-Symbole: Vor dem Fach steht ein passendes Symbol, z. B. 🔢 Mathe oder 📖 Deutsch. Abschaltbar unter Einstellungen → Darstellung.",
                "Das Widget lässt sich auch auf dem Sperrbildschirm platzieren – sofern dein Gerät das unterstützt."
            )
        )        ,
        PatchEintrag(
            id = 8,
            titel = "Noten, Wochen und Schnellzugriff",
            punkte = listOf(
                "Noten: Notenziel pro Fach (wird gespeichert), „Was wäre wenn …\"-Prognose, Vergleich mit dem Vorhalbjahr, Zeugnis-Vorschau und – ab Klasse 11 – ein Abi-Rechner (grobe Schätzung).",
                "Hausaufgaben: Fortschrittsbalken „x von y erledigt\".",
                "App-Symbol: Lange drücken öffnet Suche, Noten, Klausuren oder Aufgaben direkt.",
                "Einstellungen: Unten unter dem Build steht jetzt der Update-Verlauf („Was ist neu?\")."
            )
        )        ,
        PatchEintrag(
            id = 9,
            titel = "Mehr Übersicht",
            punkte = listOf(
                "Farbthemen: Mocha, Ozean, Wald, Rose oder Violett (Einstellungen → Darstellung).",
                "Ferien-Countdown auf der Startseite (Termine ohne Gewähr).",
                "Ausfall-Statistik: wie viele Stunden dieses Halbjahr ausfielen (Einstellungen).",
                "Hausaufgaben: Tafel fotografieren und das Foto an die Aufgabe hängen.",
                "Suche: Lehrer-Steckbrief mit Fächern, Jahrgängen und Räumen.",
                "Kurse: Hat sich deine Kursliste geändert (z. B. neues Halbjahr), weist die App darauf hin."
            )
        )        ,
        PatchEintrag(
            id = 10,
            titel = "Tage durchwischen",
            punkte = listOf(
                "Heute: Nach links oder rechts wischen (oder die Pfeile oben) wechselt zum nächsten bzw. vorigen Schultag – auch über Wochen hinweg.",
                "Blockansicht: Eine Klausur über mehrere Stunden (z. B. 3.–6.) ist jetzt ein einziger Block."
            )
        )        ,
        PatchEintrag(
            id = 11,
            titel = "Fehler behoben",
            punkte = listOf(
                "Farbthemen: Die Namen passen jetzt zu den Farben (Wald ist grün, Violett violett).",
                "GEO2 und Geo (Oberstufe und normales Fach) zählen als ein Fach – bei Lehrkräften, Farben und Noten.",
                "Farbe pro Fach zeigt alle deine Kurse.",
                "Tafel-Foto geht nicht mehr verloren und lässt sich wieder entfernen.",
                "Tage durchwischen: zuverlässiger, auch beim Aktualisieren eines anderen Tages.",
                "Kurs-Hinweis nach dem Halbjahreswechsel meldet sich erst, wenn der Befund sicher ist.",
                "Noten: Abi-Rechner mit der Formel Durchschnitt × 40; für Klasse 5–10 ein passenderer Startwert (35 %)."
            )
        )
    )

    val neuesteId: Int get() = alle.maxOfOrNull { it.id } ?: 0

    /** Die Einträge, die nach [gesehenBis] dazugekommen sind – neueste zuerst, höchstens [max]. */
    fun ungesehen(gesehenBis: Int, max: Int = 3, liste: List<PatchEintrag> = alle): List<PatchEintrag> =
        liste.filter { it.id > gesehenBis }.sortedByDescending { it.id }.take(max)
}
