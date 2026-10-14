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
            require(config.appId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                "App ID must be a UUID from the AttributeHQ dashboard"
            }

            return ResolvedConfiguration(
                apiKey = config.apiKey,
                appId = config.appId,
                baseUrl = "https://api.attributehq.com",
                batchSize = config.batchSize.coerceIn(1, 100),
                flushIntervalSeconds = config.flushIntervalSeconds.coerceAtLeast(1L),
                maxQueueSize = config.maxQueueSize.coerceAtLeast(10),
                enableDebugLogging = config.enableDebugLogging,
                requestTimeoutMs = config.requestTimeoutMs.coerceIn(5_000L, Int.MAX_VALUE.toLong())
            )
        }
    }
}
