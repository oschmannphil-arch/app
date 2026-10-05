package com.nextlesson.app.data

import java.time.Duration
import java.time.LocalTime
import java.util.UUID

/** Ein Freund mit seinen Kursen (IDs wie bei der eigenen Kurswahl, z.B. "12/5::DEU1"). */
data class Freund(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kurse: Set<String>
)

/**
 * Freie Zeit im Tagesraster, z.B. 3.–4. Stunde. [von]/[bis] sind die Stundennummern (null,
 * wenn der Plan kein Zeitraster hergibt). [beginn]/[ende] schließen angrenzende Pausen ein.
 */
data class Freiblock(val von: Int?, val bis: Int?, val beginn: LocalTime, val ende: LocalTime) {
    /** "3. Std" bzw. "3.–4. Std"; null ohne Stundennummern. */
    val stundenText: String?
        get() = if (von == null || bis == null) null else stundenListe((von..bis).toList())
}

/** Wann hat man frei, wann haben zwei Leute gleichzeitig frei, und welche Stunden zusammen? */
object Freizeit {

    /** Belegte Zeiten eines Tagesplans, zusammengelegt. Ausgefallene Stunden sind frei. */
    fun belegt(plan: TagesPlan): List<Pair<LocalTime, LocalTime>> =
        zusammenlegen(
            plan.stunden.filter { !it.entfaellt }.mapNotNull { l ->
                val b = l.beginn
                val e = l.ende
                if (b != null && e != null && b.isBefore(e)) b to e else null
            }
        )

    /**
     * Freistunden eines Plans im Zeitraster der Schule – auch die am Anfang des Tages
     * (Unterricht erst ab der 3. Stunde), nicht aber die nach der letzten Stunde.
     * [ausfallIstFrei]: zählen ausgefallene Stunden als frei? Für die eigene Stundenliste
     * nein – die stehen dort ohnehin als "entfällt".
     * [abErsterStunde]: nur Lücken nach Beginn der ersten Stunde – vorher ist man "noch nicht
     * da" (für "gerade frei" und die Lücken einer Lehrkraft).
     */
    fun freiBloecke(
        plan: TagesPlan,
        raster: List<Zeitfenster>,
        ausfallIstFrei: Boolean = false,
        abErsterStunde: Boolean = false
    ): List<Freiblock> {
        val belegend = plan.stunden.filter { !ausfallIstFrei || !it.entfaellt }
        val letzte = belegend.mapNotNull { it.ende }.maxOrNull() ?: return emptyList()
        val fenster = mitZeiten(raster)
        val alle = if (fenster.isEmpty()) {
            // Ohne Raster: nur Lücken zwischen zwei Stunden – mit denselben belegenden Stunden.
            plan.copy(stunden = belegend).freistunden().map { (_, von, bis) -> Freiblock(null, null, von, bis) }
        } else {
            bloecke(fenster, belegend) { f -> !f.ende!!.isAfter(letzte) && belegend.none { belegt(it, f) } }
        }
        if (!abErsterStunde) return alle
        val erste = belegend.mapNotNull { it.beginn }.minOrNull() ?: return alle
        return alle.filter { !it.beginn.isBefore(erste) }
    }

    /**
     * Freistunden für die Stundenliste, je an der Stelle (Index in [TagesPlan.stunden]) der
     * Stunde, die danach kommt.
     */
    fun freiVor(plan: TagesPlan, raster: List<Zeitfenster>): Map<Int, Freiblock> =
        freiBloecke(plan, raster).mapNotNull { block ->
            val danach = plan.stunden.indexOfFirst { l -> l.beginn?.let { !it.isBefore(block.beginn) } == true }
            if (danach >= 0) danach to block else null
        }.toMap()

    /**
     * Zeiten, in denen beide frei haben (Ausfall zählt als frei) – solange beide danach noch
     * Unterricht haben. Freie Stunden am Tagesanfang zählen also mit, die nach dem Schluss
     * des einen nicht. Ohne Zeitraster: nur Lücken ab [minMinuten], während beide da sind.
     */
    fun gemeinsamFrei(
        a: TagesPlan,
        b: TagesPlan,
        raster: List<Zeitfenster> = emptyList(),
        minMinuten: Long = 30
    ): List<Freiblock> = gemeinsamFreiAlle(listOf(a, b), raster, minMinuten)

    /**
     * Wie [gemeinsamFrei], aber für beliebig viele Leute: Zeiten, in denen ALLE frei haben,
     * solange alle danach noch Unterricht haben. Wer an dem Tag gar keinen Unterricht hat,
     * ist nicht in der Schule – dann gibt es keine gemeinsame Zeit.
     */
    fun gemeinsamFreiAlle(
        plaene: List<TagesPlan>,
        raster: List<Zeitfenster> = emptyList(),
        minMinuten: Long = 30
    ): List<Freiblock> {
        if (plaene.isEmpty()) return emptyList()
        val fenster = mitZeiten(raster)
        if (fenster.isEmpty()) {
            return gemeinsamFreiOhneRaster(plaene, minMinuten).map { (von, bis) -> Freiblock(null, null, von, bis) }
        }
        val aktive = plaene.map { p -> p.stunden.filter { !it.entfaellt } }
        val bis = aktive.map { s -> s.mapNotNull { it.ende }.maxOrNull() ?: return emptyList() }.min()
        return bloecke(fenster, aktive.flatten()) { f ->
            !f.ende!!.isAfter(bis) && aktive.all { s -> s.none { belegt(it, f) } }
        }
    }

    /**
     * Der Freiblock, in dem man zur Uhrzeit [jetzt] gerade frei ist (Ausfall zählt als frei),
     * oder null – wer Unterricht hat, noch nicht oder nicht mehr in der Schule ist oder heute
     * keinen hat. Vor der ersten stattfindenden Stunde ist man "noch nicht da", nicht frei.
     */
    fun jetztFrei(plan: TagesPlan, raster: List<Zeitfenster>, jetzt: LocalTime): Freiblock? {
        return freiBloecke(plan, raster, ausfallIstFrei = true, abErsterStunde = true)
            .firstOrNull { !jetzt.isBefore(it.beginn) && jetzt.isBefore(it.ende) }
    }

    /** Ist man um [jetzt] in der Schule – zwischen Beginn der ersten und Ende der letzten Stunde? */
    fun inDerSchule(plan: TagesPlan, jetzt: LocalTime): Boolean {
        val aktiv = plan.stunden.filter { !it.entfaellt }
        val beginn = aktiv.mapNotNull { it.beginn }.minOrNull() ?: return false
        val ende = aktiv.mapNotNull { it.ende }.maxOrNull() ?: return false
        return !jetzt.isBefore(beginn) && jetzt.isBefore(ende)
    }

    /**
     * Stunden aus [a], die [b] ebenfalls hat – ohne ausgefallene. Derselbe Kurs gilt auch dann
     * als gemeinsam, wenn er in der Schule unter verschiedenen Klassen geführt wird.
     */
    fun gemeinsameStunden(a: TagesPlan, b: TagesPlan): List<Lesson> {
        val schluesselB = b.stunden.filter { !it.entfaellt }.mapTo(HashSet()) { zusammenKey(it) }
        return a.stunden.filter { !it.entfaellt && zusammenKey(it) in schluesselB }
    }

    /**
     * Schlüssel für "gleicher Unterricht": Stunde, Zeit, Kurs, Lehrkraft und Jahrgang. Die
     * Klasse innerhalb des Jahrgangs (12/5 vs. 12/6) und die Unterrichtsnummer zählen nicht –
     * ein Kurs, der in mehreren Klassen steht, ist derselbe. Gleiche Kürzel in verschiedenen
     * Jahrgängen (SPO1 in 11 und 12) sind dagegen verschiedene Kurse.
     * Ohne Kurskürzel (Klassenunterricht) gehört die ganze Klasse dazu.
     */
    fun zusammenKey(l: Lesson): String {
        val kurs = l.kursKuerzel?.trim()?.takeIf { it.isNotEmpty() }
        return if (kurs != null) {
            listOf(l.stunde, l.beginn, kurs.lowercase(), l.lehrer.trim().lowercase(), jahrgang(l.klasse))
        } else {
            listOf(l.stunde, l.beginn, l.klasse, l.fach.trim().lowercase())
        }.joinToString("|")
    }

    /** "12/5" → "12", "12a" → "12", "Q1/2" → "Q1". */
    internal fun jahrgang(klasse: String): String {
        val k = klasse.trim()
        return k.takeWhile { it.isDigit() }.ifEmpty { k.substringBefore('/').trim() }
    }

    private fun mitZeiten(raster: List<Zeitfenster>) =
        raster.filter { it.beginn != null && it.ende != null && it.beginn.isBefore(it.ende) }
            .sortedBy { it.beginn }

    /** Liegt die Stunde im Fenster? Über die Uhrzeit, sonst über die Stundennummer. */
    private fun belegt(l: Lesson, f: Zeitfenster): Boolean {
        val b = l.beginn
        val e = l.ende
        return if (b != null && e != null) b.isBefore(f.ende) && f.beginn!!.isBefore(e) else l.stunde == f.stunde
    }

    /**
     * Fasst aufeinanderfolgende freie Fenster zu Blöcken zusammen. Die Pausen davor und danach
     * gehören mit zur freien Zeit. Wo vorhanden, zählen die echten Zeiten der [stunden]: frei
     * ab Ende der Stunde davor bis Beginn der Stunde danach – das Raster ist der Mehrheitswert
     * der Schule, einzelne Klassen weichen davon ab.
     */
    private fun bloecke(fenster: List<Zeitfenster>, stunden: List<Lesson>, frei: (Zeitfenster) -> Boolean): List<Freiblock> {
        val out = ArrayList<Freiblock>()
        var i = 0
        while (i < fenster.size) {
            if (!frei(fenster[i])) { i++; continue }
            var j = i
            while (j + 1 < fenster.size && frei(fenster[j + 1])) j++
            val erstesFrei = fenster[i].beginn!!
            val letztesFrei = fenster[j].ende!!
            val rasterBeginn = minOf(if (i > 0) fenster[i - 1].ende!! else erstesFrei, erstesFrei)
            val rasterEnde = maxOf(if (j + 1 < fenster.size) fenster[j + 1].beginn!! else letztesFrei, letztesFrei)
            val stundeDavor = stunden.mapNotNull { it.ende }.filter { !it.isAfter(erstesFrei) }.maxOrNull()
            val stundeDanach = stunden.mapNotNull { it.beginn }.filter { !it.isBefore(letztesFrei) }.minOrNull()
            out += Freiblock(fenster[i].stunde, fenster[j].stunde, stundeDavor ?: rasterBeginn, stundeDanach ?: rasterEnde)
            i = j + 1
        }
        return out
    }

    private fun gemeinsamFreiOhneRaster(plaene: List<TagesPlan>, minMinuten: Long): List<Pair<LocalTime, LocalTime>> {
        val zeiten = plaene.map { belegt(it) }
        if (zeiten.any { it.isEmpty() }) return emptyList()
        val von = zeiten.maxOf { it.first().first }
        val bis = zeiten.minOf { it.last().second }
        if (!von.isBefore(bis)) return emptyList()

        val frei = ArrayList<Pair<LocalTime, LocalTime>>()
        var t = von
        for ((beginn, ende) in zusammenlegen(zeiten.flatten())) {
            if (!beginn.isBefore(bis)) break
            if (beginn.isAfter(t)) frei += t to beginn
            if (ende.isAfter(t)) t = ende
        }
        if (t.isBefore(bis)) frei += t to bis
        return frei.filter { Duration.between(it.first, it.second).toMinutes() >= minMinuten }
    }

    private fun zusammenlegen(zeiten: List<Pair<LocalTime, LocalTime>>): List<Pair<LocalTime, LocalTime>> {
        val out = ArrayList<Pair<LocalTime, LocalTime>>()
        for ((beginn, ende) in zeiten.sortedBy { it.first }) {
            val letzte = out.lastOrNull()
            if (letzte != null && !beginn.isAfter(letzte.second)) {
                out[out.lastIndex] = letzte.first to maxOf(letzte.second, ende)
            } else {
                out += beginn to ende
            }
        }
        return out
    }
}
