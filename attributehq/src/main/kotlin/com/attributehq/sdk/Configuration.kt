package com.attributehq.sdk

enum class AttributeHQEnvironment(val wireValue: String) { SANDBOX("sandbox"), PRODUCTION("production") }

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
    var requestTimeoutMs: Long = 30_000L,
    /** Null preserves stored state; a new installation collects after initialize by default. */
    var initialCollectionEnabled: Boolean? = null,
    /** Null preserves the stored preference; a new installation starts unknown. */
    var initialAdvertisingSharing: AdvertisingSharing? = null,
    /** Opt-in diagnostic transport only; never sends production postbacks. */
    var enableSandboxEvidenceUpload: Boolean = false,
    /** Separate app-level tracking decision; null preserves saved state, new installs default false. */
    var initialApplicationTrackingEnabled: Boolean? = null,
    /** Declared install environment; null remains unknown. Frozen on first collection. */
    var environment: AttributeHQEnvironment? = null
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
    val requestTimeoutMs: Long,
    val enableSandboxEvidenceUpload: Boolean = false,
    val environment: AttributeHQEnvironment? = null
) {
    companion object {
        fun resolve(config: AttributeHQConfiguration): ResolvedConfiguration {
            require(config.apiKey.isNotBlank()) { "API key is required" }
            require(config.appId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                "App ID must be a UUID from the AttributeHQ dashboard"
            }

            require(!(config.environment == AttributeHQEnvironment.PRODUCTION && config.enableSandboxEvidenceUpload)) {
                "Production environment cannot enable sandbox evidence upload"
            }
            return ResolvedConfiguration(
                apiKey = config.apiKey,
                appId = config.appId,
                baseUrl = "https://api.attributehq.com",
                batchSize = config.batchSize.coerceIn(1, 100),
                flushIntervalSeconds = config.flushIntervalSeconds.coerceAtLeast(1L),
                maxQueueSize = config.maxQueueSize.coerceAtLeast(10),
                enableDebugLogging = config.enableDebugLogging,
                requestTimeoutMs = config.requestTimeoutMs.coerceIn(5_000L, Int.MAX_VALUE.toLong()),
                enableSandboxEvidenceUpload = config.enableSandboxEvidenceUpload,
                environment = config.environment ?: if (config.enableSandboxEvidenceUpload) AttributeHQEnvironment.SANDBOX else null
            )
        }
    }
}
