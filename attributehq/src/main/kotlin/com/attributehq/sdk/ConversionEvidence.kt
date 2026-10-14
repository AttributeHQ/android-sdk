package com.attributehq.sdk

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Immutable sandbox snapshot. Contains no advertising ID or server credential. */
class ConversionEvidenceTicket internal constructor(private val serialized: String) {
    val eventId: String get() = JSONObject(serialized).getString("event_id")
    /** Send through your authenticated backend; this is not permission to deliver a postback. */
    fun toJson(): JSONObject = JSONObject(serialized)
}

internal object ConversionEvidenceCollector {
    fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    @Suppress("DEPRECATION")
    fun capture(context: Context, tracking: Boolean, sharing: AdvertisingSharing): JSONObject {
        val packageName = context.packageName
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) && packageName.length <= 255)
        require(Build.VERSION.RELEASE.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){0,2}")))
        val info = context.packageManager.getPackageInfo(packageName, 0)
        return JSONObject().apply {
            put("platform", "android")
            put("package_name", packageName)
            put("os_version", Build.VERSION.RELEASE)
            put("observed_at", timestamp())
            put("evidence_id", UUID.randomUUID().toString())
            put("collection_method", "attributehq_sdk")
            put("application_tracking_enabled", tracking)
            put("sharing", sharing.toJson())
            fun optional(key: String, value: String?) {
                value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 255 && it.none { c -> c.code < 32 } }?.let { put(key, it) }
            }
            optional("app_version", info.versionName)
            optional("app_build", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString())
            optional("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
}
