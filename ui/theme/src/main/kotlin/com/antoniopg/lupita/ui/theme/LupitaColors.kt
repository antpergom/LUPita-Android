package com.antoniopg.lupita.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta del mock de Claude Design (`LUPita.dc.html`). Los nombres describen el ROL, no el color: en
 * el mock las constantes se llaman `TEAL_*` pero el acento es un rojo anaranjado (#E1472A).
 */
@Immutable
data class LupitaColors(
    val background: Color,
    /** Superficie de tarjeta / botones redondos. */
    val card: Color,
    /** Superficie de fila inactiva y de la burbuja gris. */
    val cardLight: Color,
    val ink: Color,
    /** Texto secundario. */
    val subtle: Color,
    val divider: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    /** Icono sobre el acento (fila activa). */
    val iconOnAccent: Color,
    /** Icono de una fila inactiva. */
    val iconMuted: Color,
    val danger: Color,
)

private val Cream = Color(0xFFFFF8F0)
private val Ink = Color(0xFF1C1A17)
private val Accent = Color(0xFFE1472A)
private val Peach = Color(0xFFFFCDB0)
private val Orange = Color(0xFFE38A63)
private val Danger = Color(0xFF8A2314)

val LightLupitaColors = LupitaColors(
    background = Cream,
    card = Color(0xFFEFE5D5),
    cardLight = Color(0xFFF8F0E1),
    ink = Ink,
    subtle = Color(0x9912262B),
    divider = Color(0x1A12262B),
    track = Color(0x1412262B),
    accent = Accent,
    onAccent = Cream,
    iconOnAccent = Peach,
    iconMuted = Orange,
    danger = Danger,
)

val DarkLupitaColors = LupitaColors(
    background = Ink,
    card = Color(0xFF1C3338),
    cardLight = Color(0xFF1C3338),
    ink = Cream,
    subtle = Color(0x9EF6EFE4),
    divider = Color(0x24F6EFE4),
    track = Color(0x1FF6EFE4),
    accent = Accent,
    onAccent = Cream,
    iconOnAccent = Peach,
    iconMuted = Orange,
    danger = Danger,
)
