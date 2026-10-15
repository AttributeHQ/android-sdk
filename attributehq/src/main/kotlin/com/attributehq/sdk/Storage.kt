package com.attributehq.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper with key prefix to avoid collisions.
 * Mirrors Web/iOS SDK's storage with `__ahq_` prefix.
 */
internal class Storage internal constructor(context: Context, private val appId: String) {
    private val keyPrefix = "__ahq_"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("attributehq_sdk_v2_$appId", Context.MODE_PRIVATE)

    // region String

    fun setString(key: String, value: String): Boolean =
        prefs.edit().putString(prefixed(key), value).commit()

    fun getString(key: String): String? =
        prefs.getString(prefixed(key), null)

    // endregion

    // region Boolean

    fun setBool(key: String, value: Boolean): Boolean =
        prefs.edit().putBoolean(prefixed(key), value).commit()

    fun getBool(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(prefixed(key), default)

    // endregion

    /** Freeze the first collected app open before any network/referrer wait. */
    fun firstOpenTime(now: Long = System.currentTimeMillis()): Long? {
        getLong("first_open_time_ms")?.let { return it }
        return now.takeIf { it > 0 && setLong("first_open_time_ms", it) }
    }

    // region Long

    fun setLong(key: String, value: Long): Boolean =
        prefs.edit().putLong(prefixed(key), value).commit()

    fun getLong(key: String): Long? {
        val prefixedKey = prefixed(key)
        return if (prefs.contains(prefixedKey)) prefs.getLong(prefixedKey, 0L) else null
    }

    // endregion

    // region JSON String (for serialized objects)

    fun setJsonString(key: String, value: String): Boolean =
        prefs.edit().putString(prefixed(key), value).commit()

    fun getJsonString(key: String): String? =
        prefs.getString(prefixed(key), null)

    // endregion

    // region Remove

    fun remove(key: String): Boolean =
        prefs.edit().remove(prefixed(key)).commit()

    // endregion

    private fun prefixed(key: String): String = "$keyPrefix$key"

    /** Persist withdrawal and purge pending telemetry atomically. Keep installation identity. */
    fun setCollectionEnabled(enabled: Boolean): Boolean {
        val edit = prefs.edit().putBoolean(prefixed("collection_enabled"), enabled)
        if (!enabled) {
            edit.remove(prefixed("event_queue")).remove(prefixed("user_id"))
                .remove(prefixed("session_id")).remove(prefixed("session_start"))
            // Keep monotonic ordering while purging all retained diagnostic telemetry.
            // Corrupt state must remain corrupt/blocked rather than reset its sequence.
            try {
                prefs.getString(prefixed("evidence_state"), null)?.let { raw ->
                    val old = org.json.JSONObject(raw)
                    require(old.get("sequence") is Int || old.get("sequence") is Long)
                    val sequence = old.getLong("sequence")
                    require(sequence in 0..9_007_199_254_740_991L)
                    edit.putString(prefixed("evidence_state"), org.json.JSONObject().apply {
                        put("sequence", sequence); put("pending", org.json.JSONObject.NULL)
                        put("events", org.json.JSONArray())
                    }.toString())
                }
            } catch (_: Exception) { /* Leave unknown sequence untouched, failing closed. */ }
        }
        return edit.commit()
    }

    companion object {
        @Volatile
        private var instance: Storage? = null

        fun initialize(context: Context, appId: String): Storage {
            return synchronized(this) {
                instance?.takeIf { it.appId == appId }
                    ?: Storage(context.applicationContext, appId).also { instance = it }
            }
        }

        fun getInstance(): Storage =
            instance ?: throw AttributeHQError.NotInitialized()
    }
}
