package com.attributehq.sdk

import org.junit.Assert.*
import org.junit.Test

class ConfigurationTest {

    @Test
    fun `valid config resolves successfully`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test123", appId = "app-uuid-123")
        val resolved = ResolvedConfiguration.resolve(config)

        assertEquals("ak_test123", resolved.apiKey)
        assertEquals("app-uuid-123", resolved.appId)
        assertEquals("https://attributehq.com", resolved.baseUrl)
        assertEquals(10, resolved.batchSize)
        assertEquals(5L, resolved.flushIntervalSeconds)
        assertEquals(100, resolved.maxQueueSize)
        assertFalse(resolved.enableDebugLogging)
        assertEquals(30_000L, resolved.requestTimeoutMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty apiKey throws`() {
        val config = AttributeHQConfiguration(apiKey = "", appId = "app-uuid-123")
        ResolvedConfiguration.resolve(config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank apiKey throws`() {
        val config = AttributeHQConfiguration(apiKey = "   ", appId = "app-uuid-123")
        ResolvedConfiguration.resolve(config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty appId throws`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test123", appId = "")
        ResolvedConfiguration.resolve(config)
    }

    @Test
    fun `batchSize clamped to minimum 1`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.batchSize = -5
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(1, resolved.batchSize)
    }

    @Test
    fun `batchSize clamped to maximum 100`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.batchSize = 500
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(100, resolved.batchSize)
    }

    @Test
    fun `flushInterval clamped to minimum 1`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.flushIntervalSeconds = 0
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(1L, resolved.flushIntervalSeconds)
    }

    @Test
    fun `baseUrl trailing slash stripped`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.baseUrl = "https://api.example.com///"
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals("https://api.example.com", resolved.baseUrl)
    }

    @Test
    fun `requestTimeout clamped to minimum 5000`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.requestTimeoutMs = 100
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(5_000L, resolved.requestTimeoutMs)
    }

    @Test
    fun `maxQueueSize clamped to minimum 10`() {
        val config = AttributeHQConfiguration(apiKey = "ak_test", appId = "app-uuid")
        config.maxQueueSize = 3
        val resolved = ResolvedConfiguration.resolve(config)
        assertEquals(10, resolved.maxQueueSize)
    }
}
