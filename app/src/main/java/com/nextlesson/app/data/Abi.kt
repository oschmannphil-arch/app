package com.nextlesson.app.data

import kotlin.math.roundToInt

/**
 * Grobe Schätzung der Abiturnote nach dem bundesweiten 900-Punkte-Schema: Block I (Qualifikationsphase)
 * höchstens 600 Punkte, Block II (fünf Prüfungen, vierfach gewertet) höchstens 300. Die genauen Regeln
 * (welche Kurse zählen) legt das Land fest – darum nur eine Schätzung.
 */
object Abi {
    const val BLOCK_I_MAX = 600
    const val BLOCK_II_MAX = 300
    const val BLOCK_I_MIN = 200
    const val BLOCK_II_MIN = 100
    const val GESAMT_MIN = 300

    /** Block I aus dem Durchschnitt der Kurspunkte (0–15): 15 im Schnitt = 600 Punkte. */
    fun blockI(schnittPunkte: Double): Int =
        (schnittPunkte.coerceIn(0.0, 15.0) / 15.0 * BLOCK_I_MAX).roundToInt()

    /** Block II aus den Punkten der fünf Prüfungen: jede zählt vierfach. */
    fun blockII(pruefungen: List<Int>): Int = pruefungen.sumOf { it.coerceIn(0, 15) } * 4

    fun bestanden(blockI: Int, blockII: Int): Boolean =
        blockI >= BLOCK_I_MIN && blockII >= BLOCK_II_MIN && blockI + blockII >= GESAMT_MIN

    /** Durchschnittsnote auf eine Nachkommastelle; unter 300 Punkten gibt es keine (nicht bestanden). */
    fun note(gesamt: Int): Double? {
        if (gesamt < GESAMT_MIN) return null
        val roh = (17.0 - gesamt / 60.0) / 3.0
        return ((roh * 10).roundToInt() / 10.0).coerceAtLeast(1.0)
    }
}
