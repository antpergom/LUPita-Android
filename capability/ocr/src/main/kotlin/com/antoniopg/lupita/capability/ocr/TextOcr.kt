package com.antoniopg.lupita.capability.ocr

import android.graphics.Bitmap
import com.antoniopg.lupita.core.model.SelectionRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Una linea reconocida. [bounds] es `null` si ML Kit no dio caja para esa linea (pasa alguna vez). */
data class OcrLine(val text: String, val bounds: SelectionRect?)

data class OcrResult(val fullText: String, val lines: List<OcrLine>)

/**
 * OCR en el dispositivo con ML Kit (modelo local, texto latino). Desviacion deliberada del plan original:
 * un OCR Kotlin puro necesitaria binarios nativos (tipo Tesseract) que no encajan bien en Android; esto SI
 * es `android.*`, y esta declarado como excepcion en `:tools:boundaries` junto a `:capability:screen`.
 *
 * Sin tests unitarios: el reconocedor de ML Kit no se puede invocar en una JVM de escritorio (necesita
 * Google Play Services y el modelo descargado). Solo se puede probar de verdad en el dispositivo.
 */
object TextOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Nunca lanza: un fallo de ML Kit (sin Play Services, sin modelo aun...) da un resultado vacio. */
    suspend fun recognize(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }.map { line ->
                    val box = line.boundingBox
                    // SelectionRect exige right>=left y bottom>=top; ML Kit no debería darlo al reves,
                    // pero "nunca lanza" es la promesa de esta funcion, asi que se descarta si lo hace.
                    val rect = box?.let { runCatching { SelectionRect(it.left, it.top, it.right, it.bottom) }.getOrNull() }
                    OcrLine(line.text, rect)
                }
                cont.resume(OcrResult(result.text, lines))
            }
            .addOnFailureListener { cont.resume(OcrResult("", emptyList())) }
    }
}
