package com.nextlesson.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.nextlesson.app.ui.theme.NaechsteStundeTheme
import com.nextlesson.app.widget.NextLessonWidgetReceiver
import com.nextlesson.app.work.EntfallNotifier
import com.nextlesson.app.work.LernErinnerung
import com.nextlesson.app.work.RefreshScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val planViewModel: PlanViewModel by viewModels()
    private val aufgabenViewModel: AufgabenViewModel by viewModels()

    private val benachrichtigungAnfrage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Ergebnis egal */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        EntfallNotifier.kanalAnlegen(applicationContext)
        LernErinnerung.kanalAnlegen(applicationContext)
        RefreshScheduler.periodischePruefungEinplanen(applicationContext)
        benachrichtigungErlaubnisAnfragen()

        setContent {
            NaechsteStundeTheme {
                Surface {
                    AppInhalt(planViewModel, aufgabenViewModel)
                }
            }
        }
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

private enum class Tab(val titel: String, val symbol: ImageVector) {
    HEUTE("Heute", Icons.Filled.CheckCircle),
    WOCHE("Woche", Icons.Filled.DateRange),
    HAUSAUFGABEN("Aufgaben", Icons.Filled.Edit),
    PRUEFUNGEN("Klausuren", Icons.AutoMirrored.Filled.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInhalt(
    planViewModel: PlanViewModel,
    aufgabenViewModel: AufgabenViewModel
) {
    val zustand by planViewModel.zustand.collectAsState()
    val wochenZustand by planViewModel.wochenZustand.collectAsState()
    val hausaufgaben by aufgabenViewModel.hausaufgaben.collectAsState()
    val pruefungen by aufgabenViewModel.pruefungen.collectAsState()
    val erinnerung by aufgabenViewModel.erinnerung.collectAsState()
    val grosserText by planViewModel.grosserText.collectAsState()
    val aktualisiertGerade by planViewModel.aktualisiertGerade.collectAsState()
    val uebersicht = remember(hausaufgaben, pruefungen) {
        Uebersicht.berechne(hausaufgaben, pruefungen)
    }

    var tab by remember { mutableStateOf(Tab.HEUTE) }
    var zeigeEinstellungen by remember { mutableStateOf(false) }
    var zeigeKurse by remember { mutableStateOf(false) }

    // Nach einer Kursänderung oder einem Neu-Laden steht die Woche auf "nicht geladen".
    // Ist der Wochen-Reiter dann offen, muss sie hier nachgeladen werden – sonst dreht
    // der Ladekreis endlos, weil nur ein Tipp auf den Reiter das Laden auslöste.
    LaunchedEffect(tab, wochenZustand) {
        if (tab == Tab.WOCHE && wochenZustand is WochenZustand.NichtGeladen) planViewModel.wocheLaden()
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
    val einrichtung = brauchtZugang || brauchtKurse || zeigeEinstellungen || zeigeKurse

    val offeneAufgaben = hausaufgaben.count { !it.erledigt }

    Scaffold(
        topBar = {
            TopAppBar(
                // Etwas heller als der Seitengrund, damit sich die Leiste absetzt.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        when {
                            brauchtZugang || zeigeEinstellungen -> "Einstellungen"
                            brauchtKurse || zeigeKurse -> "Deine Kurse"
                            else -> tab.titel
                        }
                    )
                },
                actions = {
                    if (!einrichtung && (tab == Tab.HEUTE || tab == Tab.WOCHE)) {
                        IconButton(onClick = {
                            planViewModel.aktualisieren()
                            if (tab == Tab.WOCHE) planViewModel.wocheLaden()
                            widgetAktualisieren()
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                    if (!brauchtZugang && !brauchtKurse) {
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
                // 1. Zugangsdaten und Einstellungen
                brauchtZugang || zeigeEinstellungen -> {
                    EinstellungenScreen(
                        credentials = planViewModel.aktuelleCredentials(),
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
                        }
                    )
                }

                // 2. Kurse wählen
                brauchtKurse || zeigeKurse -> {
                    KursAuswahlScreen(
                        verfuegbareKurse = if (brauchtKurse) {
                            (zustand as UiZustand.KurseWaehlen).kurse
                        } else {
                            planViewModel.verfuegbareKurse()
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
                                onOeffnePruefungen = { tab = Tab.PRUEFUNGEN }
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
                                planViewModel.wocheLaden()
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            WochenScreen(
                                zustand = wochenZustand,
                                auswahl = auswahl,
                                onAuswahlChange = planViewModel::setWochenAuswahl
                            )
                        }
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
}
