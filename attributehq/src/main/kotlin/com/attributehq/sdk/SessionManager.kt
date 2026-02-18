package com.attributehq.sdk

import java.util.UUID

/**
 * Manages user sessions with a 30-minute inactivity timeout.
 * Mirrors the iOS SDK's SessionManager exactly.
 */
internal class SessionManager(private val storage: Storage) {
    private var sessionId: String
    private var lastActivity: Long // epoch millis

    private val sessionTimeoutMs: Long = 30 * 60 * 1000L // 30 minutes

    init {
        val storedId = storage.getString("session_id")
        val storedStart = storage.getLong("session_start")

        if (storedId != null && storedStart != null) {
            val elapsed = System.currentTimeMillis() - storedStart
            if (elapsed < sessionTimeoutMs) {
                sessionId = storedId
                lastActivity = storedStart
                Logger.debug("Session resumed: $sessionId")
            } else {
                sessionId = UUID.randomUUID().toString()
                lastActivity = System.currentTimeMillis()
                persist()
                Logger.debug("New session (previous expired): $sessionId")
            }
        } else {
            sessionId = UUID.randomUUID().toString()
            lastActivity = System.currentTimeMillis()
            persist()
            Logger.debug("New session: $sessionId")
        }
    }

    /** Get current session ID, rotating if timeout has elapsed. */
    fun getSessionId(): String {
        val now = System.currentTimeMillis()
        if (now - lastActivity > sessionTimeoutMs) {
            sessionId = UUID.randomUUID().toString()
            Logger.debug("Session rotated: $sessionId")
        }

        lastActivity = now
        persist()
        return sessionId
    }

    private fun persist() {
        storage.setString("session_id", sessionId)
        storage.setLong("session_start", lastActivity)
    }
}
