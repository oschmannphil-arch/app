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

    /** Speichert den rohen XML-Inhalt eines Plans. */
    fun speichern(schulnummer: String, datum: LocalDate, xml: String) {
        try {
            getFile(schulnummer, datum).writeText(xml)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class CachedPlan(val xml: String, val lastModified: Long)

    /** Lädt den lokal gespeicherten XML-Inhalt, falls vorhanden. */
    fun laden(schulnummer: String, datum: LocalDate): CachedPlan? {
        val file = getFile(schulnummer, datum)
        return if (file.exists()) {
            try {
                CachedPlan(file.readText(), file.lastModified())
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
