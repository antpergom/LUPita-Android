package com.antoniopg.lupita.core.model

/**
 * Un nodo del arbol de accesibilidad, ya traducido a tipos propios: nada de `android.` aqui, para que la
 * normalizacion y los tests corran en JVM. [bounds] esta en pixeles de pantalla.
 */
data class UiNode(
    val bounds: SelectionRect,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val isPassword: Boolean = false,
    val isEditable: Boolean = false,
    val isClickable: Boolean = false,
    /** La app ha ocultado el texto a servicios que no son de accesibilidad: llega vacio y se anota. */
    val contentWithheld: Boolean = false,
    val children: List<UiNode> = emptyList(),
) {
    /** Este nodo y todos sus descendientes. */
    fun flatten(): Sequence<UiNode> = sequence {
        yield(this@UiNode)
        children.forEach { yieldAll(it.flatten()) }
    }
}

fun SelectionRect.intersects(other: SelectionRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

/** Pixeles ARGB de la region recortada. Vive en memoria mientras dura el analisis; nunca se persiste en bruto. */
class PixelBuffer(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0) { "imagen vacia: ${width}x$height" }
        require(argb.size == width * height) { "se esperaban ${width * height} pixeles y hay ${argb.size}" }
    }
}

/** La app que esta en primer plano, y lo que el sistema sabe de su ventana, ANTES de leer nada. */
data class ForegroundApp(val packageName: String, val appLabel: String?, val signals: AppSignals = AppSignals())

/** Se pide siempre una region: la fuente solo devuelve lo que cae dentro (arbol filtrado y pixeles recortados). */
data class CaptureRequest(val region: SelectionRect)

/** Lo que devuelve una fuente de pantalla: solo la region pedida. */
class RawCapture(
    val packageName: String,
    val region: SelectionRect,
    val nodes: List<UiNode>,
    val pixels: PixelBuffer?,
    val capturedAtMillis: Long,
    /** Como se obtuvo la imagen (recorte, reduccion, color): se guarda con ella para comparar variantes. */
    val provenance: ImageProvenance? = null,
)

enum class CaptureFailure {
    /** La ventana esta protegida por el sistema (`FLAG_SECURE`): no se puede capturar. */
    SECURE_WINDOW,

    /** El sistema limita la frecuencia de capturas (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`). */
    TOO_SOON,

    /** El servicio de accesibilidad no esta activo o se ha detenido. */
    SERVICE_UNAVAILABLE,

    /** No se sabe que app hay en primer plano. */
    NO_FOREGROUND_APP,

    /** La app en primer plano cambio entre la decision de privacidad y la captura: se descarta todo. */
    APP_CHANGED,

    ERROR,
}

sealed interface CaptureResult {
    class Captured(val capture: RawCapture) : CaptureResult
    data class Failed(val reason: CaptureFailure, val detail: String? = null) : CaptureResult
}

/**
 * De donde sale el contexto de pantalla. Hay que poder cambiarla sin tocar a quien la usa: la
 * accesibilidad es el mayor riesgo del proyecto (Play, Android 17), y el respaldo (captura + OCR sin
 * accesibilidad) debe implementar esta misma interfaz. Ver `docs/decisions/2026-09-21-overlay-superposicion-y-fuentes-de-pantalla.md`.
 */
interface ScreenSource {
    /** `false` si la fuente no puede funcionar ahora (p. ej. el servicio de accesibilidad esta apagado). */
    val isAvailable: Boolean get() = true

    /** La app de primer plano y las senales de su ventana. Es lo unico que se lee antes de decidir la privacidad. */
    suspend fun foreground(): ForegroundApp?

    /** Captura SOLO [CaptureRequest.region]. Debe llamarse despues de pasar por la puerta de privacidad. */
    suspend fun capture(request: CaptureRequest): CaptureResult
}
