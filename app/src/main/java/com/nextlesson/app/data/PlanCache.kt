package com.nextlesson.app.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Speichert die vom Server geladenen XML-Pläne lokal ab, damit die App auch ohne
 * Internetverbindung (z.B. im Flugmodus) funktioniert.
 */
class PlanCache(context: Context) {

    private val cacheDir = File(context.cacheDir, "plan_cache").apply { mkdirs() }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun getFile(schulnummer: String, datum: LocalDate): File {
        val fileName = "PlanKl_${schulnummer}_${datum.format(dateFormatter)}.xml"
        return File(cacheDir, fileName)
    }

    /**
     * Speichert die rohen XML-Bytes eines Plans. Bewusst Bytes statt String: die XML-Datei
     * deklariert oft ISO-8859-1, und ein Umweg über UTF-8 würde Umlaute beim Wiederlesen
     * zerstören (dann weichen "entfällt"-Erkennung und Entfall-Vergleich ab).
     * Geschrieben wird über eine Temp-Datei, damit nie eine halbe Datei im Cache liegt.
     */
    fun speichern(schulnummer: String, datum: LocalDate, xml: ByteArray) {
        try {
            val ziel = getFile(schulnummer, datum)
            val temp = File(cacheDir, ziel.name + ".tmp")
            temp.writeBytes(xml)
            if (!temp.renameTo(ziel)) {
                ziel.delete()
                temp.renameTo(ziel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class CachedPlan(val xml: ByteArray, val lastModified: Long)

    /** Lädt den lokal gespeicherten XML-Inhalt, falls vorhanden. */
    fun laden(schulnummer: String, datum: LocalDate): CachedPlan? {
        val file = getFile(schulnummer, datum)
        return if (file.exists()) {
            try {
                CachedPlan(file.readBytes(), file.lastModified())
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /** Löscht alte Pläne, die älter als [tage] sind. */
    fun aufraeumen(tage: Int = 14) {
        val cutoff = LocalDate.now().minusDays(tage.toLong())
        cacheDir.listFiles()?.forEach { file ->
            // Dateiname: PlanKl_12345678_20240908.xml
            val datePart = file.name.substringAfterLast("_").substringBefore(".xml")
            try {
                val date = LocalDate.parse(datePart, dateFormatter)
                if (date.isBefore(cutoff)) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Unbekanntes Format -> weg damit
                file.delete()
            }
        }
    }
}
