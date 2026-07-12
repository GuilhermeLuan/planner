package dev.guilhermeluan.planner.session

import android.content.Context

class ServerConfigurationStore(
    context: Context,
    preferencesName: String = "planner-server-configuration",
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(configuration: ServerConfiguration) {
        check(preferences.edit().putString(BASE_URL, configuration.baseUrl).commit()) {
            "Não foi possível salvar a configuração do servidor"
        }
    }

    fun read(): ServerConfiguration? = preferences.getString(BASE_URL, null)
        ?.let(ServerConfiguration::parse)

    fun clear() {
        preferences.edit().remove(BASE_URL).apply()
    }

    private companion object {
        const val BASE_URL = "base_url"
    }
}
