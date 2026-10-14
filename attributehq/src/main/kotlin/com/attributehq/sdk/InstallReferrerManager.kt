package com.attributehq.sdk

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Fetch Play's referrer once, with a bounded wait even when the service disconnects. */
internal object InstallReferrerManager {
    fun getInstallReferrer(context: Context, scheduler: ScheduledExecutorService, callback: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
        var client: InstallReferrerClient? = null
        val finish: (String?) -> Unit = { value ->
            if (finished.compareAndSet(false, true)) {
                try { client?.endConnection() } catch (_: Exception) { }
                callback(value)
            }
        }
        val timeout = scheduler.schedule({ finish(null) }, 5, TimeUnit.SECONDS)
        try {
            val connection = InstallReferrerClient.newBuilder(context).build()
            client = connection
            connection.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        try { connection.installReferrer.installReferrer } catch (_: Exception) { null }
                    } else null
                    finish(referrer)
                    timeout.cancel(false)
                }
                override fun onInstallReferrerServiceDisconnected() {
                    finish(null)
                    timeout.cancel(false)
                }
            })
        } catch (_: Exception) {
            finish(null)
            timeout.cancel(false)
        }
    }
}
