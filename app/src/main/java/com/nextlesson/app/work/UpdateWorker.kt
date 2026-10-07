package com.nextlesson.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nextlesson.app.R
import com.nextlesson.app.data.UpdateInfo
import com.nextlesson.app.data.UpdatePruefer
import com.nextlesson.app.data.IndiwareRepository
import com.nextlesson.app.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Prüft einmal am Tag im Hintergrund, ob es einen neueren Build gibt – WorkManager wählt dafür
 * einen günstigen Zeitpunkt (Netz da, Akku nicht schwach). Die App selbst wird dadurch nicht
 * langsamer: Das Ergebnis liegt beim nächsten Öffnen schon bereit, ohne Abruf beim Start.
 */
class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val installiert = installierterBuild(context)
        val angebot = try {
            UpdatePruefer.suchen(IndiwareRepository.httpClient)
        } catch (e: Exception) {
            return Result.retry()
        }
        UpdatePruefer.merken(context, angebot)
        if (angebot != null && UpdateInfo.istNeuer(installiert, angebot.build) &&
            !UpdatePruefer.schonBenachrichtigt(context, angebot.build)
        ) {
            if (melden(context, angebot.build)) UpdatePruefer.benachrichtigtMerken(context, angebot.build)
        }
        return Result.success()
    }

    private fun melden(context: Context, build: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(CHANNEL_ID, "App-Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meldet, wenn eine neue Version der App bereitsteht."
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lernen)
            .setContentTitle("Neue Version: Build $build")
            .setContentText("Öffne die App, um das Update zu installieren.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }.isSuccess
    }

    companion object {
        private const val WORK_NAME = "update_taeglich"
        private const val CHANNEL_ID = "update"
        private const val NOTIFICATION_ID = 4300

        private fun installierterBuild(context: Context): Int = runCatching {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
        }.getOrDefault(0)

        /** Einmal am Tag, nur mit Netz und nicht bei schwachem Akku. Mehrfaches Einplanen ändert nichts (KEEP). */
        fun einplanen(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
