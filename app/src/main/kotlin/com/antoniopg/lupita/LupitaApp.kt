package com.antoniopg.lupita

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.antoniopg.lupita.core.model.ModelCatalog
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.data.DataStoreSettingsRepository

class LupitaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Contenedor de dependencias manual (F0: sin Hilt, ver docs/decisions/...f0-paso-1...). Todo lo
 * compartido se crea una sola vez aqui; DataStore lanza si se abren dos instancias del mismo fichero.
 */
class AppContainer(private val context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("lupita_settings")
    }

    val settings: SettingsRepository = DataStoreSettingsRepository(dataStore)

    val language = LanguageSettings(context)

    /** Modelos elegibles en Ajustes, leidos de `assets/model_catalog.json`. Sin fichero: lista vacia. */
    val modelCatalog: List<ModelOption> by lazy {
        runCatching { context.assets.open("model_catalog.json").bufferedReader().use { it.readText() } }
            .map(ModelCatalog::parse)
            .getOrDefault(emptyList())
    }
}
