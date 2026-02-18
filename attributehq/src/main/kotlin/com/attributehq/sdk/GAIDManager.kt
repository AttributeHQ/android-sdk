package com.attributehq.sdk

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.util.concurrent.ExecutorService

/** Google Advertising ID (GAID) manager. */
internal object GAIDManager {

    private const val ZEROED_GAID = "00000000-0000-0000-0000-000000000000"

    /**
     * Fetch GAID on a background thread.
     * Returns null if user has opted out, GAID is zeroed, or Play Services is unavailable.
     */
    fun getGAID(context: Context, executor: ExecutorService, callback: (String?) -> Unit) {
        executor.execute {
            try {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
                if (info.isLimitAdTrackingEnabled) {
                    Logger.debug("GAID: User opted out of ad tracking")
                    callback(null)
                    return@execute
                }

                val gaid = info.id
                if (gaid.isNullOrEmpty() || gaid == ZEROED_GAID) {
                    Logger.debug("GAID: Not available or zeroed")
                    callback(null)
                    return@execute
                }

                Logger.debug("GAID: $gaid")
                callback(gaid)
            } catch (e: Exception) {
                Logger.debug("GAID: Not available (${e.message})")
                callback(null)
            }
        }
    }
}
