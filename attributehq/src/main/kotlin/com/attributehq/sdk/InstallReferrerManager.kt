package com.attributehq.sdk

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * Google Play Install Referrer API.
 * Provides deterministic attribution — server matcher uses install_referrer
 * as FIRST priority in the waterfall (highest confidence).
 */
internal object InstallReferrerManager {

    /**
     * Fetch the install referrer string from Play Store.
     * Returns null on error (no Play Store, library unavailable, etc.)
     */
    fun getInstallReferrer(context: Context, callback: (String?) -> Unit) {
        try {
            val client = InstallReferrerClient.newBuilder(context).build()

            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try {
                                val details = client.installReferrer
                                val referrer = details.installReferrer
                                Logger.debug("Install referrer: $referrer")
                                callback(referrer)
                            } catch (e: Exception) {
                                Logger.debug("Install referrer: Failed to get details (${e.message})")
                                callback(null)
                            } finally {
                                try { client.endConnection() } catch (_: Exception) {}
                            }
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                            Logger.debug("Install referrer: Not supported on this device")
                            callback(null)
                        }
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            Logger.debug("Install referrer: Service unavailable")
                            callback(null)
                        }
                        else -> {
                            Logger.debug("Install referrer: Unknown response code $responseCode")
                            callback(null)
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    Logger.debug("Install referrer: Service disconnected")
                }
            })
        } catch (e: Exception) {
            Logger.debug("Install referrer: Not available (${e.message})")
            callback(null)
        }
    }
}
