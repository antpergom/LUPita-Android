package com.antoniopg.lupita.capability.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import com.antoniopg.lupita.core.model.ImageSizing
import com.antoniopg.lupita.core.model.PixelBuffer
import com.antoniopg.lupita.core.model.SelectionRect
import java.io.ByteArrayOutputStream

/**
 * De la captura completa a la imagen que se envia (decision 2026-09-22): recorta la region, la pasa a sRGB,
 * la reduce (solo hacia abajo, tope por area) en UN solo dibujado, y la codifica una vez en WebP sin perdida.
 */
object RegionImage {

    /** [full] puede ser de hardware; se copia a software antes de tocarla. El llamador la libera. */
    internal fun crop(full: Bitmap, region: SelectionRect): PixelBuffer {
        val left = region.left.coerceIn(0, full.width - 1)
        val top = region.top.coerceIn(0, full.height - 1)
        val src = Rect(left, top, region.right.coerceIn(left + 1, full.width), region.bottom.coerceIn(top + 1, full.height))
        val size = ImageSizing.fit(src.width(), src.height())

        val soft = if (full.config == Bitmap.Config.HARDWARE) full.copy(Bitmap.Config.ARGB_8888, false) else full
        // Dibujar sobre un bitmap sRGB es lo que convierte el color (Display P3 -> sRGB); copy() no lo hace.
        val out = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.SRGB))
        Canvas(out).drawBitmap(soft, src, Rect(0, 0, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG))
        if (soft !== full) soft.recycle()

        val pixels = IntArray(size.width * size.height)
        out.getPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
        out.recycle()
        return PixelBuffer(size.width, size.height, pixels)
    }

    /** WebP sin perdida. La calidad, en lossless, es esfuerzo de compresion: 100 = fichero mas pequeno. */
    fun encodeWebpLossless(image: PixelBuffer): ByteArray {
        val bitmap = Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
