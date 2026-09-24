package com.antoniopg.lupita

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.antoniopg.lupita.capability.privacy.PrivacyCatalogParser
import com.antoniopg.lupita.capability.privacy.PrivacyGate
import com.antoniopg.lupita.core.model.AiCredentialsRepository
import com.antoniopg.lupita.core.model.AnalysisHistoryRepository
import com.antoniopg.lupita.core.model.BudgetDefaults
import com.antoniopg.lupita.core.model.BudgetSettingsRepository
import com.antoniopg.lupita.core.model.CostLogRepository
import com.antoniopg.lupita.core.model.ModelCatalog
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyRegions
import com.antoniopg.lupita.core.model.PrivacySettingsRepository
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.orchestrator.AnalysisRunner
import com.antoniopg.lupita.orchestrator.ResourceQueues
import com.antoniopg.lupita.source.openai.HttpOpenAiClient
import com.antoniopg.lupita.source.openai.OpenAiClient
import com.antoniopg.lupita.data.DataStoreBudgetSettingsRepository
import com.antoniopg.lupita.data.DataStorePrivacySettingsRepository
import com.antoniopg.lupita.data.DataStoreSettingsRepository
import com.antoniopg.lupita.data.RoomAnalysisHistoryRepository
import com.antoniopg.lupita.data.RoomCostLogRepository
import com.antoniopg.lupita.data.TinkAiCredentialsRepository
import com.antoniopg.lupita.data.crypto.AeadFieldCodec
import com.antoniopg.lupita.data.db.LupitaDatabase
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

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
        Room.databaseBuilder(context, LupitaDatabase::class.java, "lupita_database")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /** Log de coste/modelo de cada llamada de pago — el registro "facilmente accesible" pedido por el usuario. */
    val costLog: CostLogRepository by lazy { RoomCostLogRepository(database.costLogDao()) }

    /** El texto real de cada resultado — sin esto, cerrar el panel de resultados lo perdia para siempre. */
    val analysisHistory: AnalysisHistoryRepository by lazy { RoomAnalysisHistoryRepository(database.analysisHistoryDao()) }

    /**
     * Clave maestra en el Android Keystore (hardware/StrongBox si el dispositivo lo soporta): el
     * keyset que la usa vive cifrado en un `SharedPreferences` normal, pero la clave en si nunca sale
     * del Keystore. Mismo mecanismo que `app-android-rrss-publisher` (proyecto hermano), sin Hilt.
     */
    private val credentialsAead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "lupita_credentials_keyset", "lupita_credentials_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://lupita_credentials_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val credentialsDataStore by lazy {
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("lupita_credentials") }
    }

    /** Clave de API del proveedor de IA (F5 paso 1), cifrada — nunca la misma preferencia sin cifrar. */
    val aiCredentials: AiCredentialsRepository by lazy {
        TinkAiCredentialsRepository(credentialsDataStore, AeadFieldCodec(credentialsAead))
    }

    /** El unico cliente que toca la red de OpenAI de verdad (F5 paso 2). */
    private val openAiClient: OpenAiClient by lazy { HttpOpenAiClient() }

    /** Colas por clase de recurso (F4 paso 2) — una instancia para toda la app, no una por llamada. */
    private val resourceQueues: ResourceQueues by lazy { ResourceQueues() }

    /**
     * Consumidor real de F4 (F5 paso 3, generalizado en F6): presupuesto + colas + log de coste
     * alrededor de cualquiera de las 4 herramientas. Ver `orchestrator/AnalysisRunner.kt`.
     */
    val analysisRunner: AnalysisRunner by lazy {
        AnalysisRunner(aiCredentials, budgetSettings, budgetDefaults, costLog, resourceQueues, openAiClient)
    }

    private fun readAsset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }
}
