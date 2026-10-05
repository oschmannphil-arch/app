package com.nextlesson.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nextlesson.app.data.DesignModus
import com.nextlesson.app.data.DesignStore
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.FreundTeilen
import com.nextlesson.app.data.Lesson
import com.nextlesson.app.ui.theme.NaechsteStundeTheme
import com.nextlesson.app.widget.NextLessonWidgetReceiver
import com.nextlesson.app.work.EntfallNotifier
import com.nextlesson.app.work.LernErinnerung
import com.nextlesson.app.work.RefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val planViewModel: PlanViewModel by viewModels()
    private val aufgabenViewModel: AufgabenViewModel by viewModels()
    private val sucheViewModel: SucheViewModel by viewModels()
    private val freundeViewModel: FreundeViewModel by viewModels()
    private lateinit var design: DesignStore

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
        if (savedInstanceState == null && !ausVerlauf) freundLinkAuswerten(intent)

        EntfallNotifier.kanalAnlegen(applicationContext)
        LernErinnerung.kanalAnlegen(applicationContext)
        RefreshScheduler.periodischePruefungEinplanen(applicationContext)
        benachrichtigungErlaubnisAnfragen()

        setContent {
            val dunkel = when (design.modus) {
                DesignModus.SYSTEM -> isSystemInDarkTheme()
                DesignModus.HELL -> false
                DesignModus.DUNKEL -> true
            }
            // Symbole in Status- und Navigationsleiste müssen zum gewählten Design passen.
            DisposableEffect(dunkel) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dunkel },
                    // Mit Schleier wie bei enableEdgeToEdge(): Auf Android 8–9 mit Tasten-
                    // Navigation lägen die Tasten sonst direkt über dem Inhalt.
                    navigationBarStyle = SystemBarStyle.auto(NAVI_SCHLEIER_HELL, NAVI_SCHLEIER_DUNKEL) { dunkel }
                )
                onDispose { }
            }
            NaechsteStundeTheme(dunkel = dunkel, fachFarben = design.fachFarben) {
                Surface {
                    AppInhalt(planViewModel, aufgabenViewModel, sucheViewModel, freundeViewModel, design)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        freundLinkAuswerten(intent)
    }

    /** Ein angetippter Kurs-Link ("nextlesson://freund?…") wird als Freund vorgeschlagen. */
    private fun freundLinkAuswerten(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        FreundTeilen.lesen(intent?.dataString)?.let { freundeViewModel.importVorschlagen(it) }
    }

    /** Beim Öffnen immer frisch nachsehen – nicht auf den 15-Minuten-Takt warten. */
    override fun onResume() {
        super.onResume()
        planViewModel.ladeGespeichertUndAktualisiere()
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
    sucheViewModel: SucheViewModel,
    freundeViewModel: FreundeViewModel,
    design: DesignStore
) {
    val zustand by planViewModel.zustand.collectAsState()
    val wochenZustand by planViewModel.wochenZustand.collectAsState()
    val hausaufgaben by aufgabenViewModel.hausaufgaben.collectAsState()
    val pruefungen by aufgabenViewModel.pruefungen.collectAsState()
    val erinnerung by aufgabenViewModel.erinnerung.collectAsState()
    val grosserText by planViewModel.grosserText.collectAsState()
    val aktualisiertGerade by planViewModel.aktualisiertGerade.collectAsState()
    val verfuegbareKurse by planViewModel.verfuegbareKurse.collectAsState()
    val freunde by freundeViewModel.freunde.collectAsState()
    val sucheZustand by sucheViewModel.zustand.collectAsState()
    val sucheDatum by sucheViewModel.datum.collectAsState()
    val favoriten by sucheViewModel.favoriten.collectAsState()
    val sucheWoche by sucheViewModel.woche.collectAsState()
    val importVorschlag by freundeViewModel.importVorschlag.collectAsState()
    val freundeWoche by freundeViewModel.woche.collectAsState()
    val uebersicht = remember(hausaufgaben, pruefungen) {
        Uebersicht.berechne(hausaufgaben, pruefungen)
    }

    // Saveable: Beim Drehen des Handys bleibt man im gewählten Reiter bzw. Dialog.
    var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }
    var zeigeEinstellungen by rememberSaveable { mutableStateOf(false) }
    var zeigeKurse by rememberSaveable { mutableStateOf(false) }
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

    // Ein angetippter Freund-Link öffnet direkt das Formular mit den Kursen des Freundes.
    LaunchedEffect(importVorschlag) {
        importVorschlag?.let {
            zeigeEinstellungen = false
            zeigeKurse = false
            freundAnsicht = null
            gruppeOffen = false
            entwurfId = it.id
        }
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
    val overlay = zeigeEinstellungen || zeigeKurse || entwurf != null || gruppeOffen || angezeigterFreund != null
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
                    if (!brauchtZugang && !brauchtKurse && entwurf == null) {
                        IconButton(onClick = {
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
                        onKurseTeilen = {
                            val kurse = planViewModel.aktuelleKursAuswahl()
                            if (kurse.isNotEmpty()) teilen(context, FreundTeilen.nachricht("", kurse), "Kurse teilen")
                        },
                        modus = design.modus,
                        onModus = design::modusSetzen,
                        fachNamen = fachNamen,
                        fachFarben = design.fachFarben,
                        onFachFarbe = design::fachFarbeSetzen
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
                            onWocheLaden = sucheViewModel::wocheLaden
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
    aufgabeAusStunde?.let { (lesson, _) ->
        HausaufgabeDialog(
            vorschlagFach = lesson.fach.takeIf { f -> f.any { it.isLetterOrDigit() } }
                ?: lesson.kursKuerzel.orEmpty(),
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
