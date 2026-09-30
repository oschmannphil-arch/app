package com.nextlesson.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val schema = when {
        dynamischeFarben && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dunkel -> DunklesSchema
        else -> HellesSchema
    }

    MaterialTheme(
        colorScheme = schema,
        typography = AppTypografie,
        content = content
    )
}
