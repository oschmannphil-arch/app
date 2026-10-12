package com.nextlesson.app.data

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Bereitet ein Foto für die Texterkennung auf – vor allem Tafelbilder: Dunkler Grund mit heller
 * Kreide wird zu dunkler Schrift auf hellem Grund, ungleichmäßiges Licht wird ausgeglichen und der
 * Kontrast gespreizt.
 */
object BildAufbereitung {

    /** Grundton darunter gilt als dunkel (Tafel) und wird umgekehrt. */
    private const val DUNKEL_GRENZE = 110

    /**
     * Rechenkern (ohne Android): [grau] sind Helligkeiten 0–255, [hintergrund] die grob geglättete
     * Version davon (das örtliche Licht).
     */
    fun verbessern(grau: IntArray, hintergrund: IntArray): IntArray {
        if (grau.isEmpty()) return grau
        val dunkel = grau.average() < DUNKEL_GRENZE
        val g = if (dunkel) IntArray(grau.size) { 255 - grau[it] } else grau
        val bg = if (dunkel) IntArray(hintergrund.size) { 255 - hintergrund[it] } else hintergrund

        // Licht ausgleichen: jeder Punkt im Verhältnis zu seiner Umgebung.
        val n = IntArray(g.size) { (g[it] * 255 / maxOf(bg[it], 1)).coerceIn(0, 255) }

        // Kontrast spreizen zwischen dem 1. und 99. Prozent der Helligkeiten.
        val verteilung = IntArray(256)
        n.forEach { verteilung[it]++ }
        val unten = n.size / 100
        val oben = n.size - unten
        var summe = 0
        var lo = 0
        while (lo < 255 && summe + verteilung[lo] <= unten) summe += verteilung[lo++]
        summe = 0
        var hi = 255
        var rest = n.size
        while (hi > 0 && rest - verteilung[hi] >= oben) rest -= verteilung[hi--]
        if (hi - lo < 20) return n
        return IntArray(n.size) { ((n[it] - lo) * 255 / (hi - lo)).coerceIn(0, 255) }
    }

    /** Verkleinert auf höchstens [maxKante] Pixel, macht das Bild grau und wendet [verbessern] an. */
    fun fuerTexterkennung(roh: Bitmap, maxKante: Int = 2400): Bitmap {
        val faktor = minOf(1f, maxKante.toFloat() / maxOf(roh.width, roh.height))
        val bild = if (faktor < 1f) {
            Bitmap.createScaledBitmap(roh, (roh.width * faktor).toInt().coerceAtLeast(1), (roh.height * faktor).toInt().coerceAtLeast(1), true)
        } else roh
        val b = bild.width
        val h = bild.height
        val pixel = IntArray(b * h)
        bild.getPixels(pixel, 0, b, 0, 0, b, h)
        val grau = IntArray(pixel.size) {
            val p = pixel[it]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }

        // Örtliches Licht: das Graubild stark verkleinern und wieder vergrößern (weichzeichnen).
        val grauBild = Bitmap.createBitmap(grau.map { Color.rgb(it, it, it) }.toIntArray(), b, h, Bitmap.Config.ARGB_8888)
        val klein1 = Bitmap.createScaledBitmap(grauBild, maxOf(1, b / 4), maxOf(1, h / 4), true)
        val klein2 = Bitmap.createScaledBitmap(klein1, maxOf(1, b / 16), maxOf(1, h / 16), true)
        val gross = Bitmap.createScaledBitmap(klein2, b, h, true)
        val bgPixel = IntArray(b * h)
        gross.getPixels(bgPixel, 0, b, 0, 0, b, h)
        val hintergrund = IntArray(bgPixel.size) { Color.red(bgPixel[it]) }

        val neu = verbessern(grau, hintergrund)
        return Bitmap.createBitmap(IntArray(neu.size) { Color.rgb(neu[it], neu[it], neu[it]) }, b, h, Bitmap.Config.ARGB_8888)
    }
}
