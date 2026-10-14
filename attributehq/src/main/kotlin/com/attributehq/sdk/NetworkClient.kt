package com.attributehq.sdk

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object NetworkClient {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "attributehq-network").apply { isDaemon = true }
    }

    fun post(path: String, body: JSONObject, config: ResolvedConfiguration,
             callback: (Result<JSONObject>) -> Unit) {
        val payload = body.toString()
        executor.execute {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("${config.baseUrl}$path").openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    instanceFollowRedirects = false
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-API-Key", config.apiKey)
                    connectTimeout = config.requestTimeoutMs.toInt()
                    readTimeout = config.requestTimeoutMs.toInt()
                    doOutput = true
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                val status = connection.responseCode
                if (status !in 200..299) throw Exception("HTTP $status")
                Result.success(JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
            } catch (e: Exception) {
                Result.failure<JSONObject>(AttributeHQError.NetworkError(e))
            } finally {
                connection?.disconnect()
            }
            // A consumer callback must never be called a second time if it throws.
            callback(result)
        }
    }
}
