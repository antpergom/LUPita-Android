package com.antoniopg.lupita.capability.screen

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread

/**
 * Sesion de captura por proyeccion (respaldo sin accesibilidad): UNA proyeccion, UN `VirtualDisplay` y un
 * `ImageReader` que solo se queda con el ultimo fotograma. Desde Android 14 un permiso de proyeccion solo
 * sirve para crear un `VirtualDisplay`, asi que se crea una vez por sesion y se mantiene hasta que el usuario
 * la para, el sistema la corta (`onStop`) o alguien llama a [stop].
 *
 * Solo hay fotograma nuevo cuando la pantalla cambia; por eso se guarda el ultimo en vez de pedirlo al
 * capturar (`acquireLatestImage` devolveria `null` con la pantalla quieta).
 */
object ProjectionSession {

    private class Active(
        val projection: MediaProjection,
        val display: VirtualDisplay,
        val reader: ImageReader,
        val thread: HandlerThread,
        val width: Int,
        val height: Int,
    ) {
        var latest: Image? = null
    }

    private val lock = Any()
    private var active: Active? = null

    /** Se avisa cuando la sesion queda lista (tras el permiso del usuario). */
    @Volatile
    var onReady: (() -> Unit)? = null

    /** Se avisa cuando la sesion termina, por la razon que sea. */
    @Volatile
    var onStopped: (() -> Unit)? = null

    val isActive: Boolean get() = synchronized(lock) { active != null }

    /** [width] y [height]: el tamano REAL de la pantalla, para que las coordenadas coincidan con las de la seleccion. */
    fun start(projection: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        stop()
        val thread = HandlerThread("lupita-projection").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        // Desde Android 14 el callback se registra ANTES de crear el VirtualDisplay.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stop()
        }, handler)
        val display = projection.createVirtualDisplay(
            "lupita-capture", width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )
        if (display == null) {
            // El sistema se nego a crear la pantalla virtual: no hay sesion.
            reader.close()
            thread.quitSafely()
            projection.stop()
            return
        }
        val session = Active(projection, display, reader, thread, width, height)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            synchronized(lock) {
                val old = session.latest
                session.latest = image
                old?.close()
            }
        }, handler)
        synchronized(lock) { active = session }
        onReady?.invoke()
    }

    /** El ultimo fotograma copiado a un bitmap de software (con el relleno de fila que deje el buffer), o `null`. */
    internal fun latestBitmap(): Bitmap? = synchronized(lock) {
        val session = active ?: return null
        val image = session.latest ?: return null
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * session.width
        val bitmap = Bitmap.createBitmap(
            session.width + rowPadding / plane.pixelStride, session.height, Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(plane.buffer)
        bitmap
    }

    fun stop() {
        val session = synchronized(lock) { active.also { active = null } } ?: return
        runCatching { session.latest?.close() }
        runCatching { session.reader.setOnImageAvailableListener(null, null) }
        runCatching { session.display.release() }
        runCatching { session.reader.close() }
        runCatching { session.projection.stop() }
        session.thread.quitSafely()
        onStopped?.invoke()
    }
}
