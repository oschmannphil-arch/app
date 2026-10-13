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
/** Was beim Speichern einer Hausaufgabe mit dem Foto passiert. */
enum class FotoAenderung { KEINE, NEU, ENTFERNEN }

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

    private fun rohDatei(context: Context, id: String) = File(fotoOrdner(context), datei(context, id).name + ".neu")

    /**
     * Schritt 1 (sofort, ohne Warten): Das Kamerabild aus der Zwischendatei an die Aufgabe [id] hängen,
     * damit das Schließen des Dialogs es nicht mehr löschen kann.
     */
    fun sichern(context: Context, id: String): Boolean {
        val quelle = zwischendatei(context)
        if (!quelle.exists() || quelle.length() == 0L) return false
        val ziel = rohDatei(context, id)
        ziel.delete()
        return quelle.renameTo(ziel) || runCatching { quelle.copyTo(ziel, overwrite = true); quelle.delete(); true }.getOrDefault(false)
    }

    /** Schritt 2 (im Hintergrund): das gesicherte Bild verkleinern und als Foto der Aufgabe [id] ablegen. */
    /** Dreht [bild] so, wie es die EXIF-Angabe der Datei [pfad] verlangt (Fotos aus Kamera und Galerie). */
    private fun aufrichten(bild: Bitmap, pfad: String): Bitmap {
        val ausrichtung = runCatching {
            android.media.ExifInterface(pfad).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val m = android.graphics.Matrix()
        when (ausrichtung) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bild
        }
        return Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, m, true)
    }

    fun uebernehmen(context: Context, id: String): Boolean {
        val quelle = rohDatei(context, id)
        if (!quelle.exists() || quelle.length() == 0L) return false
        return runCatching {
            val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(quelle.path, grenzen)
            var stufe = 1
            while (maxOf(grenzen.outWidth, grenzen.outHeight) / stufe > MAX_KANTE * 2) stufe *= 2
            val roh = BitmapFactory.decodeFile(quelle.path, BitmapFactory.Options().apply { inSampleSize = stufe })
                ?: return false
            // Kameras speichern das Bild oft quer und vermerken die Drehung nur in den EXIF-Daten.
            val bitmap = aufrichten(roh, quelle.path)
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
