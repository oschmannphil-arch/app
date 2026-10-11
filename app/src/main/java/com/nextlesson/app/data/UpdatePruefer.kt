package com.nextlesson.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Ein gefundener, neuerer Build samt Download. */
data class UpdateAngebot(val build: Int, val url: String, val groesse: Long)

/**
 * Sucht auf der Release-Seite des (öffentlichen) Repos nach dem höchsten Build und merkt sich
 * Ergebnis und Zeitpunkt – so teilen sich der Hintergrund-Check (7, 15 und 20 Uhr) und die App
 * ein Ergebnis, und die App muss beim Öffnen meist gar nicht erst nachfragen.
 */
object UpdatePruefer {

    const val REPO = "oschmannphil-arch/app"
    const val APK_NAME = "NaechsteStunde.apk"

    private const val PREFS = "update"
    private const val KEY_ZULETZT = "zuletzt_geprueft"
    private const val KEY_NICHT_VOR = "nicht_vor"
    private const val KEY_BUILD = "gefunden_build"
    private const val KEY_URL = "gefunden_url"
    private const val KEY_GROESSE = "gefunden_groesse"
    private const val KEY_BENACHRICHTIGT = "benachrichtigt_build"

    /** Blockierend (aus Hintergrund-Thread aufrufen); wirft bei Netz- und HTTP-Fehlern. */
    fun suchen(client: OkHttpClient): UpdateAngebot? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=15")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { antwort ->
            check(antwort.isSuccessful) { "HTTP ${antwort.code}" }
            val liste = JSONArray(antwort.body?.string().orEmpty())
            var beste: UpdateAngebot? = null
            for (i in 0 until liste.length()) {
                val release = liste.getJSONObject(i)
                val build = UpdateInfo.buildAusTag(release.optString("tag_name")) ?: continue
                if (beste != null && build <= beste.build) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") == APK_NAME) {
                        beste = UpdateAngebot(build, asset.getString("browser_download_url"), asset.optLong("size", 0L))
                    }
                }
            }
            return beste
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun zuletztGeprueft(context: Context): Long = prefs(context).getLong(KEY_ZULETZT, 0L)

    /** Hält Ergebnis und Zeitpunkt der letzten erfolgreichen Suche fest ([angebot] null = kein Update). */
    fun merken(context: Context, angebot: UpdateAngebot?, jetzt: Long = System.currentTimeMillis()) {
        prefs(context).edit().apply {
            putLong(KEY_ZULETZT, jetzt)
            remove(KEY_NICHT_VOR)
            if (angebot == null) {
                remove(KEY_BUILD); remove(KEY_URL); remove(KEY_GROESSE)
            } else {
                putInt(KEY_BUILD, angebot.build); putString(KEY_URL, angebot.url); putLong(KEY_GROESSE, angebot.groesse)
            }
        }.apply()
    }

    /** Nach einem Fehler (z.B. GitHub-Limit): die App fragt erst in [wartenMillis] wieder selbst nach. */
    fun spaeterNochmal(context: Context, wartenMillis: Long, jetzt: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_NICHT_VOR, jetzt + wartenMillis).apply()
    }

    /** Soll die App beim Öffnen selbst nachfragen? Nur, wenn die letzte Prüfung länger her ist und keine Wartezeit läuft. */
    fun darfPruefen(context: Context, jetzt: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        return jetzt - p.getLong(KEY_ZULETZT, 0L) >= ABSTAND_MILLIS && jetzt >= p.getLong(KEY_NICHT_VOR, 0L)
    }

    /** Installierter Build (= Versionscode, den die CI aus der Lauf-Nummer setzt). */
    fun installierterBuild(context: Context): Int = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(0)

    /** Das zuletzt gefundene Update – nur, wenn es neuer ist als der installierte Build. */
    fun gefunden(context: Context, installiert: Int): UpdateAngebot? {
        val p = prefs(context)
        val build = p.getInt(KEY_BUILD, 0)
        val url = p.getString(KEY_URL, null)
        return if (url != null && UpdateInfo.istNeuer(installiert, build)) UpdateAngebot(build, url, p.getLong(KEY_GROESSE, 0L)) else null
    }

    /** Für den Hintergrund-Check: Meldung nur einmal je Build. */
    fun schonBenachrichtigt(context: Context, build: Int): Boolean = prefs(context).getInt(KEY_BENACHRICHTIGT, 0) >= build

    fun benachrichtigtMerken(context: Context, build: Int) {
        prefs(context).edit().putInt(KEY_BENACHRICHTIGT, build).apply()
    }

    /**
     * So lange gilt ein Ergebnis, bevor die App beim Öffnen selbst wieder nachfragt (still im
     * Hintergrund, der Start wird nicht langsamer). Kurz gehalten, damit ein neuer Build beim
     * nächsten Öffnen auftaucht; der Hintergrund-Check ([PRUEF_UHRZEITEN]) deckt die Zeit dazwischen ab.
     * Eine Anfrage pro halbe Stunde bleibt weit unter dem GitHub-Limit (60 pro Stunde).
     */
    const val ABSTAND_MILLIS = 30 * 60_000L

    /** Wann der Hintergrund-Check läuft (Ortszeit). */
    val PRUEF_UHRZEITEN: List<java.time.LocalTime> =
        listOf(java.time.LocalTime.of(7, 0), java.time.LocalTime.of(15, 0), java.time.LocalTime.of(20, 0))
}
