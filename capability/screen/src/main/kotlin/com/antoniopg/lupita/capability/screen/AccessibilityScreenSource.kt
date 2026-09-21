package com.antoniopg.lupita.capability.screen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.antoniopg.lupita.core.model.AppSignals
import com.antoniopg.lupita.core.model.CaptureFailure
import com.antoniopg.lupita.core.model.CaptureRequest
import com.antoniopg.lupita.core.model.CaptureResult
import com.antoniopg.lupita.core.model.ForegroundApp
import com.antoniopg.lupita.core.model.RawCapture
import com.antoniopg.lupita.core.model.ScreenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [ScreenSource] sobre el servicio de accesibilidad. Orden de `capture`: primero la captura de pantalla
 * (si la ventana es `FLAG_SECURE` el sistema lo dice aqui y NO se lee el arbol), despues el arbol filtrado.
 * Nunca guarda nada: devuelve solo la region pedida.
 */
class AccessibilityScreenSource(private val context: Context) : ScreenSource {

    override val isAvailable: Boolean get() = ServiceHolder.service != null

    override suspend fun foreground(): ForegroundApp? {
        val service = ServiceHolder.service ?: return null
        val pkg = appWindowRoot(service)?.packageName?.toString() ?: return null
        return ForegroundApp(pkg, label(pkg), AppSignals())
    }

    override suspend fun capture(request: CaptureRequest): CaptureResult {
        val service = ServiceHolder.service ?: return CaptureResult.Failed(CaptureFailure.SERVICE_UNAVAILABLE)

        val bitmap = when (val shot = takeScreenshot(service)) {
            is Shot.Failure -> return CaptureResult.Failed(shot.reason, shot.detail)
            is Shot.Success -> shot.bitmap
        }
        val pixels = try {
            RegionImage.crop(bitmap, request.region)
        } finally {
            bitmap.recycle()
        }

        val root = appWindowRoot(service) ?: return CaptureResult.Failed(CaptureFailure.NO_FOREGROUND_APP)
        val pkg = root.packageName?.toString() ?: return CaptureResult.Failed(CaptureFailure.NO_FOREGROUND_APP)
        val tree = TreeMapper.map(root, request.region)
        return CaptureResult.Captured(
            RawCapture(pkg, request.region, tree.roots, pixels, System.currentTimeMillis()),
        )
    }

    /**
     * La ventana de la app que el usuario ve, no la nuestra (la capa de captura o la burbuja pueden ser la
     * ventana activa): la activa si no es nuestra; si no, la de aplicacion mas alta que no sea nuestra.
     */
    private fun appWindowRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val own = context.packageName
        service.rootInActiveWindow?.takeIf { it.packageName?.toString() != own }?.let { return it }
        return service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() != own } }
    }

    private fun label(pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private sealed interface Shot {
        class Success(val bitmap: Bitmap) : Shot
        class Failure(val reason: CaptureFailure, val detail: String?) : Shot
    }

    private suspend fun takeScreenshot(service: AccessibilityService): Shot =
        suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        buffer.close()
                        if (wrapped == null) {
                            cont.resume(Shot.Failure(CaptureFailure.ERROR, "wrapHardwareBuffer devolvio null"))
                        } else {
                            cont.resume(Shot.Success(wrapped))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val reason = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> CaptureFailure.SECURE_WINDOW
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> CaptureFailure.TOO_SOON
                            else -> CaptureFailure.ERROR
                        }
                        cont.resume(Shot.Failure(reason, "codigo $errorCode"))
                    }
                },
            )
        }
}
