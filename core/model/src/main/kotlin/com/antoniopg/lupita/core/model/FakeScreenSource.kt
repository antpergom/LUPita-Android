package com.antoniopg.lupita.core.model

/**
 * Fuente de pantalla falsa, para tests y para probar el resto del sistema sin accesibilidad ni movil. Cuenta
 * las llamadas: es como se comprueba que una app protegida NUNCA llega a capturar.
 */
class FakeScreenSource(
    var foreground: ForegroundApp?,
    private val onCapture: (CaptureRequest) -> CaptureResult = { CaptureResult.Failed(CaptureFailure.ERROR, "sin configurar") },
) : ScreenSource {

    var available = true
    override val isAvailable: Boolean get() = available

    var foregroundCalls = 0
        private set
    var captureCalls = 0
        private set

    override suspend fun foreground(): ForegroundApp? {
        foregroundCalls++
        return foreground
    }

    override suspend fun capture(request: CaptureRequest): CaptureResult {
        captureCalls++
        return onCapture(request)
    }
}
