package com.attributehq.sdk

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale
import java.util.TimeZone

/** Device data for install tracking. */
internal data class DeviceInfoData(
    val screenResolution: String?,
    val timezone: String?,
    val language: String?,
    val osVersion: String?,
    val deviceModel: String?
)

/** Collects device information. */
internal object DeviceInfo {
    fun collect(context: Context): DeviceInfoData {
        return DeviceInfoData(
            screenResolution = getScreenResolution(context),
            timezone = TimeZone.getDefault().id,
            language = Locale.getDefault().language,
            osVersion = Build.VERSION.RELEASE,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }

    @Suppress("DEPRECATION")
    private fun getScreenResolution(context: Context): String? {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val metrics = DisplayMetrics()
            wm?.defaultDisplay?.getRealMetrics(metrics)
            "${metrics.widthPixels}x${metrics.heightPixels}"
        } catch (e: Exception) {
            null
        }
    }

}
