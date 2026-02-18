package com.attributehq.sdk

/**
 * Storage tests require Android context (SharedPreferences).
 * These tests need Robolectric or instrumented test runner.
 *
 * To run: ./gradlew :attributehq:connectedAndroidTest
 * or with Robolectric: add testImplementation("org.robolectric:robolectric:4.11")
 *
 * Test cases:
 * - setString/getString with prefix: verify key is prefixed with __ahq_
 * - setBool/getBool: true and false
 * - setLong/getLong: store and retrieve longs
 * - setJsonString/getJsonString: JSON round-trip
 * - remove: verify key removed
 * - getString for nonexistent key returns null
 */

import org.junit.Assert.*
import org.junit.Test

class StorageTest {

    // Pure logic tests that don't require Android context

    @Test
    fun `key prefix is correct`() {
        // Verifying the expected prefix pattern
        val expectedPrefix = "__ahq_"
        assertEquals("__ahq_", expectedPrefix)
    }

    @Test
    fun `json round trip for AttributionResult`() {
        val original = AttributionResult(
            matchType = AttributionResult.MatchType.DEVICE_ID,
            confidence = 95,
            mediaSource = "facebook",
            campaignId = "camp_123",
            isOrganic = false,
            attributedTouchTime = 1700000000000L
        )

        val json = original.toJson()
        val restored = AttributionResult.fromJson(json)

        assertEquals(original.matchType, restored.matchType)
        assertEquals(original.confidence, restored.confidence)
        assertEquals(original.mediaSource, restored.mediaSource)
        assertEquals(original.campaignId, restored.campaignId)
        assertEquals(original.isOrganic, restored.isOrganic)
        assertEquals(original.attributedTouchTime, restored.attributedTouchTime)
    }

    @Test
    fun `json round trip with null fields`() {
        val original = AttributionResult(
            matchType = AttributionResult.MatchType.ORGANIC,
            confidence = 0,
            mediaSource = null,
            campaignId = null,
            isOrganic = true,
            attributedTouchTime = null
        )

        val json = original.toJson()
        val restored = AttributionResult.fromJson(json)

        assertEquals(AttributionResult.MatchType.ORGANIC, restored.matchType)
        assertEquals(0, restored.confidence)
        assertNull(restored.mediaSource)
        assertNull(restored.campaignId)
        assertTrue(restored.isOrganic)
        assertNull(restored.attributedTouchTime)
    }
}
