package com.antoniopg.lupita

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.data.DataStoreSettingsRepository

class LupitaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Contenedor de dependencias manual (F0: sin Hilt, ver docs/decisions/...f0-paso-1...). Todo lo
 * compartido se crea una sola vez aqui; DataStore lanza si se abren dos instancias del mismo fichero.
 */
class AppContainer(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("lupita_settings")
    }

    val settings: SettingsRepository = DataStoreSettingsRepository(dataStore)
}
