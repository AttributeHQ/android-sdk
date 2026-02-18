package com.attributehq.sdk

/**
 * Public SDK configuration. Pass to [AttributeHQ.initialize].
 *
 * @property apiKey API key (format: ak_xxx). Required.
 * @property appId App UUID. Required.
 */
data class AttributeHQConfiguration(
    val apiKey: String,
    val appId: String,
    var baseUrl: String = "https://attributehq.com",
    var batchSize: Int = 10,
    var flushIntervalSeconds: Long = 5L,
    var maxQueueSize: Int = 100,
    var enableDebugLogging: Boolean = false,
    var requestTimeoutMs: Long = 30_000L
)

/** Internal validated configuration with clamped values. */
internal data class ResolvedConfiguration(
    val apiKey: String,
    val appId: String,
    val baseUrl: String,
    val batchSize: Int,
    val flushIntervalSeconds: Long,
    val maxQueueSize: Int,
    val enableDebugLogging: Boolean,
    val requestTimeoutMs: Long
) {
    companion object {
        fun resolve(config: AttributeHQConfiguration): ResolvedConfiguration {
            require(config.apiKey.isNotBlank()) { "API key is required" }
            require(config.appId.isNotBlank()) { "App ID is required" }

            return ResolvedConfiguration(
                apiKey = config.apiKey,
                appId = config.appId,
                baseUrl = config.baseUrl.trimEnd('/'),
                batchSize = config.batchSize.coerceIn(1, 100),
                flushIntervalSeconds = config.flushIntervalSeconds.coerceAtLeast(1L),
                maxQueueSize = config.maxQueueSize.coerceAtLeast(10),
                enableDebugLogging = config.enableDebugLogging,
                requestTimeoutMs = config.requestTimeoutMs.coerceAtLeast(5_000L)
            )
        }
    }
}
