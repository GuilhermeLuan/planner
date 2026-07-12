package dev.guilhermeluan.planner.session

import java.net.URI

class ServerConfiguration private constructor(
    val baseUrl: String,
    val requiresInsecureTransportConfirmation: Boolean,
) {
    companion object {
        fun parse(rawUrl: String): ServerConfiguration {
            val normalized = rawUrl.trim().trimEnd('/')
            val usesHttp = normalized.startsWith("http://")
            require(usesHttp || normalized.startsWith("https://")) {
                "A URL do servidor deve usar HTTP ou HTTPS"
            }
            val parsed = runCatching { URI(normalized) }.getOrNull()
            require(parsed?.host?.isNotBlank() == true && parsed.userInfo == null) {
                "Informe uma URL completa, com endereço do servidor"
            }
            return ServerConfiguration(
                baseUrl = normalized,
                requiresInsecureTransportConfirmation = usesHttp,
            )
        }
    }
}
