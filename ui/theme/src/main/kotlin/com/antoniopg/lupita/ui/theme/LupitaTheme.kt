package com.antoniopg.lupita.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalLupitaColors = staticCompositionLocalOf { LightLupitaColors }

/** Acceso a la paleta: `Lupita.colors.accent`. */
object Lupita {
    val colors: LupitaColors
        @Composable @ReadOnlyComposable get() = LocalLupitaColors.current
}

/**
 * Tema de LUPita: claro/oscuro segun el sistema. Ademas de la paleta propia, rellena el
 * `MaterialTheme` para que los componentes de Material (texto, tarjetas, botones) hereden los mismos
 * colores.
 *
 * Tipografia pendiente: el mock usa Ubuntu y Bricolage Grotesque (fuentes descargables); mientras
 * tanto se usa la fuente por defecto del sistema.
 */
@Composable
fun LupitaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkLupitaColors else LightLupitaColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.background, onBackground = c.ink,
            surface = c.background, onSurface = c.ink,
            surfaceVariant = c.card, onSurfaceVariant = c.subtle,
            surfaceContainerHighest = c.card, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.background, onBackground = c.ink,
            surface = c.background, onSurface = c.ink,
            surfaceVariant = c.card, onSurfaceVariant = c.subtle,
            surfaceContainerHighest = c.card, error = c.danger,
        )
    }
    CompositionLocalProvider(LocalLupitaColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
