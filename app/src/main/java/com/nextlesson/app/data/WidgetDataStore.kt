package com.nextlesson.app.data

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Kleiner, unverschlüsselter Zwischenspeicher nur für die Anzeige im Homescreen-Widget.
 * Enthält keine Zugangsdaten.
 */
class WidgetDataStore(context: Context) {

    private val prefs = context.getSharedPreferences("widget_daten", Context.MODE_PRIVATE)
    private val zeitFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val tagFmt = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN)

    /**
     * @param datum Tag, zu dem die Stunde gehört – nur so kann das Widget "morgen" statt
     *              einer irreführenden Uhrzeit anzeigen.
     */
    fun speichern(
        ergebnis: NaechsteStundeErgebnis?,
        klasse: String,
        datum: LocalDate? = null,
        fehlermeldung: String? = null,
        geprueftUm: Long = System.currentTimeMillis()
    ) {
        val editor = prefs.edit()
        editor.putString(KEY_KLASSE, klasse)
        editor.putString(KEY_FEHLER, fehlermeldung)
        editor.putLong(KEY_GEPRUEFT, geprueftUm)

        val lesson = ergebnis?.lesson
        if (lesson != null) {
            editor.putString(KEY_FACH, lesson.fach)
            editor.putString(KEY_RAUM, lesson.raum)
            editor.putString(KEY_LEHRER, lesson.lehrer)
            editor.putString(KEY_BEGINN, lesson.beginn?.format(zeitFmt))
            editor.putString(KEY_ENDE, lesson.ende?.format(zeitFmt))
            editor.putInt(KEY_STUNDE, lesson.stunde)
            editor.putBoolean(KEY_AENDERUNG, lesson.hatAenderung)
            editor.putBoolean(KEY_ENTFAELLT, lesson.entfaellt)
            editor.putBoolean(KEY_VORSCHAU, ergebnis.istVorschau)
            editor.putString(KEY_TAG, datum?.let { tagLabel(it) })
            // Absolute Zeitpunkte, damit das Widget den Countdown beim Zeichnen selbst
            // ausrechnen kann statt einen beim Speichern eingefrorenen Text zu zeigen.
            editor.putLong(KEY_BEGINN_MILLIS, epochMillis(datum, lesson.beginn))
            editor.putLong(KEY_ENDE_MILLIS, epochMillis(datum, lesson.ende))
        } else {
            listOf(KEY_FACH, KEY_RAUM, KEY_LEHRER, KEY_BEGINN, KEY_ENDE, KEY_TAG)
                .forEach { editor.remove(it) }
            editor.putInt(KEY_STUNDE, 0)
            editor.putBoolean(KEY_AENDERUNG, false)
            editor.putBoolean(KEY_ENTFAELLT, false)
            editor.putBoolean(KEY_VORSCHAU, false)
            editor.putLong(KEY_BEGINN_MILLIS, 0L)
            editor.putLong(KEY_ENDE_MILLIS, 0L)
        }
        val grosserText = prefs.getBoolean(KEY_GROSSER_TEXT, false)
        editor.putBoolean(KEY_GROSSER_TEXT, grosserText)
        editor.apply()
    }

    private fun epochMillis(datum: LocalDate?, zeit: java.time.LocalTime?): Long {
        if (datum == null || zeit == null) return 0L
        return datum.atTime(zeit).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** "heute" wird weggelassen, alles andere klar benannt. */
    private fun tagLabel(datum: LocalDate): String? {
        val heute = LocalDate.now()
        return when (datum) {
            heute -> null
            heute.plusDays(1) -> "morgen"
            else -> datum.format(tagFmt)
        }
    }

    data class WidgetInhalt(
        val klasse: String?,
        val fach: String?,
        val raum: String?,
        val lehrer: String?,
        val beginn: String?,
        val ende: String?,
        val stunde: Int,
        val tag: String?,
        val hatAenderung: Boolean,
        val entfaellt: Boolean,
        val istVorschau: Boolean,
        val fehlermeldung: String?,
        val geprueftUm: Long,
        val beginnMillis: Long,
        val endeMillis: Long,
        val grosserText: Boolean = false
    ) {
        /** "Zuletzt geprüft"-Uhrzeit, damit man erkennt, ob die App noch aktualisiert. */
        val geprueftText: String?
            get() = if (geprueftUm <= 0) null else {
                val zeit = java.time.Instant.ofEpochMilli(geprueftUm)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
                "geprüft %02d:%02d".format(zeit.hour, zeit.minute)
            }

        /** Countdown, beim Zeichnen des Widgets berechnet. */
        fun countdown(jetztMillis: Long = System.currentTimeMillis()): String? {
            if (beginnMillis <= 0L) return null
            if (endeMillis > 0L && jetztMillis in beginnMillis until endeMillis) {
                // Aufgerundet, damit Widget und App dieselbe Minute zeigen.
                val restMin = (endeMillis - jetztMillis + 59_999) / 60_000
                return if (restMin <= 0) "endet gleich" else "noch ${dauer(restMin)}"
            }
            if (beginnMillis < jetztMillis) return null
            val bisMin = (beginnMillis - jetztMillis + 59_999) / 60_000
            return when {
                bisMin == 0L -> "jetzt"
                bisMin > 600 -> null
                else -> "in ${dauer(bisMin)}"
            }
        }

        private fun dauer(minuten: Long): String {
            if (minuten < 60) return "$minuten Min"
            val std = minuten / 60
            val min = minuten % 60
            return if (min == 0L) "$std Std" else "$std Std $min"
        }
    }

    fun laden(): WidgetInhalt = WidgetInhalt(
        klasse = prefs.getString(KEY_KLASSE, null),
        fach = prefs.getString(KEY_FACH, null),
        raum = prefs.getString(KEY_RAUM, null),
        lehrer = prefs.getString(KEY_LEHRER, null),
        beginn = prefs.getString(KEY_BEGINN, null),
        ende = prefs.getString(KEY_ENDE, null),
        stunde = prefs.getInt(KEY_STUNDE, 0),
        tag = prefs.getString(KEY_TAG, null),
        hatAenderung = prefs.getBoolean(KEY_AENDERUNG, false),
        entfaellt = prefs.getBoolean(KEY_ENTFAELLT, false),
        istVorschau = prefs.getBoolean(KEY_VORSCHAU, false),
        fehlermeldung = prefs.getString(KEY_FEHLER, null),
        geprueftUm = prefs.getLong(KEY_GEPRUEFT, 0L),
        beginnMillis = prefs.getLong(KEY_BEGINN_MILLIS, 0L),
        endeMillis = prefs.getLong(KEY_ENDE_MILLIS, 0L),
        grosserText = prefs.getBoolean(KEY_GROSSER_TEXT, false)
    )

    fun grosserTextSetzen(aktiv: Boolean) {
        prefs.edit().putBoolean(KEY_GROSSER_TEXT, aktiv).apply()
    }

    companion object {
        private const val KEY_KLASSE = "klasse"
        private const val KEY_FACH = "fach"
        private const val KEY_RAUM = "raum"
        private const val KEY_LEHRER = "lehrer"
        private const val KEY_BEGINN = "beginn"
        private const val KEY_ENDE = "ende"
        private const val KEY_STUNDE = "stunde"
        private const val KEY_TAG = "tag"
        private const val KEY_AENDERUNG = "aenderung"
        private const val KEY_ENTFAELLT = "entfaellt"
        private const val KEY_VORSCHAU = "vorschau"
        private const val KEY_GEPRUEFT = "geprueft"
        private const val KEY_FEHLER = "fehler"
        private const val KEY_BEGINN_MILLIS = "beginn_millis"
        private const val KEY_ENDE_MILLIS = "ende_millis"
        private const val KEY_GROSSER_TEXT = "grosser_text"
    }
}
