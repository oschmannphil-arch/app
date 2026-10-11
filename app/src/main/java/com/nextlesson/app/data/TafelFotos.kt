package com.nextlesson.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Fotos zu Hausaufgaben (z. B. das Tafelbild). Die Kamera schreibt zuerst in eine Zwischendatei im
 * Cache; beim Speichern der Aufgabe wird das Bild verkleinert und unter der Aufgaben-ID abgelegt.
 * Alles bleibt auf dem Gerät.
 */
object TafelFotos {

    private const val MAX_KANTE = 1600
    private const val QUALITAET = 85

    private fun cacheOrdner(context: Context) = File(context.cacheDir, "tafel").apply { mkdirs() }
    private fun fotoOrdner(context: Context) = File(context.filesDir, "tafel").apply { mkdirs() }

    /** Hierhin schreibt die Kamera, bevor die Aufgabe gespeichert ist. */
    fun zwischendatei(context: Context): File = File(cacheOrdner(context), "neu.jpg")

    fun datei(context: Context, id: String): File = File(fotoOrdner(context), "${id.filter { it.isLetterOrDigit() || it == '-' }}.jpg")

    fun vorhanden(context: Context, id: String): Boolean = datei(context, id).let { it.exists() && it.length() > 0 }

    /** FileProvider-Adresse für die Kamera (Autorität wie im Manifest). */
    fun uriFuerKamera(context: Context): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zwischendatei(context))

    fun zwischendateiLoeschen(context: Context) {
        zwischendatei(context).delete()
    }

    /** Macht aus dem Kamerabild das Foto der Aufgabe [id] (verkleinert). Gibt zurück, ob es geklappt hat. */
    fun uebernehmen(context: Context, id: String): Boolean {
        val quelle = zwischendatei(context)
        if (!quelle.exists() || quelle.length() == 0L) return false
        return runCatching {
            val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(quelle.path, grenzen)
            var stufe = 1
            while (maxOf(grenzen.outWidth, grenzen.outHeight) / stufe > MAX_KANTE * 2) stufe *= 2
            val bitmap = BitmapFactory.decodeFile(quelle.path, BitmapFactory.Options().apply { inSampleSize = stufe })
                ?: return false
            val faktor = MAX_KANTE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val klein = if (faktor < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * faktor).toInt(), (bitmap.height * faktor).toInt(), true) else bitmap
            datei(context, id).outputStream().use { klein.compress(Bitmap.CompressFormat.JPEG, QUALITAET, it) }
            true
        }.getOrDefault(false).also { quelle.delete() }
    }

    /** Das Foto zum Anzeigen, höchstens [maxKante] Pixel groß. */
    fun laden(context: Context, id: String, maxKante: Int = 1024): Bitmap? = runCatching {
        val pfad = datei(context, id).path
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(pfad, grenzen)
        var stufe = 1
        while (maxOf(grenzen.outWidth, grenzen.outHeight) / (stufe * 2) >= maxKante) stufe *= 2
        BitmapFactory.decodeFile(pfad, BitmapFactory.Options().apply { inSampleSize = stufe })
    }.getOrNull()

    fun loeschen(context: Context, id: String) {
        datei(context, id).delete()
    }

    /** Fotos von Aufgaben, die es nicht mehr gibt, entfernen. */
    fun aufraeumen(context: Context, gueltigeIds: Set<String>) {
        val erlaubt = gueltigeIds.map { datei(context, it).name }.toSet()
        fotoOrdner(context).listFiles()?.filter { it.name !in erlaubt }?.forEach { it.delete() }
    }
}
