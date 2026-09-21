package com.antoniopg.lupita.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regresion: construir las familias lanzaba «'wght' must be unique» (eje repetido) y la app se cerraba
 * nada mas abrir, sin que ningun otro test lo viera. Basta con inicializarlas.
 */
class LupitaFontsTest {

    @Test
    fun `the font families can be built`() {
        assertNotNull(LupitaFonts.body)
        assertNotNull(LupitaFonts.heading)
    }

    @Test
    fun `the typography can be built`() {
        assertNotNull(lupitaTypography())
    }
}
