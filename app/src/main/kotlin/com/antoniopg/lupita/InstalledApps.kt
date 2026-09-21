package com.antoniopg.lupita

import android.content.Context
import android.content.Intent
import com.antoniopg.lupita.core.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Las apps con icono en el lanzador (sin necesitar `QUERY_ALL_PACKAGES`, que exigiria justificacion en
 * Play): basta el bloque `<queries>` del manifiesto. Excluye LUPita. Ordenadas por nombre.
 */
suspend fun loadInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
