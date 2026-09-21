package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagePipelineTest {

    private val provenance = ImageProvenance(
        source = "accessibility",
        regionWidth = 1440, regionHeight = 3120, outputWidth = 880, outputHeight = 1906,
        sourceColorSpace = "Display P3", resampling = "canvas-bilinear", maxAreaPx = ImageSizing.MAX_AREA_PX,
    )

    private val record = ImageProcessingRecord(
        pipelineVersion = ImagePipeline.VERSION, provenance = provenance, format = ImagePipeline.FORMAT,
        mime = ImagePipeline.MIME, quality = ImagePipeline.QUALITY, encoder = "test", encodedBytes = 1234,
        encodeMs = 12, androidSdk = 37, settleMs = 150,
    )

    @Test
    fun `the record survives a JSON round trip`() {
        assertEquals(record, ImageProcessingRecord.fromJson(record.toJson()))
    }

    @Test
    fun `a record without provenance or settle time round trips`() {
        val bare = record.copy(provenance = null, settleMs = null)

        val back = ImageProcessingRecord.fromJson(bare.toJson())

        assertNull(back.provenance)
        assertNull(back.settleMs)
        assertEquals(bare, back)
    }

    @Test
    fun `defaults are lossy webp at quality 90`() {
        assertEquals("webp-lossy", ImagePipeline.FORMAT)
        assertEquals("image/webp", ImagePipeline.MIME)
        assertEquals(90, ImagePipeline.QUALITY)
    }

    @Test
    fun `scale is output over region width`() {
        assertEquals(880.0 / 1440.0, provenance.scale, 1e-9)
    }
}
