package com.antoniopg.lupita.capability.image

import com.antoniopg.lupita.core.model.PixelBuffer

/** Huella de 64 bits. Dos huellas parecidas (poca distancia de Hamming) suelen venir de la misma imagen. */
data class ImageHash(val bits: Long) {
    fun hammingDistance(other: ImageHash): Int = java.lang.Long.bitCount(bits xor other.bits)

    fun toHex(): String = "%016x".format(bits)
}

/**
 * «dHash» (difference hash): reduce la imagen a una rejilla de 9x8 en gris y compara cada pixel con el de
 * su derecha. Es lo que casi todo el mundo llama «pHash» en la practica (el pHash «de verdad», con DCT, es
 * mas caro y no hace falta para lo que esto necesita: detectar la MISMA imagen reescalada o recomprimida).
 * NO sobrevive a un recorte ni a una rotacion — eso no es un fallo, es lo que dHash puede y no puede hacer.
 */
object PerceptualHash {
    private const val SIZE = 8

    fun of(image: PixelBuffer): ImageHash {
        val gray = grayscaleGrid(image, SIZE + 1, SIZE)
        var bits = 0L
        var bit = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (gray[y * (SIZE + 1) + x] < gray[y * (SIZE + 1) + x + 1]) bits = bits or (1L shl bit)
                bit++
            }
        }
        return ImageHash(bits)
    }

    /** Reduccion por vecino mas cercano (determinista, sin interpolar) + luminancia entera. */
    private fun grayscaleGrid(image: PixelBuffer, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (ny in 0 until h) {
            val sy = (ny * image.height / h).coerceIn(0, image.height - 1)
            for (nx in 0 until w) {
                val sx = (nx * image.width / w).coerceIn(0, image.width - 1)
                val argb = image.argb[sy * image.width + sx]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                out[ny * w + nx] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        return out
    }
}
