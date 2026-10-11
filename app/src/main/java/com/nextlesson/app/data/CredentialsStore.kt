package com.nextlesson.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert die Zugangsdaten verschlüsselt auf dem Gerät (AES256-GCM über
 * androidx.security EncryptedSharedPreferences). Sie verlassen das Gerät nur
 * beim Abruf direkt an stundenplan24.de.
 */
class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        prefs = try {
            oeffnen(context)
        } catch (e: Exception) {
            // Der Keystore macht auf manchen Geräten kurzzeitig Fehler – dann einmal erneut
            // versuchen (ohne zu warten, der Aufruf kann auf dem Hauptthread laufen).
            try {
                oeffnen(context)
            } catch (e2: Exception) {
                // Gelöscht wird NUR, wenn die Datei sich dauerhaft nicht mehr entschlüsseln lässt –
                // typisch nach einem Geräteumzug (Android 12+ überträgt die Datei trotz
                // allowBackup=false, den Keystore-Schlüssel aber nicht). Ohne diesen Weg stürzte
                // die App bei jedem Start ab; die Zugangsdaten müssen dann neu eingegeben werden.
                // Bei einem vorübergehenden Keystore-Fehler bleiben sie erhalten (die App meldet
                // dann den Fehler, statt die Daten zu verlieren).
                if (!istDauerhaft(e2)) throw e2
                context.deleteSharedPreferences(DATEI)
                oeffnen(context)
            }
        }
    }

    /** Entschlüsselung unmöglich (falscher/fehlender Schlüssel, zerstörte Datei) statt nur "gerade nicht erreichbar". */
    private fun istDauerhaft(fehler: Throwable): Boolean {
        var t: Throwable? = fehler
        var tiefe = 0
        while (t != null && tiefe++ < 8) {
            val name = t.javaClass.name
            if ("AEADBadTag" in name || "InvalidProtocolBuffer" in name || "KeyPermanentlyInvalidated" in name ||
                "BadPadding" in name || "InvalidKey" in name || "UnrecoverableKey" in name
            ) return true
            t = t.cause
        }
        return false
    }

    private fun oeffnen(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            DATEI,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun speichern(creds: IndiwareCredentials) {
        prefs.edit()
            .putString(KEY_SCHULNUMMER, creds.schulnummer)
            .putString(KEY_BENUTZER, creds.benutzername)
            .putString(KEY_PASSWORT, creds.passwort)
            .apply()
    }

    fun laden(): IndiwareCredentials? {
        val schulnummer = prefs.getString(KEY_SCHULNUMMER, null) ?: return null
        val benutzer = prefs.getString(KEY_BENUTZER, null) ?: return null
        val passwort = prefs.getString(KEY_PASSWORT, null) ?: return null
        val creds = IndiwareCredentials(schulnummer, benutzer, passwort)
        return if (creds.istVollstaendig()) creds else null
    }

    fun loeschen() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val DATEI = "indiware_credentials"
        private const val KEY_SCHULNUMMER = "schulnummer"
        private const val KEY_BENUTZER = "benutzername"
        private const val KEY_PASSWORT = "passwort"
    }
}
