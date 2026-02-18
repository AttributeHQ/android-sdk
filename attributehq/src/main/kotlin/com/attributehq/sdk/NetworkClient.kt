package com.attributehq.sdk

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** HttpURLConnection-based HTTP client for API communication. */
internal object NetworkClient {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "attributehq-network").apply { isDaemon = true }
    }

    /**
     * Send a POST request with JSON body.
     * Runs on background thread, callback invoked on background thread.
     */
    fun post(
        path: String,
        body: JSONObject,
        config: ResolvedConfiguration,
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            try {
                val urlString = "${config.baseUrl}$path"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-API-Key", config.apiKey)
                    connectTimeout = config.requestTimeoutMs.toInt()
                    readTimeout = config.requestTimeoutMs.toInt()
                    doOutput = true
                }

                Logger.debug("POST $path")

                // Write body
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    Logger.warn("HTTP $responseCode for $path")
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) { "" }
                    connection.disconnect()
                    callback(Result.failure(
                        AttributeHQError.NetworkError(
                            Exception("HTTP $responseCode: $errorBody")
                        )
                    ))
                    return@execute
                }

                // Read response
                val responseBody = BufferedReader(
                    InputStreamReader(connection.inputStream, Charsets.UTF_8)
                ).use { it.readText() }

                connection.disconnect()

                val json = JSONObject(responseBody)
                Logger.debug("POST $path succeeded")
                callback(Result.success(json))
            } catch (e: Exception) {
                Logger.error("Network error: ${e.message}", e)
                callback(Result.failure(AttributeHQError.NetworkError(e)))
            }
        }
    }
}
