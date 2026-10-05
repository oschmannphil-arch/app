package com.nextlesson.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nextlesson.app.data.DesignStore

/*
 * "Mocha": warmes Dunkelbraun als Grund, Karamell für die Hauptkarte,
 * gedecktes Schieferblau für die Stundenliste.
 *
 * Diese Werte sind bewusst fest verdrahtet und nicht aus dem Hintergrundbild
 * abgeleitet (Material You) – sonst sieht die App auf jedem Gerät anders aus.
 */

// --- Dunkel (das eigentliche Mocha-Design) ---
private val Grund = Color(0xFF2E241C)          // Seitenhintergrund
private val GrundAufsatz = Color(0xFF3B2E26)   // App-Leiste und untere Navigation
private val GrundAufsatzHoch = Color(0xFF473729)
private val Creme = Color(0xFFF3E7DA)          // Haupttext auf Braun

private val Karamell = Color(0xFFD9B48F)       // "Nächste Stunde"-Karte, heutiger Tag
private val KaramellText = Color(0xFF3A2A16)   // Text darauf – dunkel, nicht creme
private val KaramellHell = Color(0xFFE6C6A0)   // Akzente, Symbole

private val Schiefer = Color(0xFF3B4B53)       // Stundenzeilen
private val SchieferText = Color(0xFFC9D1D5)
private val SchieferHell = Color(0xFF46606B)   // laufende Stunde, ausgewählter Tab
private val SchieferHellText = Color(0xFFE4EFF4)

val DunklesSchema = darkColorScheme(
    primary = KaramellHell,
    onPrimary = Color(0xFF422C14),
    primaryContainer = Karamell,
    onPrimaryContainer = KaramellText,

    secondary = Color(0xFFA6C3CF),
    onSecondary = Color(0xFF123039),
    secondaryContainer = SchieferHell,
    onSecondaryContainer = SchieferHellText,

    tertiary = Color(0xFFC9B18C),
    onTertiary = Color(0xFF35291A),
    tertiaryContainer = Color(0xFF4F3F2C),
    onTertiaryContainer = Color(0xFFEDD9BC),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Grund,
    onBackground = Creme,
    surface = Grund,
    onSurface = Creme,
    surfaceVariant = Schiefer,
    onSurfaceVariant = SchieferText,

    surfaceContainerLowest = Color(0xFF241B15),
    surfaceContainerLow = Color(0xFF33281F),
    surfaceContainer = GrundAufsatz,
    surfaceContainerHigh = GrundAufsatzHoch,
    surfaceContainerHighest = Color(0xFF52402F),

    outline = Color(0xFFA08D7B),
    outlineVariant = Color(0xFF4E3F34)
)

// --- Hell: dieselbe Familie, nur andersherum (Creme-Grund, Karamell-Karten) ---
val HellesSchema = lightColorScheme(
    primary = Color(0xFF7A5A33),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0D3B0),
    onPrimaryContainer = Color(0xFF2C1B06),

    secondary = Color(0xFF4C6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE3EC),
    onSecondaryContainer = Color(0xFF08222C),

    tertiary = Color(0xFF6B5B3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4DFBA),
    onTertiaryContainer = Color(0xFF241A04),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),

    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF241A12),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF241A12),
    surfaceVariant = Color(0xFFE2E8EB),
    onSurfaceVariant = Color(0xFF44505A),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF2E8),
    surfaceContainer = Color(0xFFF6EADE),
    surfaceContainerHigh = Color(0xFFF0E3D6),
    surfaceContainerHighest = Color(0xFFEADCCE),

    outline = Color(0xFF83766A),
    outlineVariant = Color(0xFFD5C6B8)
)

/**
 * Feste Farbtupfer für Fächer. Ein Fach bekommt über seinen Namen immer dieselbe Farbe,
 * damit man den Plan mit der Zeit auch ohne Lesen überfliegen kann.
 * Die Töne sind auf das Mocha-Schema abgestimmt.
 */
private val FachFarbenHell = listOf(
    Color(0xFF8D5B2C), Color(0xFF2F6B63), Color(0xFF9C3B5A), Color(0xFF5D4037),
    Color(0xFF2A5D82), Color(0xFF6A4A86), Color(0xFF4C6B2A), Color(0xFFB2601C)
)

private val FachFarbenDunkel = listOf(
    Color(0xFFE0B183), Color(0xFF7FC7BC), Color(0xFFEE9CB4), Color(0xFFC7A99A),
    Color(0xFF8FC0E4), Color(0xFFC3A5E0), Color(0xFFB5D08A), Color(0xFFF0B278)
)

/** Auswahl für "Farbe pro Fach" in den Einstellungen – in Hell und Dunkel gut lesbar. */
val FachFarbAuswahl: List<Color> = listOf(
    Color(0xFFD9822B), Color(0xFF2E9E8F), Color(0xFFD6527A), Color(0xFF5C8DD6),
    Color(0xFF8E63CE), Color(0xFF6FA83A), Color(0xFFC9A227), Color(0xFFC25B4A),
    Color(0xFF7B6A5F), Color(0xFF4FA3C7)
)

fun fachFarbe(fach: String, dunkel: Boolean): Color {
    DesignStore.aktuell?.fachFarben?.get(fach.trim().lowercase())?.let { return Color(it) }
    if (fach.isBlank()) return if (dunkel) Color(0xFFA08D7B) else Color(0xFF83766A)
    val palette = if (dunkel) FachFarbenDunkel else FachFarbenHell
    // Stabiler, vorzeichenfreier Hash über den Fachnamen.
    var hash = 7
    for (c in fach.trim().lowercase()) hash = (hash * 31 + c.code) and 0x7FFFFFFF
    return palette[hash % palette.size]
}

/** Ist das Design gerade dunkel? Gilt auch, wenn der Nutzer es abweichend vom System gewählt hat. */
@Composable
fun istDunkel(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
