package com.antoniopg.lupita.capability.context

import com.antoniopg.lupita.core.model.ContentPattern
import com.antoniopg.lupita.core.model.NodeRole
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextNormalizerTest {

    private fun rect(l: Int, t: Int, r: Int = l + 100, b: Int = t + 40) = SelectionRect(l, t, r, b)

    private fun node(
        text: String? = null,
        bounds: SelectionRect = rect(0, 0),
        className: String? = null,
        contentDescription: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        withheld: Boolean = false,
        children: List<UiNode> = emptyList(),
    ) = UiNode(bounds, className, text, contentDescription, isClickable = clickable, isEditable = editable, contentWithheld = withheld, children = children)

    @Test
    fun `empty bounds and empty wrapper nodes are dropped`() {
        val zero = node(text = "x", bounds = SelectionRect(10, 10, 10, 50)) // ancho 0
        val wrapper = node(bounds = rect(0, 0), children = listOf(node(text = "hola", bounds = rect(0, 40))))

        val result = ContextNormalizer.normalize(listOf(zero, wrapper))

        assertEquals(listOf("hola"), result.nodes.map { it.text })
    }

    @Test
    fun `a node hiding its content is kept even without text`() {
        val hidden = node(withheld = true, bounds = rect(0, 0))

        val result = ContextNormalizer.normalize(listOf(hidden))

        assertEquals(1, result.nodes.size)
        assertEquals(null, result.nodes.single().text)
    }

    @Test
    fun `a wrapper repeating its child's bounds and text is deduplicated`() {
        val child = node(text = "hola", bounds = rect(0, 0))
        val parent = node(text = "hola", bounds = rect(0, 0), children = listOf(child))

        val result = ContextNormalizer.normalize(listOf(parent))

        assertEquals(1, result.nodes.size)
    }

    @Test
    fun `nodes come back in reading order, top to bottom then left to right`() {
        val bottomRight = node(text = "d", bounds = rect(200, 200))
        val topRight = node(text = "b", bounds = rect(200, 0))
        val topLeft = node(text = "a", bounds = rect(0, 0))
        val bottomLeft = node(text = "c", bounds = rect(0, 200))

        val result = ContextNormalizer.normalize(listOf(bottomRight, topRight, topLeft, bottomLeft))

        assertEquals(listOf("a", "b", "c", "d"), result.nodes.map { it.text })
    }

    @Test
    fun `plain text is the ordered text joined by newlines`() {
        val result = ContextNormalizer.normalize(listOf(node(text = "uno", bounds = rect(0, 0)), node(text = "dos", bounds = rect(0, 100))))

        assertEquals("uno\ndos", result.plainText)
    }

    @Test
    fun `roles come from class name and flags, never from the text`() {
        val input = node(text = "hunter2", bounds = rect(0, 0), editable = true)
        val button = node(text = "Enviar", bounds = rect(0, 50), className = "android.widget.Button")
        val image = node(contentDescription = "Foto de perfil", bounds = rect(0, 100), className = "android.widget.ImageView")
        val link = node(text = "https://example.com", bounds = rect(0, 150), clickable = true)
        val plain = node(text = "hola", bounds = rect(0, 200))

        val result = ContextNormalizer.normalize(listOf(input, button, image, link, plain))

        assertEquals(
            listOf(NodeRole.INPUT, NodeRole.BUTTON, NodeRole.IMAGE, NodeRole.LINK, NodeRole.TEXT),
            result.nodes.map { it.role },
        )
    }

    @Test
    fun `a node without text, description, click or edit falls to unknown only if it has withheld content`() {
        val withheld = node(withheld = true, bounds = rect(0, 0))

        assertEquals(NodeRole.UNKNOWN, ContextNormalizer.normalize(listOf(withheld)).nodes.single().role)
    }

    @Test
    fun `two short counters next to a real body of text look like a social post`() {
        val body = node(text = "Esto es una publicacion con bastante texto de verdad.", bounds = rect(0, 0))
        val likes = node(text = "348", bounds = rect(0, 60))
        val comments = node(text = "12", bounds = rect(0, 100))

        val result = ContextNormalizer.normalize(listOf(body, likes, comments))

        assertEquals(ContentPattern.SOCIAL_POST, result.pattern)
    }

    @Test
    fun `a single counter is not enough to call it a social post`() {
        val body = node(text = "Esto es una publicacion con bastante texto de verdad.", bounds = rect(0, 0))
        val likes = node(text = "348", bounds = rect(0, 60))

        assertEquals(ContentPattern.UNKNOWN, ContextNormalizer.normalize(listOf(body, likes)).pattern)
    }

    @Test
    fun `counters without any real body of text are not a social post either`() {
        val likes = node(text = "348", bounds = rect(0, 0))
        val comments = node(text = "12", bounds = rect(0, 60))

        assertEquals(ContentPattern.UNKNOWN, ContextNormalizer.normalize(listOf(likes, comments)).pattern)
    }

    @Test
    fun `an empty region normalizes to nothing, not a crash`() {
        val result = ContextNormalizer.normalize(emptyList())

        assertTrue(result.nodes.isEmpty())
        assertEquals("", result.plainText)
        assertEquals(ContentPattern.UNKNOWN, result.pattern)
    }
}
