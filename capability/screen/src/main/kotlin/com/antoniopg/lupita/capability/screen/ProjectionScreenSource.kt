package com.antoniopg.lupita.capability.screen

import com.antoniopg.lupita.core.model.CaptureFailure
import com.antoniopg.lupita.core.model.CaptureRequest
import com.antoniopg.lupita.core.model.CaptureResult
import com.antoniopg.lupita.core.model.ForegroundApp
import com.antoniopg.lupita.core.model.RawCapture
import com.antoniopg.lupita.core.model.ScreenSource

/**
 * Respaldo sin accesibilidad: solo pixeles (no hay arbol) y no se sabe que app hay delante, asi que TODO se
 * trata como app desconocida (*Sensible* por defecto, o lo que el usuario haya puesto para las desconocidas).
 * Una ventana protegida sale negra, igual que con accesibilidad, y la puerta la descarta por eso.
 */
class ProjectionScreenSource : ScreenSource {

    override val isAvailable: Boolean get() = ProjectionSession.isActive

    override suspend fun foreground(): ForegroundApp? =
        if (isAvailable) ForegroundApp(ForegroundApp.UNKNOWN_PACKAGE, null) else null

    override suspend fun capture(request: CaptureRequest): CaptureResult {
        if (!isAvailable) return CaptureResult.Failed(CaptureFailure.SERVICE_UNAVAILABLE)
        val bitmap = ProjectionSession.latestBitmap()
            ?: return CaptureResult.Failed(CaptureFailure.ERROR, "todavia no hay ningun fotograma")
        val cropped = try {
            RegionImage.crop(bitmap, request.region, "projection")
        } finally {
            bitmap.recycle()
        }
        return CaptureResult.Captured(
            RawCapture(
                ForegroundApp.UNKNOWN_PACKAGE, request.region, emptyList(), cropped.pixels,
                System.currentTimeMillis(), cropped.provenance,
            ),
        )
    }
}
