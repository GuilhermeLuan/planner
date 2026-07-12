package dev.guilhermeluan.planner.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpSessionApi(baseUrl: String) : SessionApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun login(username: String, password: String): LoginPayload =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
            val response = request("/api/v1/auth/login", body = body)
            val accountJson = response.getJSONObject("account")
            val plannerJson = response.getJSONObject("planner")
            LoginPayload(
                token = response.getString("token"),
                account = Account(
                    id = accountJson.getString("id"),
                    username = accountJson.getString("username"),
                    timezone = accountJson.getString("timezone"),
                    mustChangePassword = accountJson.getBoolean("must_change_password"),
                ),
                planner = Planner(
                    id = plannerJson.getString("id"),
                    accountId = plannerJson.getString("account_id"),
                ),
            )
        }

    override suspend fun changePassword(token: String, password: String) {
        withContext(Dispatchers.IO) {
            request(
                path = "/api/v1/auth/change-password",
                token = token,
                body = JSONObject().put("password", password).toString(),
            )
        }
    }

    override suspend fun logout(token: String) {
        withContext(Dispatchers.IO) {
            request(path = "/api/v1/auth/logout", token = token, body = "{}")
        }
    }

    private fun request(path: String, token: String? = null, body: String): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        connection.outputStream.bufferedWriter().use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val error = responseText.takeIf { it.isNotBlank() }?.let(::JSONObject)
            throw SessionApiException(
                status = status,
                code = error?.optString("code")?.takeIf { it.isNotBlank() },
                message = error?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "O servidor recusou a solicitação",
            )
        }
        return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
    }
}

class SessionApiException(
    val status: Int,
    val code: String?,
    override val message: String,
) : RuntimeException(message)
