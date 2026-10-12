package com.nextlesson.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Ist das Design dunkel? Vom Theme gesetzt – gilt auch, wenn es abweichend vom System gewählt ist. */
val LocalDunkel = staticCompositionLocalOf { false }

/** Eigene Fachfarben (Fach klein geschrieben → ARGB) aus den Einstellungen. */
val LocalFachFarben = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** Soll vor dem Fachnamen ein Symbol stehen? */
val LocalFachSymbole = staticCompositionLocalOf { true }

/** Eigene Anzeigenamen der Fächer (Fach-Schlüssel → Name). */
val LocalFachNamen = compositionLocalOf<Map<String, String>> { emptyMap() }

/** Zeilenabstand der Stundenliste in dp. */
val LocalListenAbstand = staticCompositionLocalOf { 10 }

/** Fachname für die Anzeige: ein eigener Name, sonst der aus dem Plan; je nach Einstellung mit Symbol. */
@Composable
fun fachAnzeige(fach: String): String {
    val name = LocalFachNamen.current[com.nextlesson.app.data.FachName.schluessel(fach)] ?: fach
    // Das Symbol gehört zum Fach aus dem Plan; der Text dahinter ist der eigene Name.
    val symbol = if (LocalFachSymbole.current) com.nextlesson.app.data.FachSymbole.fuer(fach) else null
    return if (symbol != null) "$symbol $name" else name
}

/**
 * Das Mocha-Design.
 *
 * [dynamischeFarben] ist bewusst aus: mit Material You würde Android die Farben aus dem
 * Hintergrundbild ableiten, und die App sähe auf jedem Gerät anders aus. Auf true gesetzt
 * übernimmt sie wieder die Systemfarben.
 *
 * Soll die App immer dunkel bleiben, egal was das System sagt, hier
 * `dunkel: Boolean = true` setzen.
 */
@Composable
fun NaechsteStundeTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    dynamischeFarben: Boolean = false,
    fachFarben: Map<String, Int> = emptyMap(),
    fachSymbole: Boolean = true,
    farbThema: FarbThema = FarbThema.MOCHA,
    eigenerTon: Float = 30f,
    fachNamen: Map<String, String> = emptyMap(),
    listenAbstand: Int = 10,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val schema = when {
        dynamischeFarben && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dunkel -> DunklesSchema.mitThema(farbThema, hell = false, eigenerTon = eigenerTon)
        else -> HellesSchema.mitThema(farbThema, hell = true, eigenerTon = eigenerTon)
    }

    MaterialTheme(colorScheme = schema, typography = AppTypografie) {
        CompositionLocalProvider(
            LocalDunkel provides dunkel,
            LocalFachFarben provides fachFarben,
            LocalFachSymbole provides fachSymbole,
            LocalFachNamen provides fachNamen,
            LocalListenAbstand provides listenAbstand,
            content = content
        )
    }
}
