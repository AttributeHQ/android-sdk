package com.attributehq.sdk

// Attribution serialization; real SharedPreferences checks are in ReliableDeliveryTest.

import org.junit.Assert.*
import org.junit.Test

class StorageTest {

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
