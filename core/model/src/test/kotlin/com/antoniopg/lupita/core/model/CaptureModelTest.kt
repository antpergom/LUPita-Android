package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModelTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int) = SelectionRect(l, t, r, b)

    @Test
    fun `flatten lists the node and all its descendants in order`() {
        val tree = UiNode(
            rect(0, 0, 10, 10), text = "a",
            children = listOf(
                UiNode(rect(0, 0, 5, 5), text = "b", children = listOf(UiNode(rect(0, 0, 1, 1), text = "c"))),
                UiNode(rect(5, 5, 10, 10), text = "d"),
            ),
        )

        assertEquals(listOf("a", "b", "c", "d"), tree.flatten().map { it.text }.toList())
    }

    @Test
    fun `rectangles that only touch at an edge do not intersect`() {
        assertFalse(rect(0, 0, 10, 10).intersects(rect(10, 0, 20, 10)))
        assertFalse(rect(0, 0, 10, 10).intersects(rect(0, 10, 10, 20)))
    }

    @Test
    fun `overlapping and nested rectangles intersect`() {
        assertTrue(rect(0, 0, 10, 10).intersects(rect(9, 9, 20, 20)))
        assertTrue(rect(0, 0, 100, 100).intersects(rect(40, 40, 50, 50)))
    }

    @Test
    fun `a pixel buffer must match its size`() {
        assertThrows(IllegalArgumentException::class.java) { PixelBuffer(2, 2, IntArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { PixelBuffer(0, 2, IntArray(0)) }
        assertEquals(4, PixelBuffer(2, 2, IntArray(4)).argb.size)
    }

    @Test
    fun `black detection ignores alpha and needs every pixel to be black`() {
        assertTrue(PixelBuffer(2, 1, intArrayOf(0xFF000000.toInt(), 0x00000000)).isUniformBlack())
        assertFalse(PixelBuffer(2, 1, intArrayOf(0xFF000000.toInt(), 0xFF000001.toInt())).isUniformBlack())
    }

    @Test
    fun `the default save policy is always`() {
        assertEquals(ImageSavePolicy.ALWAYS, ImageSavePolicy.DEFAULT)
    }

    @Test
    fun `a protected app never saves whatever the setting`() {
        ImageSavePolicy.entries.forEach { assertEquals(ImageSavePolicy.NEVER, it.effective(PrivacyTier.PROTECTED)) }
    }

    @Test
    fun `a sensitive app asks even when the setting is always`() {
        assertEquals(ImageSavePolicy.ASK, ImageSavePolicy.ALWAYS.effective(PrivacyTier.SENSITIVE))
        assertEquals(ImageSavePolicy.ASK, ImageSavePolicy.ASK.effective(PrivacyTier.SENSITIVE))
        assertEquals(ImageSavePolicy.NEVER, ImageSavePolicy.NEVER.effective(PrivacyTier.SENSITIVE))
    }

    @Test
    fun `a normal app follows the setting`() {
        ImageSavePolicy.entries.forEach { assertEquals(it, it.effective(PrivacyTier.NORMAL)) }
    }

    @Test
    fun `save policy keys round trip and unknown keys are null`() {
        ImageSavePolicy.entries.forEach { assertEquals(it, ImageSavePolicy.fromKey(it.key)) }
        assertNull(ImageSavePolicy.fromKey("nope"))
        assertNull(ImageSavePolicy.fromKey(null))
    }

    @Test
    fun `the fake source counts its calls`() {
        val fake = FakeScreenSource(ForegroundApp("p", null))
        kotlinx.coroutines.runBlocking {
            fake.foreground()
            fake.capture(CaptureRequest(rect(0, 0, 1, 1)))
        }

        assertEquals(1, fake.foregroundCalls)
        assertEquals(1, fake.captureCalls)
    }
}
