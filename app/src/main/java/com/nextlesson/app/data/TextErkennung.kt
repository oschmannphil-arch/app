package com.nextlesson.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Liest Text aus einem Foto (z. B. dem Tafelbild) – komplett auf dem Gerät. */
object TextErkennung {

    /** [fertig] bekommt den erkannten Text oder null, wenn nichts erkannt wurde bzw. es einen Fehler gab. */
    fun aus(context: Context, datei: File, fertig: (String?) -> Unit) {
        val bild = runCatching { InputImage.fromFilePath(context, Uri.fromFile(datei)) }.getOrNull()
        if (bild == null) {
            fertig(null)
            return
        }
        val erkenner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        erkenner.process(bild)
            .addOnSuccessListener { ergebnis ->
                // Mit Positionen der Wörter: Tabellen bleiben Tabellen. Fehlen sie, gilt der rohe Text.
                val woerter = ergebnis.textBlocks.flatMap { b -> b.lines.flatMap { l -> l.elements } }.mapNotNull { e ->
                    e.boundingBox?.let { r -> TabellenText.Wort(e.text, r.left, r.right, r.top, r.bottom) }
                }
                val text = (if (woerter.isNotEmpty()) TabellenText.aufbereiten(woerter) else ergebnis.text).trim()
                fertig(text.ifEmpty { null })
            }
            .addOnFailureListener { fertig(null) }
    }
}
