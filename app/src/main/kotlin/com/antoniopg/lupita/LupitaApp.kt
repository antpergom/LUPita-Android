package com.antoniopg.lupita

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.antoniopg.lupita.capability.privacy.PrivacyCatalogParser
import com.antoniopg.lupita.capability.privacy.PrivacyGate
import com.antoniopg.lupita.core.model.BudgetDefaults
import com.antoniopg.lupita.core.model.BudgetSettingsRepository
import com.antoniopg.lupita.core.model.CostLogRepository
import com.antoniopg.lupita.core.model.ModelCatalog
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyRegions
import com.antoniopg.lupita.core.model.PrivacySettingsRepository
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.data.DataStoreBudgetSettingsRepository
import com.antoniopg.lupita.data.DataStorePrivacySettingsRepository
import com.antoniopg.lupita.data.DataStoreSettingsRepository
import com.antoniopg.lupita.data.RoomCostLogRepository
import com.antoniopg.lupita.data.db.LupitaDatabase

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

    /** Configuracion de privacidad. De fabrica: global + el pais del sistema, y desconocidas = sensibles. */
    val privacySettings: PrivacySettingsRepository =
        DataStorePrivacySettingsRepository(dataStore, PrivacyRegions.defaultFor(language.systemCountry()))

    /** Modelos elegibles en Ajustes, leidos de `assets/model_catalog.json`. Sin fichero: lista vacia. */
    val modelCatalog: List<ModelOption> by lazy {
        runCatching { readAsset("model_catalog.json") }.map(ModelCatalog::parse).getOrDefault(emptyList())
    }

    /** Apps conocidas por la puerta de privacidad, de `assets/privacy_catalog.json`. Sin fichero: vacio (todo desconocido). */
    val privacyCatalog: PrivacyCatalog by lazy {
        runCatching { readAsset("privacy_catalog.json") }.map(PrivacyCatalogParser::parse).getOrDefault(PrivacyCatalog())
    }

    /** El unico punto que decide el nivel de una app; se consulta ANTES de capturar (F1.3). */
    val privacyGate: PrivacyGate by lazy { PrivacyGate(privacyCatalog) }

    /** Configuracion de presupuesto (F4): solo los topes que el usuario ha tocado; el resto sale de [budgetDefaults]. */
    val budgetSettings: BudgetSettingsRepository = DataStoreBudgetSettingsRepository(dataStore)

    /** Valores de fabrica de los topes, de `assets/budget_defaults.json`. Sin fichero: los de [BudgetDefaults.FALLBACK]. */
    val budgetDefaults: BudgetDefaults by lazy {
        runCatching { readAsset("budget_defaults.json") }.map(BudgetDefaults::parse).getOrDefault(BudgetDefaults.FALLBACK)
    }

    /** Primera BD Room del proyecto (F4 paso 3): solo el log de coste por ahora, ver LupitaDatabase.kt. */
    private val database: LupitaDatabase by lazy {
        Room.databaseBuilder(context, LupitaDatabase::class.java, "lupita_database").build()
    }

    /** Log de coste/modelo de cada llamada de pago — el registro "facilmente accesible" pedido por el usuario. */
    val costLog: CostLogRepository by lazy { RoomCostLogRepository(database.costLogDao()) }

    private fun readAsset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }
}
