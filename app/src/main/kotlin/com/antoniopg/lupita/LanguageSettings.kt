package com.antoniopg.lupita

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.antoniopg.lupita.core.model.LanguageOption
import com.antoniopg.lupita.core.model.Languages

/**
 * Idioma por aplicacion con `LocaleManager` (Android 13+): cambia en caliente y tambien se ve en los
 * ajustes del sistema (ver `res/xml/locales_config.xml`). La logica de decision es pura y esta en
 * `Languages`; esto solo habla con el framework.
 */
class LanguageSettings(context: Context) {
    private val manager = context.getSystemService(LocaleManager::class.java)

    val supported: List<LanguageOption> get() = Languages.supported

    /** El idioma que se esta usando: el elegido en la app o, si no hay, el del sistema. */
    fun current(): String {
        val app = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        val system = manager.systemLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        return Languages.resolve(appTag = app, systemTag = system)
    }

    /** El pais del SISTEMA (no el de la app): decide que regiones de privacidad se activan de fabrica. */
    fun systemCountry(): String? =
        manager.systemLocales.takeIf { !it.isEmpty }?.get(0)?.country?.takeIf { it.isNotBlank() }

    /** Recrea la Activity con el idioma nuevo. */
    fun set(tag: String) {
        manager.applicationLocales = LocaleList.forLanguageTags(tag)
    }
}
