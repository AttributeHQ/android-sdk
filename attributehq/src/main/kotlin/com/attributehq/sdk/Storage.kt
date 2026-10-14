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
