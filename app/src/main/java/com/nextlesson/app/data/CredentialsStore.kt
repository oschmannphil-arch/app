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
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "indiware_credentials",
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
        private const val KEY_SCHULNUMMER = "schulnummer"
        private const val KEY_BENUTZER = "benutzername"
        private const val KEY_PASSWORT = "passwort"
    }
}
