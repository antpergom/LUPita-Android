package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {

    private fun models(vararg pairs: Pair<String, String>) = pairs.map { ModelOption(it.first, it.second) }

    @Test
    fun `a valid catalog is parsed in order`() {
        val parsed = ModelCatalog.parse("""{"models":[{"id":"a","label":"Alpha"},{"id":"b","label":"Beta"}]}""")

        assertEquals(models("a" to "Alpha", "b" to "Beta"), parsed)
    }

    @Test
    fun `unknown keys are ignored so the file can grow without breaking old builds`() {
        val parsed = ModelCatalog.parse("""{"version":2,"models":[{"id":"a","label":"Alpha","price":3}]}""")

        assertEquals(models("a" to "Alpha"), parsed)
    }

    @Test
    fun `malformed json gives an empty catalog instead of crashing`() {
        assertEquals(emptyList<ModelOption>(), ModelCatalog.parse("{not json"))
        assertEquals(emptyList<ModelOption>(), ModelCatalog.parse(""))
    }

    @Test
    fun `a file without the models key gives an empty catalog`() {
        assertEquals(emptyList<ModelOption>(), ModelCatalog.parse("""{"other":[]}"""))
    }

    @Test
    fun `a wrongly typed entry makes the catalog empty rather than half-loaded`() {
        assertEquals(emptyList<ModelOption>(), ModelCatalog.parse("""{"models":[{"id":1,"label":"x"}]}"""))
    }

    @Test
    fun `entries without id or label are dropped`() {
        val parsed = ModelCatalog.parse("""{"models":[{"id":"","label":"x"},{"id":"a","label":" "},{"id":"b","label":"Beta"}]}""")

        assertEquals(models("b" to "Beta"), parsed)
    }

    @Test
    fun `the first entry wins when an id is repeated`() {
        val parsed = ModelCatalog.parse("""{"models":[{"id":"a","label":"First"},{"id":"a","label":"Second"}]}""")

        assertEquals(models("a" to "First"), parsed)
    }

    @Test
    fun `the stored model is used while it is still in the catalog`() {
        assertEquals("b", ModelCatalog.effectiveSelection(models("a" to "A", "b" to "B"), storedId = "b"))
    }

    @Test
    fun `a stored model that left the catalog falls back to the first one`() {
        assertEquals("a", ModelCatalog.effectiveSelection(models("a" to "A", "b" to "B"), storedId = "gone"))
    }

    @Test
    fun `with nothing stored the first model is used`() {
        assertEquals("a", ModelCatalog.effectiveSelection(models("a" to "A"), storedId = null))
    }

    @Test
    fun `an empty catalog selects nothing`() {
        assertNull(ModelCatalog.effectiveSelection(emptyList(), storedId = "a"))
        assertNull(ModelCatalog.effectiveSelection(emptyList(), storedId = null))
    }
}
