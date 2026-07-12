package dev.guilhermeluan.planner.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpSyncApi(baseUrl: String) : SyncApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun push(
        token: String,
        operations: List<SyncOperation>,
    ): List<PushResult> = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "operations",
            JSONArray().apply {
                operations.forEach { operation ->
                    put(
                        JSONObject()
                            .put("operation_id", operation.operationId)
                            .put("entity_type", operation.entityType)
                            .put("entity_id", operation.entityId)
                            .put("kind", operation.kind)
                            .put("payload", JSONObject(operation.payloadJson))
                            .put("client_updated_at", operation.clientUpdatedAt),
                    )
                }
            },
        )
        val response = request("POST", "/api/v1/sync/push", token, body.toString())
        response.getJSONArray("results").mapObjects { result ->
            PushResult(
                operationId = result.getString("operation_id"),
                status = PushResultStatus.valueOf(result.getString("status").uppercase()),
                version = result.optLongOrNull("version"),
                error = result.optString("error").takeIf(String::isNotBlank),
            )
        }
    }

    override suspend fun pull(token: String, cursor: Long, limit: Int): PullPage =
        withContext(Dispatchers.IO) {
            val response = request(
                method = "GET",
                path = "/api/v1/sync/pull?cursor=$cursor&limit=$limit",
                token = token,
            )
            PullPage(
                changes = response.getJSONArray("changes").mapObjects { change ->
                    SyncChange(
                        cursor = change.getLong("cursor"),
                        operationId = change.getString("operation_id"),
                        entityType = change.getString("entity_type"),
                        entityId = change.getString("entity_id"),
                        kind = change.getString("kind"),
                        payloadJson = change.getJSONObject("payload").toString(),
                        version = change.getLong("version"),
                        updatedAt = change.getString("updated_at"),
                    )
                },
                nextCursor = response.getLong("next_cursor"),
                hasMore = response.getBoolean("has_more"),
            )
        }

    private fun request(
        method: String,
        path: String,
        token: String,
        body: String? = null,
    ): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) connection.outputStream.bufferedWriter().use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val error = responseText.takeIf(String::isNotBlank)?.let(::JSONObject)
            throw SyncApiException(
                status = status,
                code = error?.optString("code")?.takeIf(String::isNotBlank),
                message = error?.optString("error")?.takeIf(String::isNotBlank)
                    ?: "Falha ao sincronizar com o servidor.",
            )
        }
        return JSONObject(responseText)
    }
}

class SyncApiException(
    val status: Int,
    val code: String?,
    override val message: String,
) : RuntimeException(message)

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) getLong(name) else null
