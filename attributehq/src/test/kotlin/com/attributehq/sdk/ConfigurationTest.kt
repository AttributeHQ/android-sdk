package com.attributehq.sdk

import org.junit.Assert.*
import org.junit.Test

class ConfigurationTest {

    @Test
    fun `valid config resolves successfully`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test123", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        val resolved = ResolvedConfiguration.resolve(config)

        assertEquals("ak_test123", resolved.apiKey)
        assertEquals("000f810a-d493-42d5-bd7d-64de2d0e7188", resolved.appId)
        assertEquals("https://api.attributehq.com", resolved.baseUrl)
        assertEquals(10, resolved.batchSize)
        assertEquals(5L, resolved.flushIntervalSeconds)
        assertEquals(100, resolved.maxQueueSize)
        assertFalse(resolved.enableDebugLogging)
        assertEquals(30_000L, resolved.requestTimeoutMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty apiKey throws`() {
        val config = AttributeHQConfiguration(apiKey = "", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        ResolvedConfiguration.resolve(config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank apiKey throws`() {
        val config = AttributeHQConfiguration(apiKey = "   ", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        ResolvedConfiguration.resolve(config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty appId throws`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test123", appId = "")
        ResolvedConfiguration.resolve(config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed app ID is rejected before storage is opened`() {
        ResolvedConfiguration.resolve(AttributeHQConfiguration("ak_test", "../other-app"))
    }

    @Test
    fun `batchSize clamped to minimum 1`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        config.batchSize = -5
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(1, resolved.batchSize)
    }

    @Test
    fun `batchSize clamped to maximum 100`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        config.batchSize = 500
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(100, resolved.batchSize)
    }

    @Test
    fun `flushInterval clamped to minimum 1`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        config.flushIntervalSeconds = 0
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(1L, resolved.flushIntervalSeconds)
    }

    @Test
    fun `requestTimeout clamped to minimum 5000`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        config.requestTimeoutMs = 100
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(5_000L, resolved.requestTimeoutMs)
    }

    @Test
    fun `maxQueueSize clamped to minimum 10`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "000f810a-d493-42d5-bd7d-64de2d0e7188")
        config.maxQueueSize = 3
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(10, resolved.maxQueueSize)
    }
}
