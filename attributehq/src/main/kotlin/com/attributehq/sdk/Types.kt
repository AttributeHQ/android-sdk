package com.attributehq.sdk

import org.json.JSONArray
import org.json.JSONObject

/** SDK version constant */
const val SDK_VERSION = "1.0.0"

// region Public Types

/** Attribution result returned by the server after install tracking. */
data class AttributionResult(
    val matchType: MatchType,
    val confidence: Int,
    val mediaSource: String?,
    val campaignId: String?,
    val isOrganic: Boolean,
    val attributedTouchTime: Long?
) {
    enum class MatchType(val value: String) {
        DEVICE_ID("device_id"),
        FINGERPRINT("fingerprint"),
        INSTALL_REFERRER("install_referrer"),
        ORGANIC("organic");

        companion object {
            fun fromValue(value: String): MatchType =
                entries.firstOrNull { it.value == value } ?: ORGANIC
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("matchType", matchType.value)
        put("confidence", confidence)
        put("mediaSource", mediaSource ?: JSONObject.NULL)
        put("campaignId", campaignId ?: JSONObject.NULL)
        put("isOrganic", isOrganic)
        put("attributedTouchTime", attributedTouchTime ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): AttributionResult = AttributionResult(
            matchType = MatchType.fromValue(json.optString("matchType", "organic")),
            confidence = json.optInt("confidence", 0),
            mediaSource = json.optString("mediaSource").takeIf { it.isNotEmpty() && it != "null" },
            campaignId = json.optString("campaignId").takeIf { it.isNotEmpty() && it != "null" },
            isOrganic = json.optBoolean("isOrganic", true),
            attributedTouchTime = if (json.has("attributedTouchTime") && !json.isNull("attributedTouchTime"))
                json.optLong("attributedTouchTime") else null
        )
    }
}

/** SDK errors */
sealed class AttributeHQError(message: String) : Exception(message) {
    class NotInitialized : AttributeHQError("SDK not initialized. Call initialize() first.")
    class InvalidConfiguration(detail: String) : AttributeHQError("Configuration error: $detail")
    class NetworkError(cause: Throwable) : AttributeHQError("Network error: ${cause.message}")
    class StorageError(detail: String) : AttributeHQError("Storage error: $detail")
}

// endregion

// region Internal Types

/** Event stored in the offline queue. */
internal data class QueuedEvent(
    val eventName: String,
    val properties: Map<String, Any?>,
    val timestamp: Long,
    val sessionId: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("event_name", eventName)
        put("properties", properties.toJSONObject())
        put("timestamp", timestamp)
        put("session_id", sessionId)
    }

    companion object {
        fun fromJson(json: JSONObject): QueuedEvent = QueuedEvent(
            eventName = json.getString("event_name"),
            properties = json.optJSONObject("properties")?.toMap() ?: emptyMap(),
            timestamp = json.getLong("timestamp"),
            sessionId = json.getString("session_id")
        )
    }
}

/** POST /api/v1/installs request body */
internal data class InstallRequestBody(
    val appId: String,
    val deviceId: String,
    val sdkVersion: String,
    val osVersion: String?,
    val deviceModel: String?,
    val screenResolution: String?,
    val timezone: String?,
    val language: String?,
    val gaid: String?,
    val installReferrer: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("app_id", appId)
        put("device_id", deviceId)
        put("platform", "android")
        put("sdk_version", sdkVersion)
        put("os_version", osVersion ?: JSONObject.NULL)
        put("device_model", deviceModel ?: JSONObject.NULL)
        put("screen_resolution", screenResolution ?: JSONObject.NULL)
        put("timezone", timezone ?: JSONObject.NULL)
        put("language", language ?: JSONObject.NULL)
        put("gaid", gaid ?: JSONObject.NULL)
        put("install_referrer", installReferrer ?: JSONObject.NULL)
    }
}

/** POST /api/v1/events batch request body */
internal data class EventsBatchRequest(
    val appId: String,
    val deviceId: String,
    val events: List<QueuedEvent>,
    val sdkVersion: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("app_id", appId)
        put("device_id", deviceId)
        put("events", JSONArray().apply {
            events.forEach { put(it.toJson()) }
        })
        put("sdk_version", sdkVersion)
    }
}

// endregion
