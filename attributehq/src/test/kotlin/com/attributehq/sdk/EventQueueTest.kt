package com.attributehq.sdk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EventQueueTest {

    @Test
    fun `QueuedEvent serializes to JSON correctly`() {
        val event = QueuedEvent(
            eventName = "purchase",
            properties = mapOf("amount" to 1500, "currency" to "NGN"),
            timestamp = 1700000000000L,
            sessionId = "test-session-id"
        )

        val json = event.toJson()
        assertEquals("purchase", json.getString("event_name"))
        assertEquals(1700000000000L, json.getLong("timestamp"))
        assertEquals("test-session-id", json.getString("session_id"))

        val props = json.getJSONObject("properties")
        assertEquals(1500, props.getInt("amount"))
        assertEquals("NGN", props.getString("currency"))
    }

    @Test
    fun `QueuedEvent round-trips through JSON`() {
        val original = QueuedEvent(
            eventName = "signup",
            properties = mapOf("source" to "referral", "premium" to true),
            timestamp = 1700000000000L,
            sessionId = "sess-123"
        )

        val json = original.toJson()
        val restored = QueuedEvent.fromJson(json)

        assertEquals(original.eventName, restored.eventName)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.sessionId, restored.sessionId)
        assertEquals("referral", restored.properties["source"])
        assertEquals(true, restored.properties["premium"])
    }

    @Test
    fun `QueuedEvent list serializes to JSONArray`() {
        val events = listOf(
            QueuedEvent("event1", emptyMap(), 1000L, "s1"),
            QueuedEvent("event2", mapOf("key" to "value"), 2000L, "s1"),
            QueuedEvent("event3", emptyMap(), 3000L, "s2")
        )

        val jsonArray = JSONArray()
        events.forEach { jsonArray.put(it.toJson()) }

        assertEquals(3, jsonArray.length())
        assertEquals("event1", jsonArray.getJSONObject(0).getString("event_name"))
        assertEquals("event2", jsonArray.getJSONObject(1).getString("event_name"))
        assertEquals("event3", jsonArray.getJSONObject(2).getString("event_name"))
    }

    @Test
    fun `QueuedEvent list restores from JSONArray`() {
        val jsonStr = """[
            {"event_name":"e1","properties":{},"timestamp":1000,"session_id":"s1"},
            {"event_name":"e2","properties":{"x":42},"timestamp":2000,"session_id":"s1"}
        ]"""

        val jsonArray = JSONArray(jsonStr)
        val events = mutableListOf<QueuedEvent>()
        for (i in 0 until jsonArray.length()) {
            events.add(QueuedEvent.fromJson(jsonArray.getJSONObject(i)))
        }

        assertEquals(2, events.size)
        assertEquals("e1", events[0].eventName)
        assertEquals("e2", events[1].eventName)
        assertEquals(42, events[1].properties["x"])
    }

    @Test
    fun `EventsBatchRequest serializes correctly`() {
        val events = listOf(
            QueuedEvent("purchase", mapOf("amount" to 500), 1000L, "s1")
        )

        val request = EventsBatchRequest(
            appId = "app-123",
            deviceId = "device-456",
            events = events,
            sdkVersion = "1.0.0"
        )

        val json = request.toJson()
        assertEquals("app-123", json.getString("app_id"))
        assertEquals("device-456", json.getString("device_id"))
        assertEquals("1.0.0", json.getString("sdk_version"))
        assertEquals(1, json.getJSONArray("events").length())
    }

    @Test
    fun `InstallRequestBody serializes with android platform`() {
        val body = InstallRequestBody(
            appId = "app-123",
            deviceId = "device-456",
            sdkVersion = "1.0.0",
            osVersion = "14",
            deviceModel = "Samsung SM-A125F",
            screenResolution = "1080x1920",
            timezone = "Africa/Lagos",
            language = "en",
            gaid = "gaid-789",
            installReferrer = "utm_source=facebook&utm_campaign=test"
        )

        val json = body.toJson()
        assertEquals("android", json.getString("platform"))
        assertEquals("app-123", json.getString("app_id"))
        assertEquals("gaid-789", json.getString("gaid"))
        assertEquals("utm_source=facebook&utm_campaign=test", json.getString("install_referrer"))
    }
}
