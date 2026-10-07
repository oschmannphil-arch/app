package com.nextlesson.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Ein gefundener, neuerer Build samt Download. */
data class UpdateAngebot(val build: Int, val url: String, val groesse: Long)

/**
 * Sucht auf der Release-Seite des (öffentlichen) Repos nach dem höchsten Build und merkt sich
 * Ergebnis und Zeitpunkt – so teilen sich der tägliche Hintergrund-Check und die App ein Ergebnis,
 * und die App muss beim Öffnen meist gar nicht erst nachfragen.
 */
object UpdatePruefer {

    const val REPO = "oschmannphil-arch/app"
    const val APK_NAME = "NaechsteStunde.apk"

    private const val PREFS = "update"
    private const val KEY_ZULETZT = "zuletzt_geprueft"
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
            if (angebot == null) {
                remove(KEY_BUILD); remove(KEY_URL); remove(KEY_GROESSE)
            } else {
                putInt(KEY_BUILD, angebot.build); putString(KEY_URL, angebot.url); putLong(KEY_GROESSE, angebot.groesse)
            }
        }.apply()
    }

    /** Beim nächsten Versuch erst nach [wartenMillis] wieder prüfen (nach einem Fehler). */
    fun spaeterNochmal(context: Context, abstandMillis: Long, wartenMillis: Long, jetzt: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_ZULETZT, jetzt - abstandMillis + wartenMillis).apply()
    }

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

    /** Wie lange ein Ergebnis gilt, bevor die App wieder nachfragt: einmal am Tag reicht. */
    const val ABSTAND_MILLIS = 24 * 60 * 60_000L
}
