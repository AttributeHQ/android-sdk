package com.attributehq.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper with key prefix to avoid collisions.
 * Mirrors Web/iOS SDK's storage with `__ahq_` prefix.
 */
internal class Storage private constructor(context: Context) {
    private val keyPrefix = "__ahq_"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("attributehq_sdk", Context.MODE_PRIVATE)

    // region String

    fun setString(key: String, value: String) {
        prefs.edit().putString(prefixed(key), value).apply()
    }

    fun getString(key: String): String? =
        prefs.getString(prefixed(key), null)

    // endregion

    // region Boolean

    fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(prefixed(key), value).apply()
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(prefixed(key), default)

    // endregion

    // region Long

    fun setLong(key: String, value: Long) {
        prefs.edit().putLong(prefixed(key), value).apply()
    }

    fun getLong(key: String): Long? {
        val prefixedKey = prefixed(key)
        return if (prefs.contains(prefixedKey)) prefs.getLong(prefixedKey, 0L) else null
    }

    // endregion

    // region JSON String (for serialized objects)

    fun setJsonString(key: String, value: String) {
        prefs.edit().putString(prefixed(key), value).apply()
    }

    fun getJsonString(key: String): String? =
        prefs.getString(prefixed(key), null)

    // endregion

    // region Remove

    fun remove(key: String) {
        prefs.edit().remove(prefixed(key)).apply()
    }

    // endregion

    private fun prefixed(key: String): String = "$keyPrefix$key"

    companion object {
        @Volatile
        private var instance: Storage? = null

        fun initialize(context: Context): Storage {
            return instance ?: synchronized(this) {
                instance ?: Storage(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): Storage =
            instance ?: throw AttributeHQError.NotInitialized()
    }
}
