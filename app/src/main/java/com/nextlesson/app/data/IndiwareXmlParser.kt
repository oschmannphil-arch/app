package com.nextlesson.app.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser für die Indiware-mobil-XML-Dateien (mobdaten/PlanKl{JJJJMMTT}.xml).
 *
 * Schema:
 *
 *   <Kopf><zeitstempel>…</zeitstempel><DatumPlan>…</DatumPlan><schulnummer>…</schulnummer></Kopf>
 *   <Klassen>
 *     <Kl>                                        (manche Versionen: <Klasse>)
 *       <Kurz>JG12</Kurz>
 *       <Kurse>
 *         <Ku><KKz KLe="Müller">D1</KKz></Ku>      ← wählbare Kurse
 *       </Kurse>
 *       <Unterricht>
 *         <Ue><UeNr UeLe="Müller" UeFa="De" UeGr="D1">101</UeNr></Ue>
 *       </Unterricht>                              ← Nr → Kurs / Fach / Lehrer
 *       <Pl>                                       ← CONTAINER für den ganzen Tag
 *         <Std><St>1</St><Beginn>07:45</Beginn><Ende>08:30</Ende>
 *              <Fa FaAe="…">De</Fa><Le>Müller</Le><Ra RaAe="…">204</Ra>
 *              <Nr>101</Nr><If>…</If></Std>        ← EINE Stunde
 *       </Pl>
 *     </Kl>
 *   </Klassen>
 *
 * Zwei Fallstricke:
 *  1. <Pl> ist der Container, nicht die Stunde. Jedes Kind-Element darin ist eine Stunde.
 *  2. Das Kurskürzel steht nicht in der Stunde – nur <Nr>. Aufgelöst wird über <Unterricht>.
 *
 * Es werden immer ALLE Klassen geparst, damit der Schüler nur seine Kurse auswählen muss
 * und die Klasse daraus abgeleitet werden kann.
 */
object IndiwareXmlParser {

    private val KLASSE_TAGS = setOf("Kl", "Klasse")

    /**
     * Als "Ausfall" gilt alles, was kein Unterricht mit Lehrkraft vor Ort ist: Entfall,
     * Selbstbeschäftigung/EVA, Aufgaben (auch über Moodle), Distanz-/Online-Unterricht.
     *
     * Stichwörter am Wortanfang ("selbst…", "aufgab…", "moodle" …) greifen auch bei
     * Zusammensetzungen wie "Selbstbeschäftigung". "eva" und "frei" nur als ganzes Wort –
     * sonst träfen sie "Freitag", "Evangelisch" oder Namen wie "Evers". "Hausaufgaben"
     * zählt nicht, weil "aufgab" nur am Wortanfang gesucht wird.
     */
    private const val STICHWOERTER_AM_WORTANFANG =
        """(?<![\p{L}\p{N}])(selbst|eigenv|aufgab|moodle|online|distanz|zuhause|homeoffice|""" +
            """stillarbeit|freiarbeit|lernzeit)"""
    private val ENTFALL_WOERTER = Regex(
        STICHWOERTER_AM_WORTANFANG + """|(?<![\p{L}\p{N}])(eva|frei)(?![\p{L}\p{N}])"""
    )

    /**
     * Fürs Lehrer-Feld nur die langen Stichwörter: "Frei" oder "Eva" sind dort Nachnamen bzw.
     * Vornamen, keine Ausfall-Meldung.
     */
    private val ENTFALL_WOERTER_LEHRER = Regex(STICHWOERTER_AM_WORTANFANG)

    /** Platzhalter statt echter Namen ("LeAe", "LeÄnderung", "LeGeaendert") – kein Lehrer. */
    private val LEHRER_PLATZHALTER = Regex("""(?i)^le\s*(ae|änd\w*)$""")

    private fun istPlatzhalter(text: String): Boolean {
        val t = text.trim()
        return t.contains("geaendert", ignoreCase = true) || t.contains("geändert", ignoreCase = true) ||
            LEHRER_PLATZHALTER.matches(t)
    }

    /** Hinweise auf eine Klausur im Infotext der Stunde. */
    private val KLAUSUR_WOERTER = Regex("klausur|klassenarbeit")

    /** Eindeutige Wendungen – hier reicht ein Teilstring. */
    private val ENTFALL_PHRASEN = AUSFALL_PHRASEN + listOf("ausfall", "absage", "abgesagt", "zu hause")

    fun parse(input: InputStream, schulnummerFallback: String): GesamtPlan? {
        val doc = try {
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(input)
        } catch (e: Exception) {
            return null // kein gültiges XML (z.B. HTML-Fehlerseite)
        }

        val root = doc.documentElement ?: return null

        val kopfEl = root.kind("Kopf")
        val kopf = PlanKopf(
            datumPlan = kopfEl.kind("DatumPlan").textOrEmpty(),
            zeitstempel = kopfEl.kind("zeitstempel").textOrEmpty(),
            schulnummer = kopfEl.kind("schulnummer").textOrEmpty().ifBlank { schulnummerFallback },
            // <ZusatzInfo><ZiZeile>…</ZiZeile></ZusatzInfo>: Tageshinweise der Schule
            zusatzInfo = root.nachfahren("ZiZeile")
                .ifEmpty { root.kind("ZusatzInfo").kinder() }
                .map { it.textOrEmpty() }
                .filter { it.isNotBlank() }
        )

        val klassenEl = root.kind("Klassen")
        val kandidaten = klassenEl.kinder()
            .filter { it.tagName in KLASSE_TAGS }
            .ifEmpty { klassenEl.kinder() }

        val klassen = kandidaten.mapNotNull { klasseElement(it) }

        if (kopf.zeitstempel.isBlank() && klassen.isEmpty()) return null

        return GesamtPlan(kopf = kopf, klassen = klassen, klausuren = klausurTermine(root))
    }

    /**
     * Die Klausurliste: <Klausur> mit Kurs, Jahrgang, Beginn und Dauer (Minuten). Die Feldnamen
     * tragen je nach Schule ein Präfix ("KlKurs", "KlBeginn" …), darum zählt nur das Ende des
     * Namens. Fehlt der Jahrgang, kommt er aus der umgebenden Klasse.
     */
    private fun klausurTermine(root: Element): List<KlausurTermin> =
        root.nachfahren("Klausur").mapNotNull { el ->
            fun feld(ende: String) = el.kinder()
                .firstOrNull { it.tagName.endsWith(ende, ignoreCase = true) }.textOrEmpty()

            val kurs = feld("Kurs")
            val beginn = parseUhrzeit(feld("Beginn"))
            val dauer = feld("Dauer").toIntOrNull()
            if (kurs.isBlank() || beginn == null || dauer == null || dauer <= 0) return@mapNotNull null

            var jahrgang = feld("Jahrgang")
            if (jahrgang.isBlank()) {
                var eltern = el.parentNode
                while (eltern != null && !(eltern is Element && eltern.tagName in KLASSE_TAGS)) eltern = eltern.parentNode
                jahrgang = (eltern as? Element).kind("Kurz").textOrEmpty()
            }
            KlausurTermin(jahrgang, kurs, beginn, dauer)
        }.distinct()

    private fun klasseElement(kl: Element): KlassenPlan? {
        val name = kl.kind("Kurz").textOrEmpty()
        if (name.isBlank()) return null

        // <Unterricht>: Nr → Kursgruppe, plus Fach/Lehrer je Gruppe
        val nrZuKurs = HashMap<String, String>()
        val kursZuFach = HashMap<String, String>()
        val kursZuLehrer = HashMap<String, String>()
        kl.kind("Unterricht").nachfahren("UeNr").forEach { ueNr ->
            val nr = ueNr.textOrEmpty()
            val gruppe = ueNr.getAttribute("UeGr").orEmpty().trim()
            val fach = ueNr.getAttribute("UeFa").orEmpty().trim()
            val lehrer = ueNr.getAttribute("UeLe").orEmpty().trim()
            if (nr.isNotBlank() && gruppe.isNotBlank()) {
                nrZuKurs[nr] = gruppe
                if (fach.isNotBlank()) kursZuFach.putIfAbsent(gruppe, fach)
                if (lehrer.isNotBlank()) kursZuLehrer.putIfAbsent(gruppe, lehrer)
            }
        }

        // <Kurse>: wählbare Kurse
        val kurse = LinkedHashMap<String, KursInfo>()
        kl.kind("Kurse").nachfahren("KKz").forEach { kkz ->
            val kuerzel = kkz.textOrEmpty()
            if (kuerzel.isBlank()) return@forEach
            kurse[kuerzel] = KursInfo(
                klasse = name,
                kuerzel = kuerzel,
                fach = kursZuFach[kuerzel].orEmpty(),
                lehrer = kkz.getAttribute("KLe").orEmpty().trim()
                    .ifBlank { kursZuLehrer[kuerzel].orEmpty() }
            )
        }
        // Kurse, die nur in <Unterricht> vorkommen, ebenfalls anbieten
        nrZuKurs.values.distinct().forEach { gruppe ->
            kurse.getOrPut(gruppe) {
                KursInfo(name, gruppe, kursZuFach[gruppe].orEmpty(), kursZuLehrer[gruppe].orEmpty())
            }
        }

        // <Pl>: jedes Kind-Element ist eine Stunde
        val stunden = kl.kind("Pl").kinder().mapNotNull { std ->
            val stText = std.kind("St").textOrEmpty()
            val fachEl = std.kind("Fa") ?: std.kind("fach")
            val raumEl = std.kind("Ra") ?: std.kind("raum")
            val lehrerEl = std.kind("Le") ?: std.kind("lehrer")

            val fach = fachEl.textOrEmpty()
            val info = (std.kind("If") ?: std.kind("info") ?: std.kind("bemerkung")).textOrEmpty()
            val nr = (std.kind("Nr") ?: std.kind("nummer")).textOrEmpty()
            
            val lehrerRoh = lehrerEl.textOrEmpty()
            // Technische Platzhalter wie "LeGeaendert" ausfiltern, die sehen in der App hässlich aus.
            // (Nicht über "Änd" suchen: "Händel", "Brändle" … sind echte Lehrernamen.)
            val lehrer = if (istPlatzhalter(lehrerRoh)) "" else lehrerRoh

            if (stText.isBlank() && fach.isBlank() && info.isBlank()) return@mapNotNull null

            val raum = raumEl.textOrEmpty()
            val nurStriche = fach.isNotEmpty() && fach.all { !it.isLetterOrDigit() }
            val infoText = info.lowercase()
            val gesamtText = std.textContent.orEmpty().lowercase()
            
            // Priorität 1: Infotext-Keywords (ENTFALL)
            // Wir prüfen sowohl das Info-Feld als auch den gesamten Text der Stunde (gesamtText),
            // falls die Info in einem anderen Unter-Tag gelandet ist.
            // Kurze Wörter ("eva", "frei", "selbst") nur als GANZES Wort werten – als Teilstring
            // träfen sie "Freitag", "Evangelisch", Lehrernamen wie "Evers" usw. und markierten
            // normalen Unterricht fälschlich als Entfall.
            val hatEntfallInfo = ENTFALL_WOERTER.containsMatchIn(infoText) ||
                                 ENTFALL_WOERTER_LEHRER.containsMatchIn(lehrerRoh.lowercase()) ||
                                 ENTFALL_PHRASEN.any { infoText.contains(it) || gesamtText.contains(it) } ||
                                 (gesamtText.contains("kein") && gesamtText.contains("unterricht")) ||
                                 (gesamtText.contains("fällt") && gesamtText.contains("aus")) ||
                                 (gesamtText.contains("faellt") && gesamtText.contains("aus"))

            // Priorität 2: "---" in Fach oder Lehrer (ENTFALL)
            val hatStrich = fach == "---" || lehrer == "---"
            
            // Indiware-interne Flags & leere Fächer (ENTFALL)
            val istAusfallFlag = std.getAttribute("Ausfall") == "1" || 
                                 (std.getAttribute("Ae") == "1" && (fach.isBlank() || nurStriche)) ||
                                 fach.isBlank()

            // Klausuren stehen im Plan als Hinweistext ("Klausur"). Sie sind Präsenz-Termine und
            // dürfen nicht wegen Wörtern wie "Aufgaben" als Ausfall gelten – nur bei
            // ausdrücklichem Entfall ("Klausur entfällt").
            val istKlausur = KLAUSUR_WOERTER.containsMatchIn(gesamtText)
            val explizitEntfall = ENTFALL_PHRASEN.any { infoText.contains(it) } || hatStrich
            val entfaelltFinal = if (istKlausur) explizitEntfall
            else hatEntfallInfo || hatStrich || istAusfallFlag

            var fachGeaendert = fachEl.hatAttribut("FaAe")
            val raumGeaendertAttribut = raumEl.hatAttribut("RaAe")
            var lehrerGeaendert = lehrerEl.hatAttribut("LeAe")

            val hatAufgaben = infoText.contains("aufgab") || infoText.contains("erteilt") || 
                             infoText.contains("hausaufg") || infoText.contains("moodle")

            // Wenn Aufgaben explizit in Moodle stehen, soll die Änderung nicht 
            // als "Vertretung" (orange) markiert werden – das graue Aufgaben-Label reicht.
            if (infoText.contains("moodle")) {
                lehrerGeaendert = false
                fachGeaendert = false
            }

            // Manche Pläne tragen statt des alten Raums/Lehrers nur einen Platzhalter
            // ("RaGeaendert", "LeGeaendert") in RaAe/LeAe – der darf nicht angezeigt werden.
            fun echterWert(text: String?): String? = text?.trim()?.takeIf {
                it.isNotBlank() && !it.contains("geaendert", ignoreCase = true) &&
                    !it.contains("geändert", ignoreCase = true)
            }
            val originalRoom = if (raumGeaendertAttribut) echterWert(raumEl?.getAttribute("RaAe")) else null
            val originalTeacher = if (lehrerGeaendert) echterWert(lehrerEl?.getAttribute("LeAe")) else null

            // Priorität 3, 4 & 5: Status-Zuordnung und leeres Raumfeld-Schutz
            val raumGeaendert = raumGeaendertAttribut && raum.isNotBlank()
            
            val status = when {
                entfaelltFinal -> LessonStatus.ENTFALL
                lehrerGeaendert -> LessonStatus.VERTRETUNG
                raumGeaendert -> LessonStatus.RAUMAENDERUNG
                else -> LessonStatus.NORMAL
            }

            Lesson(
                stunde = stText.toIntOrNull() ?: 0,
                beginn = parseUhrzeit(std.kind("Beginn").textOrEmpty()),
                ende = parseUhrzeit(std.kind("Ende").textOrEmpty()),
                fach = fach,
                fachGeaendert = fachGeaendert,
                raum = raum,
                raumGeaendert = raumGeaendert,
                originalRoom = originalRoom,
                lehrer = lehrer,
                lehrerGeaendert = lehrerGeaendert,
                originalTeacher = originalTeacher,
                info = info,
                entfaellt = entfaelltFinal,
                hatAufgaben = hatAufgaben,
                status = status,
                unterrichtsNr = nr.ifBlank { null },
                kursKuerzel = nrZuKurs[nr],
                klasse = name,
                istKlausur = istKlausur
            )
        }.sortedBy { it.stunde }

        // Sammel-Eintrag, damit auch Klassen ohne Kurssystem wählbar sind – und als
        // Notausgang, falls die Kurszuordnung an einer Schule anders funktioniert.
        val ganzeKlasse = KursInfo(
            klasse = name,
            kuerzel = KursInfo.GANZE_KLASSE,
            fach = "",
            lehrer = ""
        )

        return KlassenPlan(
            klasse = name,
            stunden = stunden,
            kurse = listOf(ganzeKlasse) + kurse.values.sortedBy { it.kuerzel }
        )
    }

    // ---------- DOM-Helfer ----------

    private fun Element?.kinder(): List<Element> {
        if (this == null) return emptyList()
        val out = ArrayList<Element>()
        val nl = childNodes
        for (i in 0 until nl.length) {
            val n = nl.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) out += n as Element
        }
        return out
    }

    /** Erstes direktes Kind-Element mit diesem Tag-Namen (Case-Insensitive). */
    private fun Element?.kind(name: String): Element? =
        this.kinder().firstOrNull { it.tagName.equals(name, ignoreCase = true) }

    /** Alle Nachfahren mit diesem Tag-Namen (Case-Insensitive, beliebige Tiefe). */
    private fun Element?.nachfahren(name: String): List<Element> {
        if (this == null) return emptyList()
        val out = ArrayList<Element>()
        
        fun search(el: Element) {
            val children = el.childNodes
            for (i in 0 until children.length) {
                val n = children.item(i)
                if (n.nodeType == Node.ELEMENT_NODE) {
                    val child = n as Element
                    if (child.tagName.equals(name, ignoreCase = true)) out += child
                    search(child)
                }
            }
        }
        
        search(this)
        return out.distinct()
    }

    private fun Element?.textOrEmpty(): String = this?.textContent?.trim().orEmpty()

    private fun Element?.hatAttribut(attribut: String): Boolean =
        this != null && getAttribute(attribut).orEmpty().isNotBlank()

    /** Einmal angelegt statt pro Stunde neu – bei einem ganzen Schulplan sind das tausende. */
    private val UHRZEIT_FORMATE = listOf(DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("HH:mm"))

    private fun parseUhrzeit(text: String): LocalTime? {
        if (text.isBlank()) return null
        for (format in UHRZEIT_FORMATE) {
            try {
                return LocalTime.parse(text, format)
            } catch (_: DateTimeParseException) {
                // nächstes Format
            }
        }
        return null
    }
}
