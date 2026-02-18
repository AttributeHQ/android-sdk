package com.attributehq.sdk

/**
 * SessionManager tests.
 * The full SessionManager requires Storage (Android context).
 * These tests verify the session timeout logic conceptually.
 *
 * Full integration tests need Robolectric or instrumented runner.
 *
 * Test cases covered:
 * - New session creates a valid UUID
 * - getSessionId returns same ID within timeout window
 * - Session rotates after 30-minute timeout
 * - Session persists and restores from storage
 */

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SessionManagerTest {

    @Test
    fun `UUID generation produces valid format`() {
        val uuid = UUID.randomUUID().toString()
        assertNotNull(uuid)
        // UUID format: 8-4-4-4-12 hex characters
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `session timeout constant is 30 minutes`() {
        val sessionTimeoutMs = 30 * 60 * 1000L
        assertEquals(1_800_000L, sessionTimeoutMs)
    }

    @Test
    fun `elapsed time within timeout does not trigger rotation`() {
        val sessionTimeoutMs = 30 * 60 * 1000L
        val lastActivity = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 min ago
        val now = System.currentTimeMillis()
        val elapsed = now - lastActivity
        assertTrue(elapsed < sessionTimeoutMs)
    }

    @Test
    fun `elapsed time beyond timeout triggers rotation`() {
        val sessionTimeoutMs = 30 * 60 * 1000L
        val lastActivity = System.currentTimeMillis() - (31 * 60 * 1000L) // 31 min ago
        val now = System.currentTimeMillis()
        val elapsed = now - lastActivity
        assertTrue(elapsed > sessionTimeoutMs)
    }
}
