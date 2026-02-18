package com.attributehq.sdk

import android.util.Log

/** Internal logger with [AttributeHQ] tag. */
internal object Logger {
    private const val TAG = "AttributeHQ"
    private var enabled = false

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Debug log — only printed when debug logging is enabled. */
    fun debug(message: String) {
        if (enabled) {
            Log.d(TAG, message)
        }
    }

    /** Warning log — always printed. */
    fun warn(message: String) {
        Log.w(TAG, message)
    }

    /** Error log — always printed. */
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }
}
