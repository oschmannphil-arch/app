package com.nextlesson.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.data.UpdateAngebot
import com.nextlesson.app.data.UpdateInfo
import com.nextlesson.app.data.UpdatePruefer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateZustand {
    object Unbekannt : UpdateZustand()
    object Sucht : UpdateZustand()
    object Aktuell : UpdateZustand()
    data class Verfuegbar(val build: Int, val url: String, val groesse: Long) : UpdateZustand()
    data class Laedt(val build: Int, val prozent: Int) : UpdateZustand()
    data class Bereit(val build: Int, val datei: File) : UpdateZustand()
    /** [wiederholbar]: der Download, der sich erneut versuchen lässt (null bei einer fehlgeschlagenen Suche). */
    data class Fehler(val nachricht: String, val wiederholbar: Verfuegbar? = null) : UpdateZustand()
}

/**
 * Sucht auf der Release-Seite des (öffentlichen) Repos nach einem neueren Build, lädt die APK
 * und startet die Installation. Android fragt vor dem Installieren immer noch einmal nach –
 * ganz ohne Bestätigung darf keine App sich selbst aktualisieren.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    // Von dem Client des Repositorys abgeleitet: teilt Verbindungen und Threads, nur mit
    // eigenen Zeitlimits (der Download der APK dauert länger als ein Planabruf).
    private val client: OkHttpClient = IndiwareRepository.httpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // kein Gesamtlimit: die APK ist ~10 MB groß
        .build()

    private val _zustand = MutableStateFlow<UpdateZustand>(UpdateZustand.Unbekannt)
    val zustand: StateFlow<UpdateZustand> = _zustand.asStateFlow()

    private var job: Job? = null

    /** Installierter Build (= Versionscode, den die CI aus der Lauf-Nummer setzt). */
    val installiert: Int = runCatching {
        val p = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }.getOrDefault(0)

    init {
        // Hat der tägliche Hintergrund-Check schon etwas gefunden, ist es sofort da – ohne Netzabruf.
        UpdatePruefer.gefunden(app, installiert)?.let {
            _zustand.value = UpdateZustand.Verfuegbar(it.build, it.url, it.groesse)
        }
    }

    /**
     * Beim Öffnen der App: höchstens einmal am Tag nachsehen, still im Hintergrund – und meist
     * gar nicht, weil der tägliche Hintergrund-Check ([UpdateWorker]) das schon erledigt hat.
     */
    fun automatischPruefen() {
        val zuletzt = UpdatePruefer.zuletztGeprueft(getApplication())
        if (System.currentTimeMillis() - zuletzt < UpdatePruefer.ABSTAND_MILLIS) return
        pruefen(still = true)
    }

    fun pruefen(still: Boolean = false) {
        val vorher = _zustand.value
        if (vorher is UpdateZustand.Laedt || vorher is UpdateZustand.Bereit) return
        if (job?.isActive == true) {
            // Läuft schon eine (stille) Suche: bei Tipp auf "Suchen" das auch zeigen.
            if (!still) _zustand.value = UpdateZustand.Sucht
            return
        }
        if (!still) _zustand.value = UpdateZustand.Sucht
        job = viewModelScope.launch {
            val ergebnis = runCatching { withContext(Dispatchers.IO) { neuesteSuchen() } }
            ergebnis.onSuccess { neu ->
                UpdatePruefer.merken(getApplication(), neu?.let { UpdateAngebot(it.build, it.url, it.groesse) })
                _zustand.value = if (neu != null && UpdateInfo.istNeuer(installiert, neu.build)) neu else UpdateZustand.Aktuell
            }.onFailure { fehler ->
                // Auch nach einem Fehlschlag nicht bei jedem Öffnen neu anfragen: erst in 30 Minuten wieder.
                UpdatePruefer.spaeterNochmal(getApplication(), UpdatePruefer.ABSTAND_MILLIS, WIEDER_NACH_FEHLER_MILLIS)
                _zustand.value = when {
                    // Eine schon bekannte Aktualisierung bleibt bekannt, auch wenn die Nachfrage scheitert.
                    vorher is UpdateZustand.Verfuegbar -> vorher
                    still -> UpdateZustand.Unbekannt
                    fehler.message?.contains("403") == true ->
                        UpdateZustand.Fehler("GitHub erlaubt gerade keine weiteren Abfragen. Versuch es in einer Stunde noch einmal.")
                    else -> UpdateZustand.Fehler("Konnte nicht nach Updates suchen. Bist du online?")
                }
            }
        }
    }

    private fun neuesteSuchen(): UpdateZustand.Verfuegbar? =
        UpdatePruefer.suchen(client)?.let { UpdateZustand.Verfuegbar(it.build, it.url, it.groesse) }

    fun herunterladen() {
        val zustand = _zustand.value
        val ziel = zustand as? UpdateZustand.Verfuegbar ?: (zustand as? UpdateZustand.Fehler)?.wiederholbar ?: return
        if (job?.isActive == true) return
        _zustand.value = UpdateZustand.Laedt(ziel.build, 0)
        job = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { laden(ziel) } }
                .onSuccess { _zustand.value = UpdateZustand.Bereit(ziel.build, it) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _zustand.value = UpdateZustand.Fehler("Download fehlgeschlagen. Versuch es noch einmal.", ziel)
                }
        }
    }

    private suspend fun laden(ziel: UpdateZustand.Verfuegbar): File {
        val ordner = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        ordner.listFiles()?.forEach { it.delete() }
        // Erst unter anderem Namen schreiben: Eine halbe Datei soll nie wie eine fertige APK aussehen.
        val teil = File(ordner, "$APK_NAME.teil")
        val datei = File(ordner, APK_NAME)
        val call = client.newCall(Request.Builder().url(ziel.url).build())
        try {
            herunterladenNach(call, teil, ziel)
            check(teil.renameTo(datei)) { "umbenennen" }
        } finally {
            if (teil.exists()) teil.delete()
        }
        return datei
    }

    private suspend fun herunterladenNach(call: okhttp3.Call, datei: File, ziel: UpdateZustand.Verfuegbar) {
        call.execute().use { antwort ->
            check(antwort.isSuccessful) { "HTTP ${antwort.code}" }
            val body = checkNotNull(antwort.body)
            val gesamt = body.contentLength().takeIf { it > 0 } ?: ziel.groesse
            var gelesen = 0L
            var letzter = -1
            body.byteStream().use { eingang ->
                datei.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    while (true) {
                        // Abbrechen, wenn die Ansicht weg ist – sonst lädt der Download unsichtbar weiter.
                        if (!currentCoroutineContext().isActive) call.cancel()
                        currentCoroutineContext().ensureActive()
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
    }

    /** Prüft die heruntergeladene Datei: eine Version dieser App, neuer, mit derselben Signatur. */
    private fun pruefeDatei(context: Context, datei: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archiv = pm.getPackageArchiveInfo(datei.path, flags)
            ?: return "Die heruntergeladene Datei ist kein gültiges Update."
        if (archiv.packageName != context.packageName) return "Die Datei gehört nicht zu dieser App."
        if (Build.VERSION.SDK_INT >= 28) {
            val eigene = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val neue = archiv.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            if (eigene.isNotEmpty() && neue.isNotEmpty() && eigene != neue) {
                return "Das Update ist anders signiert und lässt sich nicht über die installierte App legen."
            }
        }
        return null
    }

    /**
     * Startet die Installation und meldet bei Problemen selbst, woran es liegt. Erst muss
     * "Aus dieser Quelle installieren" erlaubt sein.
     */
    fun installieren(context: Context) {
        val bereit = _zustand.value as? UpdateZustand.Bereit ?: return
        fun melden(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

        if (!context.packageManager.canRequestPackageInstalls()) {
            val einstellungen = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(einstellungen) }
            melden("Erlaube die Installation für diese App und tippe dann erneut auf Installieren.")
            return
        }
        pruefeDatei(context, bereit.datei)?.let { melden(it); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", bereit.datei)
        val installieren = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(installieren) }.isFailure) {
            melden("Die Installation konnte nicht gestartet werden.")
        }
    }

    private companion object {
        const val APK_NAME = UpdatePruefer.APK_NAME
        const val WIEDER_NACH_FEHLER_MILLIS = 30 * 60_000L
    }
}
