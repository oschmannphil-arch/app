package com.nextlesson.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.Context
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

sealed class PlanResult {
    data class Success(val plan: GesamtPlan, val geprueftUm: Long = System.currentTimeMillis()) : PlanResult()
    object AuthFehler : PlanResult()           // 401 – Benutzername/Passwort falsch
    object KeinPlanFuerTag : PlanResult()      // 404 – Wochenende/Ferien, kein Plan vorhanden
    data class NetzwerkFehler(val nachricht: String) : PlanResult()
}

/**
 * Der persönliche Plan des Schülers für den gerade relevanten Tag.
 *
 * Ist der heutige Schultag vorbei, zeigt [datum] den nächsten Schultag und [naechste]
 * dessen erste Stunde – so sieht man abends schon, womit es morgen losgeht.
 */
data class PersoenlicherPlan(
    val datum: LocalDate,
    val plan: TagesPlan,
    val naechste: NaechsteStundeErgebnis?,
    val istHeute: Boolean,
    val gesamt: GesamtPlan,
    val geprueftUm: Long = System.currentTimeMillis()
)

sealed class PersoenlicherResult {
    data class Erfolg(val plan: PersoenlicherPlan) : PersoenlicherResult()
    object AuthFehler : PersoenlicherResult()
    object KeinPlan : PersoenlicherResult()
    data class NetzwerkFehler(val nachricht: String) : PersoenlicherResult()
}

/**
 * Ruft Pläne von Indiware mobil (stundenplan24.de) ab.
 *
 * Zugangsdaten liegen verschlüsselt auf dem Gerät (CredentialsStore) und gehen per
 * HTTPS Basic-Auth ausschließlich an stundenplan24.de.
 */
class IndiwareRepository(context: Context) {

    private val cache = PlanCache(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun buildUrl(schulnummer: String, datum: LocalDate): String =
        "https://www.stundenplan24.de/$schulnummer/mobil/mobdaten/PlanKl${datum.format(dateFormatter)}.xml"

    fun aufraeumen() {
        cache.aufraeumen()
    }

    suspend fun holePlan(creds: IndiwareCredentials, datum: LocalDate): PlanResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(buildUrl(creds.schulnummer, datum))
                .header("Authorization", Credentials.basic(creds.benutzername, creds.passwort))
                .header("User-Agent", "Indiware")
                // Im Normalbetrieb frisch laden, aber bei Fehlern nehmen wir den Cache.
                .header("Cache-Control", "no-cache")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 -> PlanResult.AuthFehler
                        response.code == 404 -> PlanResult.KeinPlanFuerTag
                        !response.isSuccessful -> ladeAusCacheOderFehler(creds.schulnummer, datum, "HTTP ${response.code}")
                        else -> {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes == null || bodyBytes.isEmpty()) {
                                return@use ladeAusCacheOderFehler(creds.schulnummer, datum, "Leere Antwort")
                            }
                            
                            // Im Hintergrund für später cachen
                            val xmlString = String(bodyBytes)
                            cache.speichern(creds.schulnummer, datum, xmlString)

                            val plan = ByteArrayInputStream(bodyBytes).use { stream ->
                                IndiwareXmlParser.parse(stream, creds.schulnummer)
                            }
                            if (plan == null) PlanResult.KeinPlanFuerTag else PlanResult.Success(plan)
                        }
                    }
                }
            } catch (e: IOException) {
                ladeAusCacheOderFehler(creds.schulnummer, datum, e.message ?: "Netzwerkfehler")
            }
        }

    private fun ladeAusCacheOderFehler(
        schulnummer: String,
        datum: LocalDate,
        fehlermeldung: String
    ): PlanResult {
        val cached = cache.laden(schulnummer, datum)
        if (cached != null) {
            val plan = ByteArrayInputStream(cached.xml.toByteArray()).use { stream ->
                IndiwareXmlParser.parse(stream, schulnummer)
            }
            if (plan != null) return PlanResult.Success(plan, cached.lastModified)
        }
        return PlanResult.NetzwerkFehler(fehlermeldung)
    }

    /**
     * Holt [anzahl] aufeinanderfolgende Tage ab [ab] parallel. Wird sowohl für die
     * Entfall-Überwachung als auch für die Suche nach der nächsten Stunde genutzt,
     * damit jeder Tag nur einmal abgerufen wird.
     */
    suspend fun holeTage(
        creds: IndiwareCredentials,
        ab: LocalDate = LocalDate.now(),
        anzahl: Int = 3
    ): List<Pair<LocalDate, PlanResult>> = coroutineScope {
        (0 until anzahl).map { offset ->
            val tag = ab.plusDays(offset.toLong())
            async { tag to holePlan(creds, tag) }
        }.awaitAll()
    }

    /**
     * Sucht die nächste relevante Unterrichtsstunde: heute, wenn noch etwas kommt –
     * sonst die erste Stunde des nächsten Schultags.
     */
    suspend fun holePersoenlichenPlan(
        creds: IndiwareCredentials,
        gewaehlteKurse: Set<String>,
        ab: LocalDate = LocalDate.now(),
        jetzt: LocalTime = LocalTime.now(),
        maxTage: Int = 8
    ): PersoenlicherResult {
        var heuteFallback: PersoenlicherPlan? = null

        for (offset in 0 until maxTage) {
            val datum = ab.plusDays(offset.toLong())
            when (val ergebnis = holePlan(creds, datum)) {
                is PlanResult.Success -> {
                    val plan = ergebnis.plan.tagesplanFuer(gewaehlteKurse)
                    if (offset == 0) {
                        val naechste = plan.naechsteStunde(jetzt)
                        if (naechste != null) {
                            return PersoenlicherResult.Erfolg(
                                PersoenlicherPlan(
                                    datum = datum,
                                    plan = plan,
                                    naechste = naechste,
                                    istHeute = true,
                                    gesamt = ergebnis.plan,
                                    geprueftUm = ergebnis.geprueftUm
                                )
                            )
                        }
                        // Heute ist durch – als Rückfalloption merken und morgen weitersuchen.
                        if (heuteFallback == null) {
                            heuteFallback = PersoenlicherPlan(
                                datum = datum,
                                plan = plan,
                                naechste = null,
                                istHeute = true,
                                gesamt = ergebnis.plan,
                                geprueftUm = ergebnis.geprueftUm
                            )
                        }
                    } else {
                        val erste = plan.ersteStunde()
                        if (erste != null) {
                            return PersoenlicherResult.Erfolg(
                                PersoenlicherPlan(
                                    datum = datum,
                                    plan = plan,
                                    naechste = NaechsteStundeErgebnis(erste, istVorschau = false),
                                    istHeute = false,
                                    gesamt = ergebnis.plan,
                                    geprueftUm = ergebnis.geprueftUm
                                )
                            )
                        }
                    }
                }
                is PlanResult.KeinPlanFuerTag -> Unit // Wochenende/Ferien: weiter
                is PlanResult.AuthFehler -> return PersoenlicherResult.AuthFehler
                is PlanResult.NetzwerkFehler -> return PersoenlicherResult.NetzwerkFehler(ergebnis.nachricht)
            }
        }

        return heuteFallback?.let { PersoenlicherResult.Erfolg(it) } ?: PersoenlicherResult.KeinPlan
    }

    /** Erster erreichbarer Plan – für die Kursauswahl beim Einrichten. */
    suspend fun holeNaechstenVerfuegbarenPlan(
        creds: IndiwareCredentials,
        ab: LocalDate = LocalDate.now(),
        maxTage: Int = 10
    ): PlanResult {
        var datum = ab
        repeat(maxTage) {
            when (val ergebnis = holePlan(creds, datum)) {
                is PlanResult.Success -> return ergebnis
                is PlanResult.KeinPlanFuerTag -> datum = datum.plusDays(1)
                else -> return ergebnis
            }
        }
        return PlanResult.KeinPlanFuerTag
    }

    /**
     * Holt Montag bis Freitag der Woche, in der [referenzDatum] liegt – parallel, über den
     * ganz normalen Tagesplan-Endpunkt.
     */
    suspend fun holeWoche(
        creds: IndiwareCredentials,
        referenzDatum: LocalDate = LocalDate.now()
    ): List<Pair<LocalDate, PlanResult>> = coroutineScope {
        val montag = referenzDatum.with(DayOfWeek.MONDAY)
        (0..4).map { offset ->
            val tag = montag.plusDays(offset.toLong())
            async { tag to holePlan(creds, tag) }
        }.awaitAll()
    }
}
