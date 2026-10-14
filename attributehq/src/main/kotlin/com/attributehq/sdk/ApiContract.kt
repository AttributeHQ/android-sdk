package com.attributehq.sdk

import org.json.JSONObject

internal object ApiContract {
    const val INSTALLS_PATH = "/v1/installs"
    const val EVENTS_PATH = "/v1/events"

    fun acceptedEvents(response: JSONObject?, expected: Int): Boolean {
        val count = response?.optJSONObject("data")?.opt("accepted")
        return count is Number && count.toDouble() == expected.toDouble()
    }

    fun installAttribution(response: JSONObject?): JSONObject? {
        val data = response?.optJSONObject("data") ?: return null
        val confidence = data.opt("confidence")
        return data.takeIf {
            data.opt("matchType") is String &&
                AttributionResult.MatchType.entries.any { it.value == data.optString("matchType") } &&
                confidence is Number && confidence.toDouble() in 0.0..100.0 &&
                data.opt("isOrganic") is Boolean
        }
    }
}
