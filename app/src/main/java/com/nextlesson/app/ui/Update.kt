package com.nextlesson.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateZustand {
    object Unbekannt : UpdateZustand()
    object Sucht : UpdateZustand()
    object Aktuell : UpdateZustand()
    data class Verfuegbar(val build: Int, val url: String, val groesse: Long) : UpdateZustand()
    data class Laedt(val build: Int, val prozent: Int) : UpdateZustand()
    data class Bereit(val build: Int, val datei: File) : UpdateZustand()
    data class Fehler(val nachricht: String) : UpdateZustand()
}

/**
 * Sucht auf der Release-Seite des (öffentlichen) Repos nach einem neueren Build, lädt die APK
 * und startet die Installation. Android fragt vor dem Installieren immer noch einmal nach –
 * ganz ohne Bestätigung darf keine App sich selbst aktualisieren.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _zustand = MutableStateFlow<UpdateZustand>(UpdateZustand.Unbekannt)
    val zustand: StateFlow<UpdateZustand> = _zustand.asStateFlow()

    private var job: Job? = null

    /** Installierter Build (= Versionscode, den die CI aus der Lauf-Nummer setzt). */
    val installiert: Int = runCatching {
        val p = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(0)

    /** Beim Öffnen der App: höchstens alle 6 Stunden nachsehen, still im Hintergrund. */
    fun automatischPruefen() {
        val zuletzt = prefs.getLong(KEY_ZULETZT, 0L)
        if (System.currentTimeMillis() - zuletzt < ABSTAND_MILLIS) return
        pruefen(still = true)
    }

    fun pruefen(still: Boolean = false) {
        if (job?.isActive == true) return
        val vorher = _zustand.value
        if (vorher is UpdateZustand.Laedt || vorher is UpdateZustand.Bereit) return
        if (!still) _zustand.value = UpdateZustand.Sucht
        job = viewModelScope.launch {
            val ergebnis = runCatching { withContext(Dispatchers.IO) { neuesteSuchen() } }
            ergebnis.onSuccess { neu ->
                prefs.edit().putLong(KEY_ZULETZT, System.currentTimeMillis()).apply()
                _zustand.value = if (neu != null && UpdateInfo.istNeuer(installiert, neu.build)) neu else UpdateZustand.Aktuell
            }.onFailure {
                // Ein stiller Check, der scheitert (kein Netz), soll nicht stören.
                _zustand.value = if (still) UpdateZustand.Unbekannt
                else UpdateZustand.Fehler("Konnte nicht nach Updates suchen. Bist du online?")
            }
        }
    }

    private fun neuesteSuchen(): UpdateZustand.Verfuegbar? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=15")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { antwort ->
            check(antwort.isSuccessful) { "HTTP ${antwort.code}" }
            val liste = JSONArray(antwort.body?.string().orEmpty())
            var beste: UpdateZustand.Verfuegbar? = null
            for (i in 0 until liste.length()) {
                val release = liste.getJSONObject(i)
                val build = UpdateInfo.buildAusTag(release.optString("tag_name")) ?: continue
                if (beste != null && build <= beste.build) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") == APK_NAME) {
                        beste = UpdateZustand.Verfuegbar(
                            build = build,
                            url = asset.getString("browser_download_url"),
                            groesse = asset.optLong("size", 0L)
                        )
                    }
                }
            }
            return beste
        }
    }

    fun herunterladen() {
        val ziel = _zustand.value as? UpdateZustand.Verfuegbar ?: return
        if (job?.isActive == true) return
        _zustand.value = UpdateZustand.Laedt(ziel.build, 0)
        job = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { laden(ziel) } }
                .onSuccess { _zustand.value = UpdateZustand.Bereit(ziel.build, it) }
                .onFailure { _zustand.value = UpdateZustand.Fehler("Download fehlgeschlagen. Versuch es später noch einmal.") }
        }
    }

    private fun laden(ziel: UpdateZustand.Verfuegbar): File {
        val ordner = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        ordner.listFiles()?.forEach { it.delete() }
        val datei = File(ordner, APK_NAME)
        client.newCall(Request.Builder().url(ziel.url).build()).execute().use { antwort ->
            check(antwort.isSuccessful) { "HTTP ${antwort.code}" }
            val body = checkNotNull(antwort.body)
            val gesamt = body.contentLength().takeIf { it > 0 } ?: ziel.groesse
            var gelesen = 0L
            var letzter = -1
            body.byteStream().use { eingang ->
                datei.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = eingang.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        gelesen += n
                        if (gesamt > 0) {
                            val prozent = (gelesen * 100 / gesamt).toInt().coerceIn(0, 100)
                            if (prozent != letzter) {
                                letzter = prozent
                                _zustand.value = UpdateZustand.Laedt(ziel.build, prozent)
                            }
                        }
                    }
                }
            }
            check(gesamt <= 0 || gelesen == gesamt) { "unvollständig" }
        }
        return datei
    }

    /** Startet die Installation. Erst muss "Aus dieser Quelle installieren" erlaubt sein. */
    fun installieren(context: Context): Boolean {
        val bereit = _zustand.value as? UpdateZustand.Bereit ?: return false
        if (!context.packageManager.canRequestPackageInstalls()) {
            val einstellungen = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(einstellungen) }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", bereit.datei)
        val installieren = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(installieren) }.isSuccess
    }

    private companion object {
        const val REPO = "oschmannphil-arch/app"
        const val APK_NAME = "NaechsteStunde.apk"
        const val KEY_ZULETZT = "zuletzt_geprueft"
        const val ABSTAND_MILLIS = 6 * 60 * 60_000L
    }
}
