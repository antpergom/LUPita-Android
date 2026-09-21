package com.antoniopg.lupita.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Las tipografias del mock, EMPAQUETADAS (no descargables): funcionan sin red ni Google Play Services y
 * sin el parpadeo de la primera carga. Licencias en `ui/theme/LICENSES/` y en THIRD_PARTY_NOTICES.md.
 *
 * - **Ubuntu** (cuerpo): Regular, Medium y Bold. Los pesos que Ubuntu no tiene (SemiBold, ExtraBold) los
 *   resuelve Compose con el mas cercano.
 * - **Bricolage Grotesque** (titulos): una fuente VARIABLE; se piden los pesos 600 y 700 del mock.
 */
object LupitaFonts {
    val body = FontFamily(
        Font(R.font.ubuntu_regular, FontWeight.Normal),
        Font(R.font.ubuntu_medium, FontWeight.Medium),
        Font(R.font.ubuntu_bold, FontWeight.Bold),
    )

    val heading = FontFamily(
        Font(
            R.font.bricolage_grotesque,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontWeight.SemiBold, androidx.compose.ui.text.font.FontStyle.Normal, FontVariation.weight(600)),
        ),
        Font(
            R.font.bricolage_grotesque,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontWeight.Bold, androidx.compose.ui.text.font.FontStyle.Normal, FontVariation.weight(700)),
        ),
    )
}

/** La tipografia de Material con las familias del mock: titulares en Bricolage, el resto en Ubuntu. */
internal fun lupitaTypography(): Typography {
    val base = Typography()
    val body = LupitaFonts.body
    val heading = LupitaFonts.heading
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = heading),
        displayMedium = base.displayMedium.copy(fontFamily = heading),
        displaySmall = base.displaySmall.copy(fontFamily = heading),
        headlineLarge = base.headlineLarge.copy(fontFamily = heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = heading),
        headlineSmall = base.headlineSmall.copy(fontFamily = heading),
        titleLarge = base.titleLarge.copy(fontFamily = heading),
        titleMedium = base.titleMedium.copy(fontFamily = body),
        titleSmall = base.titleSmall.copy(fontFamily = body),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}
