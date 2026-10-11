package com.nextlesson.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.data.LessonStatus
import com.nextlesson.app.data.PersoenlicherPlan
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bildTag = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val bildZeit = DateTimeFormatter.ofPattern("HH:mm")

/** Zeichnet den Tagesplan als Bild (feste Farben, unabhängig vom Farbthema der App). */
internal fun planBildZeichnen(p: PersoenlicherPlan): Bitmap {
    val breite = 1080
    val rand = 56f
    val zeile = 120f
    val kopf = 170f
    val fuss = 130f
    val stunden = p.plan.stunden.sortedBy { it.stunde }
    val hoehe = (kopf + zeile * stunden.size.coerceAtLeast(1) + fuss).toInt()
    val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
    val c = Canvas(bild)
    c.drawColor(Color.parseColor("#2E241D"))

    fun stift(groesse: Float, farbe: String, fett: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = groesse
        color = Color.parseColor(farbe)
        typeface = if (fett) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    c.drawText(p.datum.format(bildTag), rand, 100f, stift(56f, "#F2E6DA", true))
    c.drawText("NextLesson", rand, 146f, stift(30f, "#B9A99A"))

    var y = kopf
    if (stunden.isEmpty()) c.drawText("Kein Unterricht", rand, y + 70f, stift(44f, "#F2E6DA"))
    stunden.forEach { l ->
        val ausfall = l.entfaellt
        val farbeKarte = when {
            ausfall -> "#5A2A2A"
            l.istKlausur -> "#6A3A32"
            l.status == LessonStatus.VERTRETUNG || l.status == LessonStatus.RAUMAENDERUNG -> "#5A4B2A"
            else -> "#3B4D58"
        }
        val karte = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(farbeKarte) }
        c.drawRoundRect(rand, y + 8f, breite - rand, y + zeile - 8f, 28f, 28f, karte)
        val uhr = l.beginn?.format(bildZeit) ?: "${l.stunde}."
        c.drawText(uhr, rand + 28f, y + 72f, stift(40f, "#F2E6DA", true))
        val name = l.fach.ifBlank { l.kursKuerzel ?: "—" }
        val fachStift = stift(44f, if (ausfall) "#C9A9A9" else "#FFFFFF", true).apply {
            if (ausfall) flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        c.drawText(name, rand + 230f, y + 56f, fachStift)
        c.drawText(untertitel(l), rand + 230f, y + 98f, stift(30f, "#D9CABB"))
        val raum = if (ausfall) "" else l.raum
        val raumStift = stift(44f, "#FFFFFF", true)
        c.drawText(raum, breite - rand - 28f - raumStift.measureText(raum), y + 72f, raumStift)
        y += zeile
    }
    p.plan.schluss()?.let { c.drawText("Schluss ${it.format(bildZeit)}", rand, y + 60f, stift(34f, "#B9A99A")) }
    return bild
}

private fun untertitel(l: Lesson): String = when {
    l.entfaellt -> "fällt aus"
    l.istKlausur -> "Klausur"
    l.status == LessonStatus.VERTRETUNG && l.lehrer.isNotBlank() -> "Vertretung: ${l.lehrer}"
    l.status == LessonStatus.RAUMAENDERUNG -> "Raumänderung"
    else -> l.lehrer
}

/** Plan als Bild über das Teilen-Menü von Android verschicken. */
fun planAlsBildTeilen(context: Context, p: PersoenlicherPlan) {
    runCatching {
        val ordner = File(context.cacheDir, "teilen").apply { mkdirs() }
        val datei = File(ordner, "stundenplan.png")
        datei.outputStream().use { planBildZeichnen(p).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        val senden = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(senden, "Plan als Bild teilen"))
    }
}
