package com.nextlesson.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Woher ein Plan stammt: frisch vom Server oder aus dem lokalen Cache. */
enum class Quelle { NETZ, CACHE }

sealed class PlanResult {
    data class Success(
        val plan: GesamtPlan,
        val geprueftUm: Long = System.currentTimeMillis(),
        val aus: Quelle = Quelle.NETZ
    ) : PlanResult()
    object AuthFehler : PlanResult()           // 401 – Benutzername/Passwort falsch
    object KeinPlanFuerTag : PlanResult()      // 404 – Wochenende/Ferien, kein Plan vorhanden
    data class NetzwerkFehler(val nachricht: String) : PlanResult()
}

/** Anzeigetext, wenn für einen Tag (noch) kein Plan veröffentlicht ist. */
const val FEHLER_KEIN_PLAN = "Kein Plan veröffentlicht"

/** Kurzer Grund, warum der Plan eines Tages fehlt – null, wenn er geladen wurde. */
fun PlanResult.tagesFehler(): String? = when (this) {
    is PlanResult.Success -> null
    is PlanResult.AuthFehler -> "Login fehlgeschlagen"
    is PlanResult.KeinPlanFuerTag -> FEHLER_KEIN_PLAN
    is PlanResult.NetzwerkFehler -> "Laden fehlgeschlagen"
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
    val geprueftUm: Long = System.currentTimeMillis(),
    /** True, wenn der Plan nicht frisch vom Server kommt, sondern aus dem lokalen Speicher. */
    val ausCache: Boolean = false
)

sealed class PersoenlicherResult {
    data class Erfolg(val plan: PersoenlicherPlan) : PersoenlicherResult()
    object AuthFehler : PersoenlicherResult()
    /** Es gibt keinen veröffentlichten Plan (Ferien, freie Tage). */
    object KeinPlan : PersoenlicherResult()
    /** Pläne sind da, aber keiner der gewählten Kurse hat darin eine Stunde (z.B. veraltete Kurswahl). */
    object KeineStunden : PersoenlicherResult()
    data class NetzwerkFehler(val nachricht: String) : PersoenlicherResult()
}

/**
 * Ruft Pläne von Indiware mobil (stundenplan24.de) ab.
 *
 * Zugangsdaten liegen verschlüsselt auf dem Gerät (CredentialsStore) und gehen per
 * HTTPS Basic-Auth ausschließlich an stundenplan24.de.
 *
 * Damit die App schnell bleibt, teilen sich alle Instanzen (App, Worker, Widget) einen
 * HTTP-Client und ein kurzes Zwischengedächtnis: Wird derselbe Tag innerhalb von
 * [FRISCH_MILLIS] mehrfach angefragt (z.B. App-Start + Hintergrund-Worker), geht nur der
 * erste Abruf ins Netz.
 */
class IndiwareRepository(context: Context) {

    private val cache = PlanCache(context.applicationContext)

    private fun buildUrl(schulnummer: String, datum: LocalDate): String =
        "https://www.stundenplan24.de/$schulnummer/mobil/mobdaten/PlanKl${datum.format(dateFormatter)}.xml"

    fun aufraeumen() {
        cache.aufraeumen()
    }

    /**
     * Lädt den Plan eines Tages. [erzwingen] überspringt das Zwischengedächtnis
     * (z.B. beim Tippen auf "Aktualisieren").
     */
    suspend fun holePlan(
        creds: IndiwareCredentials,
        datum: LocalDate,
        erzwingen: Boolean = false
    ): PlanResult = withContext(Dispatchers.IO) {
        val key = "${creds.schulnummer}|${creds.benutzername}|${creds.passwort.hashCode()}|$datum"
        val angefragtUm = System.currentTimeMillis()
        if (!erzwingen) frischerTreffer(key)?.let { return@withContext it }

        // Pro Tag nur ein Abruf gleichzeitig; wer wartet, bekommt danach das frische Ergebnis.
        // Auch ein erzwungener Abruf nimmt, was ein anderer erzwungener Abruf in der Wartezeit
        // gerade geholt hat – sonst würde dieselbe Datei gleich noch einmal geladen.
        sperren.getOrPut(key) { Mutex() }.withLock {
            if (!erzwingen) frischerTreffer(key)?.let { return@withLock it }
            frisch[key]?.takeIf { erzwingen && it.zeit >= angefragtUm }?.let { return@withLock it.ergebnis }
            val ergebnis = ladeVomServer(creds, datum)
            if (ergebnis is PlanResult.Success && ergebnis.aus == Quelle.NETZ) {
                merken(key, ergebnis)
            }
            ergebnis
        }
    }

    /** Nur aus dem lokalen Cache – für die sofortige Anzeige beim Start. Null = nichts gespeichert. */
    suspend fun holePlanAusCache(creds: IndiwareCredentials, datum: LocalDate): PlanResult.Success? =
        withContext(Dispatchers.IO) {
            val cached = cache.laden(creds.schulnummer, datum) ?: return@withContext null
            val plan = ByteArrayInputStream(cached.xml).use { IndiwareXmlParser.parse(it, creds.schulnummer) }
                ?: return@withContext null
            PlanResult.Success(plan, cached.lastModified, Quelle.CACHE)
        }

    private suspend fun ladeVomServer(creds: IndiwareCredentials, datum: LocalDate): PlanResult {
        val request = Request.Builder()
            .url(buildUrl(creds.schulnummer, datum))
            .header("Authorization", Credentials.basic(creds.benutzername, creds.passwort))
            .header("User-Agent", "Indiware")
            // Im Normalbetrieb frisch laden, aber bei Fehlern nehmen wir den Cache.
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).ausfuehren().use { response ->
                when {
                    response.code == 401 -> PlanResult.AuthFehler
                    response.code == 404 -> PlanResult.KeinPlanFuerTag
                    !response.isSuccessful ->
                        ladeAusCacheOderFehler(creds.schulnummer, datum, "HTTP ${response.code}")
                    else -> {
                        val bodyBytes = response.body?.bytes()
                        if (bodyBytes == null || bodyBytes.isEmpty()) {
                            ladeAusCacheOderFehler(creds.schulnummer, datum, "Leere Antwort")
                        } else {
                            val plan = ByteArrayInputStream(bodyBytes).use { stream ->
                                IndiwareXmlParser.parse(stream, creds.schulnummer)
                            }
                            if (plan == null) {
                                // Kein gültiger Plan (HTML-Fehlerseite, Anmeldeseite im WLAN …): wie
                                // ein Netzfehler behandeln und den guten Cache NICHT überschreiben.
                                ladeAusCacheOderFehler(creds.schulnummer, datum, "Ungültige Antwort")
                            } else {
                                cache.speichern(creds.schulnummer, datum, bodyBytes)
                                PlanResult.Success(plan)
                            }
                        }
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
            val plan = ByteArrayInputStream(cached.xml).use { stream ->
                IndiwareXmlParser.parse(stream, schulnummer)
            }
            if (plan != null) return PlanResult.Success(plan, cached.lastModified, Quelle.CACHE)
        }
        return PlanResult.NetzwerkFehler(fehlermeldung)
    }

    private fun frischerTreffer(key: String): PlanResult? {
        val eintrag = frisch[key] ?: return null
        return if (System.currentTimeMillis() - eintrag.zeit <= FRISCH_MILLIS) eintrag.ergebnis else null
    }

    private fun merken(key: String, ergebnis: PlanResult.Success) {
        val jetzt = System.currentTimeMillis()
        frisch.entries.removeIf { jetzt - it.value.zeit > FRISCH_MILLIS }
        frisch[key] = Eintrag(ergebnis, jetzt)
    }

    /**
     * Der erste Tag ab [ab] (höchstens [maxTage] Tage weit), für den ein Plan veröffentlicht ist –
     * für "Ferien: der nächste Plan steht für … bereit". Null, wenn keiner da ist oder der Abruf scheitert.
     */
    suspend fun ersterTagMitPlan(creds: IndiwareCredentials, ab: LocalDate, maxTage: Int = 28): Result<LocalDate?> {
        var versatz = 0
        while (versatz < maxTage) {
            val tage = holeTage(creds, ab.plusDays(versatz.toLong()), anzahl = minOf(7, maxTage - versatz))
            tage.firstOrNull { it.second is PlanResult.Success }?.let { return Result.success(it.first) }
            // Ein Abruffehler heißt "unbekannt", nicht "keiner da" – das darf nicht als Ergebnis gelten.
            if (tage.any { it.second is PlanResult.AuthFehler || it.second is PlanResult.NetzwerkFehler }) {
                return Result.failure(IllegalStateException("Abruf fehlgeschlagen"))
            }
            versatz += 7
        }
        return Result.success(null)
    }

    /** Wie [holeTage], aber nur aus dem lokalen Speicher – ohne Netz und ohne Warten. */
    suspend fun holeTageAusCache(
        creds: IndiwareCredentials,
        ab: LocalDate = LocalDate.now(),
        anzahl: Int = 3
    ): List<Pair<LocalDate, PlanResult>> = coroutineScope {
        (0 until anzahl).map { offset ->
            val tag = ab.plusDays(offset.toLong())
            async { tag to (holePlanAusCache(creds, tag) ?: PlanResult.NetzwerkFehler("Nicht gespeichert")) }
        }.awaitAll()
    }

    /**
     * Holt [anzahl] aufeinanderfolgende Tage ab [ab] parallel. Wird sowohl für die
     * Entfall-Überwachung als auch für die Suche nach der nächsten Stunde genutzt,
     * damit jeder Tag nur einmal abgerufen wird.
     */
    suspend fun holeTage(
        creds: IndiwareCredentials,
        ab: LocalDate = LocalDate.now(),
        anzahl: Int = 3,
        erzwingen: Boolean = false
    ): List<Pair<LocalDate, PlanResult>> = coroutineScope {
        (0 until anzahl).map { offset ->
            val tag = ab.plusDays(offset.toLong())
            async { tag to holePlan(creds, tag, erzwingen) }
        }.awaitAll()
    }

    /**
     * Sucht die nächste relevante Unterrichtsstunde: heute, wenn noch etwas kommt –
     * sonst die erste Stunde des nächsten Schultags.
     *
     * Heute wird zuerst alleine geholt (der Normalfall braucht nur diesen einen Abruf).
     * Nur wenn heute nichts mehr kommt, werden die Folgetage PARALLEL geholt statt einer
     * nach dem anderen – vorher konnte ein Wochenende acht Abrufe hintereinander kosten.
     *
     * Mit [nurCache] wird gar nicht ins Netz gegangen (schnelle Vorab-Anzeige).
     */
    suspend fun holePersoenlichenPlan(
        creds: IndiwareCredentials,
        gewaehlteKurse: Set<String>,
        ab: LocalDate = LocalDate.now(),
        jetzt: LocalTime = LocalTime.now(),
        maxTage: Int = 8,
        nurCache: Boolean = false,
        erzwingen: Boolean = false
    ): PersoenlicherResult = withContext(Dispatchers.Default) {
        var heuteFallback: PersoenlicherPlan? = null
        var planGesehen = false

        suspend fun tag(datum: LocalDate): PlanResult =
            if (nurCache) {
                holePlanAusCache(creds, datum) ?: PlanResult.NetzwerkFehler("Nicht gespeichert")
            } else {
                holePlan(creds, datum, erzwingen)
            }

        /** Liefert das Endergebnis, sobald es feststeht – sonst null (weitersuchen). */
        fun auswerten(offset: Int, ergebnis: PlanResult): PersoenlicherResult? {
            val datum = ab.plusDays(offset.toLong())
            return when (ergebnis) {
                is PlanResult.Success -> {
                    planGesehen = true
                    val plan = ergebnis.plan.tagesplanFuer(gewaehlteKurse)
                    if (offset == 0) {
                        val naechste = plan.naechsteStunde(jetzt)
                        val persoenlich = PersoenlicherPlan(
                            datum = datum,
                            plan = plan,
                            naechste = naechste,
                            istHeute = true,
                            gesamt = ergebnis.plan,
                            geprueftUm = ergebnis.geprueftUm,
                            ausCache = ergebnis.aus == Quelle.CACHE
                        )
                        if (naechste != null) {
                            PersoenlicherResult.Erfolg(persoenlich)
                        } else {
                            // Heute ist durch – als Rückfalloption merken und morgen weitersuchen.
                            if (heuteFallback == null) heuteFallback = persoenlich
                            null
                        }
                    } else {
                        plan.ersteStunde()?.let { erste ->
                            PersoenlicherResult.Erfolg(
                                PersoenlicherPlan(
                                    datum = datum,
                                    plan = plan,
                                    naechste = NaechsteStundeErgebnis(erste, istVorschau = false),
                                    istHeute = false,
                                    gesamt = ergebnis.plan,
                                    geprueftUm = ergebnis.geprueftUm,
                                    ausCache = ergebnis.aus == Quelle.CACHE
                                )
                            )
                        }
                    }
                }
                is PlanResult.KeinPlanFuerTag -> null // Wochenende/Ferien: weiter
                is PlanResult.AuthFehler -> PersoenlicherResult.AuthFehler
                // Ist heute schon bekannt, ist ein Netzfehler bei einem Folgetag kein Grund,
                // alles zu verwerfen (z.B. abends offline).
                is PlanResult.NetzwerkFehler ->
                    heuteFallback?.let { PersoenlicherResult.Erfolg(it) }
                        ?: PersoenlicherResult.NetzwerkFehler(ergebnis.nachricht)
            }
        }

        auswerten(0, tag(ab))?.let { return@withContext it }

        if (maxTage > 1) {
            val weitere = coroutineScope {
                (1 until maxTage).map { offset -> async { tag(ab.plusDays(offset.toLong())) } }.awaitAll()
            }
            weitere.forEachIndexed { index, ergebnis ->
                auswerten(index + 1, ergebnis)?.let { return@withContext it }
            }
        }

        heuteFallback?.let { PersoenlicherResult.Erfolg(it) }
            ?: if (planGesehen) PersoenlicherResult.KeineStunden else PersoenlicherResult.KeinPlan
    }

    /** Erster erreichbarer Plan – für die Kursauswahl beim Einrichten. */
    suspend fun holeNaechstenVerfuegbarenPlan(
        creds: IndiwareCredentials,
        ab: LocalDate = LocalDate.now(),
        maxTage: Int = 10,
        erzwingen: Boolean = false
    ): PlanResult {
        val ergebnisse = holeTage(creds, ab, maxTage, erzwingen)
        for ((_, ergebnis) in ergebnisse) {
            if (ergebnis !is PlanResult.KeinPlanFuerTag) return ergebnis
        }
        return PlanResult.KeinPlanFuerTag
    }

    /**
     * Holt Montag bis Freitag der Woche, in der [referenzDatum] liegt – parallel, über den
     * ganz normalen Tagesplan-Endpunkt.
     */
    suspend fun holeWoche(
        creds: IndiwareCredentials,
        referenzDatum: LocalDate = LocalDate.now(),
        erzwingen: Boolean = false
    ): List<Pair<LocalDate, PlanResult>> = coroutineScope {
        val montag = referenzDatum.with(DayOfWeek.MONDAY)
        (0..4).map { offset ->
            val tag = montag.plusDays(offset.toLong())
            async { tag to holePlan(creds, tag, erzwingen) }
        }.awaitAll()
    }

    /** OkHttp-Aufruf, der sich mit der Coroutine abbrechen lässt (Refresh, Tab-Wechsel …). */
    private suspend fun Call.ausfuehren(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private class Eintrag(val ergebnis: PlanResult.Success, val zeit: Long)

    companion object {
        /** So lange gilt ein frisch geholter Plan als aktuell genug für weitere Anfragen. */
        private const val FRISCH_MILLIS = 30_000L

        private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        // Ein Client für alles: gemeinsamer Verbindungspool, TLS-Sitzungen werden wiederverwendet.
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
        }

        /** Derselbe Client (Pool, Threads) auch für andere Abrufe, z.B. das App-Update. */
        internal val httpClient: OkHttpClient get() = client

        private val frisch = ConcurrentHashMap<String, Eintrag>()
        private val sperren = ConcurrentHashMap<String, Mutex>()
    }
}
