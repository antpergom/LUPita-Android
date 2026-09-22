package com.antoniopg.lupita.capability.text

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun `a real spanish sentence is detected`() {
        val text = "El gato de la casa no quiere comer y por eso su dueño esta muy preocupado."

        assertEquals(DetectedLanguage.SPANISH, LanguageDetector.detect(text))
    }

    @Test
    fun `a real english sentence is detected`() {
        val text = "The cat in the house does not want to eat and that is why the owner is worried."

        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detect(text))
    }

    @Test
    fun `french, german and portuguese are also covered`() {
        assertEquals(DetectedLanguage.FRENCH, LanguageDetector.detect("Le chat de la maison ne veut pas manger et il est tout triste."))
        assertEquals(DetectedLanguage.GERMAN, LanguageDetector.detect("Die Katze im Haus will nicht essen und das ist sehr traurig fur sie."))
        assertEquals(DetectedLanguage.PORTUGUESE, LanguageDetector.detect("O gato da casa nao quer comer e por isso o dono esta muito preocupado."))
    }

    @Test
    fun `very short text is unknown, not guessed`() {
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("el la"))
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect(""))
    }

    @Test
    fun `text with no recognizable stopwords at all is unknown`() {
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("Lorem ipsum dolor sit amet consectetur adipiscing"))
    }

    @Test
    fun `a tie between two languages is unknown, never guessed`() {
        // Mismo numero de palabras funcionales de cada idioma, a proposito.
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("el la the of xyz abc"))
    }

    @Test
    fun `detection is case insensitive`() {
        assertEquals(
            LanguageDetector.detect("EL GATO DE LA CASA NO QUIERE COMER Y ESO PREOCUPA A SU DUEÑO"),
            LanguageDetector.detect("el gato de la casa no quiere comer y eso preocupa a su dueño"),
        )
    }
}
