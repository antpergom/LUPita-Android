package com.antoniopg.lupita

import android.content.Context
import com.antoniopg.lupita.ui.app.debug.DebugCaptureEntry
import java.io.File

/** El volcado de depuracion que escribe `OverlayService.debugSave`: un `.txt` + `.webp` por captura. */
private fun debugCapturesDir(context: Context) = File(context.filesDir, "debug-captures")

fun listDebugCaptures(context: Context): List<DebugCaptureEntry> {
    val dir = debugCapturesDir(context)
    val txts = dir.listFiles { f -> f.extension == "txt" } ?: return emptyList()
    return txts.sortedByDescending { it.lastModified() }.map { txt ->
        val webp = File(dir, "${txt.nameWithoutExtension}.webp")
        val summary = runCatching { txt.bufferedReader().readLine() }.getOrNull() ?: txt.name
        DebugCaptureEntry(txt.nameWithoutExtension, summary, txt.length() + webp.length())
    }
}

fun clearDebugCaptures(context: Context) {
    debugCapturesDir(context).listFiles()?.forEach { it.delete() }
}
