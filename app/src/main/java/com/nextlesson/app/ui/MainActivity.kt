package com.nextlesson.app.ui

import android.Manifest
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nextlesson.app.data.DesignModus
import com.nextlesson.app.data.DesignStore
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.FreundTeilen
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.data.Patchnotes
import com.nextlesson.app.data.anzeigeName
import com.nextlesson.app.ui.theme.NaechsteStundeTheme
import com.nextlesson.app.widget.NextLessonWidgetReceiver
import com.nextlesson.app.work.EntfallNotifier
import com.nextlesson.app.work.LernErinnerung
import com.nextlesson.app.work.RefreshScheduler
import com.nextlesson.app.work.UpdateWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val planViewModel: PlanViewModel by viewModels()
    private val aufgabenViewModel: AufgabenViewModel by viewModels()
    private val notenViewModel: NotenViewModel by viewModels()
    private val sucheViewModel: SucheViewModel by viewModels()
    private val freundeViewModel: FreundeViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private lateinit var design: DesignStore
    /** Vom Widget ("Suche") gesetzt: Suche öffnen. Wird von der Oberfläche verbraucht. */
    private val sucheOeffnen = MutableStateFlow(false)

    private val benachrichtigungAnfrage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Ergebnis egal */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        design = DesignStore(applicationContext)
        // Beim Drehen kommt derselbe Intent noch einmal, ebenso beim Öffnen aus "Zuletzt
        // verwendet" (Android liefert dann den ursprünglichen Link erneut) – nur beim echten
        // Start auswerten.
        val ausVerlauf = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !ausVerlauf) intentAuswerten(intent)

        EntfallNotifier.kanalAnlegen(applicationContext)
        LernErinnerung.kanalAnlegen(applicationContext)
        RefreshScheduler.periodischePruefungEinplanen(applicationContext)
        // Nur beim echten Start, nicht bei jedem Drehen.
        if (savedInstanceState == null) UpdateWorker.einplanen(applicationContext)
        // Nur beim echten Start fragen – nicht bei jedem Drehen erneut (Android zählt Ablehnungen).
        if (savedInstanceState == null) benachrichtigungErlaubnisAnfragen()

        setContent {
            val dunkel = when (design.modus) {
                DesignModus.SYSTEM -> isSystemInDarkTheme()
                DesignModus.HELL -> false
                DesignModus.DUNKEL -> true
            }
            // Symbole in Status- und Navigationsleiste müssen zum gewählten Design passen.
            LaunchedEffect(dunkel) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dunkel },
                    // Mit Schleier wie bei enableEdgeToEdge(): Auf Android 8–9 mit Tasten-
                    // Navigation lägen die Tasten sonst direkt über dem Inhalt.
                    navigationBarStyle = SystemBarStyle.auto(NAVI_SCHLEIER_HELL, NAVI_SCHLEIER_DUNKEL) { dunkel }
                )
            }
            NaechsteStundeTheme(dunkel = dunkel, fachFarben = design.fachFarben) {
                Surface {
                    AppInhalt(planViewModel, aufgabenViewModel, notenViewModel, sucheViewModel, freundeViewModel, design, updateViewModel, sucheOeffnen)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentAuswerten(intent)
    }

    /** Ein angetippter Kurs-Link ("nextlesson://freund?…") wird als Freund vorgeschlagen; "Suche" vom Widget öffnet die Suche. */
    private fun intentAuswerten(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OEFFNE) == OEFFNE_SUCHE) sucheOeffnen.value = true
        if (intent?.action != Intent.ACTION_VIEW) return
        FreundTeilen.lesen(intent?.dataString)?.let { freundeViewModel.importVorschlagen(it) }
    }

    companion object {
        const val EXTRA_OEFFNE = "oeffne"
        const val OEFFNE_SUCHE = "suche"
    }

    /** Beim Öffnen immer frisch nachsehen – nicht auf den 15-Minuten-Takt warten. */
    override fun onResume() {
        super.onResume()
        planViewModel.ladeGespeichertUndAktualisiere()
        updateViewModel.automatischPruefen()
        // Erst nach ein paar Sekunden: Der Worker lädt sieben Tage und würde sonst Netz und
        // CPU mit dem Laden der sichtbaren Seite teilen – die App wirkte dadurch langsam.
        RefreshScheduler.sofortAktualisieren(applicationContext, verzoegerungSekunden = 5)
    }

    private fun benachrichtigungErlaubnisAnfragen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (EntfallNotifier.darfBenachrichtigen(this)) return
        benachrichtigungAnfrage.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// Dieselben Schleier wie enableEdgeToEdge() sie standardmäßig für die Navigationsleiste nimmt.
private val NAVI_SCHLEIER_HELL = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAVI_SCHLEIER_DUNKEL = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private const val PATCH_GESEHEN = "gesehen"

/**
 * Bis zu welchem Patchnotes-Eintrag der Nutzer schon alles kennt. Eine frische Installation
 * (nie aktualisiert) soll keine "Neu in dieser Version"-Hinweise bekommen – sie kennt ja nichts Altes.
 */
private fun patchGesehenStart(context: Context, prefs: android.content.SharedPreferences): Int {
    if (prefs.contains(PATCH_GESEHEN)) return prefs.getInt(PATCH_GESEHEN, 0)
    val frisch = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        p.firstInstallTime == p.lastUpdateTime
    }.getOrDefault(false)
    if (!frisch) return 0
    prefs.edit().putInt(PATCH_GESEHEN, Patchnotes.neuesteId).apply()
    return Patchnotes.neuesteId
}

private enum class Tab(val titel: String, val symbol: ImageVector) {
    HEUTE("Heute", Icons.Filled.CheckCircle),
    WOCHE("Woche", Icons.Filled.DateRange),
    SUCHE("Suche", Icons.Filled.Search),
    HAUSAUFGABEN("Aufgaben", Icons.Filled.Edit),
    PRUEFUNGEN("Klausuren", Icons.AutoMirrored.Filled.List)
}

/** Freund, der gerade angelegt oder bearbeitet wird. */
private data class FreundEntwurf(val freund: Freund, val neu: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInhalt(
    planViewModel: PlanViewModel,
    aufgabenViewModel: AufgabenViewModel,
    notenViewModel: NotenViewModel,
    sucheViewModel: SucheViewModel,
    freundeViewModel: FreundeViewModel,
    design: DesignStore,
    updateViewModel: UpdateViewModel,
    sucheOeffnen: MutableStateFlow<Boolean>
) {
    val update by updateViewModel.zustand.collectAsState()
    // Welchen Build der Nutzer im Hinweis auf "Später" gesetzt hat – bis zum nächsten Start Ruhe.
    var spaeterBuild by rememberSaveable { mutableStateOf(0) }
    val zustand by planViewModel.zustand.collectAsState()
    val wochenZustand by planViewModel.wochenZustand.collectAsState()
    val hausaufgaben by aufgabenViewModel.hausaufgaben.collectAsState()
    val pruefungen by aufgabenViewModel.pruefungen.collectAsState()
    val erinnerung by aufgabenViewModel.erinnerung.collectAsState()
    val noten by notenViewModel.noten.collectAsState()
    val klausurAnteil by notenViewModel.klausurAnteil.collectAsState()
    val grosserText by planViewModel.grosserText.collectAsState()
    val aktualisiertGerade by planViewModel.aktualisiertGerade.collectAsState()
    val verfuegbareKurse by planViewModel.verfuegbareKurse.collectAsState()
    val freunde by freundeViewModel.freunde.collectAsState()
    val sucheZustand by sucheViewModel.zustand.collectAsState()
    val sucheDatum by sucheViewModel.datum.collectAsState()
    val favoriten by sucheViewModel.favoriten.collectAsState()
    val verlauf by sucheViewModel.verlauf.collectAsState()
    val sucheVomWidget by sucheOeffnen.collectAsState()
    var sucheFokus by remember { mutableStateOf(false) }
    val sucheWoche by sucheViewModel.woche.collectAsState()
    val importVorschlag by freundeViewModel.importVorschlag.collectAsState()
    val freundeWoche by freundeViewModel.woche.collectAsState()
    // Mit der Uhrzeit neu rechnen: Eine geschriebene Klausur soll nicht bis Mitternacht "heute" bleiben.
    val minute by rememberJetzt()
    val uebersicht = remember(hausaufgaben, pruefungen, minute) {
        Uebersicht.berechne(hausaufgaben, pruefungen, jetzt = minute)
    }

    // Saveable: Beim Drehen des Handys bleibt man im gewählten Reiter bzw. Dialog.
    var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }
    var zeigeEinstellungen by rememberSaveable { mutableStateOf(false) }
    var zeigeKurse by rememberSaveable { mutableStateOf(false) }
    var zeigeNoten by rememberSaveable { mutableStateOf(false) }
    var freundAnsicht by rememberSaveable { mutableStateOf<String?>(null) }
    // ID des Freundes, der gerade angelegt/bearbeitet wird – als ID, damit sie saveable ist.
    var entwurfId by rememberSaveable { mutableStateOf<String?>(null) }
    // Gemeinsame Freistunden der Woche: offen? und welche Freunde sind anfangs gewählt?
    var gruppeOffen by rememberSaveable { mutableStateOf(false) }
    var gruppeStart by rememberSaveable(stateSaver = KursAuswahlSaver) { mutableStateOf<Set<String>>(emptySet()) }

    // Hausaufgabe direkt aus einer angetippten Stunde: Stunde + Tag, an dem sie stattfindet.
    var aufgabeAusStunde by remember { mutableStateOf<Pair<Lesson, LocalDate>?>(null) }
    var naechsteStunde by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(aufgabeAusStunde) {
        naechsteStunde = null
        val (lesson, datum) = aufgabeAusStunde ?: return@LaunchedEffect
        naechsteStunde = try {
            planViewModel.naechsteStundeVon(lesson, datum)
        } catch (e: CancellationException) {
            throw e // Dialog geschlossen – Abbruch nicht verschlucken
        } catch (e: Exception) {
            null
        }
    }

    // Ein angetippter Freund-Link öffnet das Formular mit den Kursen des Freundes – aber erst,
    // wenn gerade nichts anderes bearbeitet wird (anderer Freund, Kurswahl), sonst gingen
    // ungespeicherte Eingaben verloren. Danach öffnet es sich von selbst.
    val bearbeitetGerade = entwurfId != null || zeigeKurse || zustand is UiZustand.KurseWaehlen
    LaunchedEffect(importVorschlag, bearbeitetGerade) {
        if (bearbeitetGerade) return@LaunchedEffect
        importVorschlag?.let {
            zeigeEinstellungen = false
            zeigeKurse = false
            zeigeNoten = false
            freundAnsicht = null
            gruppeOffen = false
            entwurfId = it.id
        }
    }

    // "Suche" im Widget: in die Suche wechseln und ins Suchfeld – außer es wird gerade etwas bearbeitet.
    LaunchedEffect(sucheVomWidget, zustand) {
        if (!sucheVomWidget) return@LaunchedEffect
        // Beim Kaltstart ist der Zustand noch "lädt": abwarten, sonst landet man nach der Einrichtung in der Suche.
        if (zustand is UiZustand.Laedt) return@LaunchedEffect
        if (entwurfId == null && !zeigeKurse && zustand !is UiZustand.KurseWaehlen && zustand !is UiZustand.LoginNoetig) {
            zeigeEinstellungen = false
            zeigeNoten = false
            freundAnsicht = null
            gruppeOffen = false
            tab = Tab.SUCHE
            sucheFokus = true
        }
        sucheOeffnen.value = false
    }

    // Nach einer Kursänderung oder einem Neu-Laden steht die Woche auf "nicht geladen".
    // Ist der Wochen-Reiter dann offen, muss sie hier nachgeladen werden – sonst dreht
    // der Ladekreis endlos, weil nur ein Tipp auf den Reiter das Laden auslöste.
    LaunchedEffect(tab, wochenZustand) {
        if (tab == Tab.WOCHE && wochenZustand is WochenZustand.NichtGeladen) planViewModel.wocheLaden()
    }
    LaunchedEffect(tab) {
        if (tab == Tab.SUCHE) sucheViewModel.oeffnen()
    }
    // Auch bei der Rückkehr in die App (z.B. am nächsten Morgen) nicht den alten Stand zeigen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (tab == Tab.SUCHE) sucheViewModel.oeffnen()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun widgetAktualisieren() {
        RefreshScheduler.sofortAktualisieren(context)
        scope.launch { NextLessonWidgetReceiver.alleWidgetsAktualisieren(context) }
    }

    // Meldungen wie "Anna ist schon gespeichert".
    val hinweis by freundeViewModel.hinweis.collectAsState()
    LaunchedEffect(hinweis) {
        hinweis?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            freundeViewModel.hinweisGezeigt()
        }
    }

    // Einrichtung geht immer vor: erst Zugang, dann Kurse.
    val brauchtZugang = zustand is UiZustand.LoginNoetig
    val brauchtKurse = zustand is UiZustand.KurseWaehlen
    // Unbekannte ID = neuer Freund, der noch nicht gespeichert ist.
    val entwurf = entwurfId?.let { id ->
        freunde.firstOrNull { it.id == id }?.let { FreundEntwurf(it, neu = false) }
            ?: FreundEntwurf(
                importVorschlag?.takeIf { it.id == id } ?: Freund(id = id, name = "", kurse = emptySet()),
                neu = true
            )
    }
    // Gelöschter Freund → Ansicht schließt sich von selbst.
    val angezeigterFreund = freundAnsicht?.let { id -> freunde.firstOrNull { it.id == id } }
    // Überlagernde Ansichten verdecken die Reiter und lassen sich mit "Zurück" schließen.
    val overlay = zeigeEinstellungen || zeigeKurse || zeigeNoten || entwurf != null || gruppeOffen || angezeigterFreund != null
    val einrichtung = brauchtZugang || brauchtKurse || overlay

    // Die Zurück-Taste schloss vorher die ganze App, auch aus den Einstellungen heraus.
    fun entwurfSchliessen() {
        entwurfId = null
        freundeViewModel.importVerwerfen()
    }

    fun zurueck() {
        when {
            entwurfId != null -> entwurfSchliessen()
            zeigeKurse -> zeigeKurse = false
            zeigeEinstellungen -> zeigeEinstellungen = false
            zeigeNoten -> zeigeNoten = false
            gruppeOffen -> gruppeOffen = false
            freundAnsicht != null -> freundAnsicht = null
            tab != Tab.HEUTE -> tab = Tab.HEUTE
        }
    }
    val zurueckMoeglich = !brauchtZugang && (overlay || (!brauchtKurse && tab != Tab.HEUTE))
    BackHandler(enabled = zurueckMoeglich) { zurueck() }

    // Fächer, für die man in den Einstellungen eine Farbe wählen kann.
    val fachNamen = remember(zustand, wochenZustand, hausaufgaben, pruefungen) {
        val ausPlan = (zustand as? UiZustand.Angezeigt)?.plan?.plan?.stunden?.map { it.fach }.orEmpty()
        val ausWoche = (wochenZustand as? WochenZustand.Geladen)?.tage
            ?.flatMap { t -> t.plan?.stunden?.map { it.fach }.orEmpty() }.orEmpty()
        (ausPlan + ausWoche + hausaufgaben.map { it.fach } + pruefungen.map { it.fach })
            .map { it.trim() }
            .filter { f -> f.any { it.isLetter() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    val offeneAufgaben = hausaufgaben.count { !it.erledigt }

    val titel = when {
        brauchtZugang -> "Einstellungen"
        entwurf != null -> if (entwurf.neu) "Freund hinzufügen" else entwurf.freund.name
        zeigeEinstellungen -> "Einstellungen"
        zeigeNoten -> "Noten"
        brauchtKurse || zeigeKurse -> "Deine Kurse"
        gruppeOffen -> "Gemeinsam frei"
        angezeigterFreund != null -> angezeigterFreund.name
        else -> tab.titel
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Etwas heller als der Seitengrund, damit sich die Leiste absetzt.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text(titel) },
                navigationIcon = {
                    if (overlay && !brauchtZugang) {
                        IconButton(onClick = { zurueck() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (!einrichtung && (tab == Tab.HEUTE || tab == Tab.WOCHE || tab == Tab.SUCHE)) {
                        IconButton(onClick = {
                            if (tab == Tab.SUCHE) {
                                sucheViewModel.aktualisieren()
                            } else {
                                planViewModel.aktualisieren()
                                if (tab == Tab.WOCHE) planViewModel.wocheLaden(erzwingen = true)
                                widgetAktualisieren()
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                    val angezeigt = zustand as? UiZustand.Angezeigt
                    if (!einrichtung && tab == Tab.HEUTE && angezeigt != null) {
                        IconButton(onClick = { teilen(context, planAlsText(angezeigt.plan), "Plan teilen") }) {
                            Icon(Icons.Filled.Share, contentDescription = "Plan teilen")
                        }
                    }
                    if (!einrichtung && (tab == Tab.HEUTE || tab == Tab.PRUEFUNGEN)) {
                        IconButton(onClick = { zeigeNoten = true }) {
                            Icon(Icons.Filled.Star, contentDescription = "Noten")
                        }
                    }
                    if (!brauchtZugang && !brauchtKurse && entwurf == null) {
                        IconButton(onClick = {
                            zeigeNoten = false
                            zeigeKurse = false
                            zeigeEinstellungen = !zeigeEinstellungen
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!einrichtung) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Tab.entries.forEach { eintrag ->
                        NavigationBarItem(
                            selected = tab == eintrag,
                            onClick = {
                                tab = eintrag
                                if (eintrag == Tab.WOCHE) planViewModel.wocheLaden()
                            },
                            icon = {
                                if (eintrag == Tab.HAUSAUFGABEN && offeneAufgaben > 0) {
                                    BadgedBox(badge = { Badge { Text("$offeneAufgaben") } }) {
                                        Icon(eintrag.symbol, contentDescription = eintrag.titel)
                                    }
                                } else {
                                    Icon(eintrag.symbol, contentDescription = eintrag.titel)
                                }
                            },
                            label = { Text(eintrag.titel) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                // Freund anlegen/bearbeiten (aus den Einstellungen oder der Freund-Ansicht)
                entwurf != null && !brauchtZugang -> {
                    if (verfuegbareKurse.isEmpty()) {
                        KurslisteLaden(aktualisiertGerade) { planViewModel.aktualisieren() }
                    } else {
                        FreundBearbeitenScreen(
                            freund = entwurf.freund,
                            istNeu = entwurf.neu,
                            verfuegbareKurse = verfuegbareKurse,
                            onSpeichern = { f ->
                                freundeViewModel.speichern(f)
                                entwurfSchliessen()
                            },
                            onLoeschen = {
                                freundeViewModel.loeschen(entwurf.freund.id)
                                entwurfSchliessen()
                            },
                            onAbbrechen = { entwurfSchliessen() }
                        )
                    }
                }

                // 1. Zugangsdaten und Einstellungen
                brauchtZugang || zeigeEinstellungen -> {
                    EinstellungenScreen(
                        // Einmal beim Öffnen entschlüsseln, nicht bei jedem Neuzeichnen.
                        credentials = remember { planViewModel.aktuelleCredentials() },
                        erinnerung = erinnerung,
                        grosserText = grosserText,
                        onZugangSpeichern = { creds ->
                            planViewModel.anmelden(creds)
                            zeigeEinstellungen = false
                            widgetAktualisieren()
                        },
                        onErinnerungSetzen = { aktiv, std, min ->
                            aufgabenViewModel.erinnerungSetzen(aktiv, std, min)
                        },
                        onGrosserTextSetzen = {
                            planViewModel.grosserTextSetzen(it)
                            widgetAktualisieren()
                        },
                        onKurseAendern = {
                            zeigeEinstellungen = false
                            zeigeKurse = true
                        },
                        freunde = freunde,
                        onFreundBearbeiten = { f -> entwurfId = f?.id ?: UUID.randomUUID().toString() },
                        onLinkImportieren = { freundeViewModel.importVorschlagen(it.copy(id = UUID.randomUUID().toString())) },
                        // Ohne gespeicherte Kurse gibt es nichts zu teilen – dann kein Knopf.
                        onKurseTeilen = planViewModel.aktuelleKursAuswahl().takeIf { it.isNotEmpty() }?.let { kurse ->
                            { teilen(context, FreundTeilen.nachricht("", kurse), "Kurse teilen") }
                        },
                        modus = design.modus,
                        onModus = design::modusSetzen,
                        fachNamen = fachNamen,
                        fachFarben = design.fachFarben,
                        onFachFarbe = design::fachFarbeSetzen,
                        update = update,
                        installierterBuild = updateViewModel.installiert,
                        onUpdatePruefen = { updateViewModel.pruefen() },
                        onUpdateLaden = updateViewModel::herunterladen,
                        onUpdateInstallieren = { updateViewModel.installieren(context) }
                    )
                }

                // Notentracker
                zeigeNoten && !brauchtKurse -> {
                    NotenScreen(
                        noten = noten,
                        klausurAnteil = klausurAnteil,
                        pruefungen = pruefungen,
                        fachVorschlaege = fachNamen,
                        onAnteil = notenViewModel::anteilSetzen,
                        onHinzufuegen = notenViewModel::hinzufuegen,
                        onBearbeiten = notenViewModel::bearbeiten,
                        onLoeschen = notenViewModel::loeschen
                    )
                }

                // 2. Kurse wählen
                // "Kurse ändern", aber noch kein Plan geladen: erst laden statt eine leere
                // Liste mit der irreführenden Meldung "keine Kurse hinterlegt" zu zeigen.
                zeigeKurse && !brauchtKurse && verfuegbareKurse.isEmpty() -> {
                    KurslisteLaden(aktualisiertGerade) { planViewModel.aktualisieren() }
                }

                brauchtKurse || zeigeKurse -> {
                    KursAuswahlScreen(
                        verfuegbareKurse = if (brauchtKurse) {
                            (zustand as UiZustand.KurseWaehlen).kurse
                        } else {
                            verfuegbareKurse
                        },
                        gewaehlteKurse = planViewModel.aktuelleKursAuswahl(),
                        onSpeichern = { kurse ->
                            planViewModel.kursAuswahlSpeichern(kurse)
                            zeigeKurse = false
                            widgetAktualisieren()
                        },
                        onAbbrechen = if (brauchtKurse) null else ({ zeigeKurse = false })
                    )
                }

                // Gemeinsame Freistunden der Woche (du + gewählte Freunde)
                gruppeOffen && !brauchtZugang -> {
                    GemeinsamFreiScreen(
                        freunde = freunde,
                        startAuswahl = gruppeStart,
                        woche = freundeWoche,
                        onWocheLaden = freundeViewModel::wocheLaden
                    )
                }

                // 3. Tag eines Freundes
                angezeigterFreund != null -> {
                    val angezeigt = zustand as? UiZustand.Angezeigt
                    if (angezeigt != null) {
                        FreundTagScreen(
                            freund = angezeigterFreund,
                            persoenlich = angezeigt.plan,
                            onWoche = {
                                gruppeStart = setOf(angezeigterFreund.id)
                                gruppeOffen = true
                            },
                            onBearbeiten = { entwurfId = angezeigterFreund.id },
                            blockAnsicht = design.blockAnsicht,
                            onBlockAnsicht = design::blockAnsichtSetzen
                        )
                    } else {
                        LadeScreen()
                    }
                }

                else -> when (tab) {
                    Tab.HEUTE -> PullToRefreshBox(
                        isRefreshing = aktualisiertGerade,
                        onRefresh = {
                            planViewModel.aktualisieren()
                            widgetAktualisieren()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (val z = zustand) {
                            is UiZustand.Laedt -> LadeScreen()
                            is UiZustand.Angezeigt -> HomeScreen(
                                persoenlich = z.plan,
                                uebersicht = uebersicht,
                                aktualisiertGerade = aktualisiertGerade,
                                onOeffneAufgaben = { tab = Tab.HAUSAUFGABEN },
                                onOeffnePruefungen = { tab = Tab.PRUEFUNGEN },
                                onStundeAntippen = { lesson -> aufgabeAusStunde = lesson to z.plan.datum },
                                freunde = freunde,
                                onFreund = { freundAnsicht = it.id },
                                onFreundeWoche = {
                                    gruppeStart = freunde.map { it.id }.toSet()
                                    gruppeOffen = true
                                },
                                blockAnsicht = design.blockAnsicht,
                                onBlockAnsicht = design::blockAnsichtSetzen,
                                onTagVorbei = { planViewModel.ladeGespeichertUndAktualisiere() }
                            )
                            is UiZustand.KeineStunden -> KeineStundenScreen(
                                onKurseAendern = { zeigeKurse = true },
                                onNeuPruefen = { planViewModel.aktualisieren() }
                            )
                            is UiZustand.KeinPlan -> KeinPlanScreen(
                                naechster = z.naechster,
                                gesucht = z.gesucht,
                                onNeuPruefen = { planViewModel.aktualisieren() }
                            )
                            is UiZustand.Fehler -> FehlerScreen(
                                nachricht = z.nachricht,
                                zugangsproblem = z.zugangsproblem,
                                onErneutVersuchen = { planViewModel.aktualisieren() },
                                onEinstellungen = { zeigeEinstellungen = true }
                            )
                            else -> LadeScreen()
                        }
                    }

                    Tab.WOCHE -> {
                        val auswahl by planViewModel.wochenAuswahl.collectAsState()
                        PullToRefreshBox(
                            isRefreshing = wochenZustand is WochenZustand.Laedt,
                            onRefresh = {
                                planViewModel.aktualisieren()
                                planViewModel.wocheLaden(erzwingen = true)
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            WochenScreen(
                                zustand = wochenZustand,
                                auswahl = auswahl,
                                onAuswahlChange = planViewModel::setWochenAuswahl,
                                blockAnsicht = design.blockAnsicht,
                                onBlockAnsicht = design::blockAnsichtSetzen
                            )
                        }
                    }

                    // Beim Laden zeigt die Suche selbst einen Ladekreis – kein zweiter oben.
                    Tab.SUCHE -> PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { sucheViewModel.aktualisieren() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SucheScreen(
                            zustand = sucheZustand,
                            datum = sucheDatum,
                            onBlaettern = sucheViewModel::blaettern,
                            onHeute = sucheViewModel::zuHeute,
                            onNeuLaden = sucheViewModel::aktualisieren,
                            favoriten = favoriten,
                            onFavorit = sucheViewModel::favoritUmschalten,
                            woche = sucheWoche,
                            onWocheLaden = sucheViewModel::wocheLaden,
                            verlauf = verlauf,
                            onGewaehlt = sucheViewModel::gewaehlt,
                            fokusAnfordern = sucheFokus,
                            onFokusErledigt = { sucheFokus = false }
                        )
                    }

                    Tab.HAUSAUFGABEN -> HausaufgabenScreen(
                        aufgaben = hausaufgaben,
                        onUmschalten = aufgabenViewModel::hausaufgabeUmschalten,
                        onLoeschen = aufgabenViewModel::hausaufgabeLoeschen,
                        onHinzufuegen = aufgabenViewModel::hausaufgabeHinzufuegen,
                        onBearbeiten = aufgabenViewModel::hausaufgabeBearbeiten
                    )

                    Tab.PRUEFUNGEN -> PruefungenScreen(
                        pruefungen = pruefungen,
                        onLoeschen = aufgabenViewModel::pruefungLoeschen,
                        onHinzufuegen = aufgabenViewModel::pruefungHinzufuegen,
                        onBearbeiten = aufgabenViewModel::pruefungBearbeiten
                    )
                }
            }
        }
    }
    // Patchnotes: einmalig nach einem Update. "Gesehen" wird erst beim Schließen gemerkt.
    val patchPrefs = remember { context.getSharedPreferences("patchnotes", Context.MODE_PRIVATE) }
    var patchGesehen by rememberSaveable { mutableStateOf(patchGesehenStart(context, patchPrefs)) }
    val patchEintraege = remember(patchGesehen) { Patchnotes.ungesehen(patchGesehen) }

    // Hinweis auf einen neuen Build – nicht mitten in der Einrichtung und nur einmal pro Start.
    val gefundenerBuild = when (val u = update) {
        is UpdateZustand.Verfuegbar -> u.build
        is UpdateZustand.Laedt -> u.build
        is UpdateZustand.Bereit -> u.build
        is UpdateZustand.Fehler -> u.wiederholbar?.build ?: 0
        else -> 0
    }
    val updateDialogOffen = gefundenerBuild != 0 && gefundenerBuild != spaeterBuild && !einrichtung
    fun patchGesehenMerken() {
        patchPrefs.edit().putInt(PATCH_GESEHEN, Patchnotes.neuesteId).apply()
        patchGesehen = Patchnotes.neuesteId
    }
    if (patchEintraege.isNotEmpty() && !einrichtung && !updateDialogOffen) {
        AlertDialog(
            onDismissRequest = { patchGesehenMerken() },
            title = { Text(patchEintraege.first().titel) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patchEintraege.forEachIndexed { index, eintrag ->
                        // Der neueste Eintrag steht im Titel; ältere, noch ungesehene, bekommen eine Überschrift.
                        if (index > 0) Text(eintrag.titel, style = MaterialTheme.typography.titleSmall)
                        eintrag.punkte.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { patchGesehenMerken() }) { Text("Verstanden") } }
        )
    }
    if (updateDialogOffen) {
        AlertDialog(
            onDismissRequest = { spaeterBuild = gefundenerBuild },
            title = { Text("Neue Version") },
            text = {
                Text(
                    when (val u = update) {
                        is UpdateZustand.Laedt -> "Lade Build ${u.build} … ${u.prozent} %"
                        is UpdateZustand.Bereit -> "Build ${u.build} ist heruntergeladen und bereit zum Installieren."
                        is UpdateZustand.Fehler -> u.nachricht
                        else -> "Build $gefundenerBuild ist verfügbar (du hast ${updateViewModel.installiert})."
                    }
                )
            },
            confirmButton = {
                when (update) {
                    is UpdateZustand.Verfuegbar ->
                        TextButton(onClick = { updateViewModel.herunterladen() }) { Text("Herunterladen") }
                    is UpdateZustand.Fehler ->
                        TextButton(onClick = { updateViewModel.herunterladen() }) { Text("Erneut versuchen") }
                    is UpdateZustand.Bereit ->
                        TextButton(onClick = { updateViewModel.installieren(context) }) { Text("Installieren") }
                    else -> Unit
                }
            },
            dismissButton = { TextButton(onClick = { spaeterBuild = gefundenerBuild }) { Text("Später") } }
        )
    }

    aufgabeAusStunde?.let { (lesson, _) ->
        HausaufgabeDialog(
            vorschlagFach = lesson.anzeigeName().orEmpty(),
            naechsteStunde = naechsteStunde,
            onAbbrechen = { aufgabeAusStunde = null },
            onSpeichern = { fach, text, faellig ->
                aufgabenViewModel.hausaufgabeHinzufuegen(fach, text, faellig)
                aufgabeAusStunde = null
            }
        )
    }
}

/**
 * Die Kursliste wird gebraucht, ist aber noch nicht geladen: laden statt eine leere Liste mit
 * der irreführenden Meldung "keine Kurse hinterlegt" zu zeigen.
 */
@Composable
private fun KurslisteLaden(aktualisiertGerade: Boolean, onLaden: () -> Unit) {
    var versucht by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onLaden()
        versucht = true
    }
    if (!versucht || aktualisiertGerade) {
        LadeScreen()
    } else {
        FehlerScreen(
            nachricht = "Die Kursliste konnte nicht geladen werden.",
            zugangsproblem = false,
            onErneutVersuchen = onLaden,
            onEinstellungen = {}
        )
    }
}
